package asyncServlets;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.util.concurrent.*;

@WebServlet(name = "NonBlocking", urlPatterns = "/nonBlocking", asyncSupported = true)
public class NonBlockingServlet extends HttpServlet {
    private static BlockingQueue<AsyncContext> jobs = new ArrayBlockingQueue<>(100);
    private ExecutorService executorService;
    private static CloseableHttpAsyncClient httpAsyncClient;

    @Override
    public void init() throws ServletException {
        int numberOfThreads = 2;
        httpAsyncClient = HttpAsyncClients.createDefault();
        executorService = new ThreadPoolExecutor(numberOfThreads, numberOfThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("NIO-Job-Worker-Thread");
                        return thread;
                    }
                }) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                t.printStackTrace();
            }
        };
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(new NonBlockingServlet.JobsExecutor());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final AsyncContext asyncContext = req.startAsync();
        submitJob(asyncContext);
    }

    @Override
    public void destroy() {
        executorService.shutdownNow();
        try {
            httpAsyncClient.close();
        }
        catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static void submitJob(AsyncContext asyncContext) {
        jobs.add(asyncContext);
    }

    private static AsyncContext takeJobFromQueue() throws InterruptedException {
        return jobs.poll(1, TimeUnit.SECONDS);
    }


    static class ResponseConsumer extends AsyncCharConsumer<Boolean> {

        private PrintWriter out;

        ResponseConsumer(PrintWriter out) {
            this.out = out;
        }

        @Override
        protected void onCharReceived(CharBuffer charBuffer, IOControl ioControl) throws IOException {
            while (charBuffer.hasRemaining()) {
                out.print(charBuffer.get());
            }
        }

        @Override
        protected void onResponseReceived(HttpResponse httpResponse) throws HttpException, IOException {

        }

        @Override
        protected Boolean buildResult(HttpContext httpContext) throws Exception {
            return Boolean.TRUE;
        }
    }

    private static void runJob(AsyncContext asyncContext) throws IOException {
        String downloadURL = asyncContext.getRequest().getParameter("url");
        HttpAsyncRequestProducer httpAsyncRequestProducer = HttpAsyncMethods.createGet(downloadURL);
        PrintWriter out = asyncContext.getResponse().getWriter();
        try {
            httpAsyncClient.start();
            Future<Boolean> future = httpAsyncClient.execute(httpAsyncRequestProducer,
                    new ResponseConsumer(out), null);

            Boolean result = future.get();
            if (result != null && result.booleanValue()) {
                System.out.println("Request successfully executed");
            } else {
                System.out.println("Request failed");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            asyncContext.complete();
        }
    }

    static class JobsExecutor implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    AsyncContext asyncContext = takeJobFromQueue();
                    if (asyncContext != null) {
                        runJob(asyncContext);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}

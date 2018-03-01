package asyncServlets;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.*;

@WebServlet(name = "AsyncURLDownload", urlPatterns = "/nonBlocking", asyncSupported = true, loadOnStartup = 1)
public class AsyncURLDownloadServlet extends HttpServlet {
    private static final Logger logger = LogManager.getLogger();

    private static BlockingQueue<AsyncContext> jobs = new ArrayBlockingQueue<>(100);

    private ExecutorService executorService;

    @Override
    public void init() throws ServletException {
        logger.info("Hit URL Download Servlet Init()");
        int numberOfThreads = 2;
        executorService = new ThreadPoolExecutor(numberOfThreads, numberOfThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("Job-Worker-Thread");
                return thread;
            }
        }) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                System.out.println("After execute");
                t.printStackTrace();
            }
        };
        for (int i=0 ;i< numberOfThreads; i++) {
            executorService.submit(new JobsExecutor());
        }
        //logger.info("Exit URL Download Servlet Init()");
    }

    @Override
    public void destroy() {
        //logger.info("Hit URL Download Servlet destroy()");
        executorService.shutdownNow();   // Need to wait for completion before force shut down.
        logger.info("Executor Service Shut down status: {}", executorService.isShutdown());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final AsyncContext asyncContext = req.startAsync();
        submitJob(asyncContext);
        logger.info("Exiting URL Download Servlet doGet() after submitJob()");
    }

    private static void submitJob(AsyncContext asyncContext) {
        logger.info("Hit submitJob() in URL Download Servlet");
        jobs.add(asyncContext);
    }

    private static void runJob(AsyncContext asyncContext) throws IOException{
        //logger.info("Hit runJob() in URL Download Servlet");
        String downloadURL = asyncContext.getRequest().getParameter("url");
        CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(downloadURL);
        HttpResponse httpResponse;
        BufferedReader br = null;
        PrintWriter out = asyncContext.getResponse().getWriter();
        try {
            //logger.info("Inside URL Download");
            httpResponse = closeableHttpClient.execute(httpGet);
            //logger.info("Got httpResponse");
            int responseStatus = httpResponse.getStatusLine().getStatusCode();
            //logger.info("Got responseStatus");
            br = new BufferedReader(new InputStreamReader(httpResponse.getEntity()
                    .getContent()));
            if (responseStatus >= 200 && responseStatus < 300) {
                String line;
                //Not thread safe????.
                while ((line = br.readLine()) != null) {
                    //logger.debug(line);
                    out.println(line);
                }
                //out.println("200 Response Code OK");
            } else {
                out.println("Unexpected Response Code: " + responseStatus);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            closeableHttpClient.close();
            out.flush();
            asyncContext.complete();
            //logger.info("Exiting runJob() method");
        }
    }

    private static AsyncContext takeJobFromQueue() throws InterruptedException{
        return jobs.poll(1, TimeUnit.SECONDS);
    }

     static class JobsExecutor implements Runnable {
        @Override
        public void run() {
            //logger.info("Hit JobsExecutor thread in URL Download servlet");
            while (true) {
                try {
                    AsyncContext asyncContext = takeJobFromQueue();
                    if (asyncContext != null) {
                        logger.info("Took job from Queue");
                        runJob(asyncContext);
                    }
                } catch (InterruptedException e) {
                    logger.info("Interrupted");
                    e.printStackTrace();
                    break;
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
}

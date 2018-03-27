package asyncServlets;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.CharBuffer;

@WebServlet(name = "NonBlocking", urlPatterns = "/nonBlocking", asyncSupported = true)
public class NonBlockingServlet extends HttpServlet {
    private static CloseableHttpAsyncClient httpAsyncClient;
    private static Logger logger = LogManager.getLogger();
    // Create I/O reactor configuration
    private IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
            .setIoThreadCount(2)
            .setConnectTimeout(200000)
            .setSoTimeout(200000)
            .build();


    @Override
    public void init() throws ServletException {
        httpAsyncClient = HttpAsyncClients.custom().setDefaultIOReactorConfig(ioReactorConfig).build();
        httpAsyncClient.start();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(900000000);
        runJob(asyncContext);
    }

    @Override
    public void destroy() {
        try {
            httpAsyncClient.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static void runJob(AsyncContext asyncContext) throws IOException {
        if (asyncContext == null) {
            throw new IllegalStateException();
        }
        String downloadURL = asyncContext.getRequest().getParameter("url");
        HttpAsyncRequestProducer httpAsyncRequestProducer = HttpAsyncMethods.createGet(downloadURL);
        PrintWriter out = asyncContext.getResponse().getWriter();
        try {
            httpAsyncClient.execute(httpAsyncRequestProducer,
                    new ResponseConsumer(out), new FutureCallback<Boolean>() {
                        @Override
                        public void completed(Boolean result) {
                            logger.info(Thread.currentThread().getName() + " Request Successful");
                            asyncContext.complete();
                        }

                        @Override
                        public void failed(Exception ex) {
                            logger.info(Thread.currentThread().getName() + " Request Failed: {}", downloadURL);
                            asyncContext.complete();
                        }

                        @Override
                        public void cancelled() {
                            logger.warn("Operation \"{}\" cancelled", downloadURL);
                            asyncContext.complete();
                        }

                    });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
                out.flush();
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


}

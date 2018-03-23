package servlets;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

@WebServlet(name = "blockingServlet", urlPatterns = "/blocking")
public class SampleBlockingServlet extends HttpServlet {
    private static void runJob(String downloadURL, HttpServletResponse response) throws IOException {
        CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
        HttpGet httpget = new HttpGet(downloadURL);
        HttpResponse httpResponse;
        BufferedReader br = null;
        try {
            httpResponse = closeableHttpClient.execute(httpget);
            int responseStatus = httpResponse.getStatusLine().getStatusCode();
            if (responseStatus >= 200 && responseStatus < 300) {
                br = new BufferedReader(new InputStreamReader(httpResponse.getEntity()
                        .getContent()));
                String line;
                while ((line = br.readLine()) != null) {
                    response.getWriter().println(line);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException();
                    }
                }
            } else {
                System.out.println("Unexpected Response Code: " + responseStatus);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new IOException();
                }
            }
            closeableHttpClient.close();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        runJob(req.getParameter("url"), resp);
        PrintWriter out = resp.getWriter();
        out.println("Returned after receiving data OK");

    }
}

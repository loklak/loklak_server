package org.loklak.api.search;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.http.RemoteAccess;
import org.loklak.server.Query;


public class TweetCheckServlet extends HttpServlet{
    private static final long serialVersionUID = 4568123491056781234L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Query query = RemoteAccess.evaluate(request);

        // manage DoS
        if (query.isDoS_blackout()) {response.sendError(503, "your request frequency is too high"); return;}

        String callback = query.get("callback", "");
        boolean jsonp = callback != null && callback.length() > 0;
        boolean minified = query.get("minified", false);

        String tweetQuery = query.get("tweet", "");

        response.setCharacterEncoding("UTF-8");

        PrintWriter sos = response.getWriter();

        JSONObject result = new JSONObject(true);

        if (tweetQuery != null) {
            String tweetId = parseTweetId(tweetQuery);
            boolean exists = DAO.existMessage(tweetId);
            if (exists) {
                JSONObject post = DAO.searchLocalTweetById(tweetId);
                result.put("tweet", post);
            }
        }

        query.setResponse(response, jsonp ? "application/javascript" : "application/json");

        if (jsonp) sos.print(callback + "(");
        sos.print(result.toString(minified ? 0 : 2));
        if (jsonp) sos.println(");");
        sos.println();
        sos.flush();
        sos.close();
    }

    protected String parseTweetId(String str) {
        if (str.contains("http")) {
            String[] split = str.split("/");
            return split[split.length - 1];
        }
        return str;
    }
}

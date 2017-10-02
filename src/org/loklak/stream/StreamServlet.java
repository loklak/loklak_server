package org.loklak.stream;

import org.eclipse.jetty.servlets.EventSource;
import org.eclipse.jetty.servlets.EventSourceServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class StreamServlet extends EventSourceServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1224323810947361163L;

    @Override
    protected EventSource newEventSource(HttpServletRequest request) {
        String channel = request.getParameter("channel");
        if (channel == null) {
            return null;
        }
        if (channel.isEmpty()) {
            return null;
        }
        return new MqttEventSource(channel);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        super.doGet(request, response);
    }

}

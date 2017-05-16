/**
 *  SettingsServlet
 *  Copyright 13.06.2015 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.api.admin;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.http.RemoteAccess;
import org.loklak.server.Query;

/**
 * submit all setting values where the settings key starts with "client."
 */
public class SettingsServlet extends HttpServlet {

    private static final long serialVersionUID = 1839868262296635665L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Query post = RemoteAccess.evaluate(request);

        if (!validateRequest(response, post))
            return;

        JSONObject json = new JSONObject(true);

        for (String key: DAO.getConfigKeys()) {
            json.put(key, DAO.getConfig(key, ""));
        }

        sendJSONData(response, post, json);
    }

    private boolean validateRequest(HttpServletResponse response, Query post) throws IOException {
        if (post.isDoS_blackout()) {
            response.sendError(503, "your request frequency is too high");
            return false;
        }

        return true;
    }

    private void sendJSONData(HttpServletResponse response, Query post, JSONObject json) throws IOException {
        post.setResponse(response, "application/javascript");
        response.setCharacterEncoding("UTF-8");

        PrintWriter sos = response.getWriter();
        sos.println(json.toString(2));
    }
}

/**
 *  AssetServlet
 *  Copyright 12.06.2015 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.api.cms;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.loklak.data.DAO;
import org.loklak.http.RemoteAccess;
import org.loklak.server.Query;
import org.loklak.tools.UTF8;

public class AssetServlet extends HttpServlet {

    private static final long serialVersionUID = -9112326722297824443L;

    @Override
    protected void doGet(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {
        Query post = RemoteAccess.evaluate(request);
        if (post.isDoS_blackout()) {  // DoS protection
            response.sendError(503, "your request frequency is too high");
            return;
        }

        // parse arguments
        String screenName = post.get("screen_name", "");
        if (screenName.length() == 0) {
            response.sendError(503, "a screen_name must be submitted");
            return;
        }
        String idStr = post.get("id_str", "");
        if (idStr.length() == 0) {
            response.sendError(503, "an id_str must be submitted");
            return;
        }
        String file = post.get("file", "");
        File assetFile = DAO.getAssetFile(screenName, idStr, file);
        if (!assetFile.exists()) {
            response.sendError(503, "asset does not exist");
            return;
        }

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] b = new byte[2048];
        InputStream is = new BufferedInputStream(new FileInputStream(assetFile));
        int c;
        try {
            while ((c = is.read(b)) >  0) {
                data.write(b, 0, c);
            }
        } catch (IOException e) {
            ;
        }

        if (file.endsWith(".png") || (file.length() == 0
            && request.getServletPath().endsWith(".png"))) {
            post.setResponse(response, "image/png");
        } else if (file.endsWith(".gif") || (file.length() == 0
            && request.getServletPath().endsWith(".gif"))) {
            post.setResponse(response, "image/gif");
        } else if (file.endsWith(".jpg") || file.endsWith(".jpeg")
            || (file.length() == 0 && request.getServletPath().endsWith(".jpg"))) {
            post.setResponse(response, "image/jpeg");
        } else {
            post.setResponse(response, "application/octet-stream");
        }

        ServletOutputStream sos = response.getOutputStream();
        sos.write(data.toByteArray());
    }

    // http://localhost:9000/api/asset?screen_name=loklak_app&id_str=123&file=image.jpg&data=

    @Override
    protected void doPost(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {
        Query post = RemoteAccess.evaluate(request);

        // manage DoS
        if (post.isDoS_blackout()) {
            response.sendError(503, "your request frequency is too high");
            return;
        }

        Map<String, byte[]> m = RemoteAccess.getPostMap(request);
        byte[] data = m.get("data");
        if (data == null || data.length == 0) {
            response.sendError(400, "your request does not contain a data object.");
            return;
        }
        byte[] screenNameB = m.get("screen_name");
        if (screenNameB == null || screenNameB.length == 0) {
            response.sendError(400, "your request does not contain a screen_name.");
            return;
        }
        byte[] idStrB = m.get("id_str");
        if (idStrB == null || idStrB.length == 0) {
            response.sendError(400, "your request does not contain a id_str.");
            return;
        }
        byte[] fileB = m.get("file");
        if (fileB == null || fileB.length == 0) {
            response.sendError(400, "your request does not contain a file name.");
            return;
        }
        String screenName = UTF8.String(screenNameB);
        String idStr = UTF8.String(idStrB);
        String file = UTF8.String(fileB);

        File f = DAO.getAssetFile(screenName, idStr, file);
        f.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(data);
        fos.close();

        post.setResponse(response, "application/octet-stream");
        post.finalize();
    }

}

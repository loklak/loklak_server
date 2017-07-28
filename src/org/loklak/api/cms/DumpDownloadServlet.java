/**
 *  DumpDownloadServlet
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.loklak.data.DAO;
import org.loklak.http.RemoteAccess;
import org.loklak.server.FileHandler;
import org.loklak.server.Query;

public class DumpDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 2839106194602799989L;

    @Override
    protected void doPost(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {
        if (!DAO.writeDump) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Dump generation is disabled on this peer");
        }
        final Query post = RemoteAccess.evaluate(request);
        String path = request.getPathInfo();
        final long now = System.currentTimeMillis();

        int limitedCount = (int) DAO.getConfig("download.limited.count", (long) Integer.MAX_VALUE);
        String limitedMessage = DAO.getConfig("download.limited.message", "");

        if (path.length() <= 1) {
            // send directory as html

            FileHandler.setCaching(response, 60);
            response.setContentType("text/html");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);

            OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw);
            writer.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
            writer.write("<html>\n");
            writer.write(" <head>\n");
            writer.write("  <title>Index of /dump</title>\n");
            writer.write(" </head>\n");
            writer.write(" <body>\n");
            writer.write("<h1>Index of /dump</h1>\n");
            writer.write("<pre>      Name \n");

            for (File dump: DAO.getTweetOwnDumps(limitedCount)) {
                String name = dump.getName();
                String space = "";
                for (int i = name.length(); i < 36; i++) {
                    space += " ";
                }
                long length = dump.length();
                String size = length < 1024
                    ? Long.toString(length) : length < 1024 * 1024
                        ? Long.toString(length / 1024) + "K"
                            : Long.toString(length / 1024 / 1024) + "M";
                while (size.length() < 5) {
                    size = " " + size;
                }
                int d = name.lastIndexOf('.');
                if (d < 0) {
                    writer.write("[   ]");
                } else {
                    String ext = name.substring(d + 1);
                    if (ext.length() > 3) {
                        ext = ext.substring(0, 3);
                    }
                    writer.write('[');
                    writer.write(ext);
                    for (int i = 0; i < 3 - ext.length(); i++) {
                        writer.write(' ');
                    }
                    writer.write(']');
                }
                writer.write(" <a href=\"" + name + "\">" + name + "</a>" + space + new Date(dump.lastModified()).toString() + "  " + size + "\n");
            }
            if (limitedCount != Integer.MAX_VALUE) {
                writer.write(limitedMessage + "\n");
            }
            writer.write("<hr></pre>\n");
            writer.write("<address>this is the download directory for dumps of the message index</address>\n");
            writer.write("<address>- import these dumps by placing them into your data/dump/import/ directory</address>\n");
            writer.write("<address>- imported dumps will be moved to data/dump/imported/</address>\n");
            writer.write("</body></html>\n");
            writer.flush();
            DAO.log(path);
            return;
        }

        if (limitedCount == 0) {
            response.sendError(404, request.getContextPath() + " " + limitedMessage);
            return;
        }

        // download a dump file
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Collection<File> ownDumps = DAO.getTweetOwnDumps(Integer.MAX_VALUE);
        File dump = ownDumps.size() == 0 ? null : new File(ownDumps.iterator().next().getParentFile(), path);
        if (dump == null || !dump.exists()) {
            response.sendError(404, request.getContextPath() + " not available");
        }

        response.setDateHeader("Last-Modified", dump.lastModified());
        response.setDateHeader("Expires", Math.max(now + ((now - dump.lastModified()) / 2), now + 600000));
        response.setContentLengthLong(dump.length());
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        try {
            InputStream fis = new BufferedInputStream(new FileInputStream(dump));
            byte[] buffer = new byte[65536];
            int c;
            while ((c = fis.read(buffer)) > 0) {
                response.getOutputStream().write(buffer, 0, c);
            }
            fis.close();
        } catch (Throwable e) {
            DAO.severe(e);
        }
        post.finalize();
    }
}

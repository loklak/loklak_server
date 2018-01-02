/**
 *  LogService
 *  Copyright 02.01.2018 by Michael Peter Christen, @0rb1t3r
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.loklak.data.DAO;
import org.loklak.http.RemoteAccess;
import org.loklak.server.FileHandler;
import org.loklak.server.Query;
import org.loklak.tools.UTF8;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * The Log Service
 * call http://127.0.0.1:9000/api/log.txt
 */
public class LogServlet extends HttpServlet {

    private static final long serialVersionUID = -7095346222464124199L;
    public static final String NAME = "log";
    

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
    
        Query post = RemoteAccess.evaluate(request);
        int length = post.get("length", 10000);
        final StringBuilder buffer = new StringBuilder(1000);

        File logfile = new File(DAO.data_dir, "loklak.log");
        if (logfile.exists()) {
            try {
                RandomAccessFile raf = new RandomAccessFile(logfile, "r");
                raf.seek(Math.max(0, raf.length() - length));
                String line;
                while ((line = raf.readLine()) != null) {
                    bufferappend(buffer, line);
                }
                raf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            bufferappend(buffer, "");
        } else {
            bufferappend(buffer, "file " + logfile.getAbsolutePath() + " does not exist");
        }

        FileHandler.setCaching(response, 10);
        post.setResponse(response, "text/plain");
        response.getOutputStream().write(UTF8.getBytes(buffer.toString()));
        post.finalize();
    }

    private static void bufferappend(final StringBuilder buffer, final String a) {
        buffer.append(a);
        buffer.append('\n');
    }

}

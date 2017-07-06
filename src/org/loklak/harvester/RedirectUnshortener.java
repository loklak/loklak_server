/**
 *  RedirectUnshortener
 *  Copyright 08.03.2015 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.harvester;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;

import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;

public class RedirectUnshortener {

    private final static String[] workingHosts = new String[] {
        "bbc.in",
        "fb.me",
        "wp.me",
        "j.mp",
        "t.co",
        "bit.ly",
        "ift.tt",
        "goo.gl",
        "tinyurl.com",
        "ow.ly",
        "tiny.cc",
        "bit.do",
        "amzn.to",
        "tmblr.co",
        "tumblr.com",
        "www.tumblr.com",
        "abo.io",
        "gdta.st",
        "wpo.st",
        "buff.ly",
        "reut.rs",
        "dlvr.it",
        "flip.it",
        "lnkd.in"
    };

    private final static String[] untestedHosts = new String[] {
        "is.gd",
        "ta.gd",
        "cli.gs",
        "sURL.co.uk",
        "y.ahoo.it",
        "yi.tl",
        "su.pr",
        "Fwd4.Me",
        "budurl.com",
        "snipurl.com",
        "igg.me",
        "twiza.ru"
    };

    public static String unShorten(String urlstring) {
        //long start = System.currentTimeMillis();
        try {
            int termination = 10; // loop for recursively shortened urls
            while (termination-- > 0 && isApplicable(urlstring)) {
                String unshortened = ClientConnection.getRedirect(urlstring);
                if (unshortened.equals(urlstring)) {
                    return urlstring;
                }
                urlstring = unshortened; // recursive apply unshortener because some unshortener are applied several times
            }
            //DAO.log("UNSHORTENED in " + (System.currentTimeMillis() - start) + " milliseconds: " + urlstring);
            return urlstring;
        } catch (IOException e) {
            DAO.log("UNSHORTEN failed for " + urlstring + " : " + e);
            return urlstring;
        }
    }

    private static boolean isApplicable(String urlstring) {
        String s = urlstring.toLowerCase();
        if (!s.startsWith("http://") && !s.startsWith("https://")) return false;
        s = s.substring(s.startsWith("https://") ? 8 : 7);
        for (String t: workingHosts) {
            if (s.startsWith(t + "/")) return true;
        }
        for (String t: untestedHosts) { // we just suspect that they work
            if (s.startsWith(t + "/")) return true;
        }
        int slp = s.indexOf('/');
        int domlength = slp < 0 ? s.length() : slp;
        if (domlength < 8 && s.length() < 23 && !s.endsWith("/") && !s.endsWith("html")) {
            // very short, because of SEO mostly urls are very long. lets try that
            return true;
        }
        return false;
    }

    /**
     * this is the raw implementation if ClientConnection.getRedirect.
     * Surprisingly it's much slower, but some redirects cannot be discovered with the other
     * method, but with this one.
     * @param urlstring
     * @return
     * @throws IOException
     */
    private static String getRedirect(String urlstring) throws IOException {
        URL url = new URL(urlstring);
        Socket socket = new Socket(url.getHost(), 80);
        socket.setSoTimeout(2000);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println("GET " + url.getPath() + " HTTP/1.1");
        out.println("Host: " + url.getHost());
        // fake a bit that we are real
        out.println("User-Agent: " + ClientConnection.USER_AGENT);
        out.println("Accept-Language: en-us,en;q=0.5");
        out.println("Accept-Encoding: gzip,deflate");
        out.println("Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        out.println("Keep-Alive: 300");
        out.println("Connection: keep-alive");
        out.println("Pragma: no-cache");
        out.println("Cache-Control: no-cache");
        out.println(""); // don't forget the empty line at the end
        out.flush();
        // read result
        String line = in.readLine();
        if (line != null && line.contains("301")) {
            // first line should be "HTTP/1.1 301 Moved Permanently"
            // skip most of the next lines, but one should start with "Location:"
            while ((line = in.readLine()) != null) {
                if (line.length() == 0) break;
                if (!line.toLowerCase().startsWith("location:")) continue;
                urlstring = line.substring(9).trim();
                break;
            }
        }
        in.close();
        out.close();
        socket.close();
        return urlstring;
    }

    public static void main(String[] args) {
        String[] test = new String[] {
                "http://tmblr.co/Z6YPNx1jL1hHK",
                "http://dlvr.it/8kTDbJ",
                "http://fb.me/4lcXZsyyO",
                "http://wp.me/p4yQu6-za0",
                "http://j.mp/1vfXKr0",
                "http://t.co/E3w7s2qdBT",
                "http://bit.ly/1h9gTTT",
                "http://ift.tt/1I2O4pF",
                "http://goo.gl/R9CVuz",
                "http://tinyurl.com/pcp7fu4",
                "http://ow.ly/JtOPA",
                "http://tiny.cc/60ohux",
                "http://bit.do/ZwrT",
                "http://amzn.to/MO51If"
        };
        for (String t: test) {
            try {
                long start = System.currentTimeMillis();
                System.out.println("Test \"" + t + "\" -> " + getRedirect(t));
                System.out.println("time: " + (System.currentTimeMillis() - start));
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                long start = System.currentTimeMillis();
                System.out.println("Test \"" + t + "\" -> " + ClientConnection.getRedirect(t));
                System.out.println("time: " + (System.currentTimeMillis() - start));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * does not work:
     * https://tr.im/v31Rf
     * http://dlvr.it/8htd6W  // works on terminal but not here
     */

}

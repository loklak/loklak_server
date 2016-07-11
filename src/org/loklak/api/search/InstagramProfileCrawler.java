/**
 *  Instagram Profile Crawler
 *  Copyright 05.07.2016 by Jigyasa Grover, @jig08
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

package org.loklak.api.search;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.loklak.http.RemoteAccess;
import org.loklak.server.Query;

public class InstagramProfileCrawler extends HttpServlet {

	private static final long serialVersionUID = -526866894907267624L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		Query post = RemoteAccess.evaluate(request);

		// manage DoS
		if (post.isDoS_blackout()) {
			response.sendError(503, "your request frequency is too high");
			return;
		}

		String profile = post.get("profile", "");

		Document htmlPage = null;
		String name = null;
		String description = null;
		String imageLink = null;
		String directedLink = null;

		try {
			htmlPage = Jsoup.connect("https://www.instagram.com/" + profile).get();
		} catch (IOException e) {
			e.printStackTrace();
		}

		name = htmlPage.getElementsByClass("_79dar").text();
		description = htmlPage.getElementsByAttributeValue("property", "og:description").attr("content");
		imageLink = htmlPage.getElementsByAttributeValue("property", "og:image").attr("content");
		directedLink = htmlPage.select("a._56pjv").attr("href");
				
		JSONObject instaProfile = new JSONObject();
		instaProfile.put("profile", profile);
		instaProfile.put("name", name);
		instaProfile.put("description", description);
		instaProfile.put("image_link", imageLink);
		instaProfile.put("directed_link", directedLink);

		// print JSON
		response.setCharacterEncoding("UTF-8");
		PrintWriter sos = response.getWriter();
		sos.print(instaProfile.toString(2));
		sos.println();
	}

}

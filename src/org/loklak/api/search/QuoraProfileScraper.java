/**
 *  Quora Profile Scraper
 *  Copyright 16.08.2016 by Jigyasa Grover, @jig08
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

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.loklak.data.DAO;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.susi.SusiThought;
import org.loklak.tools.storage.JSONObjectWithDefault;

public class QuoraProfileScraper extends AbstractAPIHandler implements APIHandler {

	private static final long serialVersionUID = -3398701925784347310L;
	private static final	 String QUORA_URL_BASE = "https://www.quora.com";

	@Override
	public String getAPIPath() {
		return "/api/quoraprofilescraper";
	}

	@Override
	public BaseUserRole getMinimalBaseUserRole() {
		return BaseUserRole.ANONYMOUS;
	}

	@Override
	public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
		return null;
	}

	@Override
	public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights,
			JSONObjectWithDefault permissions) throws APIException {
		String profile = call.get("profile", "");
		return scrapeQuora(profile);
	}

	public static SusiThought  scrapeQuora(String profile) {

		JSONObject quoraProfile = new JSONObject();

		Document userHTML = null;
		String url = QUORA_URL_BASE + "/profile/" + profile;

		try {
			userHTML = Jsoup.connect(url)
							.header("Accept-Encoding", "gzip, deflate")
							.userAgent("Mozilla")
							.maxBodySize(0)
							.timeout(600000)
							.get();
		} catch (IOException e) {
			DAO.severe(e);
			quoraProfile.put("Error", "Unable to connect to https://www.quora.com");
			JSONArray jsonArray = new JSONArray();
			jsonArray.put(quoraProfile);
			SusiThought json = new SusiThought();
			json.setData(jsonArray);
			return json;
		}

		String bio = userHTML.getElementsByAttributeValueContaining("class", "ProfileDescription").text();
		quoraProfile.put("bio", bio);

		String profileImage = userHTML.getElementsByAttributeValueContaining("class", "profile_photo_img").attr("src");
		quoraProfile.put("profileImage", profileImage);

		String userName = userHTML.getElementsByAttributeValueContaining("class", "profile_photo_img").attr("alt");
		quoraProfile.put("user", userName);

		String rssFeedLink = url + "/rss";
		quoraProfile.put("rss_feed_link", rssFeedLink);

		Elements userBasicInfo = userHTML.getElementsByAttributeValueContaining("class", "UserCredential IdentityCredential");
		for (Element info: userBasicInfo) {
			String infoText = info.text();
			if (infoText.startsWith("Studi")) {
				quoraProfile.put(infoText.split(" ")[0].toLowerCase().trim() + "_at", infoText);
			} else if (infoText.startsWith("Lives")) {
				quoraProfile.put("lives_in", infoText);
			} else {
				quoraProfile.put("works_at", infoText);
			}
		}

		Elements knowsAbout = userHTML.getElementsByAttributeValueContaining("class",  "TopicNameSpan TopicName");
		JSONArray topics = new JSONArray();
		for (Element topic: knowsAbout) {
			topics.put(topic.text());
		}
		quoraProfile.put("knows_about", topics);

		JSONObject feeds = new JSONObject();
		Elements counts = userHTML.getElementsByAttributeValueContaining("class", "list_count");
		for (Element count: counts) {
			String topic = count.parent().text();
			topic = topic.substring(0, topic.indexOf(count.text())).trim();
			feeds.put(topic.toLowerCase() + "_url", QUORA_URL_BASE + count.parent().attr("href"));
			feeds.put(topic.toLowerCase(), count.text());
		}
		quoraProfile.put("feeds", feeds);

		JSONArray jsonArray = new JSONArray();
		jsonArray.put(quoraProfile);


		SusiThought json = new SusiThought();
		json.setData(jsonArray);
		return json;
	}

}

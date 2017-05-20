/**
 *  Github Profile Crawler
 *  Copyright 22.07.2016 by Jigyasa Grover, @jig08
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
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.susi.SusiThought;
import org.loklak.tools.storage.JSONObjectWithDefault;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

public class GithubProfileScraper extends AbstractAPIHandler implements APIHandler {

	private static final long serialVersionUID = -4166800345379685201L;
	private static final String GITHUB_API_BASE = "https://api.github.com/users/";

	@Override
	public String getAPIPath() {
		return "/api/githubprofilescraper.json";
	}

	@Override
	public BaseUserRole getMinimalBaseUserRole() {
		return BaseUserRole.ANONYMOUS;
	}

	@Override
	public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
		// TODO Auto-generated method stub
		return null;
	}

	public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights, JSONObjectWithDefault permissions)
			throws APIException {
		String profile = call.get("profile", "");
		String termsParam = call.get("terms", "");
		Set terms = null;
		if  (!"".equals(termsParam)) {
			terms = new HashSet(Arrays.asList(termsParam.split(",")));
			return scrapeGithub(profile, terms);
		}
		return scrapeGithub(profile);
	}

	private static JSONArray getDataFromApi(String url) {
		URI uri = null;
		try {
				uri = new URI(url);
		} catch (URISyntaxException e1) {
			e1.printStackTrace();
		}

		JSONTokener tokener = null;
		try {
			tokener = new JSONTokener(uri.toURL().openStream());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		JSONArray arr = new JSONArray(tokener);
		return arr;
	}

	private static void scrapeGithubUser(
		JSONObject githubProfile,
		Set terms,
		String profile,
		Document html) {

		final String STARRED_ENDPOINT = "/starred";
		final String FOLLOWERS_ENDPOINT = "/followers";
		final String FOLLOWING_ENDPOINT = "/following";

		String fullName = html.getElementsByAttributeValueContaining("class", "vcard-fullname").text();
		githubProfile.put("full_name", fullName);

		String userName = html.getElementsByAttributeValueContaining("class", "vcard-username").text();
		githubProfile.put("user_name", userName);

		String bio = html.getElementsByAttributeValueContaining("class", "user-profile-bio").text();
		githubProfile.put("bio", bio);

		String atomFeedLink = html.getElementsByAttributeValueContaining("type", "application/atom+xml").attr("href");
		githubProfile.put("atom_feed_link", "https://github.com" + atomFeedLink);

		String worksFor = html.getElementsByAttributeValueContaining("itemprop", "worksFor").text();
		githubProfile.put("works_for", worksFor);

		String homeLocation = html.getElementsByAttributeValueContaining("itemprop", "homeLocation").attr("title");
		githubProfile.put("home_location", homeLocation);

		if (terms.contains("starred") || terms.contains("all")) {
			String starredUrl = GITHUB_API_BASE + profile + STARRED_ENDPOINT;
			JSONArray starredData = getDataFromApi(starredUrl);
			githubProfile.put("starred_data", starredData);

			int starred = Integer.parseInt(html.getElementsByAttributeValue("class", "Counter").get(1).text());
			githubProfile.put("starred", starred);
		}

		if (terms.contains("follows") || terms.contains("all")) {
			String followersUrl = GITHUB_API_BASE + profile + FOLLOWERS_ENDPOINT;
			JSONArray followersData = getDataFromApi(followersUrl);
			githubProfile.put("followers_data", followersData);

			int followers = Integer.parseInt(html.getElementsByAttributeValue("class", "Counter").get(2).text());
			githubProfile.put("followers", followers);
		}

		if (terms.contains("following") || terms.contains("all")) {
			String followingUrl = GITHUB_API_BASE + profile + FOLLOWING_ENDPOINT;
			JSONArray followingData = getDataFromApi(followingUrl);
			githubProfile.put("following_data", followingData);

			int following = Integer.parseInt(html.getElementsByAttributeValue("class", "Counter").get(3).text());
			githubProfile.put("following", following);
		}

		if (terms.contains("organizations") || terms.contains("all")) {
			JSONArray organizations = new JSONArray();
			Elements orgs = html.getElementsByAttributeValue("itemprop", "follows");
			for (Element e : orgs) {
				JSONObject obj = new JSONObject();

				String label = e.attr("aria-label");
				obj.put("label", label);

				String link = e.attr("href");
				obj.put("link", "https://github.com" + link);

				String imgLink = e.children().attr("src");
				obj.put("img_link", imgLink);

				String imgAlt = e.children().attr("alt");
				obj.put("img_Alt", imgAlt);

				organizations.put(obj);
			}
			githubProfile.put("organizations", organizations);
		}
	}

	private static void scrapeGithubOrg(
		String profile,
		JSONObject githubProfile,
		Document html) {

		githubProfile.put("user_name", profile);

		String shortDescription = html.getElementsByAttributeValueContaining("class", "TableObject-item TableObject-item--primary").get(0).child(2).text();
		githubProfile.put("short_description", shortDescription);

		String homeLocation = html.getElementsByAttributeValueContaining("itemprop", "location").attr("title");
		githubProfile.put("location", homeLocation);

		Elements navigation = html.getElementsByAttributeValue("class", "orgnav");
		for (Element e : navigation) {
			String orgRepositoriesLink = e.child(0).tagName("a").attr("href");
			githubProfile.put("organization_respositories_link", "https://github.com" + orgRepositoriesLink);

			String orgPeopleLink = e.child(1).tagName("a").attr("href");
			githubProfile.put("organization_people_link", "https://github.com" + orgPeopleLink);

			String orgPeopleNumber = e.child(1).tagName("a").child(1).text();
			githubProfile.put("organization_people_number", orgPeopleNumber);
		}
	}

	public static SusiThought scrapeGithub(String profile) {
		Set terms = new HashSet();
		terms.add("all");
		return scrapeGithub(profile, terms);
	}

	public static SusiThought scrapeGithub(String profile, Set terms) {

		final String GISTS_ENDPOINT = "/gists";
		final String SUBSCRIPTIONS_ENDPOINT = "/subscriptions";
		final String REPOS_ENDPOINT = "/repos";
		final String EVENTS_ENDPOINT = "/events";
		final String RECEIVED_EVENTS_ENDPOINT = "/received_events";
		Document html = null;
		String user_id;

		JSONObject githubProfile = new JSONObject();

		try {
			html = Jsoup.connect("https://github.com/" + profile).get();
		} catch (IOException e) {
			JSONArray arr = getDataFromApi("https://api.github.com/search/users?q=" + profile);
			SusiThought json = new SusiThought();
			json.setData(arr);
			return json;
		}

		String avatarUrl = html.getElementsByAttributeValueContaining("class", "avatar").attr("src");

		Pattern avatar_url_to_user_id = Pattern.compile(".com\\/u\\/([0-9]+)\\?");
		Matcher m = avatar_url_to_user_id.matcher(avatarUrl);
		m.find();
		user_id = m.group(1);
		githubProfile.put("user_id", user_id);

		githubProfile.put("avatar_url", "https://avatars0.githubusercontent.com/u/" + user_id);
	
		String email = html.getElementsByAttributeValueContaining("itemprop", "email").text();
		if (!email.contains("@"))
			email = "";
		githubProfile.put("email", email);

		String specialLink = html.getElementsByAttributeValueContaining("itemprop", "url").text();
		githubProfile.put("special_link", specialLink);

		Elements joiningDates = html.getElementsByAttributeValueContaining("class", "dropdown-item");
		for(Element joiningDate: joiningDates) {
			String joinDate = joiningDate.attr("href");
			if(joinDate.contains("join")) {
				joinDate = joinDate.substring(joinDate.length() - 10);
				githubProfile.put("joining_date", joinDate);
			}
		}
		/* If Individual User */
		if (html.getElementsByAttributeValueContaining("class", "user-profile-nav").size() != 0) {
			scrapeGithubUser(githubProfile, terms, profile, html);
		}
		if (terms.contains("gists") || terms.contains("all")) {
			String gistsUrl = GITHUB_API_BASE + profile + GISTS_ENDPOINT;
			JSONArray gists = getDataFromApi(gistsUrl);
			githubProfile.put("gists", gists);
		}
		if (terms.contains("subscriptions") || terms.contains("all")) {
			String subscriptionsUrl = GITHUB_API_BASE + profile + SUBSCRIPTIONS_ENDPOINT;
			JSONArray subscriptions = getDataFromApi(subscriptionsUrl);
			githubProfile.put("subscriptions", subscriptions);
		}
		if (terms.contains("repos") || terms.contains("all")) {
			String reposUrl = GITHUB_API_BASE + profile + REPOS_ENDPOINT;
			JSONArray repos = getDataFromApi(reposUrl);
			githubProfile.put("repos", repos);
		}
		if (terms.contains("events") || terms.contains("all")) {
			String eventsUrl = GITHUB_API_BASE + profile + EVENTS_ENDPOINT;
			JSONArray events = getDataFromApi(eventsUrl);
			githubProfile.put("events", events);
		}
		if (terms.contains("received_events") || terms.contains("all")) {
			String receivedEventsUrl = GITHUB_API_BASE + profile + RECEIVED_EVENTS_ENDPOINT;
			JSONArray receivedEvents = getDataFromApi(receivedEventsUrl);
			githubProfile.put("received_events", receivedEvents);
		}
		/* If Organization */
		if (html.getElementsByAttributeValue("class", "orgnav").size() != 0) {
			scrapeGithubOrg(profile, githubProfile, html);
		}
		JSONArray jsonArray = new JSONArray();
		jsonArray.put(githubProfile);

		SusiThought json = new SusiThought();
		json.setData(jsonArray);
		return json;
	}

}

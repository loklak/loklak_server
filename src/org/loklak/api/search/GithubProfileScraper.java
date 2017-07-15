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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;    
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.loklak.data.DAO;
import org.loklak.harvester.BaseScraper;
import org.loklak.harvester.Post;
import org.loklak.server.BaseUserRole;

public class GithubProfileScraper extends BaseScraper {

    private final long serialVersionUID = -4166800345379685202L;
    private static final String GITHUB_API_BASE = "https://api.github.com/users/";
    public List<String> termsList = null;

    public GithubProfileScraper() {
        super();
        this.baseUrl = "https://github.com/";
        this.scraperName = "Github";
    }

    public GithubProfileScraper(String _query, Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.query = _query;
    }

    public GithubProfileScraper(Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.query = this.getExtraValue("query");
    }

    public GithubProfileScraper(String _query) {
        this();
        this.query = _query;
        this.setExtraValue("query", this.query);
    }

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

    protected String prepareSearchUrl(String type) {
        URIBuilder url = null;
        String midUrl = "search/";
        try {
            switch(type) {
                case "all":
                case "user":
                    midUrl = "";
                    url = new URIBuilder(this.baseUrl + midUrl + this.query);
                    break;
                // Add more types
                default:
                    url = new URIBuilder("");
                    break;
            }
        } catch (URISyntaxException e) {
            DAO.log("Invalid Url: baseUrl = " + this.baseUrl + ", mid-URL = " + midUrl + "query = " + this.query + "type = " + type);
            return "";
        }

        return url.toString();
    }

    protected void setParam() {
        if(!"".equals(this.getExtraValue("terms"))) {
            this.termsList = Arrays.asList(this.getExtraValue("terms").trim().split("\\s*,\\s*"));
        } else {
            this.termsList = new ArrayList<String>();
            this.termsList.add("all");
            this.setExtraValue("terms", String.join(",", this.termsList));
        }
    }

    private JSONArray getDataFromApi(String url) {
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

    protected Post scrape(BufferedReader br, String type, String url) {
        Post typeArray = new Post(true);

        if ("all".equals(type) || "user".equals(type)) {
            JSONArray statuses = new JSONArray();
            typeArray.put("user", statuses.put(scrapeGithub(this.query, br)));
            return typeArray;
        } else {
            return typeArray;
        }
        //TODO: add search scrapers
    }

    private void scrapeGithubUser(
        Post githubProfile,
        String profile,
        Document html) {

        final String StarredEndpoint = "/starred";
        final String FollowersEndpoint = "/followers";
        final String FollowingEndpoint = "/following";

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

        if (this.termsList.contains("starred") || this.termsList.contains("all")) {
            String starredUrl = GITHUB_API_BASE + profile + StarredEndpoint;
            JSONArray starredData = getDataFromApi(starredUrl);
            githubProfile.put("starred_data", starredData);

            int starred = Integer.parseInt(html.getElementsByAttributeValue("class", "Counter").get(1).text());
            githubProfile.put("starred", starred);
        }

        if (this.termsList.contains("follows") || this.termsList.contains("all")) {
            String followersUrl = GITHUB_API_BASE + profile + FollowersEndpoint;
            JSONArray followersData = getDataFromApi(followersUrl);
            githubProfile.put("followers_data", followersData);

            int followers = Integer.parseInt(html.getElementsByAttributeValue("class", "Counter").get(2).text());
            githubProfile.put("followers", followers);
        }

        if (this.termsList.contains("following") || this.termsList.contains("all")) {
            String followingUrl = GITHUB_API_BASE + profile + FollowingEndpoint;
            JSONArray followingData = getDataFromApi(followingUrl);
            githubProfile.put("following_data", followingData);

            int following = Integer.parseInt(html.getElementsByAttributeValue("class", "Counter").get(3).text());
            githubProfile.put("following", following);
        }

        if (this.termsList.contains("organizations") || this.termsList.contains("all")) {
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
    
    private void scrapeGithubOrg(
        String profile,
        Post githubProfile,
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
    
    public Post scrapeGithub(String profile, BufferedReader br) {

        Document html = null;
        String userId;
        Post githubProfile = new GithubPost(profile, 0);
        try {
            html = Jsoup.parse(bufferedReaderToString(br));
        } catch (IOException e) {
            DAO.trace(e);
            JSONArray arr = getDataFromApi("https://api.github.com/search/users?q=" + profile);
            githubProfile.put("user_profiles", arr);
            return githubProfile;
        }

        String avatarUrl = html.getElementsByAttributeValueContaining("class", "avatar").attr("src");
        Pattern avatarUrlToUserId = Pattern.compile(".com\\/u\\/([0-9]+)\\?");
        Matcher m = avatarUrlToUserId.matcher(avatarUrl);
        m.find();
        userId = m.group(1);
        githubProfile.put("user_id", userId);

        githubProfile.put("avatar_url", "https://avatars0.githubusercontent.com/u/" + userId);
    
        String email = html.getElementsByAttributeValueContaining("itemprop", "email").text();
        if (!email.contains("@")) {
            email = "";
        }
        githubProfile.put("email", email);

        String specialLink = html.getElementsByAttributeValueContaining("itemprop", "url").text();
        githubProfile.put("special_link", specialLink);

        Elements joiningDates = html.getElementsByAttributeValueContaining("class", "dropdown-item");
        for (Element joiningDate: joiningDates) {
            String joinDate = joiningDate.attr("href");
            if (joinDate.contains("join")) {
                joinDate = joinDate.substring(joinDate.length() - 10);
                githubProfile.put("joining_date", joinDate);
            }
        }
        // If Individual User
        if (html.getElementsByAttributeValueContaining("class", "user-profile-nav").size() != 0) {
            this.scrapeGithubUser(githubProfile, profile, html);
        }
        
        /* If Organization */
        else if (html.getElementsByAttributeValue("class", "orgnav").size() != 0) {
            this.scrapeGithubOrg(profile, githubProfile, html);
        }

        this.scrapeTerms(githubProfile);
        
        return githubProfile;
    }

    private void scrapeTerms(Post githubProfile) {

        final String GistsEndpoint = "/gists";
        final String SubscriptionsEndpoint = "/subscriptions";
        final String ReposEndpoint = "/repos";
        final String EventsEndpoint = "/events";
        final String ReceivedEventsEndpoint = "/received_events";
        final String profile = this.query;

        if (this.termsList.contains("gists") || this.termsList.contains("all")) {
            String gistsUrl = GITHUB_API_BASE + profile + GistsEndpoint;
            JSONArray gists = getDataFromApi(gistsUrl);
            githubProfile.put("gists", gists);
        }
        if (this.termsList.contains("subscriptions") || this.termsList.contains("all")) {
            String subscriptionsUrl = GITHUB_API_BASE + profile + SubscriptionsEndpoint;
            JSONArray subscriptions = getDataFromApi(subscriptionsUrl);
            githubProfile.put("subscriptions", subscriptions);
        }
        if (this.termsList.contains("repos") || this.termsList.contains("all")) {
            String reposUrl = GITHUB_API_BASE + profile + ReposEndpoint;
            JSONArray repos = getDataFromApi(reposUrl);
            githubProfile.put("repos", repos);
        }
        if (this.termsList.contains("events") || this.termsList.contains("all")) {
            String eventsUrl = GITHUB_API_BASE + profile + EventsEndpoint;
            JSONArray events = getDataFromApi(eventsUrl);
            githubProfile.put("events", events);
        }
        if (this.termsList.contains("received_events") || this.termsList.contains("all")) {
            String receivedEventsUrl = GITHUB_API_BASE + profile + ReceivedEventsEndpoint;
            JSONArray receivedEvents = getDataFromApi(receivedEventsUrl);
            githubProfile.put("received_events", receivedEvents);
        }
    }

    public static class GithubPost extends Post {

        //github post-id, for profile it will be username
        private String githubId;
        private int githubPostNo;

        public GithubPost(String _githubId, int _githubPostNo) {
            //not UTC, may be error prone
            super();
            this.githubId = _githubId;
            this.githubPostNo = _githubPostNo;
            this.postId = this.timestamp + this.githubPostNo + this.githubId;
        }

        public void setGithubId(String _githubId) {
            this.githubId = _githubId;
        }

        public void setGithubPostNo(int _githubPostNo) {
            this.githubPostNo = _githubPostNo;
        }

        public String getPostId() {
            return String.valueOf(this.postId);
        }
        //clean data
    }

}

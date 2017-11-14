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
import java.io.BufferedReader;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.loklak.data.DAO;
import org.loklak.harvester.BaseScraper;
import org.loklak.harvester.Post;
import org.loklak.objects.PostTimeline;
import org.loklak.objects.SourceType;
import org.loklak.server.BaseUserRole;

public class QuoraProfileScraper extends BaseScraper {

    private final long serialVersionUID = -3398701925784347312L;
    private List<String> typeList = null;

    public QuoraProfileScraper() {
        super();
        this.baseUrl = "https://www.quora.com/";
        this.scraperName = "quora";
        this.sourceType = SourceType.QUORA;
    }

    public QuoraProfileScraper(String _query) {
        this();
        this.query = _query;
        this.setExtraValue("query", this.query);
    }

    public QuoraProfileScraper(String _query, Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.query = _query;
        this.setExtraValue("query", this.query);
    }

    public QuoraProfileScraper(Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
    }

    protected void setParam() {
        if(!"".equals(this.getExtraValue("type"))) {
            this.typeList = Arrays.asList(this.getExtraValue("type").trim().split("\\s*,\\s*"));
        } else {
            this.typeList = new ArrayList<String>();
            this.typeList.add("all");
            this.setExtraValue("type", String.join(",", this.typeList));
        }
        this.query = this.getExtraValue("query");
    }

    @Override
    protected void setCacheMap() {
        this.cacheMap = new HashMap<String, Map<String, String>>();

        Map<String, String> getMap = new HashMap<String, String>();
        if(this.typeList.contains("user") && this.typeList.size() == 1) {
            getMap.put("post_type", "user");
            getMap.put("user_name", this.query);
        } else if(this.typeList.contains("question") && this.typeList.size() == 1) {
            getMap.put("post_type", "question");
            getMap.put("post_ques", this.query);
        }
        getMap.put("post_scraper", this.scraperName);

        Map<String, String> alsoGetMap = new HashMap<String, String>();
        if(this.typeList.contains("question") || this.typeList.contains("all")) {
            alsoGetMap.put("post_ques", this.query);
        }
        if(this.typeList.contains("user") || this.typeList.contains("all")) {
            alsoGetMap.put("user_name", this.query);
        }

        this.cacheMap.put("get", getMap);
        this.cacheMap.put("also_get", alsoGetMap);
    }

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

    protected Map<?, ?> getExtra(String _extra) {
        return new HashMap<String, String>();
    }

    protected String prepareSearchUrl(String type) {
        URIBuilder url = null;
        String midUrl = "search/";
        try {
            switch(type) {
                case "user":
                    midUrl = "profile/";
                    url = new URIBuilder(this.baseUrl + midUrl + this.query);
                    break;
                case "question":
                    url = new URIBuilder(this.baseUrl + midUrl);
                    url.addParameter("q", this.query);
                    url.addParameter("type", "question");
                    break;
                case "answer":
                    url = new URIBuilder(this.baseUrl + midUrl);
                    url.addParameter("q", this.query);
                    url.addParameter("type", "answer");
                    break;
                default:
                    url = new URIBuilder("");
                    break;
            }
        } catch (URISyntaxException e) {
            DAO.log("Invalid Url: baseUrl = " + this.baseUrl + ", mid-URL = " + midUrl + "query = " + this.query + "type = " + type);
        }

        return url.toString();
    }

    @Override
    public Post getResults() {
        String url;
        Thread[] dataThreads = new Thread[2];
        PostTimeline postList = new PostTimeline(this.order);

        if(this.typeList.contains("user") || this.typeList.contains("all")) {
            url = prepareSearchUrl("user");
            dataThreads[0] = new ConcurrentScrape(url, "users", postList);
            dataThreads[0].start();
        } else {
            dataThreads[0] = new Thread();
        }
        if(this.typeList.contains("question") || this.typeList.contains("all")) {
            url = prepareSearchUrl("question");
            dataThreads[1] = new ConcurrentScrape(url, "question", postList);
            dataThreads[1].start();
        } else {
            dataThreads[1] = new Thread();
        }
        //TODO: add more types

        int i = 0;
        try {
            for (i = 0; i < dataThreads.length; i++) {
                dataThreads[i].join();
            }
        } catch(InterruptedException e) {
            exceptionOutputGetData(i);
        }

        // Add scraper name
        Post postArray = new Post();
        postArray.put(this.scraperName, postList.toArray());

        return postArray;
    }

    protected class ConcurrentScrape extends Thread {

        private String url = "";
        private String type = "all";
        private PostTimeline postList = null;

        public ConcurrentScrape(String url, String type, PostTimeline postList) {
            this.url = url;
            this.type = type;
            this.postList = postList;
        }

        public void run() {
            try {
                this.postList.addPost(QuoraProfileScraper.this.getDataFromConnection(this.url, this.type));
            } catch (IOException e) {
                DAO.severe("check internet connection for url: " + this.url + " type: " + this.type);
            }
        }
    }

    private void exceptionOutputGetData(int i) {
        String stuck_at = "";
        switch(i) {
            case 0:
                stuck_at = "users";
                break;
            case 1:
                stuck_at = "question";
                break;
            case 2:
                stuck_at = "answer";
                break;
            default:
                stuck_at = "unknown, check the code";
        }
        DAO.severe("Couldn't complete all threads, stuck at scraper: " + this.scraperName + " dataThread: " + stuck_at);
    }

    private PostTimeline scrapeQues(BufferedReader br, String url) {
        Pattern resultBlock = Pattern.compile("<div[^>]*[^>\\s]*[^>]*class=['\"][^>'\"]*(results_list)");
        Pattern quesLink = Pattern.compile("<a[^>]*class=['\"][^>'\"]*question_link[^>'\"]*[\"'][^>]*href=['\"]([^>'\"]*)");
        Pattern quesStart = Pattern.compile("<span[^>]*class=[\'\"][^>\'\"]*question_text[^>\'\"]*[\'\"][^>]*>");
        String ignoreTag = "(<[^>]*>)";
        Matcher matcher;
        String fromTerm = "";
        String uptoTerm = "</a>";
        Post qPost = null;

        PostTimeline quesList = new PostTimeline(this.order);

        String input = "";
        String _qPostId = "";
        String _qPostUrl = "";

        int last = 0;
        try {
            // Get to Result List block
            while(true) {
                input = br.readLine();
                if (input == null) break;

                matcher = resultBlock.matcher(input);
                if (matcher.find()) {
                    input = input.substring(matcher.end());
                    break;
                }
            }
            // Scraping starts
            for(int i = 0;input !=null ; i++) {

                // Get to first result
                while(input != null) {
                    matcher = quesLink.matcher(input);
                    if(matcher.find()) {
                        _qPostId = matcher.group(1).substring(1);
                        _qPostUrl = this.baseUrl + _qPostId;
                        input = input.substring(matcher.end());
                        break;
                    } else {
                        input = br.readLine();
                    }

                }

                qPost = new QuoraPost(_qPostId, i);
                qPost.put("search_url", url);
                qPost.put("post_url", _qPostUrl);
                qPost.put("post_type", "question");
                qPost.put("post_scraper", "quora");
                // Get questions
                while(input != null) {
                    matcher = quesStart.matcher(input);
                    if(matcher.find()) {
                        input = input.substring(matcher.end());
                        break;
                    } else {
                        input = br.readLine();
                    }
                }

                while(input != null) {
                    fromTerm = fromTerm + input;
                    last = input.indexOf(uptoTerm);
                    if(input.indexOf(uptoTerm) > 0) {
                        fromTerm = fromTerm.substring(0, input.indexOf(uptoTerm));
                        break;
                    } else {
                        input = br.readLine();
                    }

                }

                fromTerm = fromTerm.replaceAll(ignoreTag, "");
                input = input.substring(last);
                qPost.put("post_ques", fromTerm);
                fromTerm = "";
                quesList.addPost(qPost);
            }


        } catch(IOException e) {
            qPost = new QuoraPost(this.query, -1);
            qPost.put("error", "Connection error while fetching");
            qPost.put("search_url", url);
            quesList.addPost(qPost);
        } catch(NullPointerException e) { }

        return quesList;
    }

    private PostTimeline scrapeProfile(BufferedReader br, String url) {
        String html;
        Post quoraProfile = new QuoraPost(this.query, 0);
        PostTimeline usersList = new PostTimeline(this.order);
        try {
            html = bufferedReaderToString(br);
        } catch(IOException e) {
            DAO.trace(e);
            html = "";
            //TODO: output error if no output in json
        }

        Document userHTML = Jsoup.parse(html);

        quoraProfile.put("search_url", url);
        quoraProfile.put("post_type", "user");
        quoraProfile.put("post_scraper", this.scraperName);
        String bio = userHTML.getElementsByAttributeValueContaining("class", "ProfileDescription").text();
        quoraProfile.put("bio", bio);

        String profileImage = userHTML.getElementsByAttributeValueContaining("class", "profile_photo_img").attr("src");
        quoraProfile.put("profileImage", profileImage);

        String userName = userHTML.getElementsByAttributeValueContaining("class", "profile_photo_img").attr("alt");
        quoraProfile.put("user_name", userName);

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
            feeds.put(topic.toLowerCase() + "_url", baseUrl + count.parent().attr("href").substring(1));
            feeds.put(topic.toLowerCase(), count.text());
        }
        quoraProfile.put("feeds", feeds);

        usersList.addPost(quoraProfile);
        return usersList;
    }

    @Override
    protected Post scrape(BufferedReader br, String type, String url) {
        Post typeArray = new Post(true);

        switch(type) {
            case "users":
                this.putData(typeArray, "users", this.scrapeProfile(br, url));
                break;
            case "question":
                this.putData(typeArray, "question", this.scrapeQues(br, url));
                break;
            default:
                break;
        }
        return typeArray;
    }

    public static class QuoraPost extends Post {

        //quora post-id, for profile it will be username
        private String quoraId;
        private int quoraPostNo;

        public QuoraPost(String _quoraId, int _quoraPostNo) {
            //not UTC, may be error prone
            super();
            this.quoraPostNo = _quoraPostNo;
            this.setPostId(_quoraId);
        }

        public void getQuoraId(String _quoraId) {
            this.quoraId = _quoraId;
        }

        public void getQuoraPostNo(int _quoraPostNo) {
            this.quoraPostNo = _quoraPostNo;
        }

        public String getPostId() {
            return String.valueOf(this.postId);
        }
        //clean data
    }
}

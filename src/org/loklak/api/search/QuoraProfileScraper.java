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
import java.net.URISyntaxException;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
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
import org.loklak.objects.Timeline2;
import org.loklak.server.BaseUserRole;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuoraProfileScraper extends BaseScraper {

    private final long serialVersionUID = -3398701925784347312L;
    private Timeline2 postList = new Timeline2(this.order);

    public QuoraProfileScraper() {
        super();
        this.baseUrl = "https://www.quora.com/";
        String scraperName = "Quora";
        this.extra = new HashMap<String, String>();
    }

    public QuoraProfileScraper(String _query) {
        this();
        this.query = _query;
    }

    public QuoraProfileScraper(String _query, Map<String, String> _extra) {
        this();
        this.extra = _extra;
        this.query = _query;
        this.extra.put("query", this.query);    
    }

    public QuoraProfileScraper(Map<String, String> _extra) {
        this();
        this.getExtra(_extra);
        this.query = this.extra.get("query");        
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

    @Override
    public Timeline2 getData() {
        //TODO: 2) convert type to array of types
        String type = this.extra.get("type") == null ? "all": this.extra.get("type");
        String midUrl;
        URIBuilder url = null;
        Thread[] dataThreads = new Thread[3];
        this.postList = new Timeline2(this.order);

        switch(type) {
            case "all":
            case "profile":
                midUrl = "profile/";
                try {
                    url = new URIBuilder(this.baseUrl + midUrl + this.query);
                    dataThreads[0] = new ConcurrentScrape(url.toString(), "profile");
                    dataThreads[0].start();
                } catch (URISyntaxException e) {
                    DAO.severe("Invalid Url: " + this.baseUrl + midUrl + this.query);
                }
                if("profile".equals(type)) break;
            case "question":
                midUrl = "search/";
                try {
                    url = new URIBuilder(this.baseUrl + midUrl);
                    url.addParameter("q", this.query);
                    url.addParameter("type", "question");
                    dataThreads[1] = new ConcurrentScrape(url.toString(), "question");
                    dataThreads[1].start();
                } catch (URISyntaxException e) {
                    DAO.severe("Invalid Url: " + this.baseUrl + midUrl + this.query);
                }
                if("question".equals(type)) break;
            case "answer":
                midUrl = "search/";
                try {
                    url = new URIBuilder(this.baseUrl + midUrl);
                    url.addParameter("q", this.query);
                    url.addParameter("type", "answer");
                    dataThreads[2] = new ConcurrentScrape(url.toString(), "answer");
                    dataThreads[2].start();
                } catch (URISyntaxException e) {
                    DAO.severe("Invalid Url: " + this.baseUrl + midUrl + this.query);
                }
                if("answer".equals(type)) break;
            default:
                break;
        }

        try {
            for (int i=0; i<3; i++) {
                dataThreads[i].join();
            }
        } catch(InterruptedException e) {
            DAO.severe("Couldn't complete all threads");
        }

        return this.postList;
    }

    protected class ConcurrentScrape extends Thread {

        private String url = "";
        private String type = "all";

        public ConcurrentScrape(String url, String type) {
            this.url = url;
            this.type = type;
        }

        public void run() {
            try {
                QuoraProfileScraper.this.postList.mergePost(QuoraProfileScraper.this.getDataFromConnection(this.url, this.type));
                DAO.log(String.valueOf(QuoraProfileScraper.this.postList));
            } catch (IOException e) {
                DAO.severe("check internet connection");
            }
        }
    }


    private Timeline2 scrapeQues(BufferedReader br, String url) {
        Pattern resultBlock = Pattern.compile("<div[^>]*[^>\\s]*[^>]*class=['\"][^>'\"]*(results_list)");
        Pattern quesLink = Pattern.compile("<a[^>]*class=['\"][^>'\"]*question_link[^>'\"]*[\"'][^>]*href=['\"]([^>'\"]*)");
        Pattern quesStart = Pattern.compile("<span[^>]*class=[\'\"][^>\'\"]*question_text[^>\'\"]*[\'\"][^>]*>");
        String ignoreTag = "(<[^>]*>)";
        Matcher matcher;
        String fromTerm = "";
        String uptoTerm = "</a>";
        Post qPost = null;
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
            for(int i =0;input !=null ; i++) {

                // Get to first result
                while(input != null) {

                    matcher = quesLink.matcher(input);
                    if(matcher.find()) {
                        _qPostId = String.valueOf(matcher.end());
                        _qPostUrl = this.baseUrl + matcher.group(1);
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
                while(true) {
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
                postList.addPost(qPost);
            }
        } catch(IOException e) {
            qPost = new QuoraPost(this.query, -1);
            qPost.put("error", "Connection error while fetching");
            qPost.put("search_url", url);
            postList.addPost(qPost);
            return postList;
        }
        return postList;
    }

    private Timeline2 scrapeProfile(BufferedReader br, String url) {
        String html;
        Post quoraProfile = new QuoraPost(this.query, 0);

        try {
            html = bufferedReaderToString(br);
        } catch(IOException e) {
            DAO.trace(e);
            html = "";
            //TODO: output error if no output in json
        }

        Document userHTML = Jsoup.parse(html);

        quoraProfile.put("search_url", url);

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
            feeds.put(topic.toLowerCase() + "_url", baseUrl + count.parent().attr("href"));
            feeds.put(topic.toLowerCase(), count.text());
        }
        quoraProfile.put("feeds", feeds);

        this.postList.addPost(quoraProfile);

        return this.postList;
    }

    //TODO: this method shall return Timeline object
    @Override
    protected Timeline2 scrape(BufferedReader br, String type, String url) {
        Timeline2 dataSet = new Timeline2(order);
        switch(type) {
            case "all":
            case "profile":
                dataSet.mergePost(scrapeProfile(br, url));
                break;
            case "question":
                dataSet.mergePost(scrapeQues(br, url));
                break;
            //case "answer":
            //    dataSet.mergePost(scrapeAns(br));
            //    break;
            //TODO: add more...
            default:
                break;
        }

        return dataSet;
    }

    public static class QuoraPost extends Post {

        //quora post-id, for profile it will be username
        private String quoraId;
        private int quoraPostNo;

        public QuoraPost(String _quoraId, int _quoraPostNo) {
            //not UTC, may be error prone
            super();
            this.quoraId = _quoraId;
            this.quoraPostNo = _quoraPostNo;
            this.postId = this.timestamp + this.quoraPostNo + this.quoraId;
        }

        public void getQuoraId(String _quoraId) {
            this.quoraId = _quoraId;
        }

        public void getQuoraPostNo(int _quoraPostNo) {
            this.quoraPostNo = _quoraPostNo;
        }

        public void setPostId() {
            this.postId = this.timestamp + this.quoraPostNo + this.quoraId;
        }

        public String getPostId() {
            return String.valueOf(this.postId);
        }
        //clean data
    }

}

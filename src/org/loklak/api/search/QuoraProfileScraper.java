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
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.loklak.data.DAO;
import org.loklak.harvester.Post;
import org.loklak.harvester.BaseScraper;
import org.loklak.objects.Timeline;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.susi.SusiThought;
import org.loklak.tools.storage.JSONObjectWithDefault;

public class QuoraProfileScraper extends BaseScraper {

    private final long serialVersionUID = -3398701925784347310L;

    public QuoraProfileScraper() {
        super();
        this.baseUrl = "https://www.quora.com/";
        this.midUrl = "profile/";
        String scraperName = "Quora";
    }

    public QuoraProfileScraper(String _query) {
        this();
        this.query = _query;
    }

    public QuoraProfileScraper(String _query, String _extra) {
        this();
        this.query = _query;
        this.extra = _extra;
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

    private Post scrapeProfile() {

        Post quoraProfile = new QuoraPost(this.query, 0);
        Document userHTML = Jsoup.parse(this.html);

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

        //dataSet.add(quoraProfile);
        return quoraProfile;
    }

    //TODO: this method shall return Timeline object
    @Override
//    protected Timeline scrape(BufferedReader br) {
    protected Post scrape(BufferedReader br) {
//        Timeline dataSet = new Timeline(order);
        //for profile
        Post qPost;
        try {
            this.html = bufferedReaderToString(br);
        } catch(IOException e) {
            DAO.trace(e);
        }
        qPost = scrapeProfile();

//        return dataSet.add(qPost);
        return qPost;
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
        }

        public void getQuoraId(String _quoraId) {
            this.quoraId = _quoraId;
        }

        public void getQuoraPostNo(int _quoraPostNo) {
            this.quoraPostNo = _quoraPostNo;
        }

        public void getPostId() {
            this.postId = this.timestamp + this.quoraPostNo + this.quoraId;
        }

        public String setPostId() {
            this.postId = this.timestamp + this.quoraPostNo + this.quoraId;
            return String.valueOf(this.postId);
        }
        //clean data
    }

}

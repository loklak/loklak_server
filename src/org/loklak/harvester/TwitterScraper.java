/**
 *  TwitterScraper
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

package org.loklak.harvester;

import static org.apache.http.util.EntityUtils.consumeQuietly;
import static org.loklak.http.ClientConnection.getCustomClosableHttpClient;
import static org.loklak.http.ClientConnection.getHTML;

import org.loklak.objects.AbstractObjectEntry;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.loklak.api.search.ShortlinkFromTweetServlet;
import org.loklak.data.Classifier;
import org.loklak.data.DAO;
import org.loklak.data.Classifier.Category;
import org.loklak.data.Classifier.Context;
import org.loklak.geo.GeoMark;
import org.loklak.geo.LocationSource;
import org.loklak.harvester.Post;
import org.loklak.objects.QueryEntry.PlaceContext;
import org.loklak.tools.bayes.Classification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.http.ClientConnection;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.ProviderType;
import org.loklak.objects.SourceType;
import org.loklak.objects.Timeline;
import org.loklak.objects.UserEntry;


public class TwitterScraper {

    public static final ExecutorService executor = Executors.newFixedThreadPool(40);
    public static final Pattern emoji_pattern_span = Pattern.compile("<span [^>]*class=\"Emoji Emoji--forLinks\" [^>]*>[\\n]*[^<]*</span>[\\n]*<span [^>]*class=\"visuallyhidden\" [^>]*aria-hidden=\"true\"[^>]*>[\\n]*([^<]*)[\\n]*</span>");
    private static final Pattern bearerJsUrlRegex = Pattern.compile("showFailureMessage\\(\\'(.*?main.*?)\\'\\);");
    private static final Pattern guestTokenRegex = Pattern.compile("document\\.cookie \\= decodeURIComponent\\(\\\"gt\\=([0-9]+);");
    private static final Pattern bearerTokenRegex = Pattern.compile("BEARER_TOKEN:\\\"(.*?)\\\"");

    public static Timeline search(
            final String query,
            final ArrayList<String> filterList,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend,
            int jointime) {

        Timeline[] tl = search(query, filterList, order, writeToIndex, writeToBackend);
        long timeout = System.currentTimeMillis() + jointime;
        long remainingWait = 0;
        for (TwitterTweet me: tl[1]) {
            TwitterTweet tt = (TwitterTweet) me;
            remainingWait = Math.max(10, timeout - System.currentTimeMillis());
            if (tt.waitReady(remainingWait)) {
                 // double additions are detected
                tl[0].add(tt, tt.getUser());
            }
        }
        return tl[0];
    }

    public static Timeline search(
            final String query,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend,
            int jointime) {

        return search(query, new ArrayList<>(), order, writeToIndex, writeToBackend, jointime);
    }

    private static String prepareSearchUrl(final String query, final ArrayList<String> filterList) {
        // check
        // https://twitter.com/search-advanced for a better syntax
        // build queries like https://twitter.com/search?f=tweets&vertical=default&q=kaffee&src=typd
        // https://support.twitter.com/articles/71577-how-to-use-advanced-twitter-search#
        String httpsUrl = "";
        String type = "tweets";
        try {

            // query q
            StringBuilder t = new StringBuilder(query.length());
            for (String s: query.replace('+', ' ').split(" ")) {
                t.append(' ');
                if (s.startsWith("since:") || s.startsWith("until:")) {
                    int u = s.indexOf('_');
                    t.append(u < 0 ? s : s.substring(0, u));
                } else {
                    t.append(s);
                }
            }
            String q = t.length() == 0 ? "*" : URLEncoder.encode(t.substring(1), "UTF-8");

            // type of content to fetch
            if (filterList.contains("video") && filterList.size() == 1) {
                type = "videos";
            }

            // building url
            httpsUrl = "https://twitter.com/search?f="
                    + type + "&vertical=default&q=" + q + "&src=typd";

        } catch (UnsupportedEncodingException e) {}
        return httpsUrl;
    }

    @SuppressWarnings("unused")
    private static Timeline[] search(
            final String query,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        return search(query, new ArrayList<>(), order, writeToIndex, writeToBackend);
    }

    private static Timeline[] search(
            final String query,
            final ArrayList<String> filterList,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        // check
        // https://twitter.com/search-advanced for a better syntax
        // https://support.twitter.com/articles/71577-how-to-use-advanced-twitter-search#
        String https_url = prepareSearchUrl(query, filterList);
        Timeline[] timelines = null;
        try {
            ClientConnection connection = new ClientConnection(https_url);
            if (connection.inputStream == null) return null;
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.inputStream, StandardCharsets.UTF_8));

                timelines = search(br, filterList, order, writeToIndex, writeToBackend);
            } catch (IOException e) {
                DAO.severe(e);
            } finally {
                connection.close();
            }
        } catch (IOException e) {
            // this could mean that twitter rejected the connection (DoS protection?) or we are offline (we should be silent then)
            // DAO.severe(e);
            if (timelines == null) timelines = new Timeline[]{new Timeline(order), new Timeline(order)};
        };

        // wait until all messages in the timeline are ready
        if (timelines == null) {
            // timeout occurred
            timelines = new Timeline[]{new Timeline(order), new Timeline(order)};
        }
        if (timelines != null) {
            if (timelines[0] != null) timelines[0].setScraperInfo("local");
            if (timelines[1] != null) timelines[1].setScraperInfo("local");
        }
        return timelines;
    }

    private static Timeline[] parse(
            final File file,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        return parse(file, new ArrayList<>(), order, writeToIndex, writeToBackend);
    }

    private static Timeline[] parse(
            final File file,
            final ArrayList<String> filterList,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        Timeline[] timelines = null;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            timelines = search(br, filterList, order, writeToIndex, writeToBackend);
        } catch (IOException e) {
            DAO.severe(e);
        } finally {
            if (timelines == null) timelines = new Timeline[]{new Timeline(order), new Timeline(order)};
        }

        if (timelines[0] != null) timelines[0].setScraperInfo("local");
        if (timelines[1] != null) timelines[1].setScraperInfo("local");
        return timelines;
    }



    private static Timeline[] search(
            final BufferedReader br,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) throws IOException {

        return search(br, new ArrayList<>(), order, writeToIndex, writeToBackend);
    }

    /**
     * scrape messages from the reader stream: this already checks if a message is new. There are only new messages returned
     * @param br
     * @param order
     * @return two timelines in one array: Timeline[0] is the one which is finished to be used, Timeline[1] contains messages which are in postprocessing
     * @throws IOException
     */
    private static Timeline[] search(
            final BufferedReader br,
            final ArrayList<String> filterList,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) throws IOException {
        Timeline timelineReady = new Timeline(order);
        Timeline timelineWorking = new Timeline(order);
        String input;
        Map<String, prop> props = new HashMap<String, prop>();
        Set<String> images = null;
        Set<String> videos = null;
        String place_id = "";
        String place_name = "";
        boolean parsing_favourite = false, parsing_retweet = false;
        int line = 0; // first line is 1, according to emacs which numbers the first line also as 1
        boolean debuglog = DAO.getConfig("flag.debug.twitter_scraper", "false").equals("true");

        while ((input = br.readLine()) != null){
            line++;
            input = input.trim();

            if (input.length() == 0) continue;

            // debug
            if (debuglog) DAO.log(line + ": " + input);
            //if (input.indexOf("ProfileTweet-actionCount") > 0) DAO.log(input);

            // parse
            int p;
            if ((p = input.indexOf("=\"account-group")) > 0) {
                props.put("userid", new prop(input, p, "data-user-id"));
                continue;
            }
            if ((p = input.indexOf("class=\"avatar js-action-profile-avatar")) > 0) {
                props.put("useravatarurl", new prop(input, p, "src"));
                continue;
            }
            if ((p = input.indexOf("data-name=")) >= 0) {
                props.put("userfullname", new prop(input, p, "data-name"));
                // don't continue here, username is in the same line
            }
            if ((p = input.indexOf("data-screen-name=")) >= 0) {
                props.put("usernickname", new prop(input, p, "data-screen-name"));
                // don't continue here, fullname is in the same line
            }
            if ((p = input.indexOf("class=\"tweet-timestamp")) > 0) {
                props.put("tweetstatusurl", new prop(input, 0, "href"));
                props.put("tweettimename", new prop(input, p, "title"));
                // don't continue here because "class=\"_timestamp" is in the same line
            }
            if ((p = input.indexOf("class=\"_timestamp")) > 0) {
                props.put("tweettimems", new prop(input, p, "data-time-ms"));
                continue;
            }
            if ((p = input.indexOf("class=\"ProfileTweet-action--retweet")) > 0) {
                parsing_retweet = true;
                continue;
            }
            if ((p = input.indexOf("class=\"ProfileTweet-action--favorite")) > 0) {
                parsing_favourite = true;
                continue;
            }
            if ((p = input.indexOf("class=\"TweetTextSize")) > 0) {
                // read until closing p tag to account for new lines in tweets
                while (input.lastIndexOf("</p>") == -1){
                    input = input + ' ' + br.readLine();
                }
                prop tweettext = new prop(input, p, null);
                props.put("tweettext", tweettext);
                continue;
            }
            if ((p = input.indexOf("class=\"ProfileTweet-actionCount")) > 0) {
                if (parsing_retweet) {
                    prop tweetretweetcount = new prop(input, p, "data-tweet-stat-count");
                    props.put("tweetretweetcount", tweetretweetcount);
                    parsing_retweet = false;
                }
                if (parsing_favourite) {
                    props.put("tweetfavouritecount", new prop(input, p, "data-tweet-stat-count"));
                    parsing_favourite = false;
                }
                continue;
            }
            // get images
            if(images == null) images = new HashSet<>();
            if ((p = input.indexOf("data-image-url=")) >= 0) {
                String image_url = new prop(input, p, "data-image-url").value;
                if (!image_url.endsWith(".jpg") && !image_url.endsWith(".png")) {
                    DAO.log("strange image url: " + image_url);
                }
                images.add(image_url);
                continue;
            }
            // get images
            if ((p = input.lastIndexOf("background-image:url('")) >= 0) {
                int q = input.lastIndexOf("'");
                if (q > p + 22) {
                    String image_url = input.substring(p + 22, q);
                    if (!image_url.endsWith(".jpg") && !image_url.endsWith(".png")) {
                        DAO.log("strange image url: " + image_url);
                    }
                    images.add(image_url);
                }
                continue;
            }
            // we have two opportunities to get video thumbnails == more images; images in the presence of video content should be treated as thumbnail for the video
            if(videos == null) videos = new HashSet<>();
            if ((p = input.indexOf("class=\"animated-gif-thumbnail\"")) > 0) {
                String image_url = new prop(input, 0, "src").value;
                images.add(image_url);
                continue;
            }
            if ((p = input.indexOf("class=\"animated-gif\"")) > 0) {
                String image_url = new prop(input, p, "poster").value;
                images.add(image_url);
                continue;
            }
            if ((p = input.indexOf("<source video-src")) >= 0 && input.indexOf("type=\"video/") > p) {
                String video_url = new prop(input, p, "video-src").value;
                videos.add(video_url);
                continue;
            }
            if (input.indexOf("AdaptiveMedia-videoContainer") > 0) {
                /* String tweetUrl = props.get("tweetstatusurl").value;
                 * String[] videoUrls = fetchTwitterVideos(tweetUrl);
                 * Collections.addAll(videos, videoUrls);
                 *
                 * Not a good idea to fetch video right now. Need to add another endpoint which
                 * lets end users fetch complete videos from here.
                 * See https://github.com/loklak/loklak_server/issues/1298
                 **/
            }
            if ((p = input.indexOf("class=\"Tweet-geo")) > 0) {
                prop place_name_prop = new prop(input, p, "title");
                place_name = place_name_prop.value;
                continue;
            }
            if ((p = input.indexOf("class=\"ProfileTweet-actionButton u-linkClean js-nav js-geo-pivot-link")) > 0) {
                prop place_id_prop = new prop(input, p, "data-place-id");
                place_id = place_id_prop.value;
                continue;
            }

            if (props.size() == 10 || (debuglog  && props.size() > 4 && input.indexOf("stream-item") > 0)) {

                if(!filterPosts(filterList, props, videos, images)) {
                    props = new HashMap<String, prop>();
                    place_id = "";
                    place_name = "";
                    continue;
                }

                //TODO: Add more filters

                // the tweet is complete, evaluate the result
                if (debuglog) DAO.log("*** line " + line + " propss.size() = " + props.size());
                prop userid = props.get("userid"); if (userid == null) {if (debuglog) DAO.log("*** line " + line + " MISSING value userid"); continue;}
                prop usernickname = props.get("usernickname"); if (usernickname == null) {if (debuglog) DAO.log("*** line " + line + " MISSING value usernickname"); continue;}
                prop useravatarurl = props.get("useravatarurl"); if (useravatarurl == null) {if (debuglog) DAO.log("*** line " + line + " MISSING value useravatarurl"); continue;}
                prop userfullname = props.get("userfullname"); if (userfullname == null) {if (debuglog) DAO.log("*** line " + line + " MISSING value userfullname"); continue;}
                UserEntry user = new UserEntry(
                        userid.value,
                        usernickname.value,
                        useravatarurl.value,
                        MessageEntry.html2utf8(userfullname.value)
                );

                prop tweettimems = props.get("tweettimems");
                if (tweettimems == null) {
                    if (debuglog) DAO.log("*** line " + line + " MISSING value tweettimems");
                    continue;
                }
                prop tweetretweetcount = props.get("tweetretweetcount");
                if (tweetretweetcount == null) {
                    if (debuglog) DAO.log("*** line " + line + " MISSING value tweetretweetcount");
                    continue;
                }
                prop tweetfavouritecount = props.get("tweetfavouritecount");
                if (tweetfavouritecount == null) {
                    if (debuglog) DAO.log("*** line " + line + " MISSING value tweetfavouritecount");
                    continue;
                }

                TwitterTweet tweet = new TwitterTweet(
                        user.getScreenName(),
                        Long.parseLong(tweettimems.value),
                        props.get("tweettimename").value,
                        props.get("tweetstatusurl").value,
                        props.get("tweettext").value,
                        Long.parseLong(tweetretweetcount.value),
                        Long.parseLong(tweetfavouritecount.value),
                        images, videos, place_name, place_id,
                        user, writeToIndex,  writeToBackend
                );
                if (DAO.messages == null || !DAO.messages.existsCache(tweet.getPostId())) {
                    // checking against the exist cache is incomplete. A false negative would just cause that a tweet is
                    // indexed again.
                    if (tweet.willBeTimeConsuming()) {
                        executor.execute(tweet);
                        //new Thread(tweet).start();
                        // because the executor may run the thread in the current thread it could be possible that the result is here already
                        if (tweet.isReady()) {
                            timelineReady.add(tweet, user);
                            //DAO.log("SCRAPERTEST: messageINIT is ready");
                        } else {
                            timelineWorking.add(tweet, user);
                            //DAO.log("SCRAPERTEST: messageINIT unshortening");
                        }
                    } else {
                        // no additional thread needed, run the postprocessing in the current thread
                        tweet.run();
                        timelineReady.add(tweet, user);
                    }
                }
                videos = null;
                images = null;
                props.clear();
                continue;
            }
        }
        //for (prop p: props.values()) System.out.println(p);
        br.close();
        return new Timeline[]{timelineReady, timelineWorking};
    }

    public static String[] fetchTwitterVideos(String tweetUrl) {
        // Extract BEARER_TOKEN holding js and Guest token
        String mobileUrl = "https://mobile.twitter.com" + tweetUrl;
        String bearerJsUrl = null;
        String guestToken = null;
        String bearerToken = null;
        try {
            ClientConnection conn = new ClientConnection(mobileUrl);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                if (bearerJsUrl != null && guestToken != null) {
                    // Both the entities are found
                    break;
                }
                if (line.length() == 0) {
                    continue;
                }
                Matcher m = bearerJsUrlRegex.matcher(line);
                if (m.find()) {
                    bearerJsUrl = m.group(1);
                    continue;
                }
                m = guestTokenRegex.matcher(line);
                if (m.find()) {
                    guestToken = m.group(1);
                }
            }
        } catch (IOException e) {
            DAO.severe("Unable to open mobile URL: " + mobileUrl, e);
            return new String[]{};
        }

        // Get BEARER_TOKEN from bearer token holder JS
        try {
            bearerToken = getBearerTokenFromJs(bearerJsUrl);
        } catch (IOException e) {
            DAO.severe("Error while fetching BEARER_TOKEN", e);
            return new String[]{};
        }

        try {
            int slashIndex = tweetUrl.lastIndexOf('/');
            String tweetId = tweetUrl.substring(slashIndex + 1);
            return getConversationVideos(tweetId, bearerToken, guestToken);
        } catch (IOException e) {
            DAO.severe("Error while getting data JSON for Tweet " + tweetUrl, e);
        }
        return new String[]{};
    }

    private static String[] getConversationVideos(String tweetId, String bearerToken, String guestToken) throws IOException {
        String conversationApiUrl = "https://api.twitter.com/2/timeline/conversation/" + tweetId + ".json";
        CloseableHttpClient httpClient = getCustomClosableHttpClient(true);
        HttpGet req = new HttpGet(conversationApiUrl);
        req.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36");
        req.setHeader("Authorization", "Bearer " + bearerToken);
        req.setHeader("x-guest-token", guestToken);
        HttpEntity entity = httpClient.execute(req).getEntity();
        String html = getHTML(entity);
        consumeQuietly(entity);
        try {
            JSONArray arr = (new JSONObject(html)).getJSONObject("globalObjects").getJSONObject("tweets")
                    .getJSONObject(tweetId).getJSONObject("extended_entities").getJSONArray("media");
            JSONObject obj2 = (JSONObject) arr.get(0);
            JSONArray videos = obj2.getJSONObject("video_info").getJSONArray("variants");
            ArrayList<String> urls = new ArrayList<>();
            for (int i = 0; i < videos.length(); i++) {
                String url = ((JSONObject) videos.get(i)).getString("url");
                urls.add(url);
            }
            return urls.toArray(new String[urls.size()]);
        } catch (JSONException e) {
            // This is not an issue. Sometimes, there are videos in long conversations but other ones get media class
            //  div, so this fetching process is triggered.
            DAO.severe("Error while parsing videos from conversation JSON for " + tweetId, e);
        }
        return new String[]{};
    }

    private static String getBearerTokenFromJs(String jsUrl) throws IOException {
        ClientConnection conn = new ClientConnection(jsUrl);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.inputStream, StandardCharsets.UTF_8));
        String line = br.readLine();
        Matcher m = bearerTokenRegex.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        throw new IOException("Couldn't get BEARER_TOKEN");
    }

    /*
     * Filter Posts(here tweets) according to values.
        image: filter tweets with images, neglect 'tweets without images'
        video: filter tweets also having video and other values like image. For only value as video,
               tweets with videos are filtered in prepareUrl() method
     */
    private static boolean filterPosts(
            ArrayList<String> filterList,
            Map<String, prop> props,
            Set<String> videos,
            Set<String> images
    ) {
        Matcher matchVideo1;
        Matcher matchVideo2;
        Pattern[] videoUrlPatterns = {
                Pattern.compile("youtu.be\\/[0-9A-z]+"),
                Pattern.compile("youtube.com\\/watch?v=[0-9A-z]+")
        };

        // filter tweets with videos and others
        if (filterList.contains("video") && filterList.size() > 1) {
            matchVideo1 = videoUrlPatterns[0].matcher(props.get("tweettext").value);
            matchVideo2 = videoUrlPatterns[1].matcher(props.get("tweettext").value);

            if(!matchVideo1.find() && !matchVideo2.find() && videos.size() < 1) {
                return false;
            }
        }

        // filter tweets with images
        if (filterList.contains("image") && images.size() < 1) {
            return false;
        }

        //TODO: Add more filters

        return true;
    }

    private static class prop {
        public String key, value = null;
        public prop(String value) {
            this.key = null;
            this.value = value;
        }
        public prop(String line, int start, String key) {
            this.key = key;
            if (key == null) {
                int p = line.indexOf('>', start);
                if (p > 0) {
                    int c = 1;
                    int q = p + 1;
                    while (c > 0 && q < line.length()) {
                        char a = line.charAt(q);
                        if (a == '<') {
                            if (line.charAt(q+1) != 'i') {
                                if (line.charAt(q+1) == '/') c--; else c++;
                            }
                        }
                        q++;
                    }
                    assert p >= -1;
                    assert q > 0;
                    try {
                        value = line.substring(p + 1, q - 1);
                    } catch (StringIndexOutOfBoundsException e) {
                        DAO.debug(e);
                    }
                }
            } else {
                int p  = line.indexOf(key + "=\"", start);
                if (p >= 0) {
                    int q = line.indexOf('"', p + key.length() + 2);
                    if (q > 0) {
                        value = line.substring(p + key.length() + 2, q);
                    }
                }
            }
        }

        @SuppressWarnings("unused")
        public boolean success() {
            return value != null;
        }

        public String toString() {
            return this.key + "=" + (this.value == null ? "unknown" : this.value);
        }
    }


    final static Pattern hashtag_pattern = Pattern.compile("<a href=\"/hashtag/.*?\".*?class=\"twitter-hashtag.*?\".*?><s>#</s><b>(.*?)</b></a>");
    final static Pattern timeline_link_pattern = Pattern.compile("<a href=\"https://(.*?)\".*? data-expanded-url=\"(.*?)\".*?twitter-timeline-link.*?title=\"(.*?)\".*?>.*?</a>");
    final static Pattern timeline_embed_pattern = Pattern.compile("<a href=\"(https://t.co/\\w+)\" class=\"twitter-timeline-link.*?>pic.twitter.com/(.*?)</a>");
    final static Pattern emoji_pattern = Pattern.compile("<img .*?class=\"Emoji Emoji--forText\".*?alt=\"(.*?)\".*?>");
    final static Pattern doublespace_pattern = Pattern.compile("  ");
    final static Pattern cleanup_pattern = Pattern.compile(
            "</?(s|b|strong)>|" +
                    "<a href=\"/hashtag.*?>|" +
                    "<a.*?class=\"twitter-atreply.*?>|" +
                    "<span.*?span>"
    );

    public static class TwitterTweet extends MessageEntry implements Runnable {

        public final Semaphore ready;
        public UserEntry user;
        public boolean writeToIndex;
        public boolean writeToBackend;

        // a time stamp that is given in loklak upon the arrival of the tweet which is the current local time
        public Date timestampDate;
        // the time given in the tweet which is the time when the user created it.
        // This is also use to do the index partition into minute, hour, week
        public Date created_at;
        // on means 'valid from'
        public Date on;
        // 'to' means 'valid_until' and may not be set
        public Date to;

        // where did the message come from
        protected SourceType source_type;
        // who created the message
        protected ProviderType provider_type;

        protected String provider_hash, screen_name, retweet_from, postId, canonical_id, parent, text;
        protected URL status_id_url;
        protected long retweet_count, favourites_count;
        protected Set<String> images, audios, videos;
        protected String place_name, place_id;

        // the following fields are either set as a common field or generated by extraction from field 'text' or from field 'place_name'
        // coordinate order is [longitude, latitude]
        protected double[] location_point, location_mark;
        // Value in metres
        protected int location_radius;
        protected LocationSource location_source;
        protected PlaceContext place_context;
        protected String place_country;

        // The length of tweets without links, users, hashtags
        // the following can be computed from the tweet data but is stored in the search index
        // to provide statistical data and ranking attributes
        private int without_l_len, without_lu_len, without_luh_len;

        // the arrays of links, users, hashtags
        private List<String> users, hosts, links, mentions, hashtags;

        private boolean enriched;

        public TwitterTweet(
                final String user_screen_name_raw,
                final long created_at_raw,
                // Not used here but should be compared to created_at_raw
                final String created_at_name_raw,
                final String status_id_url_raw,
                final String text_raw,
                final long retweets,
                final long favourites,
                final Set<String> images,
                final Set<String> videos,
                final String place_name,
                final String place_id,
                final UserEntry user,
                final boolean writeToIndex,
                final boolean writeToBackend) throws MalformedURLException {
            super();
            this.source_type = SourceType.TWITTER;
            this.provider_type = ProviderType.SCRAPED;
            this.screen_name = user_screen_name_raw;
            this.created_at = new Date(created_at_raw);
            this.status_id_url = new URL("https://twitter.com" + status_id_url_raw);
            int p = status_id_url_raw.lastIndexOf('/');
            this.postId = p >= 0 ? status_id_url_raw.substring(p + 1) : "-1";
            this.retweet_count = retweets;
            this.favourites_count = favourites;
            this.place_name = place_name;
            this.place_id = place_id;
            this.images = images;
            this.videos = videos;
            this.text = text_raw;
            this.user = user;
            this.writeToIndex = writeToIndex;
            this.writeToBackend = writeToBackend;
            //Date d = new Date(timemsraw);
            //System.out.println(d);

            /* failed to reverse-engineering the place_id :(
            if (place_id.length() == 16) {
                String a = place_id.substring(0, 8);
                String b = place_id.substring(8, 16);
                long an = Long.parseLong(a, 16);
                long bn = Long.parseLong(b, 16);
                System.out.println("place = " + place_name + ", a = " + an + ", b = " + bn);
                // Frankfurt a = 3314145750, b = 3979907708, http://www.openstreetmap.org/#map=15/50.1128/8.6835
                // Singapore a = 1487192992, b = 3578663936
            }
            */

            // this.text MUST be analysed with analyse(); this is not done here because it should be started concurrently; run run();

            this.ready = new Semaphore(0);
        }

        public TwitterTweet(JSONObject json) {
            Object timestamp_obj = lazyGet(json, AbstractObjectEntry.TIMESTAMP_FIELDNAME);
            this.timestampDate = parseDate(timestamp_obj);
            this.timestamp = this.timestampDate.getTime();
            Object created_at_obj = lazyGet(json, AbstractObjectEntry.CREATED_AT_FIELDNAME);
            this.created_at = parseDate(created_at_obj);
            Object on_obj = lazyGet(json, "on");
            this.on = on_obj == null ? null : parseDate(on);
            Object to_obj = lazyGet(json, "to");
            this.to = to_obj == null ? null : parseDate(to);
            String source_type_string = (String) lazyGet(json, "source_type");
            try {
                this.source_type = source_type_string == null ? SourceType.GENERIC : SourceType.byName(source_type_string);
            } catch (IllegalArgumentException e) {
                this.source_type = SourceType.GENERIC;
            }
            String provider_type_string = (String) lazyGet(json, "provider_type");
            if (provider_type_string == null) provider_type_string = ProviderType.NOONE.name();
            try {
                this.provider_type = ProviderType.valueOf(provider_type_string);
            } catch (IllegalArgumentException e) {
                this.provider_type = ProviderType.NOONE;
            }
            this.provider_hash = (String) lazyGet(json, "provider_hash");
            this.screen_name = (String) lazyGet(json, "screen_name");
            this.retweet_from = (String) lazyGet(json, "retweet_from");
            this.postId = (String) lazyGet(json, "post_id");
            this.text = (String) lazyGet(json, "text");
            try {
                this.status_id_url = new URL((String) lazyGet(json, "link"));
            } catch (MalformedURLException e) {
                this.status_id_url = null;
            }
            this.retweet_count = parseLong((Number) lazyGet(json, "retweet_count"));
            this.favourites_count = parseLong((Number) lazyGet(json, "favourites_count"));
            this.images = parseArrayList(lazyGet(json, "images"));
            this.audios = parseArrayList(lazyGet(json, "audio"));
            this.videos = parseArrayList(lazyGet(json, "videos"));
            this.place_id = parseString((String) lazyGet(json, "place_id"));
            this.place_name = parseString((String) lazyGet(json, "place_name"));
            this.place_country = parseString((String) lazyGet(json, "place_country"));
            if (this.place_country != null && this.place_country.length() != 2) this.place_country = null;

            // optional location
            Object location_point_obj = lazyGet(json, "location_point");
            Object location_radius_obj = lazyGet(json, "location_radius");
            Object location_mark_obj = lazyGet(json, "location_mark");
            Object location_source_obj = lazyGet(json, "location_source");
            if (location_point_obj == null || location_mark_obj == null ||
                !(location_point_obj instanceof List<?>) ||
                !(location_mark_obj instanceof List<?>)) {
                this.location_point = null;
                this.location_radius = 0;
                this.location_mark = null;
                this.location_source = null;
            } else {
                this.location_point = new double[]{(Double) ((List<?>) location_point_obj).get(0), (Double) ((List<?>) location_point_obj).get(1)};
                this.location_radius = (int) parseLong((Number) location_radius_obj);
                this.location_mark = new double[]{(Double) ((List<?>) location_mark_obj).get(0), (Double) ((List<?>) location_mark_obj).get(1)};
                this.location_source = LocationSource.valueOf((String) location_source_obj);
            }
            this.enriched = false;

            // load enriched data
            enrich();

            // may lead to error!!
            this.ready = new Semaphore(0);
            //this.user = null;
            //this.writeToIndex = false;
            //this.writeToBackend = false;
        }

        public TwitterTweet() throws MalformedURLException {
            this.timestamp = new Date().getTime();
            this.timestampDate = new Date(this.timestamp);
            this.created_at = new Date();
            this.on = null;
            this.to = null;
            this.source_type = SourceType.GENERIC;
            this.provider_type = ProviderType.NOONE;
            this.provider_hash = "";
            this.screen_name = "";
            this.retweet_from = "";
            this.postId = "";
            this.canonical_id = "";
            this.parent = "";
            this.text = "";
            this.status_id_url = null;
            this.retweet_count = 0;
            this.favourites_count = 0;
            this.images = new HashSet<String>();
            this.audios = new HashSet<String>();
            this.videos = new HashSet<String>();
            this.place_id = "";
            this.place_name = "";
            this.place_context = null;
            this.place_country = null;
            this.location_point = null;
            this.location_radius = 0;
            this.location_mark = null;
            this.location_source = null;
            this.without_l_len = 0;
            this.without_lu_len = 0;
            this.without_luh_len = 0;
            this.hosts = new ArrayList<String>();
            this.links = new ArrayList<String>();
            this.mentions = new ArrayList<String>();
            this.hashtags = new ArrayList<String>();
            this.classifier = null;
            this.enriched = false;

            // may lead to error!!
            this.ready = new Semaphore(0);
            //this.user = null;
            //this.writeToIndex = false;
            //this.writeToBackend = false;
        }

    //TODO: fix the location issue and shift to MessageEntry class
    public void getLocation() {
        if ((this.location_point == null || this.location_point.length == 0) && DAO.geoNames != null) {
            GeoMark loc = null;
            if (place_name != null && this.place_name.length() > 0 &&
                (this.location_source == null || this.location_source == LocationSource.ANNOTATION || this.location_source == LocationSource.PLACE)) {
                loc = DAO.geoNames.analyse(this.place_name, null, 5, Integer.toString(this.text.hashCode()));
                this.place_context = PlaceContext.FROM;
                this.location_source = LocationSource.PLACE;
            }
            if (loc == null) {
                loc = DAO.geoNames.analyse(this.text, this.hashtags.toArray(new String[0]), 5, Integer.toString(this.text.hashCode()));
                this.place_context = PlaceContext.ABOUT;
                this.location_source = LocationSource.ANNOTATION;
            }
            if (loc != null) {
                if (this.place_name == null || this.place_name.length() == 0) this.place_name = loc.getNames().iterator().next();
                this.location_radius = 0;
                this.location_point = new double[]{loc.lon(), loc.lat()}; //[longitude, latitude]
                this.location_mark = new double[]{loc.mlon(), loc.mlat()}; //[longitude, latitude]
                this.place_country = loc.getISO3166cc();
            }
        }
    }

    /**
     * create enriched data, useful for analytics and ranking:
     * - identify all mentioned users, hashtags and links
     * - count message size without links
     * - count message size without links and without users
     */
    public void enrich() {
        if (this.enriched) return;

        enrichData(this.text);

        this.classifier = Classifier.classify(this.text);
        getLocation();

        this.enriched = true;
    }

    public void enrichData(String inputText) {
        StringBuilder text = new StringBuilder(inputText);

        this.links = extractLinks(text.toString());
        text = new StringBuilder(SPACEX_PATTERN.matcher(text).replaceAll(" ").trim());
        // Text's length without link
        this.without_l_len = text.length();

        this.hosts = extractHosts(links);

        this.videos = getLinksVideo(this.links, this.videos);
        this.images = getLinksImage(this.links, this.images);
        this.audios = getLinksAudio(this.links, this.audios);

        this.users = extractUsers(text.toString());
        text = new StringBuilder(SPACEX_PATTERN.matcher(text).replaceAll(" ").trim());
        // Text's length without link and users
        this.without_lu_len = text.length();

        this.mentions = new ArrayList<String>();
        for (int i = 0; i < this.users.size(); i++) {
            this.mentions.add(this.users.get(i).substring(1));
        }

        this.hashtags = extractHashtags(text.toString());
        text = new StringBuilder(SPACEX_PATTERN.matcher(text).replaceAll(" ").trim());
        // Text's length without link, users and hashtags
        this.without_luh_len = text.length();
    }

        @Override
        public void run() {
            //long start = System.currentTimeMillis();
            try {
                //DAO.log("TwitterTweet [" + this.postId + "] start");
                this.text = unshorten(this.text);
                this.user.setName(unshorten(this.user.getName()));
                //DAO.log("TwitterTweet [" + this.postId + "] unshorten after " + (System.currentTimeMillis() - start) + "ms");
                this.enrich();

                //DAO.log("TwitterTweet [" + this.postId + "] enrich    after " + (System.currentTimeMillis() - start) + "ms");
                if (this.writeToIndex) IncomingMessageBuffer.addScheduler(this, this.user, true);
                //DAO.log("TwitterTweet [" + this.postId + "] write     after " + (System.currentTimeMillis() - start) + "ms");
                if (this.writeToBackend) DAO.outgoingMessages.transmitMessage(this, this.user);
                //DAO.log("TwitterTweet [" + this.postId + "] transmit  after " + (System.currentTimeMillis() - start) + "ms");
            } catch (Throwable e) {
                DAO.severe(e);
            } finally {
                this.ready.release(1000);
            }
        }

        public boolean isReady() {
            if (this.ready == null) throw new RuntimeException("isReady() should not be called if postprocessing is not started");
            return this.ready.availablePermits() > 0;
        }

        public boolean waitReady(long millis) {
            if (this.ready == null) throw new RuntimeException("waitReady() should not be called if postprocessing is not started");
            if (this.ready.availablePermits() > 0) return true;
            try {
                return this.ready.tryAcquire(millis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        public Post toJSON() {
                // very important to include calculated data here because that is written
                // into the index using the abstract index factory
                return toJSON(null, true, Integer.MAX_VALUE, "");
            }

        public Post toJSON(final UserEntry user, final boolean calculatedData, final int iflinkexceedslength, final String urlstub) {

            // tweet data
            this.put(AbstractObjectEntry.TIMESTAMP_FIELDNAME, utcFormatter.print(getTimestampDate().getTime()));
            this.put(AbstractObjectEntry.CREATED_AT_FIELDNAME, utcFormatter.print(getCreatedAt().getTime()));
            if (this.on != null) this.put("on", utcFormatter.print(this.on.getTime()));
            if (this.to != null) this.put("to", utcFormatter.print(this.to.getTime()));
            this.put("screen_name", this.screen_name);
            if (this.retweet_from != null && this.retweet_from.length() > 0) this.put("retweet_from", this.retweet_from);
            TextLinkMap tlm = this.getText(iflinkexceedslength, urlstub, this.text, this.getLinks(), this.getPostId());
            this.put("text", tlm); // the tweet; the cleanup is a helper function which cleans mistakes from the past in scraping
            if (this.status_id_url != null) this.put("link", this.status_id_url.toExternalForm());
            this.put("id_str", this.postId);
            if (this.canonical_id != null) this.put("canonical_id", this.canonical_id);
            if (this.parent != null) this.put("parent", this.parent);
            this.put("source_type", this.source_type.toString());
            this.put("provider_type", this.provider_type.name());
            if (this.provider_hash != null && this.provider_hash.length() > 0) this.put("provider_hash", this.provider_hash);
            this.put("retweet_count", this.retweet_count);
            // there is a slight inconsistency here in the plural naming but thats how it is noted in the twitter api
            this.put("favourites_count", this.favourites_count);
            this.put("place_name", this.place_name);
            this.put("place_id", this.place_id);

            // add statistic/calculated data
            if (calculatedData) {

                // text length
                this.put("text_length", this.text.length());

                // location data
                if (this.place_context != null) this.put("place_context", this.place_context.name());
                if (this.place_country != null && this.place_country.length() == 2) {
                    this.put("place_country", DAO.geoNames.getCountryName(this.place_country));
                    this.put("place_country_code", this.place_country);
                    this.put("place_country_center", DAO.geoNames.getCountryCenter(this.place_country));
                }

                // add optional location data. This is written even if calculatedData == false if
                // the source is from REPORT to prevent that it is lost
                if (this.location_point != null && this.location_point.length == 2
                        && this.location_mark != null && this.location_mark.length == 2) {
                    // reference for this format:
                    // https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-geo-point-type.html#_lat_lon_as_array_5
                    this.put("location_point", this.location_point); // [longitude, latitude]
                    this.put("location_radius", this.location_radius);
                    this.put("location_mark", this.location_mark);
                    this.put("location_source", this.location_source.name());
                }

                // redundant data for enhanced navigation with aggregations
                this.put("hosts", this.hosts);
                this.put("hosts_count", this.hosts.size());
                this.put("links", this.links);
                this.put("links_count", this.links.size());
                this.put("unshorten", tlm.short2long);
                this.put("images", this.images);
                this.put("images_count", this.images.size());
                this.put("audio", this.audios);
                this.put("audio_count", this.audios.size());
                this.put("videos", this.videos);
                this.put("videos_count", this.videos.size());
                this.put("mentions", this.mentions);
                this.put("mentions_count", this.mentions.size());
                this.put("hashtags", this.hashtags);
                this.put("hashtags_count", this.hashtags.size());


                // experimental, for ranking
                this.put("without_l_len", this.without_l_len);
                this.put("without_lu_len", this.without_lu_len);
                this.put("without_luh_len", this.without_luh_len);
            }

            // text classifier
            if (this.classifier != null) {
                for (Map.Entry<Context, Classification<String, Category>> c: this.classifier.entrySet()) {
                    assert c.getValue() != null;
                    if (c.getValue().getCategory() == Classifier.Category.NONE) continue; // we don't store non-existing classifications
                    this.put("classifier_" + c.getKey().name(), c.getValue().getCategory());
                    this.put("classifier_" + c.getKey().name() + "_probability",
                        c.getValue().getProbability() == Float.POSITIVE_INFINITY ? Float.MAX_VALUE : c.getValue().getProbability());
                }
            }

            // add user
            if (user != null) this.put("user", user.toJSON());
            return this;
        }

        public UserEntry getUser() {
            return this.user;
        }

        public boolean willBeTimeConsuming() {
            return timeline_link_pattern.matcher(this.text).find();
        }

        public Date getTimestampDate() {
            return this.timestampDate == null ? new Date() : this.timestampDate;
        }

        public Date getCreatedAt() {
            return this.created_at == null ? new Date() : this.created_at;
        }

        public void setCreatedAt(Date created_at) {
            this.created_at = created_at;
        }

        public Date getOn() {
            return this.on;
        }

        public void setOn(Date on) {
            this.on = on;
        }

        public Date getTo() {
            return this.to;
        }

        public void setTo(Date to) {
            this.to = to;
        }

        public SourceType getSourceType() {
            return this.source_type;
        }

        public void setSourceType(SourceType source_type) {
            this.source_type = source_type;
        }

        public ProviderType getProviderType() {
            return provider_type;
        }

        public void setProviderType(ProviderType provider_type) {
            this.provider_type = provider_type;
        }

        public String getProviderHash() {
            return provider_hash;
        }

        public void setProviderHash(String provider_hash) {
            this.provider_hash = provider_hash;
        }

        public String getScreenName() {
            return screen_name;
        }

        public void setScreenName(String user_screen_name) {
            this.screen_name = user_screen_name;
        }

        public String getRetweetFrom() {
            return this.retweet_from;
        }

        public void setRetweetFrom(String retweet_from) {
            this.retweet_from = retweet_from;
        }

        public URL getStatusIdUrl() {
            return this.status_id_url;
        }

        public void setStatusIdUrl(URL status_id_url) {
            this.status_id_url = status_id_url;
        }

        public long getRetweetCount() {
            return retweet_count;
        }

        public void setRetweetCount(long retweet_count) {
            this.retweet_count = retweet_count;
        }

        public long getFavouritesCount() {
            return this.favourites_count;
        }

        public void setFavouritesCount(long favourites_count) {
            this.favourites_count = favourites_count;
        }

        public String getPlaceName() {
            return place_name;
        }

        public void setPlaceName(String place_name, PlaceContext place_context) {
            this.place_name = place_name;
            this.place_context = place_context;
        }

        public PlaceContext getPlaceContext () {
            return place_context;
        }

        public String getPlaceId() {
            return place_id;
        }

        public void setPlaceId(String place_id) {
            this.place_id = place_id;
        }

        /**
         * @return [longitude, latitude]
         */
        public double[] getLocationPoint() {
            return location_point;
        }

        /**
         * set the location
         * @param location_point in the form [longitude, latitude]
         */
        public void setLocationPoint(double[] location_point) {
            this.location_point = location_point;
        }

        public String getPostId() {
            return String.valueOf(this.postId);
        }

        //TODO: to implement this method
        private void setPostId() {
            this.postId = String.valueOf(this.timestamp) + String.valueOf(this.created_at.getTime());
        }

        /**
         * @return [longitude, latitude] which is inside of getLocationRadius() from getLocationPoint()
         */
        public double[] getLocationMark() {
            return location_mark;
        }

        /**
         * Set the location
         * @param location_point in the form [longitude, latitude]
         */
        public void setLocationMark(double[] location_mark) {
            this.location_mark = location_mark;
        }

        /**
         * Get the radius in meter
         * @return radius in meter around getLocationPoint() (NOT getLocationMark())
         */
        public int getLocationRadius() {
            return location_radius;
        }

        public void setLocationRadius(int location_radius) {
            this.location_radius = location_radius;
        }

        public LocationSource getLocationSource() {
            return location_source;
        }

        public void setLocationSource(LocationSource location_source) {
            this.location_source = location_source;
        }

        public String getText() {
            return this.text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getTextLength() {
            return this.text.length();
        }

        public long getId() {
            return Long.parseLong(this.postId);
        }

        public List<String> getHosts() {
            return this.hosts;
        }

        public Collection<String> getVideos() {
            return this.videos;
        }

        public Collection<String> getAudio() {
            return this.audios;
        }

        public Collection<String> getImages() {
            return this.images;
        }

        public void setImages(String image) {
            if(this.images == null) {
                this.images = new HashSet<String>();
            }
            this.images.add(image);
        }

        public String[] getMentions() {
            return this.mentions.toArray(new String[0]);
        }

        public String[] getHashtags() {
            return this.hashtags.toArray(new String[0]);
        }

        public String[] getLinks() {
            return this.links.toArray(new String[0]);
        }

    }

    public static String unshorten(String text) {
        while (true) {
            try {
                Matcher m = emoji_pattern.matcher(text);
                if (m.find()) {
                    String emoji = m.group(1);
                    text = m.replaceFirst(emoji);
                    continue;
                }
            } catch (Throwable e) {
                DAO.severe(e);
                break;
            }
            try {
                Matcher m = emoji_pattern_span.matcher(text);
                if (m.find()) {
                    String emoji = m.group(1);
                    text = m.replaceFirst(emoji);
                    continue;
                }
            } catch (Throwable e) {
                DAO.severe(e);
                break;
            }
            try {
                Matcher m = hashtag_pattern.matcher(text);
                if (m.find()) {
                    text = m.replaceFirst(" #" + m.group(1) + " "); // the extra spaces are needed because twitter removes them if the hashtag is followed with a link
                    continue;
                }
            } catch (Throwable e) {
                DAO.severe(e);
                break;
            }
            try {
                Matcher m = timeline_link_pattern.matcher(text);
                if (m.find()) {
                    String expanded = RedirectUnshortener.unShorten(m.group(2));
                    text = m.replaceFirst(" " + expanded);
                    continue;
                }
            } catch (Throwable e) {
                DAO.severe(e);
                break;
            }
            try {
                Matcher m = timeline_embed_pattern.matcher(text);
                if (m.find()) {
                    text = m.replaceFirst("");
                    continue;
                }
            } catch (Throwable e) {
                DAO.severe(e);
                break;
            }
            break;
        }
        text = cleanup_pattern.matcher(text).replaceAll("");
        text = MessageEntry.html2utf8(text);
        text = doublespace_pattern.matcher(text).replaceAll(" ");
        text = text.trim();
        return text;
    }

    /**
     * Usage: java twitter4j.examples.search.SearchTweets [query]
     *
     * @param args search query
     */
    public static void main(String[] args) {
        //wget --no-check-certificate "https://twitter.com/search?q=eifel&src=typd&f=realtime"
        ArrayList<String> filterList = new ArrayList<String>();
        filterList.add("image");
        Timeline[] result = null;
        if (args[0].startsWith("/"))
            result = parse(new File(args[0]),Timeline.Order.CREATED_AT, true, true);
        else
            result = TwitterScraper.search(args[0], filterList, Timeline.Order.CREATED_AT, true, true);
        int all = 0;
        for (int x = 0; x < 2; x++) {
            if (x == 0) System.out.println("Timeline[0] - finished to be used:");
            if (x == 1) System.out.println("Timeline[1] - messages which are in postprocessing");
            all += result[x].size();
            for (TwitterTweet tweet : result[x]) {
                    tweet.waitReady(10000);
                System.out.println(tweet.getCreatedAt().toString() + " from @" + tweet.getScreenName() + " - " + tweet.getText());
            }
        }
        System.out.println("count: " + all);
        System.exit(0);
    }

}

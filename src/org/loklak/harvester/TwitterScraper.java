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
import java.util.LinkedHashSet;
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
        for (MessageEntry me: tl[1]) {
            assert me instanceof TwitterTweet;
            TwitterTweet tt = (TwitterTweet) me;
            long remainingWait = Math.max(10, timeout - System.currentTimeMillis());
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
        Set<String> images = new LinkedHashSet<>();
        Set<String> videos = new LinkedHashSet<>();
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
                ArrayList<String> imgs = new ArrayList<String>(images.size()); imgs.addAll(images);
                ArrayList<String> vids = new ArrayList<String>(videos.size()); vids.addAll(videos);
                videos.clear();

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
                        imgs, vids, place_name, place_id,
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
                images.clear();
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

        private final Semaphore ready;
        private UserEntry user;
        private boolean writeToIndex;
        private boolean writeToBackend;

        public TwitterTweet(
                final String user_screen_name_raw,
                final long created_at_raw,
                final String created_at_name_raw, // not used here but should be compared to created_at_raw
                final String status_id_url_raw,
                final String text_raw,
                final long retweets,
                final long favourites,
                final Collection<String> images,
                final Collection<String> videos,
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
            this.images = new LinkedHashSet<>(); for (String image: images) this.images.add(image);
            this.videos = new LinkedHashSet<>(); for (String video: videos) this.videos.add(video);
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

        public UserEntry getUser() {
            return this.user;
        }

        public boolean willBeTimeConsuming() {
            return timeline_link_pattern.matcher(this.text).find();
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
            for (MessageEntry tweet : result[x]) {
                if (tweet instanceof TwitterTweet) {
                    ((TwitterTweet) tweet).waitReady(10000);
                }
                System.out.println(tweet.getCreatedAt().toString() + " from @" + tweet.getScreenName() + " - " + tweet.getText());
            }
        }
        System.out.println("count: " + all);
        System.exit(0);
    }

}

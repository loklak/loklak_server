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

import org.eclipse.jetty.util.log.Log;
import org.loklak.data.DAO;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.http.ClientConnection;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.ProviderType;
import org.loklak.objects.SourceType;
import org.loklak.objects.Timeline;
import org.loklak.objects.UserEntry;

public class TwitterScraper {

    public final static ExecutorService executor = Executors.newFixedThreadPool(40);
    public final static Pattern emoji_pattern_span = Pattern.compile("<span [^>]*class=\"Emoji Emoji--forLinks\" [^>]*>[\\n]*[^<]*</span>[\\n]*<span [^>]*class=\"visuallyhidden\" [^>]*aria-hidden=\"true\"[^>]*>[\\n]*([^<]*)[\\n]*</span>");

    public static Timeline search(
            final String query,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend,
            int jointime) {
        Timeline[] tl = search(query, order, writeToIndex, writeToBackend);
        long timeout = System.currentTimeMillis() + jointime;
        for (MessageEntry me: tl[1]) {
            assert me instanceof TwitterTweet;
            TwitterTweet tt = (TwitterTweet) me;
            long remainingWait = Math.max(10, timeout - System.currentTimeMillis());
            if (tt.waitReady(remainingWait)) tl[0].add(tt, tt.getUser()); // double additions are detected
        }
        return tl[0];
    }

    public static String prepareSearchURL(final String query) {
        // check
        // https://twitter.com/search-advanced for a better syntax
        // https://support.twitter.com/articles/71577-how-to-use-advanced-twitter-search#
        String https_url = "";
        try {
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
            //https://twitter.com/search?f=tweets&vertical=default&q=kaffee&src=typd
            https_url = "https://twitter.com/search?f=tweets&vertical=default&q=" + q + "&src=typd";
        } catch (UnsupportedEncodingException e) {}
        return https_url;
    }
    
    public static Timeline[] search(
            final String query,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        // check
        // https://twitter.com/search-advanced for a better syntax
        // https://support.twitter.com/articles/71577-how-to-use-advanced-twitter-search#
        String https_url = prepareSearchURL(query);
        Timeline[] timelines = null;
        try {
            ClientConnection connection = new ClientConnection(https_url);
            if (connection.inputStream == null) return null;
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.inputStream, StandardCharsets.UTF_8));
                timelines = search(br, order, writeToIndex, writeToBackend);
            } catch (IOException e) {
            	Log.getLog().warn(e);
            } finally {
                connection.close();
            }
        } catch (IOException e) {
            // this could mean that twitter rejected the connection (DoS protection?) or we are offline (we should be silent then)
            // Log.getLog().warn(e);
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
    
    public static Timeline[] parse(
            final File file,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        Timeline[] timelines = null;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            timelines = search(br, order, writeToIndex, writeToBackend);
        } catch (IOException e) {
        	Log.getLog().warn(e);
        } finally {
            if (timelines == null) timelines = new Timeline[]{new Timeline(order), new Timeline(order)};
        }

        if (timelines[0] != null) timelines[0].setScraperInfo("local");
        if (timelines[1] != null) timelines[1].setScraperInfo("local");
        return timelines;
    }
    
    /**
     * scrape messages from the reader stream: this already checks if a message is new. There are only new messages returned
     * @param br
     * @param order
     * @return two timelines in one array: Timeline[0] is the one which is finished to be used, Timeline[1] contains messages which are in postprocessing
     * @throws IOException
     */
    public static Timeline[] search(
            final BufferedReader br,
            final Timeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) throws IOException {
        Timeline timelineReady = new Timeline(order);
        Timeline timelineWorking = new Timeline(order);
        String input;
        Map<String, prop> props = new HashMap<String, prop>();
        Set<String> images = new LinkedHashSet<>();
        Set<String> videos = new LinkedHashSet<>();
        String place_id = "", place_name = "";
        boolean parsing_favourite = false, parsing_retweet = false;
        int line = 0; // first line is 1, according to emacs which numbers the first line also as 1
        boolean debuglog = true;
        while ((input = br.readLine()) != null){
            line++;
            input = input.trim();
            if (input.length() == 0) continue;
            
            // debug
            if (debuglog) System.out.println(line + ": " + input);            
            //if (input.indexOf("ProfileTweet-actionCount") > 0) System.out.println(input);

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
                    System.out.println("strange image url: " + image_url);
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
                        System.out.println("strange image url: " + image_url);
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
            if (props.size() == 10 || (debuglog  && props.size() > 4 && input.indexOf("stream-item") > 0 /* li class="js-stream-item" starts a new tweet */)) {
                // the tweet is complete, evaluate the result
                if (debuglog) System.out.println("*** line " + line + " propss.size() = " + props.size());
                prop userid = props.get("userid"); if (userid == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value userid"); continue;}
                prop usernickname = props.get("usernickname"); if (usernickname == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value usernickname"); continue;}
                prop useravatarurl = props.get("useravatarurl"); if (useravatarurl == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value useravatarurl"); continue;}
                prop userfullname = props.get("userfullname"); if (userfullname == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value userfullname"); continue;}
                UserEntry user = new UserEntry(
                        userid.value,
                        usernickname.value,
                        useravatarurl.value,
                        MessageEntry.html2utf8(userfullname.value)
                        );
                ArrayList<String> imgs = new ArrayList<String>(images.size()); imgs.addAll(images);
                ArrayList<String> vids = new ArrayList<String>(videos.size()); vids.addAll(videos);
                prop tweettimems = props.get("tweettimems"); if (tweettimems == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value tweettimems"); continue;}
                prop tweetretweetcount = props.get("tweetretweetcount"); if (tweetretweetcount == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value tweetretweetcount"); continue;}
                prop tweetfavouritecount = props.get("tweetfavouritecount"); if (tweetfavouritecount == null) {if (debuglog) System.out.println("*** line " + line + " MISSING value tweetfavouritecount"); continue;}
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
                if (DAO.messages == null || !DAO.messages.existsCache(tweet.getIdStr())) {
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
    
    public static class prop {
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
                        Log.getLog().debug(e);
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
        private boolean writeToIndex, writeToBackend;
        
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
            this.id_str = p >= 0 ? status_id_url_raw.substring(p + 1) : "-1";
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
            return timeline_link_pattern.matcher(this.text).find() || timeline_embed_pattern.matcher(this.text).find();
        }
        
        @Override
        public void run() {
            //long start = System.currentTimeMillis();
            try {
                //DAO.log("TwitterTweet [" + this.id_str + "] start");
                this.text = unshorten(this.text);
                this.user.setName(unshorten(this.user.getName()));
                //DAO.log("TwitterTweet [" + this.id_str + "] unshorten after " + (System.currentTimeMillis() - start) + "ms");
                this.enrich();
                //DAO.log("TwitterTweet [" + this.id_str + "] enrich    after " + (System.currentTimeMillis() - start) + "ms");
                if (this.writeToIndex) IncomingMessageBuffer.addScheduler(this, this.user, true);
                //DAO.log("TwitterTweet [" + this.id_str + "] write     after " + (System.currentTimeMillis() - start) + "ms");
                if (this.writeToBackend) DAO.outgoingMessages.transmitMessage(this, this.user);
                //DAO.log("TwitterTweet [" + this.id_str + "] transmit  after " + (System.currentTimeMillis() - start) + "ms");
            } catch (Throwable e) {
            	Log.getLog().warn(e);
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
            	Log.getLog().warn(e);
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
                Log.getLog().warn(e);
                break;
            }
            try {
                Matcher m = hashtag_pattern.matcher(text);
                if (m.find()) {
                    text = m.replaceFirst(" #" + m.group(1) + " "); // the extra spaces are needed because twitter removes them if the hashtag is followed with a link
                    continue;
                }
            } catch (Throwable e) {
            	Log.getLog().warn(e);
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
            	Log.getLog().warn(e);
                break;
            }
            try {
                Matcher m = timeline_embed_pattern.matcher(text);
                if (m.find()) {
                    String shorturl = RedirectUnshortener.unShorten(m.group(2));
                    text = m.replaceFirst(" https://pic.twitter.com/" + shorturl + " ");
                    continue;
                }
            } catch (Throwable e) {
            	Log.getLog().warn(e);
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
        
        Timeline[] result = null;
        if (args[0].startsWith("/"))
            result = parse(new File(args[0]),Timeline.Order.CREATED_AT, true, true);
        else
            result = TwitterScraper.search(args[0], Timeline.Order.CREATED_AT, true, true);
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



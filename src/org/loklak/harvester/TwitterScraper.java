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

import org.loklak.objects.AbstractObjectEntry;
import org.loklak.objects.BasicTimeline.Order;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.loklak.data.Classifier;
import org.loklak.data.DAO;
import org.loklak.data.Classifier.Category;
import org.loklak.data.Classifier.Context;
import org.loklak.geo.GeoMark;
import org.loklak.geo.LocationSource;
import org.loklak.objects.QueryEntry.PlaceContext;
import org.loklak.tools.bayes.Classification;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.http.ClientConnection;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.ProviderType;
import org.loklak.objects.SourceType;
import org.loklak.objects.TwitterTimeline;
import org.loklak.objects.UserEntry;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TwitterScraper {

    public static final ExecutorService executor = Executors.newFixedThreadPool(40);
    public static final Pattern emoji_pattern_span = Pattern.compile("<span [^>]*class=\"Emoji Emoji--forLinks\" [^>]*>[\\n]*[^<]*</span>[\\n]*<span [^>]*class=\"visuallyhidden\" [^>]*aria-hidden=\"true\"[^>]*>[\\n]*([^<]*)[\\n]*</span>");

    public static TwitterTimeline search(
            final String query,
            final Set<String> filterList,
            final TwitterTimeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend,
            int jointime) {
        TwitterTimeline[] tl = search(query, filterList, order, writeToIndex, writeToBackend);
        long timeout = System.currentTimeMillis() + jointime;
        long remainingWait = 0;
        for (TwitterTweet tt: tl[1]) {
            remainingWait = Math.max(10, timeout - System.currentTimeMillis());
            if (tt.waitReady(remainingWait)) {
                 // double additions are detected
                tl[0].add(tt, tt.getUser());
            }
        }
        return tl[0];
    }

    public static TwitterTimeline search(
            final String query,
            final TwitterTimeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend,
            int jointime) {

        return search(query, new HashSet<>(), order, writeToIndex, writeToBackend, jointime);
    }

    private enum FilterType {
        tweets, image, video, geo, links, mentions, hashtags, media;
    }
    
    private static String prepareSearchUrl(final String query, FilterType type) {
        // check https://twitter.com/search-advanced for a better syntax
        // https://support.twitter.com/articles/71577-how-to-use-advanced-twitter-search#

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
        if (type == FilterType.image && t.indexOf("has:images") < 0) t.append(" has:images");
        if (type == FilterType.video && t.indexOf("has:videos") < 0) t.append(" has:videos");
        
        String q;
        try {
            q = t.length() == 0 ? "*" : URLEncoder.encode(t.substring(1), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            q = t.length() == 0 ? "*" : t.substring(1);
        }

        // building url
        // see https://github.com/bisguzar/twitter-scraper/blob/master/twitter_scraper/modules/tweets.py#L12-L29
        return "https://twitter.com/i/search/timeline?f=tweets&vertical=default&q=" + q + "&src=typd&reset_error_state=false&include_available_features=1&include_entities=1&include_new_items_bar=true";
    }

    private static TwitterTimeline[] search(
            final String query,
            final Set<String> filterList,
            final TwitterTimeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) {
        // check
        // https://twitter.com/search-advanced for a better syntax
        // https://support.twitter.com/articles/71577-how-to-use-advanced-twitter-search#
        FilterType tt = FilterType.tweets;
        if (filterList.contains("image")) tt = FilterType.image;
        if (filterList.contains("video")) tt = FilterType.video;
        String https_url = prepareSearchUrl(query, tt);
        TwitterTimeline[] timelines = null;
        try {
            ClientConnection connection = new ClientConnection(https_url, query);
            if (connection.getInputStream() == null) return null;
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

                JSONTokener t = new JSONTokener(br);
                JSONObject j = new JSONObject(t);
                String html = j.getString("items_html");
                Document doc = Jsoup.parse(html);
                
                timelines = search(doc, filterList, order, writeToIndex, writeToBackend);
            } catch (IOException e) {
                DAO.severe(e);
            } finally {
                connection.close();
            }
            /*
            HtmlUnitLoader loader = new HtmlUnitLoader(https_url, "scraper");
            ByteArrayInputStream bais = new ByteArrayInputStream(loader.getXml().getBytes(StandardCharsets.UTF_8));
            BufferedReader br = new BufferedReader(new InputStreamReader(bais, StandardCharsets.UTF_8));
            timelines = search(br, filterList, order, writeToIndex, writeToBackend);
            */
        } catch (IOException e) {
            // this could mean that twitter rejected the connection (DoS protection?) or we are offline (we should be silent then)
            // DAO.severe(e);
            if (timelines == null) timelines = new TwitterTimeline[]{new TwitterTimeline(order), new TwitterTimeline(order)};
        };

        // wait until all messages in the timeline are ready
        if (timelines == null) {
            // timeout occurred
            timelines = new TwitterTimeline[]{new TwitterTimeline(order), new TwitterTimeline(order)};
        }
        if (timelines != null) {
            if (timelines[0] != null) timelines[0].setScraperInfo("local");
            if (timelines[1] != null) timelines[1].setScraperInfo("local");
        }
        return timelines;
    }

    /**
     * scrape messages from the reader stream: this already checks if a message is new. There are only new messages returned
     * @param br
     * @param order
     * @return two timelines in one array: Timeline[0] is the one which is finished to be used, Timeline[1] contains messages which are in postprocessing
     * @throws IOException
     */
    private static TwitterTimeline[] search(
            final Document doc,
            final Set<String> filterList,
            final TwitterTimeline.Order order,
            final boolean writeToIndex,
            final boolean writeToBackend) throws IOException {
        TwitterTimeline timelineReady = new TwitterTimeline(order);
        TwitterTimeline timelineWorking = new TwitterTimeline(order);
        Set<String> images = null;
        Set<String> videos = null;
        String place_id = "";
        String place_name = "";
        Set<String> mentions = new LinkedHashSet<>();
        boolean debuglog = DAO.getConfig("flag.debug.twitter_scraper", "false").equals("true");

        // parse
        Elements items = doc.getElementsByClass("stream-item");
        for (int itemc = 0; itemc < items.size(); itemc++) {
            Element item = items.get(itemc);
            if (debuglog) System.out.println(item.toString());
    
            Elements streamitem = item.getElementsByClass("stream-item");
            Element tweeti = streamitem.get(0).child(0);
            String tweetID = tweeti.attr("data-tweet-id");
            String conversationID = tweeti.attr("data-conversation-id"); // this is the parent in a conversation
            String mentions_screenname = tweeti.attr("data-mentions"); // the conversationID does not have a link to the user name which produces the actual tweet link. This here is a hint. But several names can be listed here.
            // the first nam in mentions_screenname is the latest in the conversation chain
            Set<String> mentions_screennames = new LinkedHashSet<>(); // to preserve the order we use a linked hash set
            String[] mss= mentions_screenname.split(" ");
            for (int i = 0; i < mss.length; i++) mentions_screennames.add(mss[i]);
            if (conversationID != null && conversationID.equals(tweetID)) {conversationID = ""; mentions_screennames.clear();}
            
            Elements profile = item.getElementsByClass("js-profile-popup-actionable");
            Elements avatare = item.getElementsByClass("js-action-profile-avatar");
            
            String userid = profile.attr("data-user-id");
            String usernickname = profile.attr("data-screen-name");
            String useravatarurl = avatare.attr("src");
            String userfullname = profile.attr("data-name");
            
            UserEntry user = new UserEntry(
                    userid,
                    usernickname,
                    useravatarurl,
                    MessageEntry.html2utf8(userfullname)
            );
    
            Elements timestamp = item.getElementsByClass("_timestamp");
            String rawtext = item.getElementsByClass("tweet-text").text();
            if (mentions_screennames.size() > 0) {
                // attention: this turns the order around! First entry will be conversation starter!
                for (String screenname: mentions_screennames) {
                    if (rawtext.indexOf(screenname) < 0) rawtext = "@" + screenname + " " + rawtext;
                }
            }
            
            String tweettimes = timestamp.attr("data-time-ms");
            long tweettime = Long.parseLong(tweettimes);
            long snowflaketime = snowflake2millis(Long.parseLong(tweetID));
            assert tweettime / 1000 == snowflaketime / 1000;
            
            Elements reply = item.getElementsByClass("ProfileTweet-action--reply").get(0).children();
            Elements retweet = item.getElementsByClass("ProfileTweet-action--retweet").get(0).children();
            Elements favourite = item.getElementsByClass("ProfileTweet-action--favorite").get(0).children();
            String tweetreplycount = reply.attr("data-tweet-stat-count");
            String tweetretweetcount = retweet.attr("data-tweet-stat-count");
            String tweetfavouritecount = favourite.attr("data-tweet-stat-count");
            
            String tweetstatusurl = item.getElementsByClass("tweet-timestamp").attr("href");        
            // evaluate
            
            // patch text with non-mentioned mentions (twitter obviously cuts that away)
            mentions.remove(user.getScreenName());
            for (String mention: mentions) {
                String m = "@" + mention;
                if (rawtext.indexOf(m) < 0) rawtext = rawtext + " " + m;
            }
            
            TwitterTweet tweet;
            try {
                tweet = new TwitterTweet(
                        user.getScreenName(),
                        tweettime,
                        tweetID, // like 1284585691259850753
                        conversationID, // like 1284532773563359233
                        mentions_screennames,
                        tweetstatusurl, // like /yacy_search/status/1284585691259850753
                        rawtext,
                        Long.parseLong(tweetreplycount),
                        Long.parseLong(tweetretweetcount),
                        Long.parseLong(tweetfavouritecount),
                        images, videos, place_name, place_id,
                        user, writeToIndex, writeToBackend
                );
    
                place_id = "";
                place_name = "";
                mentions.clear();
                videos = null;
                images = null;
            } catch (NullPointerException e) {
                DAO.severe(e);
                tweet = null;
            }
            
            //if (DAO.messages == null || !DAO.messages.existsCache(tweet.getPostId())) {
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
            //}
        }

        return new TwitterTimeline[]{timelineReady, timelineWorking};
    }

    private static long snowflake2millis(long sf) {
        return (sf >> 22) + 1288834974657L;
    }
    
    private final static Pattern hashtag_pattern = Pattern.compile("<a href=\"/hashtag/.*?\".*?class=\"twitter-hashtag.*?\".*?><s>#</s><b>(.*?)</b></a>");
    private final static Pattern timeline_link_pattern = Pattern.compile("<a href=\"https://(.*?)\".*? data-expanded-url=\"(.*?)\".*?twitter-timeline-link.*?title=\"(.*?)\".*?>.*?</a>");
    private final static Pattern timeline_embed_pattern = Pattern.compile("<a href=\"(https://t.co/\\w+)\" class=\"twitter-timeline-link.*?>pic.twitter.com/(.*?)</a>");
    private final static Pattern emoji_pattern = Pattern.compile("<img .*?class=\"Emoji Emoji--forText\".*?alt=\"(.*?)\".*?>");
    private final static Pattern doublespace_pattern = Pattern.compile("  ");
    private final static Pattern cleanup_pattern = Pattern.compile(
            "</?(s|b|strong)>|" +
                    "<a href=\"/hashtag.*?>|" +
                    "<a.*?class=\"twitter-atreply.*?>|" +
                    "<span.*?span>"
    );

    public static class TwitterTweet extends Post implements Runnable {

        public final Semaphore ready;
        public MessageEntry moreData = new MessageEntry();
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

        public String provider_hash, screen_name, retweet_from, postId, conversationID, canonical_id, parent, text;
        public Set<String> conversationUserIDs;
        protected URL status_id_url;
        protected long reply_count, retweet_count, favourite_count;
        public Set<String> images, audios, videos;
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
                final String tweetID, // the ID of the tweet
                final String tweetConversationID, // the ID of a tweet where this is a reply-to tweet
                final Set<String> mentions_screennames,
                final String status_id_url_raw,
                final String text_raw,
                final long replies,
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
            assert this.postId.equals(tweetID);
            this.conversationID = tweetConversationID;
            this.conversationUserIDs = mentions_screennames;
            this.reply_count = replies;
            this.retweet_count = retweets;
            this.favourite_count = favourites;
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
            this.moreData = new MessageEntry();
            Object timestamp_obj = lazyGet(json, AbstractObjectEntry.TIMESTAMP_FIELDNAME);
            this.timestampDate = MessageEntry.parseDate(timestamp_obj);
            this.timestamp = this.timestampDate.getTime();
            Object created_at_obj = lazyGet(json, AbstractObjectEntry.CREATED_AT_FIELDNAME);
            this.created_at = MessageEntry.parseDate(created_at_obj);
            Object on_obj = lazyGet(json, "on");
            this.on = on_obj == null ? null : MessageEntry.parseDate(on);
            Object to_obj = lazyGet(json, "to");
            this.to = to_obj == null ? null : MessageEntry.parseDate(to);
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
            this.postId = (String) lazyGet(json, "id_str");
            this.conversationID = (String) lazyGet(json, "conversationid_str");
            this.conversationUserIDs = MessageEntry.parseArrayList(lazyGet(json, "conversation_user"));
            this.text = (String) lazyGet(json, "text");
            try {
                this.status_id_url = new URL((String) lazyGet(json, "link"));
            } catch (MalformedURLException e) {
                this.status_id_url = null;
            }
            this.reply_count = MessageEntry.parseLong((Number) lazyGet(json, "reply_count"));
            this.retweet_count = MessageEntry.parseLong((Number) lazyGet(json, "retweet_count"));
            this.favourite_count = MessageEntry.parseLong((Number) lazyGet(json, "favourites_count")); // inconsitency in naming, but twitter api defines so
            this.images = MessageEntry.parseArrayList(lazyGet(json, "images"));
            this.audios = MessageEntry.parseArrayList(lazyGet(json, "audio"));
            this.videos = MessageEntry.parseArrayList(lazyGet(json, "videos"));
            this.place_id = MessageEntry.parseString((String) lazyGet(json, "place_id"));
            this.place_name = MessageEntry.parseString((String) lazyGet(json, "place_name"));
            this.place_country = MessageEntry.parseString((String) lazyGet(json, "place_country"));

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
                this.location_radius = (int) MessageEntry.parseLong((Number) location_radius_obj);
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
            this.moreData = new MessageEntry();
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
            this.conversationID = "";
            this.conversationUserIDs = new LinkedHashSet<>();
            this.canonical_id = "";
            this.parent = "";
            this.text = "";
            this.status_id_url = null;
            this.reply_count = 0;
            this.retweet_count = 0;
            this.favourite_count = 0;
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
            this.moreData.classifier = null;
            this.enriched = false;

            // may lead to error!!
            this.ready = new Semaphore(0);
            //this.user = null;
            //this.writeToIndex = false;
            //this.writeToBackend = false;
        }

        //TODO: fix the location issue and shift to MessageEntry class
        public void getLocation() {
            if (this.text == null) this.text = "";

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
            this.moreData.classifier = Classifier.classify(this.text);
            enrichData(this.text);
            getLocation();

            this.enriched = true;
        }

        public void enrichData(String inputText) {
            if (inputText == null) inputText = "";
            StringBuilder text = new StringBuilder(inputText);
            this.links = this.moreData.extractLinks(text.toString());
            text = new StringBuilder(MessageEntry.SPACEX_PATTERN.matcher(text).replaceAll(" ").trim());
            // Text's length without link
            this.without_l_len = text.length();

            this.hosts = this.moreData.extractHosts(links);

            this.videos = this.moreData.getLinksVideo(this.links, this.videos);
            this.images = this.moreData.getLinksImage(this.links, this.images);
            this.audios = this.moreData.getLinksAudio(this.links, this.audios);

            this.users = this.moreData.extractUsers(text.toString());
            text = new StringBuilder(MessageEntry.SPACEX_PATTERN.matcher(text).replaceAll(" ").trim());
            // Text's length without link and users
            this.without_lu_len = text.length();

            this.mentions = new ArrayList<String>();
            for (int i = 0; i < this.users.size(); i++) {
                this.mentions.add(this.users.get(i).substring(1));
            }

            this.hashtags = this.moreData.extractHashtags(text.toString());
            text = new StringBuilder(MessageEntry.SPACEX_PATTERN.matcher(text).replaceAll(" ").trim());
            // Text's length without link, users and hashtags
            this.without_luh_len = text.length();

        }

        /**
         * Channels on which the Tweet will be published -
         *      all
         *      twitter
         *      twitter/mention/*username*
         *      twitter/user/*username*         (User who posted the Tweet)
         *      twitter/hashtag/*hashtag*
         *      twitter/country/*country code*
         *      twitter/text/*token*
         * @return Array of channels to publish message to
         */
        @Override
        protected String[] getStreamChannels() {
            ArrayList<String> channels = new ArrayList<>();

            for (String mention : this.mentions) {
                channels.add("twitter/mention/" + mention);
            }

            for (String hashtag : this.hashtags) {
                channels.add("twitter/hashtag/" + hashtag);
            }

            channels.add("twitter/user/" + this.getScreenName());
            if (this.place_country != null) {
                channels.add("twitter/country/" + this.place_country);
            }

            for (String token : Classifier.normalize(this.text)) {
                channels.add("twitter/text/" + token);
            }

            channels.add("all");
            channels.add("twitter");

            return channels.toArray(new String[channels.size()]);
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
                if (this.writeToIndex) IncomingMessageBuffer.addScheduler(this, this.user, true, true);
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
            this.put(AbstractObjectEntry.TIMESTAMP_FIELDNAME, AbstractObjectEntry.utcFormatter.print(getTimestampDate().getTime()));
            this.put(AbstractObjectEntry.CREATED_AT_FIELDNAME, AbstractObjectEntry.utcFormatter.print(getCreatedAt().getTime()));
            if (this.on != null) this.put("on", AbstractObjectEntry.utcFormatter.print(this.on.getTime()));
            if (this.to != null) this.put("to", AbstractObjectEntry.utcFormatter.print(this.to.getTime()));
            this.put("screen_name", this.screen_name);
            if (this.retweet_from != null && this.retweet_from.length() > 0) this.put("retweet_from", this.retweet_from);
            // the tweet; the cleanup is a helper function which cleans mistakes from the past in scraping
            MessageEntry.TextLinkMap tlm = this.moreData.getText(iflinkexceedslength, urlstub, this.text, this.getLinks(), this.getPostId());
            this.put("text", tlm);
            if (this.status_id_url != null) this.put("link", this.status_id_url.toExternalForm());
            this.put("id_str", this.postId);
            this.put("conversation_id", this.conversationID);
            this.put("conversation_user", this.conversationUserIDs);
            if (this.canonical_id != null) this.put("canonical_id", this.canonical_id);
            if (this.parent != null) this.put("parent", this.parent);
            this.put("source_type", this.source_type.toString());
            this.put("provider_type", this.provider_type.name());
            if (this.provider_hash != null && this.provider_hash.length() > 0) this.put("provider_hash", this.provider_hash);
            this.put("reply_count", this.reply_count);
            this.put("retweet_count", this.retweet_count);
            // there is a slight inconsistency here in the plural naming but thats how it is noted in the twitter api
            this.put("favourites_count", this.favourite_count);
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

                 // text classifier
                if (this.moreData.classifier != null) {
                    for (Map.Entry<Context, Classification<String, Category>> c: this.moreData.classifier.entrySet()) {
                        assert c.getValue() != null;
                        // we don't store non-existing classifications
                        if (c.getValue().getCategory() == Classifier.Category.NONE) continue;
                        this.put("classifier_" + c.getKey().name(), c.getValue().getCategory());
                        this.put("classifier_" + c.getKey().name() + "_probability",
                                c.getValue().getProbability() == Float.POSITIVE_INFINITY
                                        ? Float.MAX_VALUE : c.getValue().getProbability());
                    }
                }
            }

            // add user
            if (user != null) this.put("user", user.toJSON());
            return this;
        }

        public boolean willBeTimeConsuming() {
            return timeline_link_pattern.matcher(this.text).find();
        }

       public Object lazyGet(JSONObject json, String key) {
            try {
                Object o = json.get(key);
                return o;
            } catch (JSONException e) {
                return null;
            }
        }

        public UserEntry getUser() {
            return this.user;
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
            return this.favourite_count;
        }

        public void setFavouritesCount(long favourites_count) {
            this.favourite_count = favourites_count;
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

        public Set<String> getVideos() {
            return this.videos;
        }

        public Set<String> getAudio() {
            return this.audios;
        }

        public Set<String> getImages() {
            return this.images;
        }

        public void setImages(String image) {
            if(this.images == null) {
                this.images = new HashSet<String>();
            }
            this.images.add(image);
        }

        public String[] getMentions() {
            if(this.mentions == null) {
                return new String[0];
            }
            return this.mentions.toArray(new String[0]);
        }

        public String[] getHashtags() {
            return this.hashtags.toArray(new String[0]);
        }

        public String[] getLinks() {
            return this.links.toArray(new String[0]);
        }

        public Classifier.Category getClassifier(Classifier.Context context) {
            return this.moreData.getClassifier(context);
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
        Set<String> filterList = new HashSet<String>();
        filterList.add("image");
        TwitterTimeline[] result = null;
        result = search(args[0], filterList, Order.CREATED_AT, false, false);
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

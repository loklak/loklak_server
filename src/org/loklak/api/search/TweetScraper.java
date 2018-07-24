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

package org.loklak.api.search;

import org.loklak.objects.AbstractObjectEntry;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.harvester.BaseScraper;
import org.loklak.harvester.Post;
import org.loklak.harvester.RedirectUnshortener;
import org.loklak.objects.PostTimeline;
import org.loklak.server.BaseUserRole;
import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;


import java.io.BufferedReader;
import java.io.IOException;
import org.json.JSONException;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.ProviderType;
import org.loklak.objects.SourceType;
import org.loklak.objects.UserEntry;

public class TweetScraper extends BaseScraper {

    /**
     * 
     */
    private static final long serialVersionUID = -3803127453010542460L;
    private static final Pattern emoji_pattern_span = Pattern.compile("<span [^>]*class=\"Emoji Emoji--forLinks\" [^>]*>[\\n]*[^<]*</span>[\\n]*<span [^>]*class=\"visuallyhidden\" [^>]*aria-hidden=\"true\"[^>]*>[\\n]*([^<]*)[\\n]*</span>");
    private final static Pattern hashtag_pattern = Pattern.compile("<a href=\"/hashtag/.*?\".*?class=\"twitter-hashtag.*?\".*?><s>#</s><b>(.*?)</b></a>");
    private final static Pattern timeline_link_pattern = Pattern.compile("<a href=\"https://(.*?)\".*? data-expanded-url=\"(.*?)\".*?twitter-timeline-link.*?title=\"(.*?)\".*?>.*?</a>");
    private final static Pattern timeline_embed_pattern = Pattern.compile("<a href=\"(https://t.co/\\w+)\" class=\"twitter-timeline-link.*?>pic.twitter.com/(.*?)</a>");
    private final static Pattern emoji_pattern = Pattern.compile("<img .*?class=\"Emoji Emoji--forText\".*?alt=\"(.*?)\".*?>");
    private final static Pattern doublespace_pattern = Pattern.compile("  ");
    private final static Pattern cleanup_pattern = Pattern.compile(
            "</?(s|b|strong)>|"
            + "<a href=\"/hashtag.*?>|"
            + "<a.*?class=\"twitter-atreply.*?>|"
            + "<span.*?span>"
    );


    private ArrayList<String> filterList = null;
    private String since = null;
    private String until = null;
    //TODO: implement with enriched data
    private boolean enrich = false;

    public TweetScraper() {
        super();
        this.baseUrl = "https://www.twitter.com/";
        this.scraperName = "twitter";
    }

    public TweetScraper(String _query) {
        this();
        this.setExtraValue("query", this.query);
        this.setParam();
    }

    public TweetScraper(String _query, Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.setParam();
        this.query = _query;
        this.setExtraValue("query", this.query);
    }

    public TweetScraper(Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.setParam();
    }

    protected void setParam() {
        // filter get argument
        this.filterList = new ArrayList<String>(
                Arrays.asList(this.getExtraValue("filter").split(",")));
        this.since = "".equals(this.getExtraValue("since")) ? null : this.getExtraValue("since");
        this.until = "".equals(this.getExtraValue("until")) ? null : this.getExtraValue("until");
        this.query = this.getExtraValue("query");
        this.enrich = this.getExtraValue("enrich").equals("true");
    }

    @Override
    public String getAPIPath() {
        return "/api/twitterscraper";
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
        String typeMedia = "tweets";
        String midUrl = "search/";

        if(this.since != null) {
            this.query = this.query + " " + "since:" + this.since;
        }
        if(this.until != null) {
            this.query = this.query + " " + "until:" + this.until;
        }

        if (this.filterList.contains("video") && this.filterList.size() == 1) {
            typeMedia = "video";
        }

        try {
            url = new URIBuilder(this.baseUrl + midUrl);
            switch(type) {
                case "user":
                    typeMedia = "users";
                    break;
                case "tweet":
                    typeMedia = "tweets";
                    break;
                case "image":
                    typeMedia = "images";
                    break;
                case "video":
                    typeMedia = "videos";
                    break;
                default:
                    typeMedia = "tweets";
                    break;
            }

            url.addParameter("f", typeMedia);
            url.addParameter("q", this.query);
            url.addParameter("vertical", "default");
            url.addParameter("src", "typd");
        } catch (URISyntaxException e) {
            DAO.log("Invalid Url: baseUrl = " + this.baseUrl + ", mid-URL = " + midUrl
                    + ", query = " + this.query + ", type = " + type);
        }
        return url.toString();
    }

    @Override
    public Post getResults() {
        String url;
        Post output = null;
        String type = this.getExtraValue("type");
        url = this.prepareSearchUrl(type);

        try {
            output = this.getDataFromConnection(url, type);
        } catch(IOException e) {
            DAO.severe("Possibly connection issue!!");
        }
        // Add scraper name
        Post postArray = new Post();
        postArray.put(this.scraperName, output);

        return postArray;
    }

    protected Post scrape(BufferedReader br, String type, String url) {
        Post typeArray = new Post(true);
        try {
            this.putData(typeArray, type, this.search(br, url));
        } catch(IOException e) { }
        return typeArray;
    }

    /**
     * scrape messages from the reader stream: this already checks if a message is new. There are only new messages returned
     * @param br
     * @param order
     * @return two timelines in one array: Timeline[0] is the one which is finished to be used, Timeline[1] contains messages which are in postprocessing
     * @throws IOException
     */
    private PostTimeline search(
            final BufferedReader br,
            String url
    ) throws IOException {
        PostTimeline timelineReady = new PostTimeline(order);
        String input;
        Map<String, prop> props = new HashMap<String, prop>();
        Set<String> images = null;
        Set<String> videos = null;
        String place_id = "";
        String place_name = "";
        boolean parsing_favourite = false;
        boolean parsing_retweet = false;
        // first line is 1, according to emacs which numbers the first line also as 1
        int line = 0;

        while ((input = br.readLine()) != null) {
            line++;
            input = input.trim();

            if (input.length() == 0) continue;

            // parse from HTML
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
            String img_link;
            if (videos == null) images = new HashSet<String>();
            if ((p = input.indexOf("<img")) >= 0) {
                img_link = new prop(input, p, "src").value;
                if (img_link != null && img_link.contains("pbs.twimg.com/media/")) {
                    images.add(img_link);
                    continue;
                }

                continue;
            }
            // we have two opportunities to get video thumbnails == more images; images in the
            // presence of video content should be treated as thumbnail for the video
            if (videos == null) videos = new HashSet<String>();
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
            /** Not a good idea to fetch video right now. Need to add another endpoint which
              * lets end users fetch complete videos from here.
              * See https://github.com/loklak/loklak_server/issues/1298
            if (input.indexOf("AdaptiveMedia-videoContainer") > 0) {
                String tweetUrl = props.get("tweetstatusurl").value;
                String[] videoUrls = fetchTwitterVideos(tweetUrl);
                Collections.addAll(videos, videoUrls);
            }
            */
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

            if (props.size() > 4 && input.indexOf("stream-item") > 0) {
                if(!filterPosts(props, videos, images)) {
                    props = new HashMap<String, prop>();
                    place_id = "";
                    place_name = "";
                    continue;
                }

                // the tweet is complete, evaluate the result
                prop userid = props.get("userid");
                if (userid == null) continue;
                prop usernickname = props.get("usernickname");
                if (usernickname == null) continue;
                prop useravatarurl = props.get("useravatarurl");
                if (useravatarurl == null) continue;
                prop userfullname = props.get("userfullname");
                if (userfullname == null) continue;

                UserEntry user = new UserEntry(
                        userid.value,
                        usernickname.value,
                        useravatarurl.value,
                        MessageEntry.html2utf8(userfullname.value)
                );

                prop tweettimems = props.get("tweettimems");
                if (tweettimems == null) continue;
                prop tweetretweetcount = props.get("tweetretweetcount");
                if (tweetretweetcount == null) continue;
                prop tweetfavouritecount = props.get("tweetfavouritecount");
                if (tweetfavouritecount == null) continue;

                TweetPost tweet = new TweetPost(
                        user.getScreenName(),
                        Long.parseLong(tweettimems.value),
                        props.get("tweettimename").value,
                        props.get("tweetstatusurl").value,
                        props.get("tweettext").value,
                        Long.parseLong(tweetretweetcount.value),
                        Long.parseLong(tweetfavouritecount.value),
                        images, videos, place_name, place_id, user, url
                );

                timelineReady.addPost(tweet);

                videos = null;
                images = null;
                props.clear();

                continue;
            }
        }
        br.close();
        return timelineReady;
    }


    /**
     * Filter Posts(here tweets) according to values.
     *   image: filter tweets with images, neglect 'tweets without images'
     *   video: filter tweets also having video and other values like image. For only value as video,
     *          tweets with videos are filtered in prepareUrl() method
     */
    private boolean filterPosts(
            Map<String, prop> props,
            Set<String> videos,
            Set<String> images
    ) {
        if (this.filterList == null) return false;

        Matcher matchVideo1;
        Matcher matchVideo2;
        Pattern[] videoUrlPatterns = {
                Pattern.compile("youtu.be\\/[0-9A-z]+"),
                Pattern.compile("youtube.com\\/watch?v=[0-9A-z]+")
        };

        // Filter tweets with videos and others
        if (this.filterList.contains("video") && this.filterList.size() > 1) {
            matchVideo1 = videoUrlPatterns[0].matcher(props.get("tweettext").value);
            matchVideo2 = videoUrlPatterns[1].matcher(props.get("tweettext").value);

            if(!matchVideo1.find() && !matchVideo2.find() && videos.size() < 1) {
                return false;
            }
        }
        // Filter tweets with images
        if (this.filterList.contains("image") && images.size() < 1) {
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

    public static class TweetPost extends Post {

        public UserEntry user;
        // a time stamp that is given in loklak upon the arrival of the tweet which is the current local time
        public Date timestampDate;
        // the time given in the tweet which is the time when the user created it.
        // This is also use to do the index partition into minute, hour, week
        public Date created_at;
        // where did the message come from
        protected SourceType source_type;
        // who created the message
        protected ProviderType provider_type;
        private String searchUrl;
        public String screen_name, retweet_from, postId, canonical_id, parent, text;
        protected URL status_id_url;
        protected long retweet_count, favourites_count;
        public Set<String> images, audios, videos;
        protected String place_name, place_id;
        private boolean enriched;

        public TweetPost(
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
                String url) throws MalformedURLException {
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
            this.images = images;
            this.videos = videos;
            this.text = text_raw;
            this.user = user;
            this.searchUrl = url;

            // Set to json
            this.toJson();
        }

        public TweetPost(JSONObject json, boolean enrich) {
            Object timestamp_obj = lazyGet(json, AbstractObjectEntry.TIMESTAMP_FIELDNAME);
            this.timestampDate = MessageEntry.parseDate(timestamp_obj);
            this.timestamp = this.timestampDate.getTime();
            Object created_at_obj = lazyGet(json, AbstractObjectEntry.CREATED_AT_FIELDNAME);
            this.created_at = MessageEntry.parseDate(created_at_obj);
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
            this.screen_name = (String) lazyGet(json, "screen_name");
            this.retweet_from = (String) lazyGet(json, "retweet_from");
            this.postId = (String) lazyGet(json, "id_str");
            this.text = (String) lazyGet(json, "text");
            try {
                this.status_id_url = new URL((String) lazyGet(json, "link"));
            } catch (MalformedURLException e) {
                this.status_id_url = null;
            }
            this.retweet_count = MessageEntry.parseLong((Number) lazyGet(json, "retweet_count"));
            this.favourites_count = MessageEntry.parseLong((Number) lazyGet(json, "favourites_count"));
            this.images = MessageEntry.parseArrayList(lazyGet(json, "images"));
            this.audios = MessageEntry.parseArrayList(lazyGet(json, "audio"));
            this.videos = MessageEntry.parseArrayList(lazyGet(json, "videos"));
            this.enriched = false;
        }

        public TweetPost() throws MalformedURLException {
            this.timestamp = new Date().getTime();
            this.timestampDate = new Date(this.timestamp);
            this.created_at = new Date();
            this.source_type = SourceType.GENERIC;
            this.provider_type = ProviderType.NOONE;
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
            this.enriched = false;
        }

        public Post toJson() {

            this.text = unshorten(this.text);
            this.user.setName(unshorten(this.user.getName()));
            // tweet data
            this.put("timestamp", AbstractObjectEntry.utcFormatter.print(getTimestampDate().getTime()));
            this.put("created_at", AbstractObjectEntry.utcFormatter.print(getCreatedAt().getTime()));
            this.put("text", this.text);
            if (this.status_id_url == null) {
                this.put("link", "");
            } else {
            this.put("link", this.status_id_url.toExternalForm());
            }
            this.put("images", images);
            this.put("images_count", images.size());
            this.put("videos", videos);
            this.put("videos_count", videos.size());
            this.put("id_str", this.postId);

            this.put("retweet_count", this.retweet_count);
            this.put("favourites_count", this.favourites_count);

            // Add places
            this.put("place_name", this.place_name);
            this.put("place_id", this.place_id);
            this.put("search_url", this.searchUrl);
            // Add user
            this.put("user", user.toJSON());
            return this;
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
        public String getScreenName() {
            return screen_name;
        }

        public void setScreenName(String user_screen_name) {
            this.screen_name = user_screen_name;
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

        //TODO: to implement this method
        private void setPostId() {
            this.postId = String.valueOf(this.timestamp) + String.valueOf(this.created_at.getTime());
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
        }
        text = cleanup_pattern.matcher(text).replaceAll("");
        text = MessageEntry.html2utf8(text);
        text = doublespace_pattern.matcher(text).replaceAll(" ");
        text = text.trim();
        return text;
    }
}

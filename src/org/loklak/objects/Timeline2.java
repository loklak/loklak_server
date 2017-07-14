/**
 *  Timeline2
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

package org.loklak.objects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.data.DAO.IndexName;
import org.loklak.harvester.Post;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.susi.SusiThought;

/**
 * A timeline2 is a structure which holds tweet for the purpose of presentation
 * There is no tweet retrieval method here, just an iterator which returns the tweets in reverse appearing order
 */
//TODO: remove MessageEntry
public class Timeline2 implements Iterable<Post> {

    public static enum Order {
        CREATED_AT("date"),
        TIMESTAMP("long"),
        RETWEET_COUNT("long"),
        FAVOURITES_COUNT("long");
        String field_type;

        Order(String field_type) {this.field_type = field_type;}

        public String getMessageFieldName() {
            return this.name().toLowerCase();
        }

        public String getMessageFieldType() {
            return this.field_type;
        }
    }
    //TODO: remove tweets
    // the key is the date plus id of the post
    private NavigableMap<String, Post> posts;
    private Map<String, UserEntry> users;
    private String lastKey = "0";
    private int hits = -1;
    private String scraperInfo = "";
    final private Order order;
    private String query;
    private IndexName indexName;
    private int cursor; // used for pagination, set this to the number of tweets returned so far to the user; they should be considered as unchangable
    private long accessTime;
    private final Map<Integer, Post> precursorPostCache = new ConcurrentHashMap<>();

    public Timeline2(Order order) {
        this.posts = new ConcurrentSkipListMap<String, Post>();
        this.users = new ConcurrentHashMap<String, UserEntry>();
        this.order = order;
        this.query = null;
        this.indexName = null;
        this.cursor = 0;
        this.accessTime = System.currentTimeMillis();
    }

    public Timeline2(Order order, String scraperInfo) {
        this(order);
        this.scraperInfo = scraperInfo;
    }

    public static Order parseOrder(String order) {
        try {
            return Order.valueOf(order.toUpperCase());
        } catch (Throwable e) {
            return Order.TIMESTAMP;
        }
    }

    public void clear() {
        this.posts.clear();
        this.users.clear();
        // we keep the other details (like order, scraperInfo and query) to be able to test with zero-size pushes
    }

    public Timeline2 setResultIndex(IndexName index) {
        this.indexName = index;
        return this;
    }

    public IndexName getResultIndex() {
        return this.indexName;
    }

    public Timeline2 setScraperInfo(String info) {
        this.scraperInfo = info;
        return this;
    }

    public String getScraperInfo() {
        return this.scraperInfo;
    }

    public long getAccessTime() {
        return this.accessTime;
    }

    public Timeline2 updateAccessTime() {
        this.accessTime = System.currentTimeMillis();
        return this;
    }

    public Order getOrder() {
        return this.order;
    }

    public String getQuery() {
        return this.query;
    }

    public Timeline2 setQuery(String query) {
        this.query = query;
        return this;
    }

    /**
     * gets the outer bound of the tweets returned to the user so far
     * @return the cursor, the next starting point for tweet retrieval from the list, not shown so far to the user
     */
    public int getCursor() {
        return this.cursor;
    }

    /**
     * sets the cursor to the outer bound of the visible tweet number.
     * That means if no tweets had been shown to the user, the number is 0.
     * @param newCursor the new cursor position which must be higher than the previous one.
     * @return this
     */
    public Timeline2 setCursor(int newCursor) {
        if (newCursor > this.cursor) this.cursor = newCursor;
        return this;
    }

    public int size() {
        return this.posts.size();
    }

    //TODO: to fix this
    public Timeline2 reduceToMaxsize(final int maxsize) {
        List<Post> m = new ArrayList<>();
        Timeline2 t = new Timeline2(this.order);
        if (maxsize < 0) return t;

        // remove tweets from this timeline2
        synchronized (posts) {
            while (this.posts.size() > maxsize) m.add(this.posts.remove(this.posts.firstEntry().getKey()));
        }

        // create new timeline2
        for (Post me: m) {
            t.addUser(this.users.get(me.get("ScreenName")));
            t.addPost(me);
        }

        // prune away users not needed any more in this structure

        Set<String> screen_names = new HashSet<>();
        for (Post me: this.posts.values()) {
            //TODO: compare with Timeline
            screen_names.add(String.valueOf(me.get("ScreenName")));
        }
        synchronized (this.users) {
            Iterator<Map.Entry<String, UserEntry>> i = this.users.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<String, UserEntry> e = i.next();
                if (!screen_names.contains(e.getValue().getScreenName())) i.remove();
            }
        }

        return t;
    }

    public Timeline2 add(Post post, UserEntry user) {
        this.addUser(user);
        this.addPost(post);
        return this;
    }

    public Timeline2 add(Post post) {
        this.addPost(post);
        return this;
    }

    private Timeline2 addUser(UserEntry user) {
        assert user != null;
        if (user != null) {
            this.users.put(user.getScreenName(), user);
        }
        return this;
    }

/*  //TODO: remove this
    private Timeline2 addTweet(MessageEntry tweet) {
        String key = "";
        if (this.order == Order.RETWEET_COUNT) {
            key = Long.toHexString(tweet.getRetweetCount());
            while (key.length() < 16) key = "0" + key;
            key = key + "_" + tweet.getPostId();
        } else if (this.order == Order.FAVOURITES_COUNT) {
            key = Long.toHexString(tweet.getFavouritesCount());
            while (key.length() < 16) key = "0" + key;
            key = key + "_" + tweet.getPostId();
        } else {
            key = Long.toHexString(tweet.getCreatedAt().getTime()) + "_" + tweet.getPostId();
        }
        synchronized (tweets) {
            MessageEntry precursorTweet = getPrecursorTweet();
            if (precursorTweet != null && tweet.getCreatedAt().before(precursorTweet.getCreatedAt())) return this; // ignore this tweet in case it would change the list of shown tweets
            this.tweets.put(key, tweet);
        }
        return this;
    }
*/

    public Timeline2 addPost(Post post) {
        String key = "";
        if (this.order == Order.TIMESTAMP) {
            if(!post.isWrapper()) {
                key = Long.toHexString(post.getTimestamp()) + "_" + post.getPostId();
            } else {
                long keyValue = Long.valueOf(lastKey) + 1;
                key = String.valueOf(keyValue);
                this.lastKey = key;
            }
        }
        synchronized (this.posts) {
            Post precursorPost = getPrecursorPost();
            if (precursorPost == null || !post.getTimestampDate().before(precursorPost.getTimestampDate())) {
                // ignore this post in case it would change the list of shown posts
                this.posts.put(key, post);
            }
        }
        return this;
    }

    public void mergePost(Timeline2 list) {
        for (Post post: list) {
            this.add(post);
        }
    }

    public void mergePost(Timeline2[] lists) {
        for (Timeline2 list: lists) {
            this.mergePost(list);
        }
    }

    protected UserEntry getUser(String user_screen_name) {
        return this.users.get(user_screen_name);
    }

    public UserEntry getUser(Post fromPost) {
        return this.users.get(fromPost.get("ScreenName"));
    }

    public void putAll(Timeline2 other) {
        if (other == null) return;
        assert this.order.equals(other.order);
        for (Map.Entry<String, UserEntry> u: other.users.entrySet()) {
            UserEntry t = this.users.get(u.getKey());
            if (t == null || !t.containsProfileImage()) {
                this.users.put(u.getKey(), u.getValue());
            }
        }
        for (Post t: other) this.addPost(t);
    }

    public Post getBottomTweet() {
        synchronized (posts) {
            return this.posts.firstEntry().getValue();
        }
    }

    public Post getTopTweet() {
        synchronized (posts) {
            return this.posts.lastEntry().getValue();
        }
    }

    /**
     * get the precursor tweet, which is the latest tweet that the user has seen.
     * It is the tweet which appears at the end of the list.
     * This tweet may be used to compute the insert date which is valid for new tweets.
     * Therefore there is a cache which contains the latest tweet shown to the user so fa.
     * New tweets must have an entry date after that last tweet to create a stable list
     * @return the last tweet that a user has seen. It is also the oldest tweet that the user has seen.
     */
/*
    private MessageEntry getPrecursorTweet() {
        if (this.cursor == 0) return null;
        MessageEntry m = this.precursorTweetCache.get(this.cursor);
        if (m != null) return m;
        synchronized (messageEntry) {
            int count = 0;
            for (MessageEntry messageEntry: this) {
                if (++count == this.cursor) {
                    this.precursorTweetCache.put(this.cursor, messageEntry);
                    return messageEntry;
                }
            }
        }
        return null;
    }
*/
    private Post getPrecursorPost() {
        if (this.cursor == 0) {
            return null;
        }
        Post post = this.precursorPostCache.get(this.cursor);

        if (post != null) {
            return post;
        }
        synchronized (posts) {
            int count = 0;
            for (Post p: this) {
                if (++count == this.cursor) {
                    this.precursorPostCache.put(this.cursor, p);
                    post = p;
                    break;
                }
            }
        }
        return post;
    }

    public List<Post> getNextTweets(int start, int maxCount) {
        List<Post> posts = new ArrayList<>();
        synchronized (posts) {
            int count = 0;
            for (Post post: this) {
                if (count >= start) posts.add(post);
                if (posts.size() >= maxCount) break;
                count++;
            }
            if (start >= this.cursor) this.cursor = start + posts.size();
        }
        return posts;
    }

    public String toString() {
        return toJSON(true, "search_metadata", "statuses").toString();
        //return new ObjectMapper().writer().writeValueAsString(toMap(true));
    }

    public JSONObject toJSON(boolean withEnrichedData, String metadata_field_name, String statuses_field_name) throws JSONException {
        JSONObject json = toSusi(withEnrichedData, new SusiThought(metadata_field_name, statuses_field_name));
        json.getJSONObject(metadata_field_name).put("count", Integer.toString(this.posts.size()));
        json.put("peer_hash", DAO.public_settings.getPeerHash());
        json.put("peer_hash_algorithm", DAO.public_settings.getPeerHashAlgorithm());
        return json;
    }

    public JSONObject toJSON() throws JSONException {
        SusiThought susiThought = new SusiThought();
        SusiThought json = toSusi(false, susiThought);
        json.getJSONObject(susiThought.metadata_name).put("count", Integer.toString(this.posts.size()));
        json.put("peer_hash", DAO.public_settings.getPeerHash());
        json.put("peer_hash_algorithm", DAO.public_settings.getPeerHashAlgorithm());
        return json;
    }

    public SusiThought toSusi(boolean withEnrichedData) throws JSONException {
        return toSusi(withEnrichedData, new SusiThought());
    }
    private SusiThought toSusi(boolean withEnrichedData, SusiThought json) throws JSONException {
        UserEntry user;
        json.setQuery(this.query)
            .setHits(Math.max(this.hits, this.size()));
        if (this.scraperInfo.length() > 0) json.setScraperInfo(this.scraperInfo);
        JSONArray statuses = new JSONArray();
        for (JSONObject t: this) {
            if(t.has("ScreenName")) {
                user = this.users.get(t.get("ScreenName"));
                // add user
                if (user != null) t.put("user", user.toJSON());
            }
            statuses.put(t);
            //TODO: add data with enriched data
        }
        json.setData(statuses);
        return json;
    }

    /**
     * the tweet iterator returns tweets in descending appearance order (top first)
     */
    @Override
    public Iterator<Post> iterator() {
        return this.posts.descendingMap().values().iterator();
    }

    /**
     * compute the average time between any two consecutive tweets
     * @return time in milliseconds
     */
    public long period() {
        if (this.size() < 1) return Long.MAX_VALUE;

        // calculate the time based on the latest 20 tweets (or less)
        long latest = 0;
        long time;
        long earliest = 0;
        int count = 0;
        for (Post post: this) {
            time = post.getTimestamp();
            if (latest == 0) {
                latest = time;
                continue;
            }
            earliest = time;
            count++;
            if (count >= 19) break;
        }

        if (count == 0) return Long.MAX_VALUE;
        long timeInterval = latest - earliest;
        long p = 1 + timeInterval / count;
        return p < 4000 ? p / 4 + 3000 : p;
    }

    public JSONArray toArray() {
        JSONArray postArray = new JSONArray();
        for (JSONObject post : this) {
            postArray.put(post);
        }
        return postArray;
    }

    //TODO: this passes Timeline as argument
    public void writeToIndex() {
        //IncomingMessageBuffer.addScheduler(this, true);
    }

    public Timeline2 setHits(int hits) {
        this.hits = hits;
        return this;
    }

    public int getHits() {
        return this.hits == -1 ? this.size() : this.hits;
    }

    public Timeline toTimeline() {

        Timeline postList = new Timeline(Timeline.Order.CREATED_AT);
        for (Post post : this) {
            assert post instanceof TwitterTweet;
            TwitterTweet tweet = (TwitterTweet)post;
            postList.add(tweet, tweet.getUser());
        }
        return postList;
    }

    public boolean isEmpty() {
        return this.posts.isEmpty();
    }
}

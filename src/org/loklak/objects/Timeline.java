/**
 *  Timeline
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
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.data.DAO.IndexName;
import org.loklak.susi.SusiThought;

/**
 * A timeline is a structure which holds tweet for the purpose of presentation
 * There is no tweet retrieval method here, just an iterator which returns the tweets in reverse appearing order
 */
public class Timeline implements Iterable<MessageEntry> {

    public static enum Order {
        CREATED_AT("date"),
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
    
    private NavigableMap<String, MessageEntry> tweets; // the key is the date plus id of the tweet
    private Map<String, UserEntry> users;
    private int hits = -1;
    private String scraperInfo = "";
    final private Order order;
    private String query;
    private IndexName indexName;
    private int cursor; // used for pagination, set this to the number of tweets returned so far to the user; they should be considered as unchangable
    private long accessTime;
    
    public Timeline(Order order) {
        this.tweets = new ConcurrentSkipListMap<String, MessageEntry>();
        this.users = new ConcurrentHashMap<String, UserEntry>();
        this.order = order;
        this.query = null;
        this.indexName = null;
        this.cursor = 0;
        this.accessTime = System.currentTimeMillis();
    }
    public Timeline(Order order, String scraperInfo) {
        this(order);
        this.scraperInfo = scraperInfo;
    }
    
    public static Order parseOrder(String order) {
        try {
            return Order.valueOf(order.toUpperCase());
        } catch (Throwable e) {
            return Order.CREATED_AT;
        }
    }

    public void clear() {
        this.tweets.clear();
        this.users.clear();
        // we keep the other details (like order, scraperInfo and query) to be able to test with zero-size pushes
    }

    public Timeline setResultIndex(IndexName index) {
        this.indexName = index;
        return this;
    }
    
    public IndexName getResultIndex() {
        return this.indexName;
    }
    
    public Timeline setScraperInfo(String info) {
        this.scraperInfo = info;
        return this;
    }
    
    public String getScraperInfo() {
        return this.scraperInfo;
    }
    
    public long getAccessTime() {
        return this.accessTime;
    }
    
    public Timeline updateAccessTime() {
        this.accessTime = System.currentTimeMillis();
        return this;
    }
    
    public Order getOrder() {
        return this.order;
    }
    
    public String getQuery() {
        return this.query;
    }
    
    public Timeline setQuery(String query) {
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
    public Timeline setCursor(int newCursor) {
        if (newCursor > this.cursor) this.cursor = newCursor;
        return this;
    }
    
    public int size() {
        return this.tweets.size();
    }
    
    public Timeline reduceToMaxsize(final int maxsize) {
        List<MessageEntry> m = new ArrayList<>();
        Timeline t = new Timeline(this.order);
        if (maxsize < 0) return t;
        
        // remove tweets from this timeline
        synchronized (tweets) {
            while (this.tweets.size() > maxsize) m.add(this.tweets.remove(this.tweets.firstEntry().getKey()));
        }
        
        // create new timeline
        for (MessageEntry me: m) {
            t.addUser(this.users.get(me.getScreenName()));
            t.addTweet(me);
        }
        
        // prune away users not needed any more in this structure
        Set<String> screen_names = new HashSet<>();
        for (MessageEntry me: this.tweets.values()) screen_names.add(me.getScreenName());
        synchronized (this.users) {
            Iterator<Map.Entry<String, UserEntry>> i = this.users.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<String, UserEntry> e = i.next();
                if (!screen_names.contains(e.getValue().getScreenName())) i.remove();
            }
        }        
        return t;
    }
    
    public Timeline add(MessageEntry tweet, UserEntry user) {
        this.addUser(user);
        this.addTweet(tweet);
        return this;
    }
    
    private Timeline addUser(UserEntry user) {
        assert user != null;
        if (user != null) this.users.put(user.getScreenName(), user);
        return this;
    }
    
    private Timeline addTweet(MessageEntry tweet) {
        String key = "";
        if (this.order == Order.RETWEET_COUNT) {
            key = Long.toHexString(tweet.getRetweetCount());
            while (key.length() < 16) key = "0" + key;
            key = key + "_" + tweet.getIdStr();
        } else if (this.order == Order.FAVOURITES_COUNT) {
            key = Long.toHexString(tweet.getFavouritesCount());
            while (key.length() < 16) key = "0" + key;
            key = key + "_" + tweet.getIdStr();
        } else {
            key = Long.toHexString(tweet.getCreatedAt().getTime()) + "_" + tweet.getIdStr();
        }
        synchronized (tweets) {
            MessageEntry precursorTweet = getPrecursorTweet();
            if (precursorTweet != null && tweet.getCreatedAt().before(precursorTweet.getCreatedAt())) return this; // ignore this tweet in case it would change the list of shown tweets
            this.tweets.put(key, tweet);
        }
        return this;
    }

    protected UserEntry getUser(String user_screen_name) {
        return this.users.get(user_screen_name);
    }
    
    public UserEntry getUser(MessageEntry fromTweet) {
        return this.users.get(fromTweet.getScreenName());
    }
    
    public void putAll(Timeline other) {
        if (other == null) return;
        assert this.order.equals(other.order);
        for (Map.Entry<String, UserEntry> u: other.users.entrySet()) {
            UserEntry t = this.users.get(u.getKey());
            if (t == null || !t.containsProfileImage()) {
                this.users.put(u.getKey(), u.getValue());
            }
        }
        for (MessageEntry t: other) this.addTweet(t);
    }
    
    public MessageEntry getBottomTweet() {
        synchronized (tweets) {
            return this.tweets.firstEntry().getValue();
        }
    }
    
    public MessageEntry getTopTweet() {
        synchronized (tweets) {
            return this.tweets.lastEntry().getValue();
        }
    }
    
    private final Map<Integer, MessageEntry> precursorTweetCache = new ConcurrentHashMap<>();
    /**
     * get the precursor tweet, which is the latest tweet that the user has seen.
     * It is the tweet which appears at the end of the list.
     * This tweet may be used to compute the insert date which is valid for new tweets.
     * Therefore there is a cache which contains the latest tweet shown to the user so fa.
     * New tweets must have an entry date after that last tweet to create a stable list
     * @return the last tweet that a user has seen. It is also the oldest tweet that the user has seen.
     */
    private MessageEntry getPrecursorTweet() {
        if (this.cursor == 0) return null;
        MessageEntry m = this.precursorTweetCache.get(this.cursor);
        if (m != null) return m;
        synchronized (tweets) {
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
    
    public List<MessageEntry> getNextTweets(int start, int maxCount) {
        List<MessageEntry> tweets = new ArrayList<>();
        synchronized (tweets) {
            int count = 0;
            for (MessageEntry messageEntry: this) {
                if (count >= start) tweets.add(messageEntry);
                if (tweets.size() >= maxCount) break;
                count++;
            }
            if (start >= this.cursor) this.cursor = start + tweets.size();
        }
        return tweets;
    }
    
    public String toString() {
        return toJSON(true, "search_metadata", "statuses").toString();
        //return new ObjectMapper().writer().writeValueAsString(toMap(true));
    }

    public JSONObject toJSON(boolean withEnrichedData, String metadata_field_name, String statuses_field_name) throws JSONException {
        JSONObject json = toSusi(withEnrichedData, new SusiThought(metadata_field_name, statuses_field_name));
        json.getJSONObject(metadata_field_name).put("count", Integer.toString(this.tweets.size()));
        json.put("peer_hash", DAO.public_settings.getPeerHash());
        json.put("peer_hash_algorithm", DAO.public_settings.getPeerHashAlgorithm());
        return json;
    }
    
    public SusiThought toSusi(boolean withEnrichedData) throws JSONException {
        return toSusi(withEnrichedData, new SusiThought());
    }
    
    private SusiThought toSusi(boolean withEnrichedData, SusiThought json) throws JSONException {
        json
            .setQuery(this.query)
            .setHits(Math.max(this.hits, this.size()));
        if (this.scraperInfo.length() > 0) json.setScraperInfo(this.scraperInfo);
        JSONArray statuses = new JSONArray();
        for (MessageEntry t: this) {
            UserEntry u = this.users.get(t.getScreenName());
            statuses.put(t.toJSON(u, withEnrichedData, Integer.MAX_VALUE, ""));
        }
        json.setData(statuses);
        return json;
    }
    
    /**
     * the tweet iterator returns tweets in descending appearance order (top first)
     */
    @Override
    public Iterator<MessageEntry> iterator() {
        return this.tweets.descendingMap().values().iterator();
    }

    /**
     * compute the average time between any two consecutive tweets
     * @return time in milliseconds
     */
    public long period() {
        if (this.size() < 1) return Long.MAX_VALUE;
        
        // calculate the time based on the latest 20 tweets (or less)
        long latest = 0;
        long earliest = 0;
        int count = 0;
        for (MessageEntry messageEntry: this) {
            if (latest == 0) {latest = messageEntry.created_at.getTime(); continue;}
            earliest = messageEntry.created_at.getTime();
            count++;
            if (count >= 19) break;
        }

        if (count == 0) return Long.MAX_VALUE;
        long timeInterval = latest - earliest;
        long p = 1 + timeInterval / count;
        return p < 4000 ? p / 4 + 3000 : p;
    }    
    
    public void writeToIndex() {
        IncomingMessageBuffer.addScheduler(this, true);
    }
    
    public Timeline setHits(int hits) {
        this.hits = hits;
        return this;
    }
    
    public int getHits() {
        return this.hits == -1 ? this.size() : this.hits;
    }
}

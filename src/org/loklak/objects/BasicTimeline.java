/**
 *  BasicTimeline
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.loklak.data.DAO.IndexName;
import org.loklak.harvester.Post;
import org.loklak.harvester.TwitterScraper.TwitterTweet;

/**
 * A timeline is a structure which holds tweet for the purpose of presentation
 * There is no tweet retrieval method here, just an iterator which returns the tweets in reverse appearing order
 */
public class BasicTimeline<A extends Post> implements Iterable<A> {

    public static enum Order {
        CREATED_AT("date"),
        TIMESTAMP("date"),
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
    
    protected NavigableMap<String, A> posts; // the key is the date plus id of the tweet
    protected Map<String, UserEntry> users;
    protected int hits = -1;
    protected String scraperInfo = "";
    final protected Order order;
    protected String query;
    protected IndexName indexName;
    protected int cursor; // used for pagination, set this to the number of tweets returned so far to the user; they should be considered as unchangable
    protected long accessTime;
    
    public BasicTimeline(Order order) {
        this.posts = new ConcurrentSkipListMap<String, A>();
        this.users = new ConcurrentHashMap<String, UserEntry>();
        this.order = order;
        this.query = null;
        this.indexName = null;
        this.cursor = 0;
        this.accessTime = System.currentTimeMillis();
    }
    
    public BasicTimeline(Order order, String scraperInfo) {
        this(order);
        this.scraperInfo = scraperInfo;
    }
    
    public BasicTimeline<A> addUser(UserEntry user) {
        assert user != null;
        if (user != null) this.users.put(user.getScreenName(), user);
        return this;
    }
    
    public UserEntry getUser(TwitterTweet fromTweet) {
        return this.users.get(fromTweet.getScreenName());
    }
    
    /**
     * get the precursor tweet, which is the latest tweet that the user has seen.
     * It is the tweet which appears at the end of the list.
     * This tweet may be used to compute the insert date which is valid for new tweets.
     * New tweets must have an entry date after that last tweet to create a stable list
     * @return the last tweet that a user has seen. It is also the oldest tweet that the user has seen.
     */
    public A getPrecursorTweet() {
        if (this.cursor == 0) return null;
        synchronized (this.posts) {
            int count = 0;
            for (A p: this) {
                if (++count == this.cursor) return p;
            }
        }
        return null;
    }
    
    public static Order parseOrder(String order) {
        try {
            return Order.valueOf(order.toUpperCase());
        } catch (Throwable e) {
            return Order.CREATED_AT;
        }
    }

    public void clear() {
        this.posts.clear();
        this.users.clear();
        // we keep the other details (like order, scraperInfo and query) to be able to test with zero-size pushes
    }

    public BasicTimeline<A> setResultIndex(IndexName index) {
        this.indexName = index;
        return this;
    }
    
    public IndexName getResultIndex() {
        return this.indexName;
    }
    
    public BasicTimeline<A> setScraperInfo(String info) {
        this.scraperInfo = info;
        return this;
    }
    
    public String getScraperInfo() {
        return this.scraperInfo;
    }
    
    public long getAccessTime() {
        return this.accessTime;
    }
    
    public BasicTimeline<A> updateAccessTime() {
        this.accessTime = System.currentTimeMillis();
        return this;
    }
    
    public Order getOrder() {
        return this.order;
    }
    
    public String getQuery() {
        return this.query;
    }
    
    public BasicTimeline<A> setQuery(String query) {
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
    public BasicTimeline<A> setCursor(int newCursor) {
        if (newCursor > this.cursor) this.cursor = newCursor;
        return this;
    }
    
    public int size() {
        return this.posts.size();
    }
    
    protected UserEntry getUser(String user_screen_name) {
        return this.users.get(user_screen_name);
    }
    
    public A getBottomTweet() {
        synchronized (posts) {
            return this.posts.firstEntry().getValue();
        }
    }
    
    public A getTopTweet() {
        synchronized (posts) {
            return this.posts.lastEntry().getValue();
        }
    }
    
    public List<A> getNextTweets(int start, int maxCount) {
        List<A> tweets = new ArrayList<>();
        synchronized (tweets) {
            int count = 0;
            for (A TwitterTweet: this) {
                if (count >= start) tweets.add(TwitterTweet);
                if (tweets.size() >= maxCount) break;
                count++;
            }
            if (start >= this.cursor) this.cursor = start + tweets.size();
        }
        return tweets;
    }
    
    /**
     * the tweet iterator returns tweets in descending appearance order (top first)
     */
    @Override
    public Iterator<A> iterator() {
        return this.posts.descendingMap().values().iterator();
    }

    
    public BasicTimeline<A> setHits(int hits) {
        this.hits = hits;
        return this;
    }
    
    public int getHits() {
        return this.hits == -1 ? this.size() : this.hits;
    }

}

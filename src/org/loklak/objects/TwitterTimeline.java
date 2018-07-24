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
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.harvester.Post;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.objects.PostTimeline;
import org.loklak.susi.SusiThought;

/**
 * A timeline is a structure which holds tweet for the purpose of presentation
 * There is no tweet retrieval method here, just an iterator which returns the tweets in reverse appearing order
 */
public class TwitterTimeline extends BasicTimeline<TwitterTweet> implements Iterable<TwitterTweet> {
    
    public TwitterTimeline(Order order) {
        super(order);
    }
    
    public TwitterTimeline(Order order, String scraperInfo) {
        super(order, scraperInfo);
    }
    
    public TwitterTimeline reduceToMaxsize(final int maxsize) {
        List<TwitterTweet> m = new ArrayList<>();
        TwitterTimeline t = new TwitterTimeline(this.order);
        if (maxsize < 0) return t;
        
        // remove tweets from this timeline
        synchronized (this.posts) {
            while (this.posts.size() > maxsize) m.add(this.posts.remove(this.posts.firstEntry().getKey()));
        }
        
        // create new timeline
        for (TwitterTweet me: m) {
            t.addUser(this.users.get(me.getScreenName()));
            t.addTweet(me);
        }
        
        // prune away users not needed any more in this structure
        Set<String> screen_names = new HashSet<>();
        for (TwitterTweet me: this.posts.values()) screen_names.add(me.getScreenName());
        synchronized (this.users) {
            Iterator<Map.Entry<String, UserEntry>> i = this.users.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<String, UserEntry> e = i.next();
                if (!screen_names.contains(e.getValue().getScreenName())) i.remove();
            }
        }        
        return t;
    }
    
    public TwitterTimeline add(TwitterTweet tweet, UserEntry user) {
        this.addUser(user);
        this.addTweet(tweet);
        return this;
    }
    
    private TwitterTimeline addTweet(TwitterTweet tweet) {
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
        synchronized (this.posts) {
            TwitterTweet precursorTweet = getPrecursorTweet();
            if (precursorTweet != null && tweet.getCreatedAt().before(precursorTweet.getCreatedAt())) return this; // ignore this tweet in case it would change the list of shown tweets
            this.posts.put(key, tweet);
        }
        return this;
    }
    
    public void putAll(TwitterTimeline other) {
        if (other == null) return;
        assert this.order.equals(other.order);
        for (Map.Entry<String, UserEntry> u: other.users.entrySet()) {
            UserEntry t = this.users.get(u.getKey());
            if (t == null || !t.containsProfileImage()) {
                this.users.put(u.getKey(), u.getValue());
            }
        }
        for (TwitterTweet t: other) this.addTweet(t);
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
    
    public SusiThought toSusi(boolean withEnrichedData) throws JSONException {
        return toSusi(withEnrichedData, new SusiThought());
    }
    
    private SusiThought toSusi(boolean withEnrichedData, SusiThought json) throws JSONException {
        json
            .setQuery(this.query)
            .setHits(Math.max(this.hits, this.size()));
        if (this.scraperInfo.length() > 0) json.setScraperInfo(this.scraperInfo);
        JSONArray statuses = new JSONArray();
        for (TwitterTweet t: this) {
            UserEntry u = this.users.get(t.getScreenName());
            statuses.put(t.toJSON(u, withEnrichedData, Integer.MAX_VALUE, ""));
        }
        json.setData(statuses);
        return json;
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
        for (TwitterTweet TwitterTweet: this) {
            if (latest == 0) {latest = TwitterTweet.created_at.getTime(); continue;}
            earliest = TwitterTweet.created_at.getTime();
            count++;
            if (count >= 19) break;
        }

        if (count == 0) return Long.MAX_VALUE;
        long timeInterval = latest - earliest;
        long p = 1 + timeInterval / count;
        return p < 4000 ? p / 4 + 3000 : p;
    }    
    
    public void writeToIndex() {
        IncomingMessageBuffer.addScheduler(this, true, true);
    }

    //TODO: temporary method to prevent issues related to Timeline class popping-up till next PR
    public PostTimeline toPostTimeline() {
        PostTimeline postList = new PostTimeline(Order.TIMESTAMP);
        for (TwitterTweet me : this) {
            assert me instanceof Post;
            postList.add(me);
        }
        return postList;
    }

}

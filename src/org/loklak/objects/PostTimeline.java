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
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.harvester.Post;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.susi.SusiThought;

/**
 * A timeline2 is a structure which holds tweet for the purpose of presentation
 * There is no tweet retrieval method here, just an iterator which returns the tweets in reverse appearing order
 */
public class PostTimeline extends BasicTimeline<Post> implements Iterable<Post> {

    private long lastKey = 0;
    public boolean dump = false;

    public PostTimeline(Order order) {
        super(order);
    }

    public PostTimeline(Order order, String scraperInfo) {
        super(order, scraperInfo);
    }

    //TODO: to fix this
    public PostTimeline reduceToMaxsize(final int maxsize) {
        List<Post> m = new ArrayList<>();
        PostTimeline t = new PostTimeline(this.order);
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

    public void add(Post post) {
        this.addPost(post);
    }

    public PostTimeline addPost(Post post) {
        String key = "";
        if (this.order == Order.TIMESTAMP) {
            if(post.has("id_str")) {
                key = String.valueOf(post.getPostId());
            } else {
                long keyValue = this.lastKey + 1;
                this.lastKey = keyValue;
                key = String.valueOf(keyValue);
            }
        }
        this.posts.put(key, post);
        return this;
    }

    public PostTimeline addPost(JSONObject post) {
        this.addPost(new Post(post));
        return this;
    }

    public void mergePost(PostTimeline list) {
        for (Post post: list) {
            this.add(post);
        }
    }

    public void mergePost(Post post) {
        this.add(post);

    }

    public void mergePost(PostTimeline[] lists) {
        for (PostTimeline list: lists) {
            this.mergePost(list);
        }
    }

    public void collectMetadata(JSONObject metadata) {
        int hits = 0;
        int count = 0;
        Set<Object> scrapers = new HashSet<>();

        List<String> listKeys = new ArrayList<String>(this.posts.keySet());
        int n = listKeys.size();
        for (int i = 0; i < n; i++) {
            Post postMetadata = (Post) this.posts.get(listKeys.get(i)).get("metadata");
            hits = hits + Integer.parseInt(String.valueOf(postMetadata.get("hits")));
            count = count + Integer.parseInt(String.valueOf(postMetadata.get("count")));
            scrapers.add(postMetadata.get("scraper"));
        }

        metadata.put("hits", hits);
        metadata.put("count", count);
        metadata.put("scraper_count", scrapers.size());
        metadata.put("scrapers", scrapers);
    }

    public UserEntry getUser(Post fromPost) {
        return this.users.get(fromPost.get("ScreenName"));
    }

    public void putAll(PostTimeline other) {
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
        this.dump = true;
        IncomingMessageBuffer.addScheduler(this);
    }


    public TwitterTimeline toTimeline() {

        TwitterTimeline postList = new TwitterTimeline(Order.CREATED_AT);
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

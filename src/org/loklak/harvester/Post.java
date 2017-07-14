package org.loklak.harvester;

import java.util.Date;
import org.json.JSONObject;

/**
 * @author vibhcool (Vibhor Verma)
 * @version 0.1
 * @since 07.06.2017
 *
 * Post abstract class for data objects.
 */
public class Post extends JSONObject {

    protected long timestamp = 0;
    protected String postId;
    private boolean wrapper = false;

    public Post() {
        this.setTimestamp();
    }

    /*
     * If wrapper=true, then this object will be used just to as wrapper of data
     * with no post-id and timestamp
     */
    public Post(boolean wrapper) {
        if(!wrapper) {
            this.setTimestamp();
        } else {
            this.wrapper = true;
        }
    }


    public Post(JSONObject json) {
        this();
    }

    protected Post(long timestamp) {
        this.setTimestamp(timestamp);
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.put("timestamp", timestamp);
        this.timestamp = timestamp;
    }

    public void setTimestamp() {
        if(this.has("timestamp")) {
            long timestampValue = Long.parseLong(String.valueOf(this.get("timestamp")));
            this.setTimestamp(timestampValue);
        } else {
            long timestamp = System.currentTimeMillis();
            this.setTimestamp(timestamp);
        }
    }

    public Date getTimestampDate() {
        return new Date(this.timestamp);
    }

    private void setPostId() {
        if(this.has("post_id")) {
            this.setPostId(String.valueOf(this.get("post_id")));
        } else {
            this.setPostId(String.valueOf(this.getTimestamp()));
        }
    }

    public void setPostId(String postId) {
        this.put("post_id", postId);
        this.postId = postId;
    }

    public String getPostId() {
        return this.postId;
    }

    public boolean isWrapper() {
        return this.wrapper;
    }
}


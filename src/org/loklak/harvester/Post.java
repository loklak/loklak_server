package org.loklak.harvester;

import org.json.JSONObject;

/**
 * @author vibhcool (Vibhor Verma)
 * @version 0.1
 * @since 07.06.2017
 *
 * Post abstract class for data objects.
 */
public abstract class Post extends JSONObject {

    protected long timestamp = 0;
    protected String postId;
    protected Post() {
        this.setTimestamp();
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
        long timestamp = System.currentTimeMillis();
        this.setTimestamp(timestamp);
    }

    //public abstract void getPostId();

    //public abstract String setPostId();
}


package org.loklak.harvester;

import java.util.Date;
import java.util.Map;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.objects.ObjectEntry;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * @author vibhcool (Vibhor Verma)
 * @version 0.1
 * @since 07.06.2017
 *
 * Post abstract class for data objects.
 */
public class Post extends JSONObject implements ObjectEntry {

    protected long timestamp = 0;
    protected String postId = null;
    private boolean wrapper = false;
    private Date createdAt = null;

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
        super(json.toString());
    }

    public Post(Map<String, Object> map) {
        super(map);
    }

    public Post(String data, String query) {
        super(data);
        this.setTimestamp();
        this.setPostId(query);
    }

    protected Post(long timestamp) {
        this.setTimestamp(timestamp);
    }

    public long getTimestamp() {
        if(this.timestamp == 0 && this.has("timestamp_id")) {
            this.timestamp = Long.parseLong(String.valueOf(this.get("timestamp_id")));
        }
        this.setTimestamp(this.timestamp);
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.put("timestamp_id", timestamp);
        this.timestamp = timestamp;
    }

    public void setTimestamp() {
        if(this.has("timestamp_id")) {
            long timestampValue = Long.parseLong(String.valueOf(this.get("timestamp_id")));
            this.setTimestamp(timestampValue);
        } else {
            long timestamp = System.currentTimeMillis();
            this.setTimestamp(timestamp);
        }
    }

    public Date getTimestampDate() {
        DAO.log("date is " + String.valueOf(this.getTimestamp()) + "    " + String.valueOf(new Date(this.getTimestamp())));
        return new Date(this.getTimestamp());
    }

    public DateTime getTimestampDate(String a) {
        DAO.log("date is " + String.valueOf(this.getTimestamp()) + "    " + String.valueOf(new Date(this.getTimestamp())));
        return new DateTime(this.getTimestamp(), DateTimeZone.UTC);
    }

    // if the Post doesn't have 
    public Date getCreated() {
        if(this.createdAt == null) {
            return this.getTimestampDate();
        } else {
            return this.createdAt;
        }
    }

    public void setCreated(Date _createdAt) {
        this.createdAt = _createdAt;
    }

    private void setPostId() {
        if(this.has("id_str")) {
            this.setPostId(String.valueOf(this.get("id_str")));
        } else {
            this.setPostId(String.valueOf(this.getTimestamp()));
        }
    }

    public void setPostId(String postId) {
        this.put("id_str", postId);
        this.postId = postId;
    }

    public String getPostId() {
        if(this.has("id_str")) {
            this.postId = String.valueOf(this.get("id_str"));
        }
        return this.postId;
    }

    public boolean isWrapper() {
        return this.wrapper;
    }

    public String toString() {
        return super.toString();
    }

    public JSONObject toJSON() {
        return this;
    }

    protected String[] getStreamChannels() {
        return new String[] {
            "all"
        };
    }

    public final void publishToMQTT() {
        if (DAO.mqttPublisher != null) {  // Will be null if stream is disabled
            DAO.mqttPublisher.publish(this.getStreamChannels(), this.toString());
        }
    }
}

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
 * @since 07.06.2017
 *
 * Post abstract class for data objects. This object is created to be used as `generic data
 * carrier` object for data scraped by scrapers.
 *
 * Some of the ways this class can be used:
 *
 * 1. This class can also be inherited for specialised data carriers like for twitter, quora,
 *    etc.
 * 2. Object of this class can be directly implemented in scrapers to add data to the data object.
 * 3. This class can also be used at other places other than scrapers as data objects in Loklak
 *    Server.
 *
 * NOTE: Every Post object has unique `postId` else it may lead to loss of pre-existing data with
 *       same `postId`.
 */
public class Post extends JSONObject implements ObjectEntry {

    /**
     * timestamp is when the data was scraped in int datatype
     */
    protected long timestamp = 0;

    /**
     * createdAt is date when the data in Post Object was created in source. If there isn't any
     * value, it is set to timestamp.
     */
    private Date createdAt = null;

    /**
     * Unique id for Post data object.
     */
    protected String postId = null;

    /**
     * Whether Post data object is wrapper of Post objects or not. By default, it is set to false.
     */
    private boolean wrapper = false;

    /**
     * Default constructor with timestamp of Post object created
     */
    public Post() {
        this.setTimestamp();
    }

    /**
     * This is a wrapper Post constructor. This acts as wrapper for Post data objects. This object
     * doesn't have post-id or any timestamp.
     *
     * @param wrapper
     *          if wrapper=true, it is a wrapper else it isn't.
     */
    public Post(boolean wrapper) {
        if(!wrapper) {
            this.setTimestamp();
        } else {
            this.wrapper = true;
        }
    }

    /**
     * Post constructor to convert JSONObject data to Post object.
     *
     * @param json
     *          JSON data as input to Post
     */
    public Post(JSONObject json) {
        super(json.toString());
    }

    /**
     * Post constructor to convert Map to Post object.
     *
     * @param map
     *          A map with key as String and value as Object
     */
    public Post(Map<String, Object> map) {
        super(map);
    }

    /**
     * Post constructor to convert json data in string to Post object.
     *
     * @param data
     *          A string data in json format
     * @param query
     *          Query value to set it as post-id
     */
    public Post(String data, String query) {
        super(data);
        this.setTimestamp();
        this.setPostId(query);
    }

    /**
     * Post constructor with input value of timestamp.
     *
     * @param timestamp
     *          Timestamp at which data was scraped.
     */
    protected Post(long timestamp) {
        this.setTimestamp(timestamp);
    }

    /**
     * To check if this Post object is wrapper or not.
     *
     * @return
     *          A boolean value, true if this is wrapper, else returns false.
     */
    public boolean isWrapper() {
        return this.wrapper;
    }

    /**
     * Returns Post data object's data to JSON string
     *
     * @return
     *          Data as JSON string
     */
    public String toString() {
        return super.toString();
    }

    /**
     * Returns Post data object's data to JSONObject
     *
     * @return
     *          Data as JSONObject
     */
    public JSONObject toJSON() {
        return this;
    }

    /**
     * Channels on which the Tweet will be published - all (default)
     *
     * @return
     *          Array of channels to publish message to.
     */
    protected String[] getStreamChannels() {
        return new String[] {"all"};
    }

    /**
     * Publish data to MQTT from Post data object
     */
    public final void publishToMQTT() {
        if (DAO.mqttPublisher != null) {
            // Will be null if stream is disabled
            DAO.mqttPublisher.publish(this.getStreamChannels(), this.toString());
        }
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

    public long getTimestamp() {
        if(this.timestamp == 0 && this.has("timestamp_id")) {
            this.timestamp = Long.parseLong(String.valueOf(this.get("timestamp_id")));
        }
        this.setTimestamp(this.timestamp);
        return this.timestamp;
    }

    public Date getTimestampDate() {
        DAO.log("date is " + String.valueOf(this.getTimestamp()) + "    " + String.valueOf(new Date(this.getTimestamp())));
        return new Date(this.getTimestamp());
    }

    public DateTime getTimestampDate(String a) {
        DAO.log("date is " + String.valueOf(this.getTimestamp()) + "    " + String.valueOf(new Date(this.getTimestamp())));
        return new DateTime(this.getTimestamp(), DateTimeZone.UTC);
    }

    public void setCreated(Date _createdAt) {
        this.createdAt = _createdAt;
    }

    public Date getCreated() {
        if(this.createdAt == null) {
            this.createdAt = this.getTimestampDate();
        }
        return this.createdAt;
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
}

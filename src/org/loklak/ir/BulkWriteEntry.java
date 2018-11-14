package org.loklak.ir;

import java.util.Map;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class BulkWriteEntry {

    private String id;
    private String type;
    private Long version;
    private Map<String, Object> jsonMap;

    private final static DateTimeFormatter utcFormatter = ISODateTimeFormat.dateTime().withZoneUTC();
    
    /**
     * initialize entry for bulk writes
     * @param id the id of the entry
     * @param type the type name
     * @param timestamp_fieldname the name of the timestamp field, null for unused. If a name is given here, then this field is filled with the current time
     * @param version the version number >= 0 for external versioning or null for forced updates without versioning
     * @param jsonMap the payload object
     */
    public BulkWriteEntry(final String id, final String type, final String timestamp_fieldname, final Long version, final Map<String, Object> jsonMap) {
        this.id = id;
        this.type = type;
        this.version = version;
        this.jsonMap = jsonMap;
        if (timestamp_fieldname != null && !this.jsonMap.containsKey(timestamp_fieldname)) {
            this.jsonMap.put(timestamp_fieldname, utcFormatter.print(System.currentTimeMillis()));
        }
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Long getVersion() {
        return version;
    }

    public Map<String, Object> getJsonMap() {
        return jsonMap;
    }
}

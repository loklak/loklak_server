/**
 *  BulkWriteEntry
 *  Copyright 14.11.2018 by Michael Peter Christen, @0rb1t3r
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

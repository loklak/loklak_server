/**
 *  TimelineCache
 *  Copyright 05.11.2016 by Michael Peter Christen, @0rb1t3r
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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.loklak.server.ClientIdentity;

/**
 * The purpose of the timeline cache is, to provide a storage for paginated search
 */
public class TimelineCache {

    private final long ttl;
    private final Map<String, Timeline> cache;
    
    
    public TimelineCache(long ttl) {
        this.ttl = ttl;
        this.cache = new ConcurrentHashMap<>();
    }
    
    public TimelineCache clean() {
        Iterator<Map.Entry<String, Timeline>> i = this.cache.entrySet().iterator();
        long duetime = System.currentTimeMillis() - this.ttl;
        while (i.hasNext()) {
            if (i.next().getValue().getAccessTime() < duetime) i.remove();
        }
        return this;
    }

    public Timeline getOrCreate(ClientIdentity identity, String query, boolean head, Timeline.Order order) {
        Timeline t = null;
        String cacheID = toKey(identity, query);
        if (!head) {
            t = this.cache.get(cacheID);
            if (t != null) t.updateAccessTime();
        }
        if (t == null) {
            t = new Timeline(order);
            this.cache.put(cacheID, t);
        }
        this.clean();
        return t;
    }
    
    private static String toKey(ClientIdentity identity, String query) {
        return identity.toString() + ":" + query;
    }
}

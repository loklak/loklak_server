/**
 *  IndexFactory
 *  Copyright 26.04.2015 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.data;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.List;

import org.json.JSONObject;
import org.loklak.harvester.Post;
import org.loklak.objects.ObjectEntry;
import org.loklak.objects.SourceType;

public interface IndexFactory<Entry extends ObjectEntry> {

    Entry init(JSONObject json) throws IOException;

    boolean exists(String id);

    boolean existsCache(String id);
    
    Set<String> existsBulk(Collection<String> ids);
    
    boolean delete(String id, SourceType sourceType);

    JSONObject readJSON(String id);
    
    JSONObject readJSONCache(String id);

    boolean writeEntry(IndexEntry<Entry> entry) throws IOException;

    boolean writeEntry(JSONObject json) throws IOException;
    
    ElasticsearchClient.BulkWriteResult writeEntries(Collection<IndexEntry<Entry>> entries) throws IOException;

    ElasticsearchClient.BulkWriteResult writeEntries(List<Post> entries) throws IOException;
    
    void close();
    
}

/**
 *  BulkWriteResult
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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BulkWriteResult {
    
    private Map<String, String> errors;
    private Set<String> created;
    
    public BulkWriteResult() {
        this.errors = new LinkedHashMap<>();
        this.created = new LinkedHashSet<>();
    }
    
    public Map<String, String> getErrors() {
        return this.errors;
    }
    
    public Set<String> getCreated() {
        return this.created;
    }
    
}

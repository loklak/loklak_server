/**
 *  Authorization
 *  Copyright 17.05.2016 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.server;

import org.json.JSONObject;
import org.loklak.tools.storage.JsonFile;

/**
 * Authorization asks: what is the user allowed to do? This class holds user rights.
 * An object instance of this class is handed to each serivce call to enable that
 * service to work according to granted service level.
 * One part of authorization decisions is the history of the past user action.
 * Therefore an authorization object has the attachment of an Accounting object;  
 */
public class Authorization {

    private JsonFile parent;
    private JSONObject json;
    private Accounting accounting;
    
    /**
     * create a new authorization object. The given json object must be taken
     * as value from a parent json. If the parent json is a JsonFile, then that
     * file can be handed over as well to enable persistency.
     * @param json object for storage of the authorization
     * @param parent the parent file or null if there is no parent file (no persistency)
     */
    public Authorization(final JSONObject json, JsonFile parent) {
        this.json = json;
        this.parent = parent;
        this.accounting = null;
    }
    
    public Accounting setAccounting(Accounting accounting) {
        this.accounting = accounting;
        return this.accounting;
    }
    
    public Accounting getAccounting() {
        return this.accounting;
    }
    
    public Authorization setAdmin() {
        this.json.put("admin", true);
        if (parent != null) parent.commit();
        return this;
    }
    
    boolean isAdmin() {
        if (!this.json.has("admin")) return false;
        return this.json.getBoolean("admin");
    }
    
    public Authorization setRequestFrequency(String path, int reqPerHour) {
        if (!this.json.has("frequency")) {
            this.json.put("frequency", new JSONObject());
        }
        JSONObject paths = this.json.getJSONObject("frequency");
        paths.put(path, reqPerHour);
        if (parent != null) parent.commit();
        return this;
    }
    
    public int getRequestFrequency(String path) {
        if (!this.json.has("frequency")) return -1;
        JSONObject paths = this.json.getJSONObject("frequency");
        if (!paths.has(path)) return -1;
        return paths.getInt(path);
    }

}

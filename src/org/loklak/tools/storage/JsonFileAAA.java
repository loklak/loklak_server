/**
 *  JsonFileAAA
 *  Copyright 03.06.2016 by Robert Mader, @treba123
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

package org.loklak.tools.storage;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONObject;

/**
 * This extends JsonFile with a cleanup function, handy for thread-save cleanup of loklaks Authentication, Authorization and Accounting files
 *
 */
public class JsonFileAAA extends JsonFile {

	public JsonFileAAA(File file) throws IOException{
		super(file);
	}
	
	// remove all expired objects
	public synchronized void cleanupExpirded(){
		Iterator<String> keys = this.keys();
		Set<String> expiredKeys = new HashSet<String>();
		
		// iterate over all keys
		while(keys.hasNext()){
			String key = keys.next();
			JSONObject object = this.getJSONObject(key);
			
			// check if timestamp is still valid
			if(object.has("expires_on") && object.getLong("expires_on") < Instant.now().getEpochSecond()){
				// remove keys at the end to not confuse the iterator
				expiredKeys.add(key);
			}
		}
		
		// if keys are expired, remove them in all
		if(!expiredKeys.isEmpty()) super.removeSet(expiredKeys);
	}
}

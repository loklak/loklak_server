/**
 *  DatabaseCleanupThread
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

import org.eclipse.jetty.util.log.Log;


/**
 * This thread calls a cleanup function on a set of JsonFileAAA
 *
 */
public class DatabaseCleanupThread extends Thread {
	
	private boolean shallRun = true;
	private JsonFileAAA[] files;
	
	public DatabaseCleanupThread(JsonFileAAA[] files){
		this.files = files;
	}
	
	/**
     * ask the thread to shut down
     */
    public void shutdown() {
    	this.shallRun = false;
        this.interrupt();
        Log.getLog().info("catched database cleanup termination signal");
    }
    
    @Override
    public void run() {
    	while(this.shallRun) try{
    		for(JsonFileAAA file : files){
    			file.cleanupExpirded();
    		}
    		sleep(10 * 60 * 1000); // sleep ten minutes
    	} catch (InterruptedException e){
    	} catch (Throwable e){
    		Log.getLog().warn("DATABASE CLEANUP THREAD", e);
    	}
    	Log.getLog().info("database cleanup terminated");
    }
}

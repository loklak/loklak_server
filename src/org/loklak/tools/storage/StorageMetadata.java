/**
 *  StorageMetadata
 *  Copyright 21.08.2020 by Michael Peter Christen, @0rb1t3r
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

public class StorageMetadata {

    public final String bucketName, objectName, contentType;
    public final long createdTime, length;

    public StorageMetadata(String bucketName, String objectName, String contentType, long createdTime, long length) {
        this.bucketName = bucketName;
        this.objectName = objectName;
        this.contentType = contentType;
        this.createdTime = createdTime;
        this.length = length;
    }

}

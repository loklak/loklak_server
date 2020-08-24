/**
 *  Storage
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public interface Storage {

    public Storage setTimeout(long connectTimeout, long writeTimeout, long readTimeout);

    public boolean exists(String bucket) throws IOException;
    public StorageMetadata exists(String bucket, String object) throws IOException;

    public InputStream get(String bucket, String object) throws IOException;
    public byte[] getBytes(String bucket, String object, byte[] dflt) throws IOException;
    public String getString(String bucket, String object, Charset encoding, String dflt) throws IOException;
    public Boolean getBoolean(String bucket, String object, Boolean dflt) throws IOException;
    public Double getDouble(String bucket, String object, Double dflt) throws IOException;
    public Float getFloat(String bucket, String object, Float dflt) throws IOException;
    public Integer getInt(String bucket, String object, Integer dflt) throws IOException;
    public Long getLong(String bucket, String object, Long dflt) throws IOException;
    public JSONObject getJSON(String bucket, String object, JSONObject dflt) throws IOException;

    public Storage put(String bucket, String object, String value, Charset encoding) throws IOException;
    public Storage put(String bucket, String object, boolean value) throws IOException;
    public Storage put(String bucket, String object, double value) throws IOException;
    public Storage put(String bucket, String object, float value) throws IOException;
    public Storage put(String bucket, String object, int value) throws IOException;
    public Storage put(String bucket, String object, long value) throws IOException;
    public Storage put(String bucket, String object, JSONObject value) throws IOException;

    public Storage put(String bucket, String object, File f) throws IOException;
    public Storage put(String bucket, String object, byte[] b) throws IOException;
    public Storage put(String bucket, String object, InputStream is) throws IOException;

    public Storage delete(String bucket, String... objects) throws IOException;
    public Storage copy(String toBucket, String toObject, String fromBucket, String fromObject) throws IOException;

    public Storage append(String toBucket, String toObject, String fromBucket, String... fromObjects) throws IOException;
    public Storage append(String toBucket, String toObject, Map<String, Collection<String>> fromMap) throws IOException;

    public Storage make(String bucket) throws IOException;

    public List<String> list(String bucket, String prefix) throws IOException;
    public List<String> list() throws IOException;

    public List<String> incomplete(String bucket, String prefix) throws IOException;

}

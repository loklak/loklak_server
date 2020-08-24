/**
 *  AbstractStorage
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONTokener;

public abstract class AbstractStorage implements Storage {


    @Override
    public String getString(String bucket, String object, Charset encoding, String dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return new String(b, encoding);
    }

    @Override
    public byte[] getBytes(String bucket, String object, byte[] dflt) throws IOException {
        InputStream is;
        try {
            is = this.get(bucket, object);
        } catch (IOException e) {
            return dflt;
        }
        if (is == null) return dflt;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int i;
        byte[] data = new byte[2048];
        while ((i = is.read(data, 0, data.length)) > 0) {
            buffer.write(data, 0, i);
        }
        byte[] b = buffer.toByteArray();
        return b;
    }

    @Override
    public Boolean getBoolean(String bucket, String object, Boolean dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return Boolean.parseBoolean(new String(b, StandardCharsets.UTF_8));
    }

    @Override
    public Double getDouble(String bucket, String object, Double dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return Double.parseDouble(new String(b, StandardCharsets.UTF_8));
    }

    @Override
    public Float getFloat(String bucket, String object, Float dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return Float.parseFloat(new String(b, StandardCharsets.UTF_8));
    }

    @Override
    public Integer getInt(String bucket, String object, Integer dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return Integer.parseInt(new String(b, StandardCharsets.UTF_8));
    }

    @Override
    public Long getLong(String bucket, String object, Long dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return Long.parseLong(new String(b, StandardCharsets.UTF_8));
    }

    @Override
    public JSONObject getJSON(String bucket, String object, JSONObject dflt) throws IOException {
        byte[] b = getBytes(bucket, object, null);
        if (b == null) return dflt;
        return new JSONObject(new JSONTokener(new String(b, StandardCharsets.UTF_8)));
    }

    @Override
    public Storage put(String bucket, String object, String value, Charset encoding) throws IOException {
        return this.put(bucket, object, value.getBytes(encoding));
    }

    @Override
    public Storage put(String bucket, String object, boolean value) throws IOException {
        return this.put(bucket, object, Boolean.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Storage put(String bucket, String object, double value) throws IOException {
        return this.put(bucket, object, Double.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Storage put(String bucket, String object, float value) throws IOException {
        return this.put(bucket, object, Float.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Storage put(String bucket, String object, int value) throws IOException {
        return this.put(bucket, object, Integer.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Storage put(String bucket, String object, long value) throws IOException {
        return this.put(bucket, object, Long.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Storage put(String bucket, String object, File f) throws IOException {
        return this.put(bucket, object, new FileInputStream(f));
    }

    @Override
    public Storage put(String bucket, String object, byte[] b) throws IOException {
        return this.put(bucket, object, new ByteArrayInputStream(b));
    }

    @Override
    public Storage put(String bucket, String object, JSONObject value) throws IOException {
        return this.put(bucket, object, value.toString(0).getBytes(StandardCharsets.UTF_8));
    }
    
    public static void main(String[] args) {
        try {
            Storage s = new FileStorage(new File("/tmp/storagetest"));

            s
            .make("test1")
            .put("test2", "p1", "hello", StandardCharsets.UTF_8)
            .put("test2", "p2", " world", StandardCharsets.UTF_8)
            .append("test2", "p3", "test2", "p1", "p2");

            List<String> l = s.list();
            System.out.println("buckets: " + l.toString());
            for (String bucket: l) {
                System.out.println("files in bucket " + bucket + ": " + s.list(bucket, ""));
            }

            System.out.println("p3: " + s.getString("test2", "p3", StandardCharsets.UTF_8, ""));

            System.out.println("filter1 in test2: " + s.list("test2", ""));
            System.out.println("filter2 in test2: " + s.list("test2", "p"));
            System.out.println("filter3 in test2: " + s.list("test2", "p1"));

            s.delete("test2", "p1", "p2", "p3");
            s.delete("test1");
            s.delete("test2");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

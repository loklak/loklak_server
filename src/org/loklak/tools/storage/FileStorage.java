/**
 *  FileStorage
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FileStorage extends AbstractStorage implements Storage {

    private File baseFile;

    public FileStorage(File base) throws IOException {
        if (!base.exists()) base.mkdirs();
        if (!base.isDirectory()) throw new IOException(base.toString() + " must be a directory");
        this.baseFile = base;
    }
    public FileStorage(Path base) throws IOException {
        this.baseFile = base.toFile();
        if (!baseFile.isDirectory()) throw new IOException(base.toString() + " must be a directory");
    }

    @Override
    public S3Storage setTimeout(long connectTimeout, long writeTimeout, long readTimeout) {
        return null; // do nothing
    }

    private File constructFile(String bucket) throws IOException {
        if (bucket.indexOf("..") >= 0) throw new IOException("operation not allowed");
        return new File(this.baseFile, bucket);
    }

    private File constructFile(String bucket, String object) throws IOException {
        if (bucket.indexOf("..") >= 0 || object.indexOf("..") >= 0) throw new IOException("operation not allowed");
        File f = new File(this.baseFile, bucket);
        String[] o = object.split("/");
        for (int i = 0; i < o.length; i++) f = new File(f, o[i]);
        return f;
    }

    @Override
    public boolean exists(String bucket) throws IOException {
        return constructFile(bucket).exists();
    }

    @Override
    public StorageMetadata exists(String bucket, String object) throws IOException {
        File f = constructFile(bucket, object);
        if (!f.exists()) throw new IOException("file does not exist: " + f.toString());
        String contentType = Files.probeContentType(f.toPath());
        long createdTime = f.lastModified();
        long length = f.length();
        return new StorageMetadata(bucket, object, contentType, createdTime, length);
    }

    @Override
    public InputStream get(String bucket, String object) throws IOException {
        return new FileInputStream(constructFile(bucket, object));
    }

    @Override
    public Storage put(String bucket, String object, InputStream is) throws IOException {
        byte[] buffer = new byte[2048];
        File f = constructFile(bucket, object);
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        int len;
        while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
        return this;
    }

    @Override
    public Storage delete(String bucket, String... objects) throws IOException {
        if (objects.length == 0) {
            // delete the bucket
            File f = constructFile(bucket);
            f.delete();
        } else {
            // delete objects
            for (String object: objects) {
                File f = constructFile(bucket, object);
                if (f.exists()) f.delete();
            }
        }
        return this;
    }

    @Override
    public Storage copy(String toBucket, String toObject, String fromBucket, String fromObject) throws IOException {
        byte[] buffer = new byte[2048];
        File fin = constructFile(fromBucket, fromObject);
        File fout = constructFile(toBucket, toObject);
        FileInputStream fis = new FileInputStream(fin);
        FileOutputStream fos = new FileOutputStream(fout);
        int len;
        while ((len = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fis.close();
        fos.close();
        return this;
    }

    @Override
    public Storage append(String toBucket, String toObject, String fromBucket, String... fromObjects) throws IOException {
        byte[] buffer = new byte[2048];
        File fout = constructFile(toBucket, toObject);
        FileOutputStream fos = new FileOutputStream(fout, true);
        for (String fromObject: fromObjects) {
            File fin = constructFile(fromBucket, fromObject);
            FileInputStream fis = new FileInputStream(fin);
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fis.close();
        }
        fos.close();
        return this;
    }

    @Override
    public Storage append(String toBucket, String toObject, Map<String, Collection<String>> fromMap) throws IOException {
        byte[] buffer = new byte[2048];
        File fout = constructFile(toBucket, toObject);
        FileOutputStream fos = new FileOutputStream(fout, true);
        for (Map.Entry<String, Collection<String>> fromEntry: fromMap.entrySet()) {
            for (String fromObject: fromEntry.getValue()) {
                File fin = constructFile(fromEntry.getKey(), fromObject);
                FileInputStream fis = new FileInputStream(fin);
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fis.close();
            }
        }
        fos.close();
        return this;
    }

    @Override
    public Storage make(String bucket) throws IOException {
        File f = constructFile(bucket);
        f.mkdirs();
        return this;
    }

    @Override
    public List<String> list(String bucket, String prefix) throws IOException {
        File f = constructFile(bucket, prefix);

        List<String> objects = new ArrayList<>();
        if (f.exists() && f.isDirectory()) {
            String[] x = f.list();
            for (String y: x) objects.add(y);
        } else {
            String n = f.getName();
            f = f.getParentFile();
            if (f.exists()) {
                String[] x = f.list();
                for (String y: x) if (y.startsWith(n)) objects.add(y);
            }
        }
        return objects;
    }

    @Override
    public List<String> list() throws IOException {
        String[] files = this.baseFile.list();
        List<String> buckets = new ArrayList<>();
        for (String file: files) {
            if (new File(this.baseFile, file).isDirectory()) buckets.add(file);
        }
        return buckets;
    }

    @Override
    public List<String> incomplete(String bucket, String prefix) throws IOException {
        return new ArrayList<>(0);
    }

}

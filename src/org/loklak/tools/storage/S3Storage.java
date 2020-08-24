/**
 *  S3Storage
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.DisableVersioningArgs;
import io.minio.GetObjectArgs;
import io.minio.ListIncompleteUploadsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.SetBucketNotificationArgs;
import io.minio.StatObjectArgs;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteObject;
import io.minio.messages.EventType;
import io.minio.messages.Item;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import io.minio.messages.Upload;

public class S3Storage extends AbstractStorage implements Storage {

    private String endpoint, accessKey, secretKey;
    MinioClient s3;

    public S3Storage(String endpoint, String accessKey, String secretKey) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.s3 = MinioClient.builder()
                .endpoint(this.endpoint)
                .credentials(this.accessKey, this.secretKey)
                .build();
    }

    @Override
    public Storage setTimeout(long connectTimeout, long writeTimeout, long readTimeout) {
        this.s3.setTimeout(connectTimeout, writeTimeout, readTimeout);
        return this;
    }

    @Override
    public boolean exists(String bucket) throws IOException {
        try {
            return this.s3.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket).build());
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public StorageMetadata exists(String bucket, String object) throws IOException {
        try {
            ObjectStat stat = this.s3.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(object).build());
            StorageMetadata m = new StorageMetadata(
                    stat.bucketName(), stat.name(), stat.contentType(),
                    stat.createdTime().toEpochSecond() * 1000L, stat.length());
            return m;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public InputStream get(String bucket, String object) throws IOException {
        try {
            return this.s3.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(object).build());
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Storage put(String bucket, String object, InputStream is) throws IOException {
        return this.put(bucket, object, is, is.available(), -1);
    }

    private Storage put(String bucket, String object, InputStream is, long objectSize, long partSize) throws IOException {
        try {
            /*ObjectWriteResponse response = */ this.s3.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(object).stream(is, objectSize, partSize).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Storage delete(String bucket, String... objects) throws IOException {
        if (objects.length == 0) try {
            /*ObjectWriteResponse response = */ this.s3.removeBucket(RemoveBucketArgs.builder()
                    .bucket(bucket).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } else  if (objects.length == 1) try {
            /*ObjectWriteResponse response = */ this.s3.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objects[0]).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } else try {
            List<DeleteObject> o = new ArrayList<>();
            for (int i = 0; i < objects.length; i++) {
                o.add(new DeleteObject(objects[i]));
            }
            this.s3.removeObjects(RemoveObjectsArgs.builder().bucket(bucket).objects(o).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Storage copy(String toBucket, String toObject, String fromBucket, String fromObject) throws IOException {
        try {
            /*ObjectWriteResponse response = */ this.s3.copyObject(CopyObjectArgs.builder()
                    .bucket(toBucket).object(toObject).source(CopySource.builder().bucket(fromBucket).object(fromObject).build()).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Storage append(String toBucket, String toObject, String fromBucket, String... fromObjects) throws IOException {
        try {
            List<ComposeSource> list = new ArrayList<>();
            for (String o: fromObjects) {
                list.add(ComposeSource.builder().bucket(fromBucket).object(o).build());
            }
            /*ObjectWriteResponse response = */ this.s3.composeObject(ComposeObjectArgs.builder()
                    .bucket(toBucket).object(toObject).sources(list).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Storage append(String toBucket, String toObject, Map<String, Collection<String>> fromMap) throws IOException {
        try {
            List<ComposeSource> list = new ArrayList<>();
            for (Map.Entry<String, Collection<String>> me: fromMap.entrySet()) {
                for (String o: me.getValue()) {
                    list.add(ComposeSource.builder().bucket(me.getKey()).object(o).build());
                }
            }
            /*ObjectWriteResponse response = */ this.s3.composeObject(ComposeObjectArgs.builder()
                    .bucket(toBucket).object(toObject).sources(list).build());
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Storage make(String bucket) throws IOException {
        try {
            /*ObjectWriteResponse response = */ this.s3.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket).build());
            disableVersioning(bucket);
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    private Storage disableVersioning(String bucket) throws IOException {
        try {
            this.s3.disableVersioning(DisableVersioningArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        return this;
    }

    @Override
    public List<String> list(String bucket, String prefix) throws IOException {
        try {
            Iterable<Result<Item>> results = this.s3.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(prefix).build());
            List<String> i = new ArrayList<>();
            for (Result<Item> r: results) {
                i.add(r.get().objectName());
            }
            return i;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public List<String> list() throws IOException {
        try {
            List<Bucket> buckets = this.s3.listBuckets();
            List<String> bs = new ArrayList<>(buckets.size());
            buckets.forEach(b -> bs.add(b.name()));
            return bs;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public List<String> incomplete(String bucket, String prefix) throws IOException {
        try {
            Iterable<Result<Upload>> results = this.s3.listIncompleteUploads(ListIncompleteUploadsArgs.builder()
                    .bucket(bucket).recursive(true).prefix(prefix).build());
            List<String> i = new ArrayList<>();
            for (Result<Upload> r: results) {
                i.add(r.get().objectName());
            }
            return i;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private Storage notification(String bucket, String prefix, String suffix) throws IOException {
        try {
            List<EventType> eventList = new ArrayList<>();
            eventList.add(EventType.OBJECT_CREATED_PUT);
            eventList.add(EventType.OBJECT_CREATED_COPY);
            eventList.add(EventType.OBJECT_ACCESSED_ANY);
            QueueConfiguration qc = new QueueConfiguration();
            qc.setQueue("arn:minio:sqs::1:webhook");
            qc.setEvents(eventList);
            qc.setPrefixRule(prefix);
            qc.setSuffixRule(suffix);
            List<QueueConfiguration> cl = new ArrayList<>();
            cl.add(qc);
            NotificationConfiguration config = new NotificationConfiguration();
            config.setQueueConfigurationList(cl);

            /*ObjectWriteResponse response = */ this.s3.setBucketNotification(SetBucketNotificationArgs.builder()
                    .bucket(bucket).config(config).build());
            disableVersioning(bucket);
            return this;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

}

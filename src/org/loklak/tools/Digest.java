/**
 *  Digest
 *  (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 28.12.2008 on http://yacy.net
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

package org.loklak.tools;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.loklak.data.DAO;

public class Digest {

    public static Queue<MessageDigest> digestPool = new ConcurrentLinkedQueue<MessageDigest>();
    private static Map<String, byte[]> md5Cache = new HashMap<String, byte[]>();

    /**
     * clean the md5 cache
     */
    public static void cleanup() {
        md5Cache.clear();
    }

    public static String encodeHex(final long in, final int length) {
        String s = Long.toHexString(in);
        while (s.length() < length) s = "0" + s;
        return s;
    }

    public static String encodeOctal(final byte[] in) {
        if (in == null) return "";
        final StringBuilder result = new StringBuilder(in.length * 8 / 3);
        for (final byte element : in) {
            if ((0Xff & element) < 8) result.append('0');
            result.append(Integer.toOctalString(0Xff & element));
        }
        return result.toString();
    }

    public static String encodeHex(final byte[] in) {
        if (in == null) return "";
        final StringBuilder result = new StringBuilder(in.length * 2);
        for (final byte element : in) {
            if ((0Xff & element) < 16) result.append('0');
            result.append(Integer.toHexString(0Xff & element));
        }
        return result.toString();
    }

    public static byte[] decodeHex(final String hex) {
        final byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (16 * Integer.parseInt(Character.toString(hex.charAt(i * 2)), 16) + Integer.parseInt(Character.toString(hex.charAt(i * 2 + 1)), 16));
        }
        return result;
    }

    public static String encodeMD5Hex(final String key) {
        // generate a hex representation from the md5 of a string
        return encodeHex(encodeMD5Raw(key));
    }

    public static String encodeMD5Hex(final File file) throws IOException {
        // generate a hex representation from the md5 of a file
        return encodeHex(encodeMD5Raw(file));
    }

    public static String encodeMD5Hex(final byte[] b) {
        // generate a hex representation from the md5 of a byte-array
        return encodeHex(encodeMD5Raw(b));
    }

    public static byte[] encodeMD5Raw(final String key) {

        byte[] h = md5Cache.get(key);
        if (h != null) return h;

        MessageDigest digest = digestPool.poll();
        if (digest == null) {
            // if there are no digest objects left, create some on the fly
            // this is not the most effective way but if we wouldn't do that the encoder would block
            try {
                digest = MessageDigest.getInstance("MD5");
                digest.reset();
            } catch (final NoSuchAlgorithmException e) {
            }
        } else {
            digest.reset(); // they should all be reseted but anyway; this is safe
        }
        byte[] keyBytes;
        keyBytes = UTF8.getBytes(key);
        digest.update(keyBytes);
        final byte[] result = digest.digest();
        digest.reset(); // to be prepared for next
        digestPool.add(digest);
        //System.out.println("Digest Pool size = " + digestPool.size());

        // update the cache
        md5Cache.put(key, result); // prevent expensive MD5 computation and encoding
        return result;
    }

    public static byte[] encodeMD5Raw(final File file) throws IOException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (final java.io.FileNotFoundException e) {
        	DAO.severe("file not found:" + file.toString(), e);
            return null;
        }

        // create a concurrent thread that consumes data as it is read
        // and computes the md5 while doing IO
        final md5FilechunkConsumer md5consumer = new md5FilechunkConsumer(1024 * 64, 8);
        final ExecutorService service = Executors.newSingleThreadExecutor();
        final Future<MessageDigest> md5result = service.submit(md5consumer);
        service.shutdown();

        filechunk c;
        try {
            while (true) {
                c = md5consumer.nextFree();
                if (c == null) throw new IOException("c == null, probably interrupted");
                c.n = in.read(c.b);
                if (c.n <= 0) break;
                md5consumer.consume(c);
            }
        } catch (final IOException e) {
        	DAO.severe("file error with " + file.toString() + ": " + e.getMessage(), e);
            md5consumer.consume(md5FilechunkConsumer.poison);
            throw e;
        } finally {
            try {in.close();} catch (final IOException e) {}
        }
        // put in poison into queue to tell the consumer to stop
        md5consumer.consume(md5FilechunkConsumer.poison);

        // return the md5 digest from future task
        try {
            return md5result.get().digest();
        } catch (final InterruptedException e) {
        	DAO.severe(e);
            throw new IOException(e);
        } catch (final ExecutionException e) {
        	DAO.severe(e);
            throw new IOException(e);
        }
    }

    private static class filechunk {
        public byte[] b;
        public int n;
        public filechunk(final int len) {
            this.b = new byte[len];
            this.n = 0;
        }
    }

    private static class md5FilechunkConsumer implements Callable<MessageDigest> {

        private   final BlockingQueue<filechunk> empty;
        private   final BlockingQueue<filechunk> filed;
        protected static filechunk poison = new filechunk(0);
        private   MessageDigest digest;

        public md5FilechunkConsumer(final int bufferSize, final int bufferCount) {
            this.empty = new ArrayBlockingQueue<filechunk>(bufferCount);
            this.filed = new LinkedBlockingQueue<filechunk>();
            // fill the empty queue
            for (int i = 0; i < bufferCount; i++) this.empty.add(new filechunk(bufferSize));
            // init digest
            try {
                this.digest = MessageDigest.getInstance("MD5");
            } catch (final NoSuchAlgorithmException e) {
                System.out.println("Internal Error at md5:" + e.getMessage());
            }
            this.digest.reset();
        }

        public void consume(final filechunk c) {
            try {
                this.filed.put(c);
            } catch (final InterruptedException e) {
            	DAO.severe(e);
            }
        }

        public filechunk nextFree() throws IOException {
            try {
                return this.empty.take();
            } catch (final InterruptedException e) {
            	DAO.severe(e);
                throw new IOException(e);
            }
        }

        @Override
        public MessageDigest call() {
            try {
                filechunk c;
                while(true) {
                    c = this.filed.take();
                    if (c == poison) break;
                    this.digest.update(c.b, 0, c.n);
                    this.empty.put(c);
                }
            } catch (final InterruptedException e) {
            	DAO.severe(e);
            }
            return this.digest;
        }

    }

    public static String fastFingerprintHex(final File file, final boolean includeDate) {
        try {
            return encodeHex(fastFingerprintRaw(file, includeDate));
        } catch (final IOException e) {
            return null;
        }
    }


    /**
     * the fast fingerprint computes a md5-like hash from a given file,
     * which is different from a md5 because it does not read the complete file
     * but reads only the first and last megabyte of it. In case that the
     * file is less or equal of one megabyte, the fast fingerprint is equal
     * to the md5. In other cases the fingerprint is computed from a
     * array = byte[32k + 8] array, which consists of:
     * array[0   .. 16k - 1] = first MB of file
     * array[16k .. 32k - 1] = last MB of file
     * array[32k .. 32k + 7] = length of file as long
     * if the date flag is set, the array is extended to
     * array[32k + 8 .. 32k + 15] = lastModified of file as long
     * @param file
     * @return fingerprint in md5 raw format
     * @throws IOException
     */
    public static byte[] fastFingerprintRaw(final File file, final boolean includeDate) throws IOException {
        final int mb = 16 * 1024;
        final long fl = file.length();
        if (fl <= 2 * mb) return encodeMD5Raw(file);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
        	DAO.severe(e);
            return null;
        }
        final RandomAccessFile raf = new RandomAccessFile(file, "r");
        final byte[] a = new byte[mb];
        try {
            raf.seek(0);
            raf.readFully(a, 0, mb);
            digest.update(a, 0, mb);
            raf.seek(fl - mb);
            raf.readFully(a, 0, mb);
            digest.update(a, 0, mb);
            digest.update(longToBytes(fl), 0, 8);
            if (includeDate) digest.update(longToBytes(file.lastModified()), 0, 8);
        } finally {
            raf.close();
            try {raf.getChannel().close();} catch (final IOException e) {}
        }
        return digest.digest();
    }
    
    private static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        byte[] b = buffer.array();
        assert b.length == 8;
        return b;
    }
    
    private static byte[] encodeMD5Raw(final byte[] b) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.reset();
            final InputStream  in = new ByteArrayInputStream(b);
            final byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
            in.close();
            // now compute the hex-representation of the md5 digest
            return digest.digest();
        } catch (final java.security.NoSuchAlgorithmException e) {
            System.out.println("Internal Error at md5:" + e.getMessage());
        } catch (final java.io.IOException e) {
            System.out.println("byte[] error: " + e.getMessage());
        }
        return null;
    }

}

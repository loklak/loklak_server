package org.loklak;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import org.json.JSONException;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.harvester.TwitterScraper;
import org.loklak.objects.AbstractObjectEntry;
import org.loklak.objects.MessageEntry;
import org.loklak.tools.DateParser;
import org.loklak.tools.GZip;
import org.loklak.tools.storage.JsonFactory;
import org.loklak.tools.storage.JsonStreamReader;

public class DumpResorter {

    public static Set<String> getOwnDumps(File in) {
        String[] list = in.list();
        TreeSet<String> dumps = new TreeSet<>(); // sort the names with a tree set
        for (String s: list) {
            if (s.startsWith(DAO.MESSAGE_DUMP_FILE_PREFIX))
                try {
                    dumps.add(new File(in, s).getCanonicalFile().toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        return dumps;
    }

    private static String metaPath(String dumpPath) {
        String metaPath = dumpPath.substring(0, dumpPath.length() - 6) + "metadata";
        return metaPath;
    }
    
    public static String getNextRaw(File in, int count) {
        Set<String> dumps = getOwnDumps(in);
        for (String dump: dumps) {
            if (dump.endsWith(".txt.gz")) {
                String metaPath = metaPath(dump);
                if (dumps.contains(metaPath)) continue;
                if (count == 0) return dump;
                count--;
                continue;
            }
        }
        return null;
    }

    public final static TimeZone UTCtimeZone = TimeZone.getTimeZone("UTC");

    public static void main(String[] args) {
        File in = FileSystems.getDefault().getPath("data","dump","own").toFile();
        File out = FileSystems.getDefault().getPath("data","dump","byday").toFile();
        out.mkdirs();
        File next = new File(getNextRaw(in, 0));
        ConcurrentHashMap<String, ConcurrentHashMap<String, byte[]>> buffer = new ConcurrentHashMap<>();
        try {
            JsonStreamReader dumpReader = new JsonStreamReader(new GZIPInputStream(new FileInputStream(next)), next.getAbsolutePath(), Runtime.getRuntime().availableProcessors());
            final Thread readerThread = new Thread(dumpReader);
            readerThread.start();
            Thread[] indexerThreads = new Thread[dumpReader.getConcurrency()];
            for (int i = 0; i < dumpReader.getConcurrency(); i++) {
                indexerThreads[i] = new Thread() {
                    public void run() {
                        JsonFactory tweet;
                        try {
                            while ((tweet = dumpReader.take()) != JsonStreamReader.POISON_JSON_MAP) {
                                try {
                                    String original = tweet.getString();
                                    JSONObject json = tweet.getJSON();
                                    String id = json.optString("id_str");
                                    if (id == null) continue;

                                    TwitterScraper.TwitterTweet t = new TwitterScraper.TwitterTweet(json);
                                    // pure tweets: no images, no videos, no audio, no mentions, no links, no hashtags

                                    Object created_at_obj = json.opt(AbstractObjectEntry.CREATED_AT_FIELDNAME);
                                    if (created_at_obj == null) continue;
                                    Date created_at = MessageEntry.parseDate(created_at_obj);
                                    String fileName = "";
                                    synchronized (DateParser.dayDateFormat) {
                                        fileName = DateParser.dayDateFormat.format(created_at);
                                    }
                                    String sortName = "";
                                    synchronized (DateParser.iso8601MillisFormat) {
                                        sortName = DateParser.iso8601MillisFormat.format(created_at);
                                    }
                                    String sortKey = id;
                                    while (sortKey.length() < 20) sortKey = "0" + sortKey;
                                    sortKey = sortName + " " + sortKey;

                                    ConcurrentHashMap<String, byte[]> dayMap = buffer.get(fileName);
                                    if (dayMap == null) {
                                        dayMap = new ConcurrentHashMap<>();
                                        ConcurrentHashMap<String, byte[]> previous = buffer.putIfAbsent(fileName, dayMap);
                                        if (previous != null) dayMap = previous;
                                    }
                                    dayMap.put(sortKey, GZip.gzip(original));

                                    //System.out.println(sortKey + ": " + json.toString());
                                    /*
                                    Calendar cal = Calendar.getInstance();
                                    cal.setTimeZone(UTCtimeZone);
                                    cal.setTime(created_at);
                                    cal.get(Calendar.YEAR);
                                    cal.get(Calendar.MONTH);
                                    cal.get(Calendar.DAY_OF_MONTH);
                                    */
                                } catch (IOException e) {
                                    DAO.severe(e);
                                }
                            }
                        } catch (InterruptedException e) {
                            DAO.severe(e);
                        }
                    }
                };
                indexerThreads[i].start();
            }
            boolean running = true;
            while (running) {
                running = false;
                for (int i = 0; i < dumpReader.getConcurrency(); i++) {
                    if (indexerThreads[i].isAlive()) running = true;
                }
                try {Thread.sleep(1000);} catch (InterruptedException e) {}
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        // buffer has now the full file aggregated. sort out the contents
        buffer.forEach((fileName, map) -> {
            // sort the tweet map
            TreeMap<String, byte[]> sorted = new TreeMap<>();
            sorted.putAll(map);

            // store the dump
            File dump = new File(out, fileName + ".txt");

            try {
                FileWriter writer = new FileWriter(dump);
                sorted.values().forEach(gzip -> {
                    String tweet = GZip.gunzipString(gzip);
                    try {
                        writer.write(tweet);
                        writer.write("\n");
                    } catch (IOException e) {}
                });
                writer.close();
            } catch (IOException e) {
                throw new JSONException(e.getMessage());
            }
            System.out.println(fileName + ": " + map.size() + " entries");
        });
    }
}

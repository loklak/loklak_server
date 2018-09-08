/**
 *  DumpProcessConversation
 *  Copyright 18.04.2018 by Michael Peter Christen, @0rb1t3r
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

package org.loklak;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.harvester.TwitterScraper.TwitterTweet;

import org.loklak.tools.storage.JsonFactory;
import org.loklak.tools.storage.JsonReader;
import org.loklak.tools.storage.JsonStreamReader;

public class DumpProcessConversation extends Thread {

    private boolean shallRun = true, isBusy = false;
    private int count = Integer.MAX_VALUE;

    public DumpProcessConversation(int count) {
        this.count = count;
    }

    /**
     * ask the thread to shut down
     */
    public void shutdown() {
        this.shallRun = false;
        this.interrupt();
        DAO.log("catched ProcessConversation termination signal");
    }

    public boolean isBusy() {
        return this.isBusy;
    }

    @Override
    public void run() {

        Set<File> processedFiles = new HashSet<>();
        
        // work loop
        loop: while (this.shallRun) try {

            this.isBusy = false;

            // scan dump input directory to import files
            Collection<File> dumps = DAO.message_dump.getOwnDumps(this.count);

            File dump = null;
            select: for (File d: dumps) {
                if (processedFiles.contains(d)) continue select;
                dump = d; break;
            }
            if (dump == null) {
                Thread.currentThread();
                Thread.sleep(10000);
                continue loop;
            }
            this.isBusy = true;

            // take only one file and process this file
            final JsonReader dumpReader = DAO.message_dump.getDumpReader(dump);
            final AtomicLong tweetcount = new AtomicLong(0);
            DAO.log("started scan of dump file " + dump.getAbsolutePath());

            // aggregation object
            Map<String, Map<Long, TwitterTweet>> usertweets = new ConcurrentHashMap<>();
            
            // we start concurrent indexing threads to process the json objects
            Thread[] indexerThreads = new Thread[dumpReader.getConcurrency()];
            for (int i = 0; i < dumpReader.getConcurrency(); i++) {
                indexerThreads[i] = new Thread() {
                    public void run() {
                        JsonFactory tweet;
                        try {
                            while ((tweet = dumpReader.take()) != JsonStreamReader.POISON_JSON_MAP) {
                                try {
                                    JSONObject json = tweet.getJSON();
                                    if (json.remove("user") == null) continue;
                                    TwitterTweet t = new TwitterTweet(json);
                                    String[] mentions = t.getMentions();
                                    if (mentions.length != 1) continue;
                                    boolean pure = t.getImages().size() == 0 && t.getLinks().length == 0 && t.getHashtags().length == 0;
                                    if (!pure) continue;
                                    Map<Long, TwitterTweet> tweets = usertweets.get(t.getScreenName());
                                    if (tweets == null) {
                                        tweets = new TreeMap<>();
                                        usertweets.put(t.getScreenName(), tweets);
                                    }
                                    tweets.put(t.getTimestamp(), t);
                                    tweetcount.incrementAndGet();
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

            // wait for termination of the indexing threads and do logging meanwhile
            boolean running = true;
            while (running) {
                long startTime = System.currentTimeMillis();
                long startCount = tweetcount.get();
                running = false;
                for (int i = 0; i < dumpReader.getConcurrency(); i++) {
                    if (indexerThreads[i].isAlive()) running = true;
                }
                try {Thread.sleep(10000);} catch (InterruptedException e) {}
                long runtime = System.currentTimeMillis() - startTime;
                long count = tweetcount.get() - startCount;
                DAO.log("processed " + tweetcount.get() + " tweets at " + (count * 1000 / runtime) + " tweets per second from " + dump.getName());
            }
            
            // evaluate usertweets object
            for (Map.Entry<String, Map<Long, TwitterTweet>> entry: usertweets.entrySet()) {
                String from = entry.getKey();
                conversation: for (Map.Entry<Long, TwitterTweet> ut: entry.getValue().entrySet()) {
                    // detect a chain for each of these tweets
                    long time = ut.getKey().longValue();
                    TwitterTweet tweet = ut.getValue();
                    TwitterTweet originalTweet = tweet;
                    List<String> conversation = new ArrayList<>();
                    conversation.add(tweet.getText());
                    String to = tweet.getMentions()[0];
                    if (to.equals(from)) continue conversation;
                    // now find an answer to this tweet from 'to' user
                    TwitterTweet response = null;
                    while ((response = getResponse(usertweets, from, to, time, 3600000)) != null) {
                        conversation.add(response.getText());
                        String x = from; from = to; to = x;
                        time = response.getTimestamp() + 1;
                        if (conversation.size() > 10) break;
                    }
                    if (conversation.size() < 2) continue conversation;
                    String headline = "conversation from " + originalTweet.getTimestampDate() + " between " + originalTweet.getScreenName() + " and " + originalTweet.getMentions()[0];
                    System.out.println("# " + headline);
                    for (String s: conversation) System.out.println(s);
                    System.out.println("");
                }
            }
            
            processedFiles.add(dump);
            // catch up the number of processed tweets
            DAO.log("finished scan of dump file " + dump.getAbsolutePath() + ", " + tweetcount.get() + " new tweets");

            this.isBusy = false;

        } catch (Throwable e) {
            DAO.severe("ProcessConversation THREAD", e);
            try {Thread.sleep(10000);} catch (InterruptedException e1) {}
        }

        DAO.log("ProcessConversation terminated");
    }

    private static TwitterTweet getResponse(Map<String, Map<Long, TwitterTweet>> usertweets, String from, String to, long time, long maxwaiting) {
        Map<Long, TwitterTweet> t = usertweets.get(to);
        if (t == null) return null;
        if (!(t instanceof TreeMap)) {
            TreeMap<Long, TwitterTweet> tm = new TreeMap<>();
            tm.putAll(t);
            usertweets.put(to, tm);
            t = tm;
        }
        SortedMap<Long, TwitterTweet> st = (TreeMap<Long, TwitterTweet>) t;
        st = st.tailMap(time);
        st = st.headMap(time + maxwaiting);
        for (TwitterTweet r: st.values()) {
            if (from.equals(r.getMentions()[0])) return r;
        }
        return null;
    }
    
}

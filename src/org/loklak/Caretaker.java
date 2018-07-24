/**
 *  Caretaker
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.elasticsearch.search.sort.SortOrder;
import org.loklak.api.p2p.HelloService;
import org.loklak.api.p2p.PushServlet;
import org.loklak.api.search.SuggestServlet;
import org.loklak.data.DAO;
import org.loklak.data.DAO.IndexName;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.harvester.TwitterAPI;
import org.loklak.objects.QueryEntry;
import org.loklak.objects.TwitterTimeline;
import org.loklak.objects.BasicTimeline.Order;
import org.loklak.tools.DateParser;
import org.loklak.tools.OS;

import twitter4j.TwitterException;

/**
 * The caretaker class is a concurrent thread which does peer-to-peer operations
 * and data transmission asynchronously.
 */
public class Caretaker extends Thread {

    private static final Random random = new Random(System.currentTimeMillis());
    
    private boolean shallRun = true;
    
    public  final static long startupTime = System.currentTimeMillis();
    private final static long upgradeWait = DateParser.DAY_MILLIS; // 1 day
    public        static long upgradeTime = startupTime + upgradeWait;
    private final static long helloPeriod = 600000; // one ping each 10 minutes
    private       static long helloTime   = 0; // latest hello ping time
    private       static long deletionTime = 0;

    private static final int TIMELINE_PUSH_MINSIZE = 200;
    private static final int TIMELINE_PUSH_MAXSIZE = 1000;
    
    /**
     * ask the thread to shut down
     */
    public void shutdown() {
        this.shallRun = false;
        this.interrupt();
        DAO.log("catched caretaker termination signal");
    }
    
    @Override
    public void run() {
        Thread.currentThread().setName("CARETAKER");
        // send a message to other peers that I am alive
        String[] backends = DAO.getBackend();
        int maxRetries = Integer.valueOf(DAO.getConfig("caretaker.backendpush.retries", "5"));
        int backOffParameter = Integer.valueOf(DAO.getConfig("caretaker.backendpush.backoff", "3000"));

        boolean busy = false;
        // work loop
        beat: while (this.shallRun) try {
            // check upgrade time
            if (System.currentTimeMillis() > upgradeTime) {
                // increase the upgrade time to prevent that the peer runs amok (re-tries the attempt all the time) when upgrade fails for any reason
                upgradeTime = upgradeTime + upgradeWait;
                
                // do an upgrade
                DAO.log("UPGRADE: starting an upgrade");
                upgrade();
                DAO.log("UPGRADE: started an upgrade");
            }
            
            // check ping
            if (System.currentTimeMillis() - helloPeriod > helloTime) {
                helloTime = System.currentTimeMillis();
                HelloService.propagate(backends);
            }
            
            // clear caches
            if (SuggestServlet.cache.size() > 100) SuggestServlet.cache.clear();
            
            // sleep a bit to prevent that the DoS limit fires at backend server
            try {Thread.sleep(busy ? 500 : 5000);} catch (InterruptedException e) {}
            if (!this.shallRun) break beat;
            busy = false;
            
            //DAO.log("connection pool: " + ClientConnection.cm.getTotalStats().toString());
            
            // peer-to-peer operation
            TwitterTimeline tl = DAO.outgoingMessages.takeTimelineMin(Order.CREATED_AT, TIMELINE_PUSH_MINSIZE, TIMELINE_PUSH_MAXSIZE);
            if (tl != null && tl.size() > 0 && backends.length > 0) {
                // transmit the timeline
                long start = System.currentTimeMillis();
                boolean success = PushServlet.push(backends, tl);
                if (success) {
                    DAO.log("success pushing " + tl.size() + " messages to backend " + Arrays.toString(backends) + " in 1st attempt in " + (System.currentTimeMillis() - start) + " ms");
                }
                if (!success) {
                    // we should try again.. but not an infinite number because then
                    // our timeline in RAM would fill up our RAM creating a memory leak
                    retrylook: for (int retry = 0; retry < maxRetries; retry++) {
                        // give back-end time to recover
                        try {Thread.sleep((retry + 1) * backOffParameter);} catch (InterruptedException e) {}
                        DAO.log("trying to push (again) " + tl.size() + " messages to backend " + Arrays.toString(backends) + ", attempt #" + (retry + 1) + "/5");
                        start = System.currentTimeMillis();
                        if (PushServlet.push(backends, tl)) {
                            DAO.log("success pushing " + tl.size() + " messages to backend " + Arrays.toString(backends) + " in " + (retry + 2) + ". attempt in " + (System.currentTimeMillis() - start) + " ms");
                            success = true;
                            break retrylook;
                        }
                    }
                    if (!success) DAO.log("failed pushing " + tl.size() + " messages to backend " + Arrays.toString(backends));
                }
                busy = true;
            }
            
            // scan dump input directory to import files
            try {
                DAO.importAccountDumps(Integer.MAX_VALUE);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            
            // run some harvesting steps
            if (DAO.getConfig("retrieval.forbackend.enabled", false) &&
                DAO.getConfig("backend.push.enabled", false) &&
                (DAO.getBackend().length > 0) &&
                DAO.outgoingMessages.timelineSize() < TIMELINE_PUSH_MAXSIZE) {
                int retrieval_forbackend_concurrency = (int) DAO.getConfig("retrieval.forbackend.concurrency", 1);
                int retrieval_forbackend_loops = (int) DAO.getConfig("retrieval.forbackend.loops", 10);
                int retrieval_forbackend_sleep_base = (int) DAO.getConfig("retrieval.forbackend.sleep.base", 300);
                int retrieval_forbackend_sleep_randomoffset = (int) DAO.getConfig("retrieval.forbackend.sleep.randomoffset", 100);
                hloop: for (int i = 0; i < retrieval_forbackend_loops; i++) {
                    Thread[] rts = new Thread[retrieval_forbackend_concurrency];
                    final AtomicInteger acccount = new AtomicInteger(0);
                    for (int j = 0; j < retrieval_forbackend_concurrency; j++) {
                        rts[j] = new Thread() {
                            public void run() {
                                int count = LoklakServer.harvester.harvest();
                                acccount.addAndGet(count);
                            }
                        };
                        rts[j].start();
                        try {Thread.sleep(retrieval_forbackend_sleep_base + random.nextInt(retrieval_forbackend_sleep_randomoffset));} catch (InterruptedException e) {}
                    }
                    for (Thread t: rts) t.join();
                    if (acccount.get() < 0) break hloop;
                    try {Thread.sleep(retrieval_forbackend_sleep_base + random.nextInt(retrieval_forbackend_sleep_randomoffset));} catch (InterruptedException e) {}
                }
                busy = true;
            }
            
            // run some crawl steps
            if (Crawler.pending() > 0) {
                crawler: for (int i = 0; i < 10; i++) {
                    List<Thread> t = new ArrayList<>();
                    final AtomicBoolean finished = new AtomicBoolean(false);
                    for (int j = 0; j < 10; j++) {
                        Thread u = new Thread() {
                            public void run() {
                                if (Crawler.process() == 0) finished.set(true);
                            }
                        };
                        u.start();
                        t.add(u);
                        try {Thread.sleep(random.nextInt(20));} catch (InterruptedException e) {}
                    }
                    t.forEach(u -> {
                        try {u.join(1000);} catch (InterruptedException e) {}
                    });
                    if (finished.get()) break crawler; else busy = true;
                }
            }
            
            // run searches
            if (DAO.getConfig("retrieval.queries.enabled", false) && IncomingMessageBuffer.addSchedulerAvailable()) {
                // execute some queries again: look out in the suggest database for queries with outdated due-time in field retrieval_next
                List<QueryEntry> queryList = DAO.SearchLocalQueries("", 10, "retrieval_next", "date", SortOrder.ASC, null, new Date(), "retrieval_next");
                queriesloop: for (QueryEntry qe: queryList) {
                    if (!acceptQuery4Retrieval(qe.getQuery())) {
                        DAO.deleteQuery(qe.getQuery(), qe.getSourceType());
                        continue queriesloop;
                    }
                    TwitterTimeline t;
                    try {
                        t = DAO.scrapeTwitter(
                                null,
                                qe.getQuery(),
                                Order.CREATED_AT,
                                qe.getTimezoneOffset(),
                                false,
                                10000,
                                true
                        );
                        DAO.announceNewUserId(t);
                    } catch (NullPointerException e) {
                        DAO.severe("TwitterScraper.search() returns null (no twitter results)"
                                + " or any other issue in DAO.scrapeTwitter() method", e);
                        t = new TwitterTimeline(Order.CREATED_AT);
                    }
                    DAO.log("retrieval of " + t.size() + " new messages for q = \"" + qe.getQuery() + "\"");

                    try {
                        Thread.sleep(random.nextInt(200));
                    } catch (InterruptedException e) {}
                    busy = true;
                }
            }
            
            // retrieve user data
            Set<Number> ids = DAO.getNewUserIdsChunk();
            if (ids != null && DAO.getConfig("retrieval.user.enabled", false) && TwitterAPI.getAppTwitterFactory() != null) {
                try {
                    TwitterAPI.getScreenName(ids, 10000, false);
                } catch (IOException | TwitterException e) {
                    for (Number n: ids) DAO.announceNewUserId(n); // push back unread values
                    if (e instanceof TwitterException) try {Thread.sleep(10000);} catch (InterruptedException ee) {}
                }
            }
            
            if (!busy) {
                // start a crawler
                String startTerm = DAO.getRandomTerm();
                if (startTerm != null && startTerm.length() > 0) {
                    Crawler.stack(startTerm, 3, true, true, true);
                    DAO.log("started a crawler for term " + startTerm);
                    busy = true;
                }
            }
            
            // heal the latency to give peers with out-dated information a new chance
            DAO.healLatency(0.95f);
            
            // delete messages out of time frames
            // this is IO heavy, so prevent to do this a lot
            if (System.currentTimeMillis() - deletionTime > 60000) {
                int d;
                d = DAO.deleteOld(IndexName.messages_hour, DateParser.oneHourAgo());
                if (d > 0) DAO.log("Deleted " + d + " outdated(hour) messages");
                d = DAO.deleteOld(IndexName.messages_day, DateParser.oneDayAgo());
                if (d > 0) DAO.log("Deleted " + d + " outdated(day) messages");
                d = DAO.deleteOld(IndexName.messages_week, DateParser.oneWeekAgo());
                if (d > 0) DAO.log("Deleted " + d + " outdated(week) messages");
                if (DAO.getConfig("autodeletion", false)) {
                    d = DAO.deleteOld(IndexName.messages, DateParser.oneMonthAgo());
                    if (d > 0) DAO.log("Deleted " + d + " outdated(month) messages");
                }
                deletionTime = System.currentTimeMillis();
            }
        } catch (Throwable e) {
            DAO.severe("CARETAKER THREAD", e);
        }

        DAO.log("caretaker terminated");
    }

    public static boolean acceptQuery4Retrieval(String q) {
        return q.length() > 1 && q.length() <=16 && (q.indexOf(':') < 0 || q.startsWith("from:"));
    }
    
    /**
     * loklak upgrades itself if this is called
     */
    public static void upgrade() {
        final File upgradeScript = new File(DAO.bin_dir.getAbsolutePath().replaceAll(" ", "\\ "), "upgrade.sh");
      
        try {
            List<String> rsp = OS.execSynchronous(upgradeScript.getAbsolutePath());
            for (String s: rsp) DAO.log("UPGRADE: " + s);
        } catch (IOException e) {
        	DAO.severe("UPGRADE failed: " + e.getMessage(), e);
        }
    }
    
}

/**
 *  IncomingMessageBuffer
 *  Copyright 04.01.2016 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.objects.Timeline;
import org.loklak.objects.Timeline2;
import org.loklak.objects.UserEntry;

public class IncomingMessageBuffer extends Thread {

    private final static int MESSAGE_QUEUE_MAXSIZE = 10000;
    private final static int bufferLimit = MESSAGE_QUEUE_MAXSIZE * 3 / 4;
    private static BlockingQueue<DAO.MessageWrapper> messageQueue = new ArrayBlockingQueue<DAO.MessageWrapper>(MESSAGE_QUEUE_MAXSIZE);
    private static BlockingQueue<Timeline2> postQueue = new ArrayBlockingQueue<Timeline2>(MESSAGE_QUEUE_MAXSIZE);
    private static AtomicInteger queueClients = new AtomicInteger(0);

    private boolean shallRun = true, isBusy = false;

    public static int getMessageQueueSize() {
        return messageQueue.size();
    }

    public static int getMessageQueueMaxSize() {
        return MESSAGE_QUEUE_MAXSIZE;
    }

    public static int getMessageQueueClients() {
        return queueClients.get();
    }

    public IncomingMessageBuffer() {
    }

    public TwitterTweet readMessage(String id) {
        if (id == null || id.length() == 0) return null;
        for (DAO.MessageWrapper mw: messageQueue) {
            if (id.equals(mw.t.getPostId())) return mw.t;
        }
        return null;
    }

    /**
     * ask the thread to shut down
     */
    public void shutdown() {
        this.shallRun = false;
        this.interrupt();
        DAO.log("catched QueuedIndexing termination signal");
    }

    public boolean isBusy() {
        return this.isBusy;
    }

    @Override
    public void run() {
        // work loop
        while (!DAO.wait_ready(1000)) {
            try {Thread.sleep(10000);} catch (InterruptedException e) {}
        }
        loop: while (this.shallRun) try {
            this.isBusy = false;

            if (messageQueue.isEmpty() && postQueue.isEmpty()) {
                // in case that the queue is empty, try to fill it with previously pushed content
                //List<Map<String, Object>> shard = this.jsonBufferHandler.getBufferShard();
                // if the shard has content, turn this into messages again

                // if such content does not exist, simply sleep a while
                try {Thread.sleep(10000);} catch (InterruptedException e) {}
                continue loop;
            }
            this.isBusy = true;
            if (messageQueue.size() > 0) indexTweets();
            else if (postQueue.size() > 0) indexPosts();
            this.isBusy = false;

        } catch (Throwable e) {
            DAO.severe("QueuedIndexing THREAD", e);
        }

        DAO.log("QueuedIndexing terminated");
    }

    private void indexPosts() {
        int maxBulkSize = 1;
        Timeline2 postListObj = null;
        Set<Timeline2> bulk = new HashSet<Timeline2>();

        while((postListObj = postQueue.poll()) != null) {
            bulk.add(postListObj);
            if (bulk.size() >= maxBulkSize) {
                dumpbulk(bulk);
                bulk.clear();
            }
        }
    }

    private void indexTweets() {
        DAO.MessageWrapper mw;
        AtomicInteger candMessages = new AtomicInteger();
        AtomicInteger knownMessagesCache = new AtomicInteger();
        int maxBulkSize = 200;
        List<DAO.MessageWrapper> bulk = new ArrayList<>();
        pollloop: while ((mw = messageQueue.poll()) != null) {
            if (DAO.messages.existsCache(mw.t.getPostId())) {
                 knownMessagesCache.incrementAndGet();
                 continue pollloop;
            }
            candMessages.incrementAndGet();

            // in case that the message queue is too large, dump the queue into a file here
            // to make room that clients can continue to push without blocking
            if (messageQueue.size() > bufferLimit) {
                try {
                    DAO.message_dump.write(mw.t.toJSON(mw.u, false, Integer.MAX_VALUE, ""), false);
                    DAO.log("writing message directly to dump, messageQueue.size() = " + messageQueue.size() + ", bufferLimit = " + bufferLimit);
                } catch (IOException e) {
                    DAO.severe("writing of dump failed", e);
                    e.printStackTrace();
                }
                continue pollloop;
            }
            
            // if there is time enough to finish this, continue to write into the index

            mw.t.enrich(); // we enrich here again because the remote peer may have done this with an outdated version or not at all
            bulk.add(mw);
            if (bulk.size() >= maxBulkSize) {
                dumpbulk(bulk, candMessages, knownMessagesCache);
                bulk.clear();
            }
        }
        if (bulk.size() >= 0) {
            dumpbulk(bulk, candMessages, knownMessagesCache);
            bulk.clear();
        }
    }

    private void dumpbulk(List<DAO.MessageWrapper> bulk, AtomicInteger candMessages, AtomicInteger knownMessagesCache) {
        long dumpstart = System.currentTimeMillis();
        int notWrittenDouble = DAO.writeMessageBulk(bulk).size();
        knownMessagesCache.addAndGet(notWrittenDouble);
        candMessages.addAndGet(-notWrittenDouble);
        long dumpfinish = System.currentTimeMillis();
        DAO.log("dumped timelines: " + candMessages + " new " + knownMessagesCache + " known from cache, storage time: " + (dumpfinish - dumpstart) + " ms, remaining messages: " + messageQueue.size());
        candMessages.set(0);
        knownMessagesCache.set(0);
    }

    private void dumpbulk(Set<Timeline2> bulk) {
        //TODO: use this
        int notWrittenDouble = DAO.writeMessageBulk(bulk).size();
        DAO.log("dumped timelines: "  + postQueue.size());
    }

    public static void addScheduler(Timeline tl, final boolean dump) {
        queueClients.incrementAndGet();
        for (TwitterTweet me: tl) addScheduler(me, tl.getUser(me), dump);
        queueClients.decrementAndGet();
    }

    public static void addScheduler(final TwitterTweet t, final UserEntry u, final boolean dump) {
        try {
            messageQueue.put(new DAO.MessageWrapper(t, u, dump));
        } catch (InterruptedException e) {
        	DAO.severe(e);
        }
    }

    public static void addScheduler(Timeline2 postList) {
        queueClients.incrementAndGet();
       try {
            postQueue.put(postList);
        } catch (InterruptedException e) {
        	DAO.severe(e);
        }
        queueClients.decrementAndGet();
    }

    public static boolean addSchedulerAvailable() {
        return messageQueue.remainingCapacity() > 0;
    }

}

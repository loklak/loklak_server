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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.objects.TwitterTimeline;
import org.loklak.objects.PostTimeline;
import org.loklak.objects.UserEntry;

public class IncomingMessageBuffer extends Thread {

    private final static int MESSAGE_QUEUE_MAXSIZE = 20000;
    private final static int bufferLimit = MESSAGE_QUEUE_MAXSIZE * 3 / 4;
    private static LinkedBlockingDeque<DAO.MessageWrapper> messageQueue = new LinkedBlockingDeque<DAO.MessageWrapper>(MESSAGE_QUEUE_MAXSIZE);
    private static BlockingQueue<PostTimeline> postQueue = new ArrayBlockingQueue<PostTimeline>(MESSAGE_QUEUE_MAXSIZE);
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
                try {Thread.sleep(2000);} catch (InterruptedException e) {}
                continue loop;
            }
            this.isBusy = true;
            if (messageQueue.size() > 0) indexTweets();
            if (postQueue.size() > 0) indexPosts();
            this.isBusy = false;

        } catch (Throwable e) {
            DAO.severe("QueuedIndexing THREAD", e);
        }

        DAO.log("QueuedIndexing terminated");
    }

    private void indexPosts() {
        int maxBulkSize = 1;
        PostTimeline postListObj = null;
        Set<PostTimeline> bulk = new HashSet<PostTimeline>();

        pollloop: while((postListObj = postQueue.poll()) != null) {
            bulk.add(postListObj);
            if (bulk.size() >= maxBulkSize) break pollloop;
        }
        if (bulk.size() >= 0) {
            dumpPostBulk(bulk);
            bulk.clear();
        }
    }

    private void indexTweets() {
        DAO.MessageWrapper mw;
        AtomicInteger newMessageCounter = new AtomicInteger();
        AtomicInteger doubleMessageCounter = new AtomicInteger();
        int maxBulkSize = 200;
        List<DAO.MessageWrapper> bulk = new ArrayList<>();
        pollloop: while ((mw = messageQueue.poll()) != null) {
            if (DAO.messages.existsCache(mw.t.getPostId())) {
                 doubleMessageCounter.incrementAndGet();
                 continue pollloop;
            }
            newMessageCounter.incrementAndGet();

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
            if (bulk.size() >= maxBulkSize) break pollloop;
        }
        if (bulk.size() >= 0) {
            dumpMessageBulk(bulk, newMessageCounter, doubleMessageCounter);
            bulk.clear();
        }
    }

    private void dumpMessageBulk(List<DAO.MessageWrapper> bulk, AtomicInteger newMessageCounter, AtomicInteger doubleMessageCounter) {
        long dumpstart = System.currentTimeMillis();
        int newWritten = DAO.writeMessageBulk(bulk).size();
        doubleMessageCounter.addAndGet(bulk.size() - newWritten);
        newMessageCounter.addAndGet(newWritten);
        long dumpfinish = System.currentTimeMillis();
        DAO.log("dumped timelines: " + newMessageCounter + " new, " + doubleMessageCounter + " known from cache, storage time: " + (dumpfinish - dumpstart) + " ms, remaining messages: " + messageQueue.size());
        newMessageCounter.set(0);
        doubleMessageCounter.set(0);
    }

    private void dumpPostBulk(Set<PostTimeline> bulk) {
        //TODO: use this
        int notWrittenDouble = DAO.writeMessageBulk(bulk).size();
        DAO.log("dumped timelines: "  + postQueue.size());
    }

    public static void addScheduler(TwitterTimeline tl, final boolean dump, boolean priority) {
        queueClients.incrementAndGet();
        for (TwitterTweet me: tl) addScheduler(me, tl.getUser(me), dump, priority);
        queueClients.decrementAndGet();
    }

    public static void addScheduler(final TwitterTweet t, final UserEntry u, final boolean dump, boolean priority) {
        try {
            if (priority) {
                try {
                    messageQueue.addFirst(new DAO.MessageWrapper(t, u, dump));
                } catch (IllegalStateException ee) {
                    // case where the queue is full
                    messageQueue.put(new DAO.MessageWrapper(t, u, dump));
                }
            } else {
                messageQueue.put(new DAO.MessageWrapper(t, u, dump));
            }
        } catch (InterruptedException e) {
        	DAO.severe(e);
        }
    }

    public static void addScheduler(PostTimeline postList) {
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

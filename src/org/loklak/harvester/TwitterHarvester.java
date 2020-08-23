/**
 *  ClassicHarvester
 *  Copyright 13.11.2015 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; wo even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.harvester;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.loklak.api.p2p.PushServlet;
import org.loklak.api.search.SuggestServlet;
import org.loklak.data.DAO;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.objects.QueryEntry;
import org.loklak.objects.ResultList;
import org.loklak.objects.TwitterTimeline;
import org.loklak.objects.BasicTimeline.Order;
import org.loklak.tools.DateParser;

public class TwitterHarvester {

    private final static int FETCH_MIN = 20;
    //private final static int HITS_LIMIT_4_QUERIES = 20;
    private final static int MAX_PENDING_CONTEXT = 300; // this could be much larger but we don't want to cache too many of these
    private final static int MAX_HARVESTED_CONTEXT = 10000; // just to prevent a memory leak with possible OOM after a long time we flush that cache after a while
    //private final static Random random = new Random(System.currentTimeMillis());

    private LinkedHashSet<String> pendingQueries = new LinkedHashSet<>();
    private ConcurrentLinkedDeque<String> pendingContext = new ConcurrentLinkedDeque<>();
    private Set<String> harvestedContext = ConcurrentHashMap.newKeySet();

    private final AtomicInteger latest_query_hit_count_on_backend = new AtomicInteger(100);

    private void checkContext(TwitterTimeline tl, boolean front) {
        for (TwitterTweet tweet: tl) {
            for (String user: tweet.getMentions()) checkContext("from:" + user, front);
            for (String hashtag: tweet.getHashtags()) checkContext(hashtag, front);
        }
    }

    private void checkContext(String s, boolean front) {
        if (!front && pendingContext.size() > MAX_PENDING_CONTEXT) return; // queue is full
        if (!harvestedContext.contains(s) && !pendingContext.contains(s)) {
            if (front) pendingContext.addFirst(s); else pendingContext.addLast(s);
        }
        while (pendingContext.size() > MAX_PENDING_CONTEXT) pendingContext.removeLast();
        if (harvestedContext.size() > MAX_HARVESTED_CONTEXT) harvestedContext.clear();
    }

    public int get_harvest_queries() throws IOException {
        String[] backend = DAO.getBackend();

        /*
        if (random.nextInt(100) != 0 && latest_query_hit_count_on_backend.get() < HITS_LIMIT_4_QUERIES && pendingQueries.size() == 0 && pendingContext.size() > 0) {
            // harvest using the collected keys instead using the queries
            String q = pendingContext.removeFirst();
            harvestedContext.add(q);
            TwitterTimeline tl = TwitterScraper.search(q, Order.CREATED_AT, true, true, 400);
            if (tl == null || tl.size() == 0) return -1;

            // find content query strings and store them in the context cache
            checkContext(tl, false);
            DAO.log("retrieval of " + tl.size() + " new messages for q = " + q + ", scheduled push; pendingQueries = " + pendingQueries.size() + ", pendingContext = " + pendingContext.size() + ", harvestedContext = " + harvestedContext.size());
            return tl.size();
        }
         */

        // Load more queries if pendingQueries is empty
        if (pendingQueries.size() == 0) {
            int fetch_random = Math.min(100, Math.max(FETCH_MIN, latest_query_hit_count_on_backend.get() / 10));

            DAO.log("START loading " + fetch_random + " suggestions for harvesting");
            ResultList<QueryEntry> rl = SuggestServlet.suggest(backend, "", "query", fetch_random * 5, "asc", "retrieval_next", DateParser.getTimezoneOffset(), null, "now", "retrieval_next", fetch_random);
            for (QueryEntry qe: rl) {
                pendingQueries.add(qe.getQuery());
            }
            latest_query_hit_count_on_backend.set((int) rl.getHits());
            DAO.log("STOP loading suggestions, got " + rl.size() + " for harvesting from " + latest_query_hit_count_on_backend + " in backend");
        }

        return pendingQueries.size();
    }

    public TwitterTimeline harvest_timeline() {

        if (pendingQueries.size() == 0) return null;

        // take one of the pending queries or pending context and load the tweets
        String q = "";
        try {
            q = pendingQueries.iterator().next();
        } catch (NoSuchElementException e) {
            return null;
        }
        if (q == null || q.length() == 0) return null;
        pendingQueries.remove(q);
        pendingContext.remove(q);
        harvestedContext.add(q);
        TwitterTimeline tl = TwitterScraper.search(q, Order.CREATED_AT, true, false, 400);

        if (tl != null && tl.size() > 0) {
            // find content query strings and store them in the context cache
            tl.setQuery(q);
            checkContext(tl, true);
        }

        return tl;
    }

    public void push_timeline_to_backend(TwitterTimeline tl) {
        String[] backend = DAO.getBackend();
        DAO.log( "starting push to backend; pendingQueries = " + pendingQueries.size() + ", pendingContext = " +
                pendingContext.size() + ", harvestedContext = " + harvestedContext.size());

        boolean success = false;
        for (int i = 0; i < 5; i++) {
            try {
                long start = System.currentTimeMillis();
                success = PushServlet.push(backend, tl);
                if (success) {
                    DAO.log("retrieval of " + tl.size() + " new messages for q = " + tl.getQuery() +
                            ", pushed to backend synchronously in " + (System.currentTimeMillis() - start) + " ms; amount = " + tl.size());
                    return;
                }
            } catch (Throwable e) {
                DAO.log("failed synchronous push to backend, attempt " + i);
                try {Thread.sleep((i + 1) * 3000);} catch (InterruptedException e1) {}
            }
        }
        String q = tl.getQuery();
        tl.setQuery(null);
        DAO.outgoingMessages.transmitTimelineToBackend(tl);
        DAO.log("retrieval of " + tl.size() + " new messages for q = " + q + ", scheduled push");
    }

}


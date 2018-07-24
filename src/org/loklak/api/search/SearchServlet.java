/**
 *  SearchServlet
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

package org.loklak.api.search;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;
import org.loklak.http.RemoteAccess;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.objects.BasicTimeline.Order;
import org.loklak.objects.QueryEntry;
import org.loklak.objects.TwitterTimeline;
import org.loklak.objects.UserEntry;
import org.loklak.rss.RSSFeed;
import org.loklak.rss.RSSMessage;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.ClientIdentity;
import org.loklak.server.Query;
import org.loklak.tools.CharacterCoding;
import org.loklak.tools.UTF8;

/**
 * The search servlet. we provide opensearch/rss and twitter-like JSON as result.
 */
public class SearchServlet extends HttpServlet {

    private static final long serialVersionUID = 563533152152063908L;

    private final static String SEARCH_LOW_COUNT_NAME = "search.count.low";
    private final static String SEARCH_DEFAULT_COUNT_NAME = "search.count.default";
    private final static String SEARCH_MAX_PUBLIC_COUNT_NAME = "search.count.max.public";
    private final static String SEARCH_MAX_LOCALHOST_COUNT_NAME = "search.count.max.localhost";

    private final static int SEARCH_CACHE_THREASHOLD_TIME = 3000;

    private final static AtomicLong last_cache_search_time = new AtomicLong(10L);

    public final static String backend_hash = Integer.toHexString(Integer.MAX_VALUE);
    public final static String frontpeer_hash = Integer.toHexString(Integer.MAX_VALUE - 1);

    // possible values: cache, twitter, all
    public static TwitterTimeline search(final String[] protocolhostportstubs, final String query, final ArrayList<String> filterList, final TwitterTimeline.Order order, final String source, final int count, final int timezoneOffset, final String provider_hash, final long timeout) throws IOException {
        TwitterTimeline tl = new TwitterTimeline(order);
        if ("".equals(query)) return tl;
        String urlstring = "";
        String filterString = "";

        try {
            byte[] jsonb = null;
            IOException ee = null;
            backendloop: for (String protocolhostportstub: protocolhostportstubs) {
                ee = null;
                try {
                    urlstring = protocolhostportstub + "/api/search.json?q=" + URLEncoder.encode(query.replace(' ', '+'), "UTF-8") + "&timezoneOffset=" + timezoneOffset + "&maximumRecords=" + count + "&source=" + (source == null ? "all" : source) + "&minified=true&shortlink=false&timeout=" + timeout;

                    if (!"".equals(filterString = String.join(", ", filterList))) {
                        urlstring = urlstring + "&filter=" + filterString;
                    }

                    jsonb = ClientConnection.download(urlstring);
                    break backendloop;
                } catch (IOException e) {
                    ee = e;
                }
            }
            if (jsonb == null || jsonb.length == 0) throw ee == null ? new IOException("empty content from " + protocolhostportstubs) : ee;
            String jsons = UTF8.String(jsonb);
            JSONObject json = new JSONObject(jsons);
            if (json == null || json.length() == 0) return tl;
            JSONArray statuses = json.getJSONArray("statuses");
            if (statuses != null) {
                for (int i = 0; i < statuses.length(); i++) {
                    JSONObject tweet = statuses.getJSONObject(i);
                    JSONObject user = tweet.getJSONObject("user");
                    if (user == null) continue;
                    tweet.remove("user");
                    UserEntry u = new UserEntry(user);
                    TwitterTweet t = new TwitterTweet(tweet);
                    tl.add(t, u);
                }
            }
            JSONObject metadata = json.getJSONObject("search_metadata");
            if (metadata != null) {
                Integer hits = metadata.has("hits") ? (Integer) metadata.get("hits") : null;
                if (hits != null) tl.setHits(hits.intValue());
                String scraperInfo = metadata.has("scraperInfo") ? (String) metadata.get("scraperInfo") : null;
                if (scraperInfo != null) tl.setScraperInfo(scraperInfo);
            }
        } catch (Throwable e) {
            //DAO.severe(e);
            throw new IOException(e.getMessage());
        }
        //System.out.println(parser.text());
        return tl;
    }

    public static TwitterTimeline search(
            final String[] protocolhostportstubs, final String query, final TwitterTimeline.Order order,
            final String source, final int count, final int timezoneOffset,
            final String provider_hash, final long timeout) throws IOException {
        return search(protocolhostportstubs, query, new ArrayList<>(), order, source, count, timezoneOffset, provider_hash, timeout);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final long start = System.currentTimeMillis();
        final Query post = RemoteAccess.evaluate(request);
        Map<String, String> inputMap = post.getMap();
        JSONObject metadata = new JSONObject(true);

        final ClientIdentity identity = AbstractAPIHandler.getIdentity(request, response, post);
        JSONArray dataArray = null;
        try {

            // manage DoS
            if (post.isDoS_blackout()) {response.sendError(503, "your (" + post.getClientHost() + ") request frequency is too high"); return;}

            // check call type
            String callType = "json";
            if(request.getServletPath().endsWith(".json")){
                callType = "json";
            } else if(request.getServletPath().endsWith(".rss")) {
                callType = "rss";
            } else if(request.getServletPath().endsWith(".txt")) {
                callType = "txt";
            }

            // evaluate get parameter
            String scraper = post.get("scraper", "");
            String callback = post.get("callback", "");
            boolean jsonp = callback != null && callback.length() > 0;
            boolean minified = post.get("minified", false);
            boolean shortlink_request = post.get("shortlink", true);

            // query parameters
            // naming schema according to SRU 2.0
            // see http://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part3-sru2.0/searchRetrieve-v1.0-os-part3-sru2.0.html
            // for compatibility "query" can be replaced with "q"
            String query = post.get("q", post.get("query", ""));
            query = CharacterCoding.html2unicode(query).replaceAll("\\+", " ");
            // paging options, syntax and meaning as defined by SRU 2.0
            final int startRecord = Math.max(1, post.get("startRecord", 1)); // the first record is number 1, not 0, see http://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part3-sru2.0/searchRetrieve-v1.0-os-part3-sru2.0.html#startAndMax
            final int maximumRecords = Math.min(
                    post.get("count", post.get("maximumRecords", (int) DAO.getConfig(SEARCH_DEFAULT_COUNT_NAME, 10))),
                    (int) (post.isDoS_servicereduction() ?
                            DAO.getConfig(SEARCH_LOW_COUNT_NAME, 10) :
                            post.isLocalhostAccess() ?
                                    DAO.getConfig(SEARCH_MAX_LOCALHOST_COUNT_NAME, 1000) :
                                    DAO.getConfig(SEARCH_MAX_PUBLIC_COUNT_NAME, 100)));

            String filter = post.get("filter", "").replaceAll("\\s","");
            ArrayList<String> filterList = new ArrayList<String>(Arrays.asList(filter.split(",")));

            // create tweet timeline
            final String ordername = post.get("order", Order.CREATED_AT.getMessageFieldName());
            final TwitterTimeline.Order order = TwitterTimeline.parseOrder(ordername);
            TwitterTimeline tl = DAO.timelineCache.getOrCreate(identity, query, startRecord <= 1, order);
            JSONObject hits = new JSONObject(true);
            final JSONObject[] aggregations = new JSONObject[]{null};

            if (!"".equals(scraper)) {
                // start a scraper
                inputMap.put("query", query);
                DAO.log(request.getServletPath() + " scraping with query: "
                        + query + " scraper: " + scraper);
                dataArray = DAO.scrapeLoklak(inputMap, true, true, metadata);
            } else if (tl.size() > 0) {
                // return the timeline from a cached search result
                // in case that the number of available records in the cache is too low, try to get more
                // otherwise there might be nothing to do here
            } else {
                final long timeout = (long) post.get("timeout", DAO.getConfig("search.timeout", 2000));
                String source = post.isDoS_servicereduction() ? "cache" : post.get("source", "all"); // possible values: cache, backend, twitter, all
                int agregation_limit = post.get("limit", 10);
                String[] fields = post.get("fields", new String[0], ",");
                int timezoneOffset = post.get("timezoneOffset", 0);
                if (query.indexOf("id:") >= 0 && ("all".equals(source) || "twitter".equals(source)))
                    source = "cache"; // id's cannot be retrieved from twitter with the scrape-api (yet), only from the cache

                final AtomicInteger cache_hits = new AtomicInteger(0);
                final AtomicInteger count_backend = new AtomicInteger(0);
                final AtomicInteger count_twitter_all = new AtomicInteger(0);
                final AtomicInteger count_twitter_new = new AtomicInteger(0);
                final boolean backend_push = DAO.getConfig("backend.push.enabled", false);
                final QueryEntry.Tokens tokens = new QueryEntry.Tokens(query);

                if ("all".equals(source)) {
                    // start all targets for search concurrently
                    final int timezoneOffsetf = timezoneOffset;
                    final String queryf = query;

                    // start a scraper
                    Thread scraperThread = tokens.raw.length() == 0 ? null : new Thread() {
                        public void run() {
                            final String scraper_query = tokens.translate4scraper();
                            DAO.log(request.getServletPath() + " scraping with query: " + scraper_query);
                            TwitterTimeline twitterTl = DAO.scrapeTwitter(post, filterList, scraper_query, order, timezoneOffsetf, true, timeout, true);
                            count_twitter_new.set(twitterTl.size());
                            tl.putAll(QueryEntry.applyConstraint(twitterTl, tokens, false)); // pre-localized results are not filtered with location constraint any more
                            tl.setScraperInfo(twitterTl.getScraperInfo());
                            post.recordEvent("twitterscraper_time", System.currentTimeMillis() - start);
                        }
                    };
                    if (scraperThread != null) scraperThread.start();

                    // start a local search
                    Thread localThread = queryf == null || queryf.length() == 0 ? null : new Thread() {
                        public void run() {

                            DAO.SearchLocalMessages localSearchResult =
                                    new DAO.SearchLocalMessages(
                                            queryf,
                                            order,
                                            timezoneOffsetf,
                                            last_cache_search_time.get() > SEARCH_CACHE_THREASHOLD_TIME ? Math.min(maximumRecords,
                                            (int) DAO.getConfig(SEARCH_LOW_COUNT_NAME, 10)) : maximumRecords,
                                            agregation_limit,
                                            filterList,
                                            fields
                                    );
                            long time = System.currentTimeMillis() - start;
                            last_cache_search_time.set(time);
                            post.recordEvent("cache_time", time);
                            cache_hits.set(localSearchResult.timeline.getHits());
                            tl.putAll(localSearchResult.timeline);
                            tl.setResultIndex(localSearchResult.timeline.getResultIndex());
                            aggregations[0] = localSearchResult.getAggregations();
                        }
                    };
                    if (localThread != null) localThread.start();

                    // start a backend search, but only if backend_push == true or result from scraper is too bad
                    boolean start_backend_thread = false;
                    if (backend_push) start_backend_thread = true;
                    else {
                        // wait now for termination of scraper thread and local search
                        // to evaluate how many results are available
                        if (scraperThread != null) try {
                            scraperThread.join(Math.max(10000, timeout - System.currentTimeMillis() + start));
                        } catch (InterruptedException e) {
                        }
                        if (localThread != null) try {
                            localThread.join(Math.max(100, timeout - System.currentTimeMillis() + start));
                        } catch (InterruptedException e) {
                        }
                        localThread = null;
                        scraperThread = null;
                        if (tl.size() < maximumRecords) start_backend_thread = true;
                    }
                    Thread backendThread = tokens.original.length() == 0 || !start_backend_thread ? null : new Thread() {
                        public void run() {
                            TwitterTimeline backendTl = DAO.searchBackend(tokens.original, filterList, order, maximumRecords, timezoneOffsetf, "cache", timeout);
                            if (backendTl != null) {
                                tl.putAll(QueryEntry.applyConstraint(backendTl, tokens, true));
                                count_backend.set(tl.size());
                                // TODO: read and aggregate aggregations from backend as well
                            }
                            post.recordEvent("backend_time", System.currentTimeMillis() - start);
                        }
                    };
                    if (backendThread != null) backendThread.start();

                    // wait for termination of all threads
                    if (scraperThread != null) try {
                        scraperThread.join(Math.max(10000, timeout - System.currentTimeMillis() + start));
                    } catch (InterruptedException e) {
                    }

                    // in case that the scraper thread had been started and was successful, we do not wait for the other threads to terminate
                    if (scraperThread == null || tl.getHits() == 0 || query.indexOf(':') >= 0 || query.indexOf('/') >= 0 || fields.length > 0) {
                        if (localThread != null) try {
                            localThread.join(Math.max(100, timeout - System.currentTimeMillis() + start));
                        } catch (InterruptedException e) {
                        }
                    }
                    if (scraperThread == null || tl.getHits() == 0) {
                        if (backendThread != null) try {
                            backendThread.join(Math.max(100, timeout - System.currentTimeMillis() + start));
                        } catch (InterruptedException e) {
                        }
                    }
                } else if ("twitter".equals(source) && tokens.raw.length() > 0) {
                    final String scraper_query = tokens.translate4scraper();
                    DAO.log(request.getServletPath() + " scraping with query: " + scraper_query);
                    TwitterTimeline twitterTl = DAO.scrapeTwitter(post, filterList, scraper_query, order, timezoneOffset, true, timeout, true);

                    count_twitter_new.set(twitterTl.size());
                    tl.putAll(QueryEntry.applyConstraint(twitterTl, tokens, false)); // pre-localized results are not filtered with location constraint any more
                    tl.setScraperInfo(twitterTl.getScraperInfo());
                    post.recordEvent("twitterscraper_time", System.currentTimeMillis() - start);
                    // in this case we use all tweets, not only the latest one because it may happen that there are no new and that is not what the user expects

                } else if ("cache".equals(source)) {
                    DAO.SearchLocalMessages localSearchResult =
                            new DAO.SearchLocalMessages(
                                    query,
                                    order,
                                    timezoneOffset,
                                    last_cache_search_time.get() > SEARCH_CACHE_THREASHOLD_TIME
                                            ? Math.min(maximumRecords,(int) DAO.getConfig(SEARCH_LOW_COUNT_NAME, 10))
                                            : maximumRecords,
                                    agregation_limit,
                                    filterList,
                                    fields
                            );
                    cache_hits.set(localSearchResult.timeline.getHits());
                    tl.putAll(localSearchResult.timeline);
                    tl.setResultIndex(localSearchResult.timeline.getResultIndex());
                    aggregations[0] = localSearchResult.getAggregations();
                    long time = System.currentTimeMillis() - start;
                    last_cache_search_time.set(time);
                    post.recordEvent("cache_time", time);

                } else if ("backend".equals(source) && query.length() > 0) {
                    TwitterTimeline backendTl = DAO.searchBackend(query, filterList, order, maximumRecords, timezoneOffset, "cache", timeout);
                    if (backendTl != null) {
                        tl.putAll(QueryEntry.applyConstraint(backendTl, tokens, true));
                        tl.setScraperInfo(backendTl.getScraperInfo());
                        // TODO: read and aggregate aggregations from backend as well
                        count_backend.set(tl.size());
                    }
                    post.recordEvent("backend_time", System.currentTimeMillis() - start);

                }

                // check the latest user_ids
                DAO.announceNewUserId(tl);

                hits.put("count_twitter_all", count_twitter_all.get());
                hits.put("count_twitter_new", count_twitter_new.get());
                hits.put("count_backend", count_backend.get());
                hits.put("cache_hits", cache_hits.get());
            }

            // create json or xml according to path extension
            int shortlink_iflinkexceedslength = shortlink_request ? (int) DAO.getConfig("shortlink.iflinkexceedslength", 500L) : Integer.MAX_VALUE;
            String shortlink_urlstub = DAO.getConfig("shortlink.urlstub", "http://127.0.0.1:9000");

            dataOutput( response, dataArray, query, startRecord, maximumRecords, minified,
                    callType, jsonp, post, tl, aggregations[0], hits, callback, shortlink_urlstub,
                    shortlink_iflinkexceedslength, filter, metadata);

            post.recordEvent("result_count", tl.size());
            post.recordEvent("postprocessing_time", System.currentTimeMillis() - start);
            post.recordEvent("hits", hits);
            DAO.log(request.getServletPath() + "?" + request.getQueryString()
                    + " -> " + tl.size() + " records returned");
            post.finalize();
        } catch (Throwable e) {
            DAO.severe(e.getMessage(), e);
            //DAO.severe(e);
        }
    }

    private static void dataOutput(HttpServletResponse response, JSONArray dataArray,
            String query, int startRecord, int maximumRecords, boolean minified,
            String callType, boolean jsonp, Query post, TwitterTimeline tl, JSONObject aggregations,
            JSONObject hits, String callback, String shortlink_urlstub,
            int shortlink_iflinkexceedslength, String filter, JSONObject metadata) throws IOException {
        if ("json".equals(callType)) {
            post.setResponse(response, jsonp ? "application/javascript" : "application/json");

            JSONObject m = new JSONObject(true);
            JSONArray statuses = new JSONArray();

            try {
                for (TwitterTweet t : tl.getNextTweets(startRecord - 1, maximumRecords)) {
                    UserEntry u = tl.getUser(t);
                    statuses.put(t.toJSON(u, true, shortlink_iflinkexceedslength, shortlink_urlstub));
                }
            } catch (ConcurrentModificationException e) {
                // late incoming messages from concurrent peer retrieval may cause this
                // we silently do nothing here and return what we listed so far
            }

            if (!minified) setReadMe(m);
            if (dataArray == null) {
                setTwitterMetaData(metadata, startRecord, maximumRecords, tl, query, filter, post, hits);
                m.put("search_metadata", metadata);
                m.put("statuses", statuses);
            } else {
                moreMetaData(metadata, post);
                m.put("results", dataArray);
                m.put("metadata", metadata);
            }
            m.put("aggregations", aggregations);

            // write json
            response.setCharacterEncoding("UTF-8");
            PrintWriter sos = response.getWriter();
            if (jsonp) sos.print(callback + "(");
            sos.print(m.toString(minified ? 0 : 2));
            if (jsonp) sos.println(");");
            sos.println();
        } else if ("rss".equals(callType)) {
            response.setCharacterEncoding("UTF-8");
            post.setResponse(response, "application/rss+xml;charset=utf-8");

            // generate xml
            RSSMessage channel = new RSSMessage();
            channel.setPubDate(new Date());
            channel.setTitle("RSS feed for Twitter search for " + query);
            channel.setDescription("");
            channel.setLink("");
            RSSFeed feed = new RSSFeed(tl.size());
            feed.setChannel(channel);

            try {
                for (TwitterTweet t : tl.getNextTweets(startRecord - 1, maximumRecords)) {
                    UserEntry u = tl.getUser(t);
                    RSSMessage m = new RSSMessage();
                    m.setLink(t.getStatusIdUrl().toExternalForm());
                    m.setAuthor(u.getName() + " @" + u.getScreenName());
                    m.setTitle(u.getName() + " @" + u.getScreenName());
                    m.setDescription(t.moreData.getText(shortlink_iflinkexceedslength, shortlink_urlstub, t.getText(), t.getLinks(), t.getPostId()).text);
                    m.setPubDate(t.getCreatedAt());
                    m.setGuid(t.getPostId());
                    feed.addMessage(m);
                }
            } catch (ConcurrentModificationException e) {
                // late incoming messages from concurrent peer retrieval may cause this
                // we silently do nothing here and return what we listed so far
            }
            String rss = feed.toString();
            //System.out.println("feed has " + feed.size() + " entries");

            // write xml
            response.getOutputStream().write(UTF8.getBytes(rss));
        } else if ("txt".equals(callType)) {
            post.setResponse(response, "text/plain");
            final StringBuilder buffer = new StringBuilder(1000);

            // Write text file
            try {
                for (TwitterTweet t : tl) {
                    UserEntry u = tl.getUser(t);
                    buffer.append(t.getCreatedAt()).append(" ")
                            .append(u.getScreenName())
                            .append(": ")
                            .append(t.moreData.getText(
                                    shortlink_iflinkexceedslength, shortlink_urlstub,
                                    t.getText(), t.getLinks(), t.getPostId()).text)
                            .append('\n');
                }
            } catch (ConcurrentModificationException e) {
                // late incoming messages from concurrent peer retrieval may cause this
                // we silently do nothing here and return what we listed so far
            }
            response.getOutputStream().write(UTF8.getBytes(buffer.toString()));
        }
    }

    private static void setReadMe(JSONObject m) {
        m.put("readme_0", "THIS JSON IS THE RESULT OF YOUR SEARCH QUERY - THERE IS NO WEB PAGE WHICH SHOWS THE RESULT!");
        m.put("readme_1", "loklak.org is the framework for a message search system, not the portal, read: http://api.loklak.org/about.html#notasearchportal");
        m.put("readme_2", "This is supposed to be the back-end of a search portal. For the api, see http://api.loklak.org/api.html");
        m.put("readme_3", "Parameters q=(query), source=(cache|backend|twitter|all), callback=p for jsonp, maximumRecords=(message count), minified=(true|false)");
    }

    private static void moreMetaData(JSONObject metadata, Query post) {
        metadata.put("client", post.getClientHost());
        metadata.put("time", System.currentTimeMillis() - post.getAccessTime());
        metadata.put("servicereduction", post.isDoS_servicereduction() ? "true" : "false");
//        metadata.put("scraper", scraperInfo);
//        String index = tl.getResultIndex() == null ? "" : String.valueOf(tl.getResultIndex());
//        metadata.put("index", index);
    }

    private static void setTwitterMetaData(JSONObject metadata, final int startRecord,
            final int maximumRecords, TwitterTimeline tl, String query, String filter, Query post, JSONObject hits) {
        // the number of the first record (according to SRU set to 1 for very first)
        metadata.put("startRecord", startRecord);
        // number of records within this json result set returned in the api call
        metadata.put("maximumRecords", maximumRecords);
        // number of records available in the search cache (so far, may be increased later > hits)
        metadata.put("count", tl.size());
        // number of records in the search index (so far, may be increased later as well)
        metadata.put("hits", tl.getHits());
        if (tl.getOrder() == Order.CREATED_AT) metadata.put("period", tl.period());
        metadata.put("query", query);
        metadata.put("filter", filter);
        metadata.put("client", post.getClientHost());
        metadata.put("time", System.currentTimeMillis() - post.getAccessTime());
        metadata.put("servicereduction", post.isDoS_servicereduction() ? "true" : "false");
        metadata.putAll(hits);

        String scraperInfo = tl.getScraperInfo() == null ? "" : tl.getScraperInfo();
        metadata.put("scraperInfo", scraperInfo);
        String index = tl.getResultIndex() == null ? "" : String.valueOf(tl.getResultIndex());
        metadata.put("index", index);
    }

    public static void main(String[] args) {
        try {
            TwitterTimeline tl = search(new String[]{"http://root.loklak.org"}, "beer", Order.CREATED_AT, "cache", 20, -120, backend_hash, 10000);
            System.out.println(tl.toJSON(false, "search_metadata", "statuses").toString(2));
        } catch (IOException e) {
            DAO.severe(e);
        }
    }
}

package org.loklak.harvester.strategy;

import org.eclipse.jetty.util.log.Log;
import org.loklak.api.search.SearchServlet;
import org.loklak.api.search.SuggestServlet;
import org.loklak.data.DAO;
import org.loklak.harvester.PushThread;
import org.loklak.harvester.TwitterAPI;
import org.loklak.harvester.TwitterScraper;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.QueryEntry;
import org.loklak.objects.ResultList;
import org.loklak.objects.Timeline;
import twitter4j.Location;
import twitter4j.Trend;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KaizenHarvester
 *
 * Kaizen is targeted to do more information and query grabbing, whether it
 * uses the official Twitter API, meta-data from collected tweets, or the
 * analysis of tweets.
 *
 * @author Kaisar Arkhan, @yuki_is_bored
 */
public class KaizenHarvester implements Harvester {

    private final String BACKEND;
    private final int SUGGESTIONS_COUNT;
    private final int SUGGESTIONS_RANDOM;
    private final int PLACE_RADIUS;
    private final int QUERIES_LIMIT;
    private final boolean VERBOSE;
    private final DateFormat dateToString = new SimpleDateFormat("yyyy-MM-dd");

    private Random random;

    private HashSet<String> queries = new HashSet<>();
    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    private Twitter twitter = null;

    public KaizenHarvester() {
        BACKEND = DAO.getConfig("backend", "http://loklak.org");
        SUGGESTIONS_COUNT = DAO.getConfig("harvester.kaizen.suggestions_count", 1000);
        SUGGESTIONS_RANDOM = DAO.getConfig("harvester.kaizen.suggestions_random", 5);
        PLACE_RADIUS = DAO.getConfig("harvester.kaizen.place_radius", 5);
        QUERIES_LIMIT = DAO.getConfig("harvester.kaizen.queries_limit", 500);
        VERBOSE = DAO.getConfig("harvester.kaizen.verbose", true);

        random = new Random();

        twitter = TwitterAPI.getAppTwitterFactory().getInstance();

        if (twitter == null)
            DAO.log("Kaizen can utilize Twitter API to get more queries, If you want to use it, " +
                    "Please add Application and Access tokens (twitterAccessToken, twitterAccessTokenSecret, " +
                    "client.twitterConsumerKey, client.twitterConsumerSecret)");
    }

    private void addQuery(String query) {
        if (QUERIES_LIMIT > 0 && queries.size() > QUERIES_LIMIT)
            return;

        if (queries.contains(query))
            return;

        if (VERBOSE)
            DAO.log("Adding '" + query + "' to queries");

        queries.add(query);
    }

    private void grabInformation(Timeline timeline) {
        String query = timeline.getQuery();
        if (VERBOSE)
            DAO.log("Kaizen is going to grab more information" +
                    (query != null ? " from results of '" + query + "'" : ""));

        Date oldestTweetDate = null;

        for (MessageEntry message : timeline) {

            // Calculate date for oldest Tweet
            if (oldestTweetDate == null) {
                oldestTweetDate = message.getCreatedAt();
            } else if (oldestTweetDate.compareTo(message.getCreatedAt()) > 0) {
                oldestTweetDate = message.getCreatedAt();
            }

            for (String user : message.getMentions())
                addQuery("from:" + user);

            for (String hashtag : message.getHashtags())
                addQuery(hashtag);

            String place = message.getPlaceName();
            if (!place.isEmpty())
               addQuery("near:\"" + message.getPlaceName() + "\" within:" + PLACE_RADIUS + "mi");
        }

        if (query != null && oldestTweetDate != null) {
            String oldestTweetDateStr = dateToString.format(oldestTweetDate);
            int startIndex = query.indexOf("until:");
            if (startIndex == -1) {
                addQuery(query + " until:" + oldestTweetDateStr);
            } else {
                int endIndex = startIndex + 16;  // until:yyyy-MM-dd = 16
                addQuery(query.replace(query.substring(startIndex + 6, endIndex), oldestTweetDateStr));
            }
        }
    }

    private void pushToBackend(Timeline timeline) {
        DAO.log("Pushing " + timeline.size() + " to backend ..." );
        executorService.execute(new PushThread(BACKEND, timeline));
    }

    private int harvestMessages() {
        if (VERBOSE)
            DAO.log(queries.size() + " available queries, Harvest season!");

        String query = queries.iterator().next();
        queries.remove(query);

        if (VERBOSE)
            DAO.log("Kaizen is going to harvest messages with query '" + query + "'");

        Timeline timeline = TwitterScraper.search(query, Timeline.Order.CREATED_AT, true, false, 400);

        if (timeline == null)
            timeline = new Timeline(Timeline.Order.CREATED_AT);

        if (timeline.size() == 0) {
            if (VERBOSE)
                DAO.log(query + " gives us no result, Pushing to backend anyway ...");
            timeline.setQuery(query);
            pushToBackend(timeline);

            return -1;
        }

        if (VERBOSE)
            DAO.log("'" + query + "' gives us " + timeline.size() + " messages, Pushing to backend ...");

        pushToBackend(timeline);

        grabInformation(timeline);

        return timeline.size();
    }

    private void grabTrending() {
        try {
            if (VERBOSE)
                DAO.log("Kaizen is going to get trending topics ...");

            for (Location location : twitter.trends().getAvailableTrends())
                for (Trend trend : twitter.trends().getPlaceTrends(location.getWoeid()).getTrends())
                    addQuery(trend.getQuery());
        } catch (TwitterException e) {
            if (e.getErrorCode() != 88)
                Log.getLog().warn(e);
        }
    }

    private void grabSuggestions() {
        if (VERBOSE)
            DAO.log("Kaizen is going to request for queries suggestions from backend ...");

        try {
            ResultList<QueryEntry> suggestedQueries =
                    SuggestServlet.suggest(BACKEND, "", "all", SUGGESTIONS_COUNT,
                            "desc", "retrieval_next", 0, null, "now",
                            "retrieval_next", SUGGESTIONS_RANDOM);

            if (VERBOSE)
                DAO.log("Backend gave us " + suggestedQueries.size() + " suggested queries");

            for (QueryEntry query : suggestedQueries) {
                addQuery(query.getQuery());
            }

            if (suggestedQueries.size() == 0) {
                if (VERBOSE)
                    DAO.log("It looks like backend doesn't have any suggested queries. "+
                            "Grabbing relevant context from backend collected messages ...");

                Timeline timeline = SearchServlet.search(BACKEND, "", Timeline.Order.CREATED_AT, "cache",
                        SUGGESTIONS_RANDOM, 0, SearchServlet.backend_hash, 60000);

                grabInformation(timeline);
            }
        } catch (IOException e) {
            Log.getLog().warn(e);
        }

        if (twitter != null)
            grabTrending();
    }

    @Override
    public int harvest() {
        if (!queries.isEmpty() && random.nextBoolean())
            return harvestMessages();

        grabSuggestions();

        return 0;
    }

    @Override
    public void stop() {
        executorService.shutdown();
    }
}

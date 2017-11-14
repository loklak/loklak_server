package org.loklak.harvester.strategy;

import org.loklak.api.search.SearchServlet;
import org.loklak.api.search.SuggestServlet;
import org.loklak.data.DAO;
import org.loklak.harvester.PushThread;
import org.loklak.harvester.TwitterAPI;
import org.loklak.harvester.TwitterScraper;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.objects.BasicTimeline.Order;
import org.loklak.objects.QueryEntry;
import org.loklak.objects.ResultList;
import org.loklak.objects.TwitterTimeline;
import twitter4j.Location;
import twitter4j.Trend;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    private final String[] BACKEND;
    private final int SUGGESTIONS_COUNT;
    private final int SUGGESTIONS_RANDOM;
    private final int PLACE_RADIUS;
    private final boolean VERBOSE;
    private final DateFormat dateToString = new SimpleDateFormat("yyyy-MM-dd");

    private Random random;

    private KaizenQueries queries = null;
    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    private Twitter twitter = null;

    public KaizenHarvester(KaizenQueries queries) {
        BACKEND = DAO.getBackend();
        SUGGESTIONS_COUNT = DAO.getConfig("harvester.kaizen.suggestions_count", 1000);
        SUGGESTIONS_RANDOM = DAO.getConfig("harvester.kaizen.suggestions_random", 5);
        PLACE_RADIUS = DAO.getConfig("harvester.kaizen.place_radius", 5);
        VERBOSE = DAO.getConfig("harvester.kaizen.verbose", true);

        random = new Random();
        this.queries = queries;

        TwitterFactory twitterFactory = TwitterAPI.getAppTwitterFactory();

        if (twitterFactory != null)
            twitter = twitterFactory.getInstance();

        if (twitter == null)
            DAO.log("Kaizen can utilize Twitter API to get more queries, If you want to use it, " +
                    "Please add Application and Access tokens (twitterAccessToken, twitterAccessTokenSecret, " +
                    "client.twitterConsumerKey, client.twitterConsumerSecret)");
    }

    public KaizenHarvester() {
        this(KaizenQueries.getDefaultKaizenQueries(DAO.getConfig("harvester.kaizen.queries_limit", 500)));
    }

    private void grabInformation(TwitterTimeline timeline) {
        String query = timeline.getQuery();
        if (VERBOSE) {
            DAO.log("Kaizen is going to grab more information" +
                    (query != null ? " from results of '" + query + "'" : ""));
        }

        Date oldestTweetDate = null;

        for (TwitterTweet message : timeline) {

            double score = this.getScore(message);

            // Calculate date for oldest Tweet
            if (oldestTweetDate == null) {
                oldestTweetDate = message.getCreatedAt();
            } else if (oldestTweetDate.compareTo(message.getCreatedAt()) > 0) {
                oldestTweetDate = message.getCreatedAt();
            }

            for (String user : message.getMentions()) {
                this.queries.addQuery("from:" + user, score);
            }

            for (String hashtag : message.getHashtags()) {
                this.queries.addQuery(hashtag, score);
            }

            String place = message.getPlaceName();
            if (!place.isEmpty()) {
                this.queries.addQuery("near:\"" + message.getPlaceName() + "\" within:" + PLACE_RADIUS + "mi", score);
            }
        }

        if (query != null && oldestTweetDate != null) {
            String oldestTweetDateStr = dateToString.format(oldestTweetDate);
            int startIndex = query.indexOf("until:");
            if (startIndex == -1) {
                this.queries.addQuery(query + " until:" + oldestTweetDateStr);
            } else {
                int endIndex = startIndex + 16;  // until:yyyy-MM-dd = 16
                this.queries.addQuery(query.replace(query.substring(startIndex + 6, endIndex), oldestTweetDateStr));
            }
        }
    }

    private void pushToBackend(TwitterTimeline timeline) {
        DAO.log("Pushing " + timeline.size() + " to backend ..." );
        executorService.execute(new PushThread(BACKEND, timeline));
    }

    private int harvestMessages() {
        if (VERBOSE) {
            DAO.log(this.queries.getSize() + " available queries, Harvest season!");
        }

        String query = this.queries.getQuery();

        if (VERBOSE)
            DAO.log("Kaizen is going to harvest messages with query '" + query + "'");

        TwitterTimeline timeline = TwitterScraper.search(query, Order.CREATED_AT, true, false, 400);

        if (timeline == null)
            timeline = new TwitterTimeline(Order.CREATED_AT);

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
            if (VERBOSE) {
                DAO.log("Kaizen is going to get trending topics ...");
            }

            for (Location location : twitter.trends().getAvailableTrends()) {
                for (Trend trend : twitter.trends().getPlaceTrends(location.getWoeid()).getTrends()) {
                    this.queries.addQuery(trend.getQuery());
                }
            }
        } catch (TwitterException e) {
            if (e.getErrorCode() != 88) {
                DAO.severe(e);
            }
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

            if (VERBOSE) {
                DAO.log("Backend gave us " + suggestedQueries.size() + " suggested queries");
            }

            for (QueryEntry query : suggestedQueries) {
                this.queries.addQuery(query.getQuery());
            }

            if (suggestedQueries.size() == 0) {
                if (VERBOSE) {
                    DAO.log("It looks like backend doesn't have any suggested queries. " +
                            "Grabbing relevant context from backend collected messages ...");
                }

                TwitterTimeline timeline = SearchServlet.search(BACKEND, "", Order.CREATED_AT, "cache",
                        SUGGESTIONS_RANDOM, 0, SearchServlet.backend_hash, 60000);

                grabInformation(timeline);
            }
        } catch (IOException e) {
            DAO.severe(e);
        }

        if (twitter != null)
            grabTrending();
    }

    protected boolean shallHarvest() {
        float targetProb = random.nextFloat();
        float prob = 0.5F;
        if (this.queries.getMaxSize() > 0) {
            prob = queries.getSize() / (float)queries.getMaxSize();
        }
        return !this.queries.isEmpty() && targetProb < prob;
    }

    protected double getScore(TwitterTweet message) {
        long score = message.getFavouritesCount() + message.getRetweetCount() * 5;
        return score / (score + 10 * Math.exp(-0.1 * score));
    }

    @Override
    public int harvest() {
        if (this.shallHarvest()) {
            return harvestMessages();
        }

        grabSuggestions();

        return 0;
    }

    @Override
    public void stop() {
        executorService.shutdown();
    }
}

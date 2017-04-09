package org.loklak.harvester;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.loklak.objects.Timeline;
import org.loklak.harvester.TwitterScraper;
import org.loklak.http.ClientConnection;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.Timeline;

/*
    This unit test tests org.loklak.harvester.TwitterScraper.java
    Twitter profile @loklak_test has been used for testing search method of TwitterScraper
*/
public class TwitterScraperTest {

    Timeline ftweet_list;

    @Test
    public void test_prepareSearchURL() {
        String url;
        String[] query = {"fossasia", "from:loklak_test",
            "spacex since:2017-04-03 until:2017-04-05", };
        String[] out_url = {
            "https://twitter.com/search?f=tweets&vertical=default&q=fossasia&src=typd",
            "https://twitter.com/search?f=tweets&vertical=default&q=from%3Aloklak_test&src=typd",
            "https://twitter.com/search?f=tweets&vertical=default&q=spacex+since%3A2017-04-03+until%3A2017-04-05&src=typd"};

        for (int i = 0; i < query.length; i++) {
            url = TwitterScraper.prepareSearchURL(query[i]);

            //compare urls with urls created
            assertEquals(url, out_url[i]);
        }

    }

    @Test
    public void test_search() {

        Timeline.Order order = Timeline.parseOrder("created_at");
        String https_url = "https://twitter.com/search?f=tweets&vertical=default&q=from%3Aloklak_test&src=typd";
        Timeline[] tweet_list = null;
        ClientConnection connection;
        BufferedReader br;

        try {
            //scrap all html from the https_url link
            connection = new ClientConnection(https_url);
            if (connection.inputStream == null) return;
            try {

                //read all scraped html data
                br = new BufferedReader(new InputStreamReader(connection.inputStream, StandardCharsets.UTF_8));

                //fetch list of tweets and set in ftweet_list
                tweet_list = TwitterScraper.search(br, order, true, true);
                this.ftweet_list = process_tweet_list(tweet_list);

            } catch (IOException e) {
                System.out.println(e.getMessage());
            } finally {
                connection.close();
            }
        } catch (IOException e) {

            // this could mean that twitter rejected the connection (DoS protection?) or we are offline (we should be silent then)
            if (tweet_list == null) tweet_list = new Timeline[]{new Timeline(order), new Timeline(order)};
        };

        //compare no. of tweets with fetched no. of tweets
        assertEquals(2, this.ftweet_list.size());
    }

    public Timeline process_tweet_list(Timeline[] tweet_list) {

        for (MessageEntry me: tweet_list[1]) {
            assert me instanceof TwitterScraper.TwitterTweet;
            TwitterScraper.TwitterTweet tweet = (TwitterScraper.TwitterTweet) me;
            if (tweet.waitReady(400)) tweet_list[0].add(tweet, tweet.getUser());
        }
        return tweet_list[0];

    }
}

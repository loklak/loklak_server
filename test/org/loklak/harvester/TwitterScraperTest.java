package org.loklak.harvester;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import org.junit.Test;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.loklak.objects.Timeline;
import org.loklak.harvester.TwitterScraper;
import org.loklak.objects.MessageEntry;
import org.loklak.http.ClientConnection;

/*
    These unit-tests test org.loklak.harvester.TwitterScraper.java
    Twitter profile @loklak_test has been used for testing search method of TwitterScraper
*/
public class TwitterScraperTest {

/*
    This unit-test tests twitter url creation
*/
    @Test
    public void testPrepareSearchURL() {
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
            assertThat(out_url[i], is(url));
        }
    }

/*
    This unit-test tests tweet data fetched from TwitterScraper object.
    THis test uses TwitterTweet object to get and check tweets
*/
    @Test
    public void testSearch() {
        Timeline ftweet_list;
        Timeline.Order order = Timeline.parseOrder("created_at");
        String https_url = "https://twitter.com/search?f=tweets&vertical=default&q=from%3Aloklak_test&src=typd";
        Timeline[] tweet_list = null;
        ClientConnection connection;
        BufferedReader br;
        // Tweet data to check with TwitterTweet object
        Map<String, String> tweet_check = new HashMap<String, String>();
        tweet_check.put("user", "\"profile_image_url_https\":\"https://pbs.twimg.com/profile_images/855169412164444160/EU53XJwX_bigger.jpg\",\"screen_name\":\"loklak_test\",\"user_id\":\"849919296977416192\",\"name\":\"loklak test\",\"appearance_latest\":");
        tweet_check.put("source_type", "TWITTER");
        tweet_check.put("provider_type", "SCRAPED");
        tweet_check.put("screen_name", "loklak_test");
        tweet_check.put("created_at", "2017-04-06T09:44:32.000Z");
        tweet_check.put("status_id_url", "https://twitter.com/loklak_test/status/849920752149254144");
        tweet_check.put("place_name", "");
        tweet_check.put("place_id", "");
        tweet_check.put("text", "car photo #test #car https://pic.twitter.com/vd1itvy8Mx");
        tweet_check.put("writeToIndex", "true");
        tweet_check.put("writeToBackend", "true");
        //TODO: add these.
        //put("videos", "");
        //put("images", "");

        try {
            // Scrap all html from the https_url link
            connection = new ClientConnection(https_url);
            if (connection.inputStream == null) return;
            try {

                // Read all scraped html data
                br = new BufferedReader(new InputStreamReader(connection.inputStream, StandardCharsets.UTF_8));

                // Fetch list of tweets and set in ftweet_list
                tweet_list = TwitterScraper.search(br, order, true, true);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } finally {
                connection.close();
            }
        } catch (IOException e) {
            // This could mean that twitter rejected the connection (DoS protection?) or we are offline (we should be silent then)
            if (tweet_list == null) {
                tweet_list = new Timeline[]{new Timeline(order), new Timeline(order)};
            }
        }

        //compare no. of tweets with fetched no. of tweets
        ftweet_list = processTweetList(tweet_list);
        assertThat(ftweet_list.size(), is(2));

        // Test tweets data with TwitterTweet object
        TwitterScraper.TwitterTweet tweet = (TwitterScraper.TwitterTweet) ftweet_list.iterator().next();

        assertTrue(String.valueOf(tweet.user).contains(tweet_check.get("user")));
        assertTrue(String.valueOf(tweet.status_id_url).equals(tweet_check.get("status_id_url")));

        String created_date = dateFormatChange(String.valueOf(tweet.created_at));
        assertTrue(created_date.equals(tweet_check.get("created_at")));
        
        assertTrue(String.valueOf(tweet.screen_name).equals(tweet_check.get("screen_name")));
        assertTrue(String.valueOf(tweet.source_type).equals(tweet_check.get("source_type")));
        assertTrue(String.valueOf(tweet.provider_type).equals(tweet_check.get("provider_type")));
        assertTrue(String.valueOf(tweet.text).equals(tweet_check.get("text")));
        assertTrue(tweet.writeToIndex);
        assertTrue(tweet.writeToBackend);

    }

/*
    This method merges 2 arrays of Timeline Objects(containing array of TwitterTweet objects) into one Timeline object
*/
    public Timeline processTweetList(Timeline[] tweet_list) {

        for (MessageEntry me: tweet_list[1]) {
            assert me instanceof TwitterScraper.TwitterTweet;
            TwitterScraper.TwitterTweet tweet = (TwitterScraper.TwitterTweet) me;
            if (tweet.waitReady(400)) {
                tweet_list[0].add(tweet, tweet.getUser());
            }
        }
        return tweet_list[0];

    }

/*
    Change Date format to compare with other dates
*/
    public String dateFormatChange(String time) {

        String k;

        DateTimeFormatter input_format =
                DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");
        DateTimeFormatter output_format =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        LocalDateTime parsed_time = LocalDateTime.parse(time, input_format);
        ZonedDateTime ldtZoned = parsed_time.atZone(ZoneId.systemDefault());
        ZonedDateTime final_time = ldtZoned.withZoneSameInstant(ZoneId.of("UTC"));
        k = String.valueOf(output_format.format(final_time));

        return k;
    }

}

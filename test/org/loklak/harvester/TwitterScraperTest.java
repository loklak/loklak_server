package org.loklak.harvester;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;
import org.loklak.objects.Timeline;
import org.loklak.objects.MessageEntry;
import org.loklak.http.ClientConnection;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;


/**
 * These unit-tests test org.loklak.harvester.TwitterScraper.java
 * Twitter profile @loklak_test has been used for testing search method of TwitterScraper
 */
public class TwitterScraperTest {

    /**
     * This unit-test tests twitter url creation
     */
    @Test
    public void testPrepareSearchUrl() {
        String url;
        String[] query = {"fossasia", "from:loklak_test",
                "spacex since:2017-04-03 until:2017-04-05"};
        ArrayList<String>[] filterList = (ArrayList<String>[])new ArrayList[3];
        for (int i = 0; i < filterList.length; i++) {
            filterList[i] = new ArrayList<String>();
        }
        filterList[0].add("video");
        filterList[1].addAll(Arrays.asList("image", "video"));
        filterList[2].addAll(Arrays.asList("abc", "video"));

        String[] out_url = {
            "https://twitter.com/search?f=tweets&vertical=default&q=fossasia&src=typd",
            "https://twitter.com/search?f=tweets&vertical=default&q=from%3Aloklak_test&src=typd",
            "https://twitter.com/search?f=tweets&vertical=default&q=spacex+since%3A2017-04-03+until%3A2017-04-05&src=typd",
            "https://twitter.com/search?f=videos&vertical=default&q=fossasia&src=typd",
            "https://twitter.com/search?f=tweets&vertical=default&q=fossasia&src=typd",
            "https://twitter.com/search?f=tweets&vertical=default&q=fossasia&src=typd",
        };

        // checking simple urls
        for (int i = 0; i < query.length; i++) {
            try {
                url = (String)executePrivateMethod(TwitterScraper.class, "prepareSearchUrl",new Class[]{String.class, String.class}, query[i], "");

                //compare urls with urls created
                assertThat(out_url[i], is(url));
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }

        // checking urls having filters
        for (int i = 0; i < filterList.length; i++) {
            try {
                url = (String)executePrivateMethod(TwitterScraper.class, "prepareSearchUrl",new Class[]{String.class, String.class}, query[0], filterList[i]);
                //compare urls with urls created
                assertThat(out_url[i+3], is(url));
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }

        }

    }

    /**
     * This unit-test tests data fetched in TwitterScraper.search() method.
     * This test uses TwitterTweet object to get and check tweets
     */
    @Test
    public void testSimpleSearch() {
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
        tweet_check.put("created_at", "2017-06-02T07:03:23.000Z");
        tweet_check.put("status_id_url", "https://twitter.com/loklak_test/status/870536303569289216");
        tweet_check.put("place_name", "");
        tweet_check.put("place_id", "");
        tweet_check.put("text", "Tweet with a video");
        tweet_check.put("writeToIndex", "true");
        tweet_check.put("writeToBackend", "true");

        try {
            // Scrap all html from the https_url link
            connection = new ClientConnection(https_url);
            if (connection.inputStream == null) return;
            try {
                // Read all scraped html data
                br = new BufferedReader(new InputStreamReader(connection.inputStream, StandardCharsets.UTF_8));

                // Fetch list of tweets and set in ftweet_list
                tweet_list = (Timeline[])executePrivateMethod(TwitterScraper.class, "search",new Class[]{BufferedReader.class, Timeline.Order.class, boolean.class, boolean.class},br, order, true, true);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                connection.close();
            }
        } catch (IOException e) {
            // This could mean that twitter rejected the connection (DoS protection?) or we are offline (we should be silent then)
            tweet_list = new Timeline[]{new Timeline(order), new Timeline(order)};
        }

        //compare no. of tweets with fetched no. of tweets
        ftweet_list = processTweetList(tweet_list);
        assertThat(ftweet_list.size(), is(6));

        // Test tweets data with TwitterTweet object
        TwitterScraper.TwitterTweet tweet = (TwitterScraper.TwitterTweet) ftweet_list.iterator().next();

        assertThat(String.valueOf(tweet.getUser()), containsString(tweet_check.get("user")));
        assertEquals(String.valueOf(tweet.getStatusIdUrl()), tweet_check.get("status_id_url"));

        String created_date = dateFormatChange(String.valueOf(tweet.getCreatedAt()));
        assertThat(created_date, is(tweet_check.get("created_at")));

        assertEquals(tweet.getScreenName(), tweet_check.get("screen_name"));
        assertEquals(String.valueOf(tweet.getSourceType()), tweet_check.get("source_type"));
        assertEquals(String.valueOf(tweet.getProviderType()), tweet_check.get("provider_type"));
        assertEquals(tweet.getText(), tweet_check.get("text"));
        assertEquals(tweet.getPlaceId(), tweet_check.get("place_name"));
        assertEquals(tweet.getPlaceName(), tweet_check.get("place_id"));

        try {
            // Other parameters of twittertweet(used )
            assertThat(getPrivateField(TwitterScraper.TwitterTweet.class, "writeToIndex", tweet), is(Boolean.parseBoolean(tweet_check.get("writeToIndex"))));
            assertThat(getPrivateField(TwitterScraper.TwitterTweet.class, "writeToBackend", tweet), is(Boolean.parseBoolean(tweet_check.get("writeToBackend"))));
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * This method merges 2 arrays of Timeline Objects(containing array of TwitterTweet objects) into one Timeline object
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

    /**
     * Change Date format to compare with other dates
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

    public Object executePrivateMethod(
            Class<?> clazz,
            Object instanceObj,
            String methodName,
            Class<?>[] parameterTypes,
            Object ... args
    ) throws Exception {

        // Get declared Method for execution
        Method privateMethod = clazz.getDeclaredMethod(methodName, parameterTypes);
        privateMethod.setAccessible(true);

        // Invoke method and return object
        if (Modifier.isStatic(privateMethod.getModifiers())) {
            return privateMethod.invoke(null, args);
        } else if(instanceObj != null) {
            return privateMethod.invoke(instanceObj, args);
        } else {
            return privateMethod.invoke(clazz.newInstance(), args);
        }
    }

    public Object executePrivateMethod(
            Class<?> clazz,
            String methodName,
            Class<?>[] parameterTypes,
            Object ... args
    ) throws Exception {
        return executePrivateMethod(
            clazz,
            null,
            methodName,
            parameterTypes,
            args
    );

    }

    public static Object getPrivateField(
            Class<?> clazz,
            String fieldName,
            Object instanceObj
    ) throws Exception {

        // Get declared field
        Field privateField = clazz.getDeclaredField(fieldName);
        privateField.setAccessible(true);

        // Return field
        if (Modifier.isStatic(privateField.getModifiers())) {
            return privateField.get(null);
        }
        else if (instanceObj != null) {
            return privateField.get(instanceObj);
        }
        else {
            return privateField.get(clazz.newInstance());
        }
    }

    public static Object getPrivateField(
            Class<?> clazz,
            String fieldName
    ) throws Exception {
        return getPrivateField(clazz, fieldName, null);
    }

}

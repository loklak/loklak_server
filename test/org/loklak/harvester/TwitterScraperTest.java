package org.loklak.harvester;

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
import org.loklak.objects.TwitterTimeline;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;


/**
 * These unit-tests test org.loklak.harvester.TwitterScraper.java
 * Twitter profile @loklak_test has been used for testing search method of TwitterScraper
 */
public class TwitterScraperTest {

    /**
     * This method merges 2 arrays of Timeline Objects(containing array of TwitterTweet objects) into one Timeline object
     */
    public TwitterTimeline processTweetList(TwitterTimeline[] tweet_list) {

        for (TwitterTweet me: tweet_list[1]) {
            assert me instanceof TwitterTweet;
            TwitterTweet tweet = (TwitterTweet) me;
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

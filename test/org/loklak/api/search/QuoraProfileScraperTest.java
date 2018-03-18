package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import org.junit.Test;
import org.loklak.api.search.QuoraProfileScraper;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;
import org.loklak.objects.PostTimeline;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import org.loklak.harvester.TwitterScraperTest;

/**
 * These unit-tests test org.loklak.api.search.QuoraProfileScraper.java
 */
public class QuoraProfileScraperTest {

    @Test
    public void quoraProfileScraperUserTest() {

        QuoraProfileScraper quoraScraper = new QuoraProfileScraper();
        PostTimeline profileListTimeLine = null;
        String url = "https://www.quora.com/profile/Saptak-Sengupta";
        BufferedReader br = null;

        String userName = "Saptak Sengupta";
        String rssFeedLink = "https://www.quora.com/profile/Saptak-Sengupta/rss";
        String profileImagePath = "/main-thumb-24728160-200-igibbfdmibqxdtrjlrdnejpvjqepxpnn.jpeg";
        String topicsUrl = "https://www.quora.com/profile/Saptak-Sengupta/topics";
        String followingUrl = "https://www.quora.com/profile/Saptak-Sengupta/following";
        String blogsUrl = "https://www.quora.com/profile/Saptak-Sengupta/blogs";
        String editsUrl = "https://www.quora.com/profile/Saptak-Sengupta/log";
        String postsUrl = "https://www.quora.com/profile/Saptak-Sengupta/all_posts";
        String questionsUrl = "https://www.quora.com/profile/Saptak-Sengupta/questions";

        try {
            ClientConnection connection = new ClientConnection(url);
            //Check Network issue
            assertThat(connection.getStatusCode(), is(200));

            br = quoraScraper.getHtml(connection);
        } catch (IOException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperUserTest() failed to connect to network. url:" + url);
        }

        try {
            profileListTimeLine = (PostTimeline)TwitterScraperTest.executePrivateMethod(QuoraProfileScraper.class, quoraScraper, "scrapeProfile", new Class[]{BufferedReader.class, String.class}, br, url);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        JSONArray profileList = profileListTimeLine.toArray();
        JSONObject quoraProfile = (JSONObject)profileList.get(0);

        assertEquals(quoraProfile.getString("search_url"), url);
        assertEquals(quoraProfile.getString("user_name"), userName);
        assertEquals(quoraProfile.getString("rss_feed_link"), rssFeedLink);
        assertEquals(quoraProfile.getString("profileImage").substring(quoraProfile.getString("profileImage").length()-profileImagePath.length()), profileImagePath);
        assertEquals(quoraProfile.getJSONObject("feeds").getString("topics_url"), topicsUrl);
        assertEquals(quoraProfile.getJSONObject("feeds").getString("following_url"), followingUrl);
        assertEquals(quoraProfile.getJSONObject("feeds").getString("blogs_url"), blogsUrl);
        assertEquals(quoraProfile.getJSONObject("feeds").getString("edits_url"), editsUrl);
        assertEquals(quoraProfile.getJSONObject("feeds").getString("posts_url"), postsUrl);
        assertEquals(quoraProfile.getJSONObject("feeds").getString("questions_url"), questionsUrl);
    }

    @Test
    public void quoraQuestionScraperUserTest() {

        QuoraProfileScraper quoraScraper = new QuoraProfileScraper();
        PostTimeline questionListTimeLine = null;
        String url = "https://www.quora.com/search/?q=fossasia&type=question";
        String postType = "question";
        BufferedReader br = null;

        try {
            ClientConnection connection = new ClientConnection(url);
            //Check Network issue
            assertThat(connection.getStatusCode(), is(200));

            br = quoraScraper.getHtml(connection);
        } catch (IOException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperUserTest() failed to connect to network. url:" + url);
        }

        try {
            questionListTimeLine = (PostTimeline)TwitterScraperTest.executePrivateMethod(QuoraProfileScraper.class, quoraScraper, "scrapeQues", new Class[]{BufferedReader.class, String.class}, br, url);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        JSONArray qList = questionListTimeLine.toArray();

        assertFalse(qList.length() == 0);

        for (int i = 0; i < qList.length(); i++) {
            JSONObject question = (JSONObject)qList.get(i);
            assertEquals(question.getString("post_type"), postType);
            assertEquals(question.getString("search_url"), url);
        }
    }
}

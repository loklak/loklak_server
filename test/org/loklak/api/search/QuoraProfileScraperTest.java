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
import static org.junit.Assert.assertNotNull;
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
        String profileImagePath = "/main-thumb-24728160-200-igibbfdmibqxdtrjlrdnejpvjqepxpnn.jpeg";
        String topicsUrl = "https://www.quora.com/profile/Saptak-Sengupta/topics";
        String followingUrl = "https://www.quora.com/profile/Saptak-Sengupta/following";
        String blogsUrl = "https://www.quora.com/profile/Saptak-Sengupta/blogs";
        String editsUrl = "https://www.quora.com/profile/Saptak-Sengupta/log";
        String postsUrl = "https://www.quora.com/profile/Saptak-Sengupta/all_posts";
        String questionsUrl = "https://www.quora.com/profile/Saptak-Sengupta/questions";
        String postType = "user";
        String post_scraper = "quora";

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
        JSONObject quoraProfile = profileList.getJSONObject(0);

        assertNotNull(quoraProfile.getString("bio"));
        assertEquals(post_scraper, quoraProfile.getString("post_scraper"));
        assertEquals(postType, quoraProfile.getString("post_type"));
        assertEquals(url, quoraProfile.getString("search_url"));
        assertEquals(userName, quoraProfile.getString("user_name"));
        assertEquals(profileImagePath, quoraProfile.getString("profileImage").substring(quoraProfile.getString("profileImage").length()-profileImagePath.length()));
        assertEquals(topicsUrl, quoraProfile.getJSONObject("feeds").getString("topics_url"));
        assertEquals(followingUrl, quoraProfile.getJSONObject("feeds").getString("following_url"));
        assertEquals(blogsUrl, quoraProfile.getJSONObject("feeds").getString("blogs_url"));
        assertEquals(editsUrl, quoraProfile.getJSONObject("feeds").getString("edits_url"));
        assertEquals(postsUrl, quoraProfile.getJSONObject("feeds").getString("posts_url"));
        assertEquals(questionsUrl, quoraProfile.getJSONObject("feeds").getString("questions_url"));
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
            assertEquals(postType, question.getString("post_type"));
            assertEquals(url, question.getString("search_url"));
        }
    }
}

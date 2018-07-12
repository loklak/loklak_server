package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.loklak.api.search.GithubProfileScraper;
import org.loklak.data.DAO;
import org.loklak.harvester.Post;
import org.loklak.http.ClientConnection;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * These unit-tests test org.loklak.api.search.GithubProfileScraper.java
 */
public class GithubProfileScraperTest {

    @Test
    public void apiPathTest() {
        GithubProfileScraper githubScraper = new GithubProfileScraper();
        assertEquals("/api/githubprofilescraper.json", githubScraper.getAPIPath());
    }

    @Test
    public void githubProfileScraperOrgTest() throws NullPointerException {

        GithubProfileScraper githubScraper = new GithubProfileScraper();
        String url = "https://github.com/fossasia/";
        BufferedReader br = null;
        githubScraper.termsList = new ArrayList<String>();
        githubScraper.termsList.add("all");

        String profile = "fossasia";
        String shortDescription = "Open Technologies in Asia";
        String userName = "fossasia";
        String userId = "6295529";
        String location = "Singapore";
        String specialLink = "https://fossasia.org";

        try {
            ClientConnection connection = new ClientConnection(url);
            //Check Network issue
            assertThat(connection.getStatusCode(), is(200));

            br = githubScraper.getHtml(connection);
        } catch (IOException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperUserTest() failed to connect to network. url:" + url);
        }

        Post fetchedProfile = new Post();
        try {
            fetchedProfile = githubScraper.scrapeGithub(profile, br);
            assertEquals(shortDescription, fetchedProfile.getString("short_description"));
            assertEquals(userName, fetchedProfile.getString("user"));
            assertEquals(userId, fetchedProfile.getString("user_id"));
            assertEquals(location, fetchedProfile.getString("location"));
            assertEquals(specialLink, fetchedProfile.getString("special_link"));
        } catch (IllegalStateException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperOrgTest() failed with an IllegalStateException");
        } catch (NullPointerException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperOrgTest() failed with an NullPointerException");
        }
    }

    @Test
    public void githubProfileScraperUserTest() throws NullPointerException {

        GithubProfileScraper githubScraper = new GithubProfileScraper();
        String url = "https://github.com/djmgit/";
        BufferedReader br = null;
        githubScraper.termsList = new ArrayList<String>();
        githubScraper.termsList.add("all");

        String profile = "djmgit";
        String userName = "djmgit";
        String fullName = "Deepjyoti Mondal";
        String specialLink = "http://djmgit.github.io";
        String userId = "16368427";

        try {
            ClientConnection connection = new ClientConnection(url);
            //Check Network issue
            assertThat(connection.getStatusCode(), is(200));

            br = githubScraper.getHtml(connection);
        } catch (IOException e) {
                DAO.log("GithubProfileScraperTest.githubProfileScraperUserTest() failed to connect to network. url:" + url);
        }

        Post fetchedProfile = new Post();
        try {
            fetchedProfile = githubScraper.scrapeGithub(profile, br);
            assertEquals(userName, fetchedProfile.getString("user"));
            assertEquals(fullName, fetchedProfile.getString("full_name"));
            assertEquals(specialLink, fetchedProfile.getString("special_link"));
            assertEquals(userId, fetchedProfile.getString("user_id"));
        } catch (IllegalStateException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperOrgTest() failed with an IllegalStateException");
        } catch (NullPointerException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperOrgTest() failed with an NullPointerException");
        }
    }
}

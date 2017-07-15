package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.loklak.api.search.GithubProfileScraper;
import org.loklak.data.DAO;
import org.loklak.harvester.Post;
import org.loklak.http.ClientConnection;

import static org.junit.Assert.assertEquals;

/**
 * These unit-tests test org.loklak.api.search.GithubProfileScraper.java
 */
public class GithubProfileScraperTest {

    @Test
    public void githubProfileScraperOrgTest() {

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
        String specialLink = "http://fossasia.org";

        try {
            ClientConnection connection = new ClientConnection(url);
            br = githubScraper.getHtml(connection);
        } catch (IOException e) {
            DAO.log("GithubProfileScraperTest.githubProfileScraperUserTest() failed to connect to network. url:" + url);
        }

	    Post fetchedProfile = githubScraper.scrapeGithub(profile, br);

        assertEquals(fetchedProfile.getString("short_description"), shortDescription);
        assertEquals(fetchedProfile.getString("user_name"), userName);
        assertEquals(fetchedProfile.getString("user_id"), userId);
        assertEquals(fetchedProfile.getString("location"), location);
        assertEquals(fetchedProfile.getString("special_link"), specialLink);
        
    }

	@Test
    public void githubProfileScraperUserTest() {

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
            br = githubScraper.getHtml(connection);
           } catch (IOException e) {
                DAO.log("GithubProfileScraperTest.githubProfileScraperUserTest() failed to connect to network. url:" + url);
           }

            Post fetchedProfile = githubScraper.scrapeGithub(profile, br);

            assertEquals(fetchedProfile.getString("user_name"), userName);
            assertEquals(fetchedProfile.getString("full_name"), fullName);
            assertEquals(fetchedProfile.getString("special_link"), specialLink);
            assertEquals(fetchedProfile.getString("user_id"), userId);
    }

}

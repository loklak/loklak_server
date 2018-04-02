package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.loklak.api.search.InstagramProfileScraper;
import org.loklak.data.DAO;
import org.loklak.harvester.Post;
import org.loklak.http.ClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertNotNull;

/**
 * These unit-tests test org.loklak.api.search.InstagramProfileScraper.java
 */

public class InstagramProfileScraperTest {

	@Test
	public void instagramProfileScraperUserTest() {

		InstagramProfileScraper instagramScraper = new InstagramProfileScraper();
		String url = "https://www.instagram.com/fossasia/";
		BufferedReader br = null;

		try {
			ClientConnection connection = new ClientConnection(url);
			//Check Network issue
			assertThat(connection.getStatusCode(), is(200));
			System.out.println("saurabh well done connected");
			br = instagramScraper.getHtml(connection);
		} catch (IOException e) {
			DAO.log("InstagramProfileScraperTest.instagramProfileScraperUserTest() failed to connect to network. url:" + url);
		}

		JSONArray instaProfile = new JSONArray();
		instaProfile = instagramScraper.scrapeInstagram(br, url);
		assertNotNull(instaProfile);
	}
}

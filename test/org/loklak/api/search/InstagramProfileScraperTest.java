package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import org.junit.Test;
import org.loklak.api.search.InstagramProfileScraper;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
			br = instagramScraper.getHtml(connection);
		} catch (IOException e) {
			DAO.log("InstagramProfileScraperTest.instagramProfileScraperUserTest() failed to connect to network. url:" + url);
		}

		JSONArray instaProfile = new JSONArray();
		instaProfile = instagramScraper.scrapeInstagram(br, url);

		String hostname = "www.instagram.com";
		for(int i=0; i<instaProfile.length(); i++) {
			JSONObject json = (JSONObject)instaProfile.get(i);
			JSONObject entry_data = json.getJSONObject("entry_data");
			JSONObject config = json.getJSONObject("config");
			try {
				assertNotNull(config);
				assertNotNull(instaProfile);
				assertNotNull(entry_data);
				assertTrue(json.has("activity_counts"));
				assertTrue(json.has("country_code"));
				assertTrue(json.has("platform"));
				assertTrue(json.has("language_code"));
				assertTrue(json.has("gatekeepers"));
				assertTrue(entry_data.has("ProfilePage"));			
				assertTrue(config.has("viewer"));
				assertTrue(config.has("csrf_token"));
				assertEquals(json.getString("hostname"), hostname);
			} catch (Exception e) {
				DAO.log("InstagramProfileScraperTest.instagramProfileScraperUserTest() assert error");
			}
		}
	}
}

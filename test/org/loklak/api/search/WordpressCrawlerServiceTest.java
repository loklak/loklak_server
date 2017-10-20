package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import org.junit.Test;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.api.search.WordpressCrawlerService;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;

/**
 * These unit-tests test org.loklak.api.search.WordpressCrawlerService
 */
public class WordpressCrawlerServiceTest {
	@Test
	public void wordpressCrawlerServiceTest() {
        BufferedReader br = null;
		String url = "http://blog.fossasia.org/author/saptaks/";
		String author = "saptaks";
        WordpressCrawlerService wordpressCrawler = new WordpressCrawlerService(url);
        try {
            ClientConnection connection = new ClientConnection(url);
            //Check Network issue
            assertThat(connection.getStatusCode(), is(200));

            br = wordpressCrawler.getHtml(connection);
        } catch (IOException e) {
            DAO.log("WordpressCrawlerServiceTest.WordpressCrawlerServiceTest() failed to connect to network. url:" + url);
        }

		JSONArray blogs = wordpressCrawler.crawlWordpress(url, br).toArray();

		for(int i = 0;i < blogs.length(); i++) {
			JSONObject blog = (JSONObject)blogs.get(i);
			assertTrue(blog.has("blog_url"));
			assertTrue(blog.has("title"));
			assertTrue(blog.has("posted_on"));
			assertTrue(blog.has("content"));
			assertTrue(blog.has("author"));
			assertThat(blog.getString("author"), is(author));
		}
	}
}

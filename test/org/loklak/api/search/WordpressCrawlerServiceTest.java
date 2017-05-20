package org.loklak.api.search;

import org.junit.Test;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.api.search.WordpressCrawlerService;
import org.loklak.susi.SusiThought;

/*
    These unit-tests test org.loklak.api.search.WordpressCrawlerService
*/
public class WordpressCrawlerServiceTest {
	@Test
	public void wordpressCrawlerServiceTest() {
		String url = "http://blog.fossasia.org/author/saptaks/";
		String author = "saptaks";

		SusiThought response = WordpressCrawlerService.crawlWordpress(url);
		JSONArray blogs = response.getData();

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
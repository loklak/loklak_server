package org.loklak.api.search;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.json.JSONObject;
import org.loklak.api.search.GithubProfileScraper;
import org.loklak.susi.SusiThought;

public class GithubProfileScraperTest {
	@Test
	public void githubProfileScraperOrgTest() {
		String profile = "fossasia";
		String shortDescription = "Open Technologies in Asia";
		String userName = "fossasia";
		String userId = "6295529";
		String location = "Singapore";
		String specialLink = "http://fossasia.org";

		SusiThought response = GithubProfileScraper.scrapeGithub(profile);
		JSONObject fetchedProfile = (JSONObject)response.getData().get(0);

		assertEquals(fetchedProfile.getString("short_description"), shortDescription);
		assertEquals(fetchedProfile.getString("user_name"), userName);
		assertEquals(fetchedProfile.getString("user_id"), userId);
		assertEquals(fetchedProfile.getString("location"), location);
		assertEquals(fetchedProfile.getString("special_link"), specialLink);
	}

	@Test
	public void githubProfileScraperUserTest() {
		String profile = "djmgit";
		String userName = "djmgit";
		String fullName = "Deepjyoti Mondal";
		String specialLink = "http://djmgit.github.io";
		String userId = "16368427";

		SusiThought response = GithubProfileScraper.scrapeGithub(profile);
		JSONObject fetchedProfile = (JSONObject)response.getData().get(0);

		assertEquals(fetchedProfile.getString("user_name"), userName);
		assertEquals(fetchedProfile.getString("full_name"), fullName);
		assertEquals(fetchedProfile.getString("special_link"), specialLink);
		assertEquals(fetchedProfile.getString("user_id"), userId);
	}
}

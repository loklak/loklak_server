/**
 *  Instagram Profile Scraper
 *  Copyright 08.08.2016 by Jigyasa Grover, @jig08
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.api.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import org.apache.http.client.utils.URIBuilder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.loklak.data.DAO;
import org.loklak.harvester.BaseScraper;
import org.loklak.harvester.Post;
import org.loklak.server.BaseUserRole;

public class InstagramProfileScraper extends BaseScraper {

	private static final long serialVersionUID = -3360416757176406604L;
    private Pattern instaJsonData = Pattern.compile("(\\{\"activity_counts\").*(\\});");

    public InstagramProfileScraper() {
        super();
        this.baseUrl = "https://www.instagram.com/";
        this.scraperName = "instagram";
   }

    public InstagramProfileScraper(String _query, Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.query = _query;
    }

    public InstagramProfileScraper(Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
    }

    public InstagramProfileScraper(String _query) {
        this();
        this.query = _query;
    }

	@Override
	public String getAPIPath() {
		return "/api/instagramprofilescraper.json";
	}

	@Override
	public BaseUserRole getMinimalBaseUserRole() {
		return BaseUserRole.ANONYMOUS;
	}

	@Override
	public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
		return null;
	}

    protected String prepareSearchUrl(String type) {
        URIBuilder url = null;
        try {
            url = new URIBuilder(this.baseUrl + this.query);
        } catch (URISyntaxException e) {
            DAO.log("Invalid Url: baseUrl = " + this.baseUrl + ", mid-URL = " + midUrl + "query = " + this.query + "type = " + type);
            return "";
        }

        return url.toString();
    }

    protected void setParam() {
        if("".equals(this.getExtraValue("profile"))) {
            this.setExtraValue("profile", this.query);
        }
        this.setExtraValue("query", this.query);
    }

    protected Post scrape(BufferedReader br, String type, String url) {
   	    Post instaObj = new Post(true);
        this.putData(instaObj, "profile_posts", this.scrapeInstagram(br, url));
        return instaObj;
    }

	public JSONArray scrapeInstagram(BufferedReader br, String url) {
		Document htmlPage = null;
        Post instaObj = null;
        JSONArray instaProfile = new JSONArray();
        try {
            htmlPage = Jsoup.parse(this.bufferedReaderToString(br));
        } catch (IOException e) {
            DAO.trace(e);
		}

		String script = htmlPage.getElementsByTag("script").html();
        Matcher m = instaJsonData.matcher(script);
        m.find();
        int start = m.start(1);
        int end = m.start(2) + 1;
        script = script.substring(start, end);

        //TODO: pre-process the posts captured. At present, complete array of posts are output.
        //Only useful data shall be outputted.
		instaObj = new Post(script, this.query);
        instaProfile.put(instaObj);
       return instaProfile;
	}

}

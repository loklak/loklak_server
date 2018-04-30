/**
 *  Wordpress Crawler
 *  Copyright 08.06.2016 by Jigyasa Grover, @jig08
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

import java.io.IOException;
import java.io.BufferedReader;
import java.util.Map;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.loklak.data.DAO;
import org.loklak.harvester.BaseScraper;
import org.loklak.harvester.Post;
import org.loklak.objects.PostTimeline;
import org.loklak.server.BaseUserRole;

public class WordpressCrawlerService extends BaseScraper {

	private static final long serialVersionUID = -5357182691897402354L;

    /**
     * Constructor to set baseUrl and scraperName in the super(Base-Interface) for
     * current Search-Scrapper
     */
    public WordpressCrawlerService() {
        super();
        this.baseUrl = "";
        this.scraperName = "wordpress";
    }

    /**
     * Constructor to map the given _extra param with key and value as String
     */
    public WordpressCrawlerService(Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.query = this.getExtraValue("query");
    }

    /**
     * Constructor to set String query
     * @param query as string
     */
    public WordpressCrawlerService(String _query) {
        this();
        this.query = _query;
        this.setExtraValue("query", this.query);
    }

    /**
     * Method to get api path of Wordpress Crawler Service
     * @return api endpoint of Wordpress Crawler Service in form of String
     */
	@Override
	public String getAPIPath() {
		return "/api/wordpresscrawler.json";
	}

	@Override
	public BaseUserRole getMinimalBaseUserRole() {
		return BaseUserRole.ANONYMOUS;
	}

    /**
     * @return null when asked for the default permissions
     * of the given base user role in form of JSONObject
     */
	@Override
	public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
		return null;
	}

    /**
     * Method to to prepare search url
     * @param type as String input
     * @return query in string format
     */
    protected String prepareSearchUrl(String type) {
        return this.query;
    }

    /**
     * Method to set the "url" key with value of query if the "url" key is empty/null
     * else to set the "query" key with value of query
     */
    protected void setParam() {
        if ("".equals(this.getExtraValue("url"))) {
            this.setExtraValue("url", this.query);
        } else {
            this.query  = this.getExtraValue("url");
        }
    }

    /**
     * Method to scrape the given url and store the result with key as "blogs"
     * @return typeArray as a Post object 
     */
    protected Post scrape(BufferedReader br, String type, String url) {
        Post typeArray = new Post(true);
        this.putData(typeArray, "blogs", this.crawlWordpress(this.query, br));
        return typeArray;
    }

    /**
     * Method to parse the result with Jsoup and extract the blog details from it.
     * The extracted result is put on the blogPost and each blogPost is 
     * added to blogList which is returned as result of type 'PostTimeline'.
     * @return blogList as a PostTimeline object containing each blogPost.
     */
    public PostTimeline crawlWordpress(String blogURL, BufferedReader br) {
        Post blogPost = null;
        PostTimeline blogList = new PostTimeline(this.order);

        Document blogHTML = null;
        Elements articles = null;
        Elements articleList_title = null;
        Elements articleList_content = null;
        Elements articleList_dateTime = null;
        Elements articleList_author = null;
        try {
	        blogHTML = Jsoup.parse(bufferedReaderToString(br));
        } catch (IOException e) {
	        DAO.trace(e);
        }

        articles = blogHTML.getElementsByTag("article");
        for (Element article : articles) {
            blogPost = new Post();
            blogPost.put("blog_url", blogURL);
            // Blog title
	        articleList_title = article.getElementsByClass("entry-title");
	        for (Element blogs : articleList_title) {
		        blogPost.put("title", blogs.text().toString());
	        }
            // Posted On
	        articleList_dateTime = article.getElementsByClass("posted-on");
	        for (Element blogs : articleList_dateTime) {
		        blogPost.put("posted_on", blogs.text().toString());
	        }
            // Author
	        articleList_author = article.getElementsByClass("byline");
	        for (Element blogs : articleList_author) {
		        String author = blogs.text().toString();
		        author = author.substring(author.indexOf(' ') + 1);
		        blogPost.put("author", author);
	        }
            // Content of article
	        articleList_content = article.getElementsByClass("entry-content");
	        for (Element blogs : articleList_content) {
		        blogPost.put("content", blogs.text().toString());
	        }
	        blogList.addPost(blogPost);
        }
        return blogList;
    }
}

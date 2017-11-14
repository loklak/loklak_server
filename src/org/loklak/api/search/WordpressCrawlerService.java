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

    public WordpressCrawlerService() {
        super();
        this.baseUrl = "";
        this.scraperName = "wordpress";
    }

    public WordpressCrawlerService(Map<String, String> _extra) {
        this();
        this.setExtra(_extra);
        this.query = this.getExtraValue("query");
    }

    public WordpressCrawlerService(String _query) {
        this();
        this.query = _query;
        this.setExtraValue("query", this.query);
    }

	@Override
	public String getAPIPath() {
		return "/api/wordpresscrawler.json";
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
        return this.query;
    }

    protected void setParam() {
        if ("".equals(this.getExtraValue("url"))) {
            this.setExtraValue("url", this.query);
        } else {
            this.query  = this.getExtraValue("url");
        }
    }

   protected Post scrape(BufferedReader br, String type, String url) {
        Post typeArray = new Post(true);
        this.putData(typeArray, "blogs", this.crawlWordpress(this.query, br));
        return typeArray;
    }

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

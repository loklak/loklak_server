/**
 *  Wordpress Blog Scraper
 *  By Jigyasa Grover, @jig08
 **/

package org.loklak.harvester;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class WordpressBlogScraper {
	public static void main(String args[]){
		
		String blogURL = "https://loklaknet.wordpress.com/";
		Document blogHTML = null;
		
		Elements articles = null;
		Elements articleList_title = null;
		Elements articleList_content = null;
		Elements articleList_dateTime = null;
		Elements articleList_author = null;

		String[][] blogPosts = new String[100][4];
		
		//blogPosts[][0] = Blog Title
		//blogPosts[][1] = Posted On
		//blogPosts[][2] = Author
		//blogPosts[][3] = Blog Content
		
		Integer numberOfBlogs = 0;
		Integer iterator = 0;
		
		try{
			
			blogHTML = Jsoup.connect(blogURL).get();
			
			articles = blogHTML.getElementsByTag("article");
			
			iterator = 0;
			for(Element article : articles){
				
				articleList_title = article.getElementsByClass("entry-title");				
				for(Element blogs : articleList_title){
					blogPosts[iterator][0] = blogs.text().toString();
				}
				
				articleList_dateTime = article.getElementsByClass("posted-on");				
				for(Element blogs : articleList_dateTime){
					blogPosts[iterator][1] = blogs.text().toString();
				}
				
				articleList_author = article.getElementsByClass("byline");				
				for(Element blogs : articleList_author){
					blogPosts[iterator][2] = blogs.text().toString();
				}
				
				articleList_content = article.getElementsByClass("entry-content");				
				for(Element blogs : articleList_content){
					blogPosts[iterator][3] = blogs.text().toString();
				}
				
				iterator++;
			}
			
			numberOfBlogs = iterator;
			System.out.println("BLOG : " + blogURL);
			for(int k = 0; k<numberOfBlogs; k++){
				System.out.println("\n\nBlog Number " + k + 
							"\n\tTitle: " + blogPosts[k][0] + 
							"\n\tPosted On: " + blogPosts[k][1] +
							"\n\tAuthor: " + blogPosts[k][2] +
							"\n\tContent: " + blogPosts[k][3]);
			}
			
		}catch (IOException e) {
            e.printStackTrace();
        }
	}
}

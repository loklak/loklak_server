/**
 *  Weibo Scraper
 *  By Jigyasa Grover, @jig08
 **/

package org.loklak.harvester;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class WeiboScraper {
	
	public static void main(String args[]){
 
        // Parsing Weibo using JSoup
		
		String q = "Berlin"; 
		//Get the Search Query in "q" here
        Document doc = null;
        String title = null, text = null;
        
        try {
            doc = Jsoup.connect("http://s.weibo.com/weibo/"+q).get();
            title = doc.title();
            text = doc.text();
        } catch (IOException e) {
            e.printStackTrace();
        }
 
        System.out.println("Jsoup Can read HTML page from URL, \ntitle : " + title + "\ntext: " + text);
	}

}

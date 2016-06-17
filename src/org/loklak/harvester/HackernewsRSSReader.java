/**
 *  Hacker News RSS Reader
 *  By Jigyasa Grover, @jig08
 **/

package org.loklak.harvester;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

public class HackernewsRSSReader {
	
	public static void main(String[] args){
		
		String url = "https://news.ycombinator.com/rss";
		@SuppressWarnings("unused")
		JSONObject newsFromHackernews = hackernewsRSSReader(url);
	
    }
	
	@SuppressWarnings({ "unchecked", "static-access" })
	public static JSONObject hackernewsRSSReader(String url){
		 
	        URL feedUrl = null;
			try {
				feedUrl = new URL(url);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
	        
	        SyndFeedInput input = new SyndFeedInput();
	        
	        SyndFeed feed = null;
			try {
				feed = input.build(new XmlReader(feedUrl));
			} catch (Exception e) {
				e.printStackTrace();
			}
	        
	        String[][] result = new String[100][7];
	        //result[][0] = Title
	        //result[][1] = Link
	        //result[][2] = URI
	        //result[][3] = Hash Code
	        //result[][4] = PublishedDate
	        //result[][5] = Updated Date
	        //result[][6] = Description
	        
	        @SuppressWarnings("unused")
			int totalEntries = 0;
	        int i = 0;
	        
	        JSONArray jsonArray = new JSONArray();
	        
	        for (SyndEntry entry : (List<SyndEntry>)feed.getEntries()) {
	        	
	        	result[i][0] = entry.getTitle().toString();
	        	result[i][1] = entry.getLink().toString();
	        	result[i][2] = entry.getUri().toString();
	        	result[i][3] = Integer.toString(entry.hashCode()); 
	        	result[i][4] = entry.getPublishedDate().toString();
	        	result[i][5] = ( (entry.getUpdatedDate() == null) ? ("null") : (entry.getUpdatedDate().toString()) );
	        	result[i][6] = entry.getDescription().toString();
	        	
		        JSONObject jsonObject = new JSONObject();

	        	jsonObject.put("RSS Feed", url);
	        	jsonObject.put("Title", result[i][0]);
	        	jsonObject.put("Link", result[i][1]);
	        	jsonObject.put("URI", result[i][2]);
	        	jsonObject.put("Hash-Code", result[i][3]);
	        	jsonObject.put("Published-Date", result[i][4]);
	        	jsonObject.put("Updated-Date", result[i][5]);
	        	jsonObject.put("Description", result[i][6]);
	        	
	        	jsonArray.put(i, jsonObject);
	        	
	        	i++;
	        }
	        
	        totalEntries = i;
	        
	        /*System.out.println("RSS Feed parsed\n");
	        for(int k=0; k<totalEntries; k++){
	        	System.out.println("\nFeed Number: " + k + 
	        						"\n\tTitle: " + result[k][0] +
	        						"\n\tLink: " + result[k][1] +
	        						"\n\tURI: " + result[k][2] +
	        						"\n\tHash Code: " + result[k][3] +
	        						"\n\tPublished Date: " + result[k][4] +
	        						"\n\tUpdated Link: " + result[k][5] +
	        						"\n\tDescription: " + result[k][6] );       	
	        
	        } */
	        
		//System.out.println(jsonArray);
	    JSONObject rssFeed = new JSONObject();
	    rssFeed.put("Hackernews RSS Feed", jsonArray);
	    System.out.println(rssFeed);
		return rssFeed;
		
	}

}

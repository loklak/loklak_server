/**
 *  Events Crawler
 *  By Jigyasa Grover, @jig08
 **/

package org.loklak.harvester;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class EventsCrawler {
	
	public static void main(String args[]){
		eventBrite("http://www.eventbrite.com/e/canux-2016-tickets-22503907794?aff=ehomecard");
	}
	
	public static JSONObject eventBrite(String url){
		
		Document htmlPage = null;
		Element eventSummary = null;
		String eventName = null;
		
		try{
			htmlPage = Jsoup.connect(url).get();
			
			eventSummary = htmlPage.getElementById("event_header");
			eventName = eventSummary.text();
			
			System.out.println(eventName);
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		//Eventually put all information in the JSON Object
		JSONObject eventInfo = new JSONObject();
		//System.out.println(eventInfo);
		return eventInfo;
	}

}

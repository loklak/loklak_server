/**
 *  Shuoshuo Crawler
 *  By Jigyasa Grover, @jig08
 **/

package org.loklak.harvester;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class ShuoshuoCrawler {
	public static void main(String args[]){
		
		Document shuoshuoHTML = null;
		Element recommendedTalkBox = null;
		Elements recommendedTalksList = null;
		String recommendedTalksResult[] = new String[100];
		Integer numberOfrecommendedTalks = 0;
		Integer i = 0;
		
		try {
			shuoshuoHTML = Jsoup.connect("http://www.qqshuoshuo.com/").get();
			
			recommendedTalkBox = shuoshuoHTML.getElementById("list2");
			recommendedTalksList = recommendedTalkBox.getElementsByTag("li");
			
			i=0;
			for (Element recommendedTalks : recommendedTalksList)
			{
				//System.out.println("\nLine: " + recommendedTalks.text());
				recommendedTalksResult[i] = recommendedTalks.text().toString();
				i++;
			}			
			numberOfrecommendedTalks = i;
			System.out.println("Total Recommended Talks: " + numberOfrecommendedTalks);
			for(int k=0; k<numberOfrecommendedTalks; k++){
				System.out.println("Recommended Talk " + k + ": " + recommendedTalksResult[k]);
			}
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
	}
}

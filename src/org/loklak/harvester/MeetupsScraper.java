/**
 *  Meetups Scraper
 *  By Jigyasa Grover, @jig08
 **/

package org.loklak.harvester;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class MeetupsScraper {
	public static void main(String args[]){
		
		Document meetupHTML = null;
		String meetupGroupName = "Women-Who-Code-Delhi";
		// fetch group name here
		Element groupDescription = null;
		String groupDescriptionString = null;
		Element topicList = null;
		Elements topicListStrings = null;
		String[] topicListArray = new String[100];
		Integer numberOfTopics = 0;
		Element recentMeetupsSection = null;
		Elements recentMeetupsList = null;
		Integer numberOfRecentMeetupsShown = 0;
		Integer i = 0, j = 0;
		String recentMeetupsResult[][] = new String[100][3];
		
		// recentMeetupsResult[i][0] == date && time
		// recentMeetupsResult[i][1] == Attendance && Review
		// recentMeetupsResult[i][2] == Information
				
		try{
			meetupHTML = Jsoup.connect("http://www.meetup.com/" + meetupGroupName).userAgent("Mozilla)").get();
			
			groupDescription = meetupHTML.getElementById("groupDesc");
			groupDescriptionString = groupDescription.text();
			System.out.println(meetupGroupName + "\n\tGroup Description: \n\t\t" + groupDescriptionString);
			
			topicList = meetupHTML.getElementById("topic-box-2012");
			topicListStrings = topicList.getElementsByTag("a");
			
			int p = 0;
			for(Element topicListStringsIterator : topicListStrings){
				topicListArray[p] = topicListStringsIterator.text().toString();
				p++;
			}
			numberOfTopics = p;
			
			System.out.println("\nGroup Topics:");
			for(int l = 0; l<numberOfTopics; l++){
				System.out.println("\n\tTopic Number "+ l + " : " + topicListArray[l]);
			}
			
			recentMeetupsSection = meetupHTML.getElementById("recentMeetups");
			recentMeetupsList = recentMeetupsSection.getElementsByTag("p");
			
			i = 0;
			j = 0;
			
			for(Element recentMeetups : recentMeetupsList ){				
				if(j%3==0){
					j = 0;
					i++;
				}
				
				recentMeetupsResult[i][j] = recentMeetups.text().toString();
				j++;
				
			}
			
			numberOfRecentMeetupsShown = i;
			
			for(int k = 1; k < numberOfRecentMeetupsShown; k++){
				System.out.println("\n\nRecent Meetup Number" + k + " : \n" + 
						"\n\t Date & Time: " + recentMeetupsResult[k][0] + 
						"\n\t Attendance: " + recentMeetupsResult[k][1] + 
						"\n\t Information: " + recentMeetupsResult[k][2]);
			}

		}catch (IOException e) {
            e.printStackTrace();
        }
		
	}
}

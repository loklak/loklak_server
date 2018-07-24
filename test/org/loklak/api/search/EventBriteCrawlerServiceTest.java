package org.loklak.api.search;

import org.loklak.api.search.EventBriteCrawlerService;
import org.loklak.susi.SusiThought;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
/**
* This test file tests org.loklak.api.search.EventBriteCrawlerService.java.
* For testing purpose, a sample test event has been created by Saurabh Srivastava(simsausaurabh).
* Link to test event - https://www.eventbrite.com/e/testevent-tickets-46017636991
*/
public class EventBriteCrawlerServiceTest {

    @Test
    public void apiPathTest() {
        EventBriteCrawlerService eventBriteCrawlerService = new EventBriteCrawlerService();
        assertEquals("/api/eventbritecrawler.json", eventBriteCrawlerService.getAPIPath());
    }

    @Test
    public void eventBriteEventTest() throws NullPointerException {
        EventBriteCrawlerService eventBriteCrawlerService = new EventBriteCrawlerService();
        String eventTestUrl = "https://www.eventbrite.com/e/testevent-tickets-46017636991";
        try {
            SusiThought resultPage = new SusiThought();
            resultPage = eventBriteCrawlerService.crawlEventBrite(eventTestUrl);
            JSONArray jsonArray = resultPage.getData();
            JSONObject creator = jsonArray.getJSONObject(0).getJSONObject("creator");
            String id = creator.getString("id");
            String email = creator.getString("email");
            
            JSONArray social_links = jsonArray.getJSONObject(0).getJSONArray("social_links");
            String fbname = social_links.getJSONObject(0).getString("name");
            String fbid = social_links.getJSONObject(0).getString("id");
            String twname = social_links.getJSONObject(1).getString("name");
            String twid = social_links.getJSONObject(1).getString("id");

            double latitude = jsonArray.getJSONObject(0).getDouble("latitude");
            double latDiff = latitude - 23.158096313476562;
            double longitude = jsonArray.getJSONObject(0).getDouble("longitude");
            double lonDiff = longitude - 72.66976928710938;

            String background_url = jsonArray.getJSONObject(0).getString("background_url");
            String end_time = jsonArray.getJSONObject(0).getString("end_time");
            String description = jsonArray.getJSONObject(0).getString("description");
            String privacy = jsonArray.getJSONObject(0).getString("privacy");
            String type = jsonArray.getJSONObject(0).getString("type");
            String ticket_url = jsonArray.getJSONObject(0).getString("ticket_url");
            String event_url = jsonArray.getJSONObject(0).getString("event_url");
            String start_time = jsonArray.getJSONObject(0).getString("start_time");
            String location_name = jsonArray.getJSONObject(0).getString("location_name");
            String name = jsonArray.getJSONObject(0).getString("name");
            String logo = jsonArray.getJSONObject(0).getString("logo");
            String topic = jsonArray.getJSONObject(0).getString("topic");
            String orgId = jsonArray.getJSONObject(0).getString("id");
            String organizer_name = jsonArray.getJSONObject(0).getString("organizer_name");
            String state = jsonArray.getJSONObject(0).getString("state");

            assertEquals("1", id);
            assertEquals("", email);
            assertEquals("https://img.evbuc.com/https%3A%2F%2Fcdn.evbuc.com%2Fimages%2F44763103%2F256428075899%2F1%2Foriginal.jpg?w=1000&auto=compress&rect=0%2C67%2C400%2C200&s=9018130444506b1cffa6d4bd11b0fddb", background_url);
            assertEquals("Facebook", fbname);
            assertEquals("1", fbid);
            assertEquals("Twitter", twname);
            assertEquals("2", twid);
            assertEquals("2030-05-25T22:00:00", end_time);
            assertEquals("It is a test event made only for testing purpose. Apologies for trouble.", description);
            assertEquals("public", privacy);
            assertEquals("", type);
            assertEquals("https://www.eventbrite.com/e/testevent-tickets-46017636991#tickets", ticket_url);
            assertEquals("https://www.eventbrite.com/e/testevent-tickets-46017636991", event_url);
            assertEquals("2030-05-18T19:00:00", start_time);
            assertEquals("TestEvent by Saurabh Srivastava", name);
            assertEquals("https://img.evbuc.com/https%3A%2F%2Fcdn.evbuc.com%2Fimages%2F44763103%2F256428075899%2F1%2Foriginal.jpg?w=1000&auto=compress&rect=0%2C67%2C400%2C200&s=9018130444506b1cffa6d4bd11b0fddb", logo);
            assertEquals("", topic);
            assertEquals("46017636991", orgId);
            assertEquals("aurabh Srivastava", organizer_name);
            assertEquals("completed", state);
            assertTrue("Not equals", latDiff == 0);
            assertTrue("Not equals", lonDiff == 0);
            assertNotNull(location_name);
        } catch (NullPointerException e) {
            DAO.log("EventBriteCrawlerServiceTest.eventBriteEventTest() failed with a NullPointerException");
        }
    }

    @Test
    public void eventBriteOrganizerTest() throws NullPointerException {
        EventBriteCrawlerService eventBriteCrawlerService = new EventBriteCrawlerService();
        String eventTestUrl = "https://www.eventbrite.com/e/testevent-tickets-46017636991";
        try {
            SusiThought resultPage = new SusiThought();
            resultPage = eventBriteCrawlerService.crawlEventBrite(eventTestUrl);
            JSONArray jsonArray = resultPage.getData();
            JSONObject organizer_details = jsonArray.getJSONObject(1);
            String organizer_contact_info = organizer_details.getString("organizer_contact_info");
            String organizer_link = organizer_details.getString("organizer_link");
            String organizer_profile_link = organizer_details.getString("organizer_profile_link");
            String organizer_name = organizer_details.getString("organizer_name");

            assertEquals("https://www.eventbrite.com/e/testevent-tickets-46017636991#lightbox_contact", organizer_contact_info);
            assertEquals("https://www.eventbrite.com/e/testevent-tickets-46017636991#listing-organizer", organizer_link);
            assertEquals("", organizer_profile_link);
            assertEquals("aurabh Srivastava", organizer_name);
        } catch (NullPointerException e) {
            DAO.log("EventBriteCrawlerServiceTest.eventBriteOrganizerTest() failed with a NullPointerException");
        }
    }

    @Test
    public void eventExtraDetailsTest() throws NullPointerException {
        EventBriteCrawlerService eventBriteCrawlerService = new EventBriteCrawlerService();
        String eventTestUrl = "https://www.eventbrite.com/e/testevent-tickets-46017636991";
        try {
            SusiThought resultPage = new SusiThought();
            resultPage = eventBriteCrawlerService.crawlEventBrite(eventTestUrl);
            JSONArray jsonArray = resultPage.getData();
            JSONArray microlocations = jsonArray.getJSONObject(2).getJSONArray("microlocations");
            JSONArray customForms = jsonArray.getJSONObject(3).getJSONArray("customForms");
            JSONArray sessionTypes = jsonArray.getJSONObject(4).getJSONArray("sessionTypes");
            JSONArray sessions = jsonArray.getJSONObject(5).getJSONArray("sessions");
            JSONArray sponsors = jsonArray.getJSONObject(6).getJSONArray("sponsors");
            JSONArray speakers = jsonArray.getJSONObject(7).getJSONArray("speakers");
            JSONArray tracks = jsonArray.getJSONObject(8).getJSONArray("tracks");

            assertEquals(microlocations.length(), 0);
            assertEquals(customForms.length(), 0);
            assertEquals(sessionTypes.length(), 0);
            assertEquals(sessions.length(), 0);
            assertEquals(sponsors.length(), 0);
            assertEquals(speakers.length(), 0);
            assertEquals(tracks.length(), 0);
        } catch (NullPointerException e) {
            DAO.log("EventBriteCrawlerServiceTest.eventExtraDetailsTest() failed with a NullPointerException");
        }
    }
}

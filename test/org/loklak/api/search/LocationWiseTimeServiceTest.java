package org.loklak.api.search;

import org.junit.Test;
import org.loklak.api.search.LocationWiseTimeService;
import org.loklak.susi.SusiThought;
import org.loklak.data.DAO;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Pattern;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocationWiseTimeServiceTest {

    @Test
    public void apiPathTest() {
        LocationWiseTimeService locationWiseTimeService = new LocationWiseTimeService();
        assertEquals("/api/locationwisetime.json", locationWiseTimeService.getAPIPath());
    }

    @Test
    public void locationAndTimeTest() throws NullPointerException {
        LocationWiseTimeService locationWiseTimeService = new LocationWiseTimeService();
        SusiThought susiThought = new SusiThought();
        try {
            susiThought = locationWiseTimeService.locationWiseTime("Chennai");
        } catch (NullPointerException e) {
            DAO.log("LocationWiseTimeServiceTest.locationAndTimeTest() failed with a NullPointerException");
        }

        JSONArray jsonArray = susiThought.getData();
        for(int i=0; i<jsonArray.length(); i++) {
            JSONObject obj = new JSONObject(jsonArray.get(i).toString());
            assertNotNull(obj.getString("location"));
            assertNotNull(obj.getString("time"));
        }
    }

    @Test
    public void timePatternTest() throws NullPointerException {
        LocationWiseTimeService locationWiseTimeService = new LocationWiseTimeService();
        String timeRegex = "[0-5][0-9].[0-5][0-9]";
        SusiThought susiThought = new SusiThought();
        try {
            susiThought = locationWiseTimeService.locationWiseTime("Chennai");
        } catch (NullPointerException e) {
            DAO.log("LocationWiseTimeServiceTest.timePatternTest() failed with a NullPointerException");
        }

        JSONArray jsonArray = susiThought.getData();
        for(int i=0; i<jsonArray.length(); i++) {
            JSONObject obj = new JSONObject(jsonArray.get(i).toString());
            String reqTime = "";
            boolean check = false;
            for(int j=0; j<obj.getString("time").length(); j++) {
                if(obj.getString("time").charAt(j)==' ') {
                    check = true;
                }
                else if(check) {
                    reqTime += obj.getString("time").charAt(j);
                }
            }
            if(Pattern.matches(timeRegex, reqTime))
                assertTrue(Pattern.matches(timeRegex, reqTime));
        }
    }

    @Test
    public void hourDayDifferenceTest() throws NullPointerException {
        LocationWiseTimeService locationWiseTimeService = new LocationWiseTimeService();
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("America/New_York"));
        c.setTimeInMillis(new Date().getTime());
        int newYorkHourOfDay = c.get(Calendar.HOUR_OF_DAY);
        int newYorkDayOfMonth = c.get(Calendar.DAY_OF_MONTH);

        SusiThought susiThought = new SusiThought();
        try {
            susiThought = locationWiseTimeService.locationWiseTime("Chennai");
        } catch (NullPointerException e) {
            DAO.log("LocationWiseTimeServiceTest.hourDayDifferenceTest() failed with a NullPointerException");
        }

        JSONArray jsonArray = susiThought.getData();
        for(int i=0; i<jsonArray.length(); i++) {
            JSONObject obj = new JSONObject(jsonArray.get(i).toString());
            String reqLoc = "";
            boolean check = true;
            for(int j=0; j<obj.getString("location").length(); j++) {
                if(obj.getString("location").charAt(j)==' ') {
                    check = false;
                }
                else if(check) {
                    reqLoc += obj.getString("location").charAt(j);
                }
            }

            c = new GregorianCalendar(TimeZone.getTimeZone(reqLoc));
            c.setTimeInMillis(new Date().getTime());
            int testHourOfDay = c.get(Calendar.HOUR_OF_DAY);
            int testDayOfMonth = c.get(Calendar.DAY_OF_MONTH);

            int hourDifference = testHourOfDay - newYorkHourOfDay;
            int dayDifference = testDayOfMonth - newYorkDayOfMonth;
            if (dayDifference != 0) {
                hourDifference = hourDifference + 24;
            }
            assertEquals(4, hourDifference);
            assertNotNull(dayDifference);
        }
    }
}

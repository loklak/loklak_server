/**
 *  Time and Date Service
 *  Copyright 20.07.2016 by Jigyasa Grover, @jig08
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
 */

package org.loklak.api.search;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.susi.SusiThought;
import org.loklak.tools.storage.JSONObjectWithDefault;

public class TimeAndDateService extends AbstractAPIHandler implements APIHandler {

    private static final long serialVersionUID = 6808423132726076271L;

    @Override
    public String getAPIPath() {
        return "/api/timeanddate.json";
    }

    @Override
    public BaseUserRole getMinimalBaseUserRole() {
        return BaseUserRole.ANONYMOUS;
    }

    @Override
    public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
        return null;
    }

    public JSONObject serviceImpl(
            Query call,
            HttpServletResponse response,
            Authorization rights,
            JSONObjectWithDefault permissions
    ) throws APIException {
        return timeAndDate();
    }

    public static SusiThought timeAndDate() {

        JSONObject timeAndDate = new JSONObject();

        Date time_and_date = new Date();
        String time_and_date_UTC = timeToUTC(time_and_date).toString().replaceAll(
                time_and_date.toString().substring(20,23),
                "UTC"
        );

        timeAndDate.put("local_time_and_date", time_and_date.toString());
        timeAndDate.put("UTC_time_and_date", time_and_date_UTC);
        timeAndDate.put("time_diff", timeDiff(time_and_date));

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(timeAndDate);

        SusiThought result = new SusiThought();
        result.setData(jsonArray);
        return result;
    }

    /*
     * Convert localzone time to UTC zone time
    */
    private static Date timeToUTC(Date date) {

        Date dateUtc;
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        
        //Local time zone   
        SimpleDateFormat dateFormatLocal = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");

        try {
           //Time in GMT
           dateUtc = dateFormatLocal.parse(dateFormatGmt.format(date));
        } catch (ParseException e) {
            DAO.severe(e);
            dateUtc = new Date();
        }
        return dateUtc;
    }

    /*
     * Return difference between the local time and UTC time in hours
    */
    private static String timeDiff(Date timeLocal) {
    
        long diff = timeLocal.getTime() - timeToUTC(timeLocal).getTime();
        long diffMin = diff / (60 * 1000) % 60;
        long diffHrs = diff / (60 * 60 * 1000) % 24;
        String min = String.format("%02d", diffMin).replace("-", "");
        String hrs = String.format("%02d", diffHrs);

        String diffTime = hrs + ":" + min;//.replace("-","");
        if (diffHrs > 0) {
            diffTime = "+" + diffTime;
        }
        return diffTime;
    }

}

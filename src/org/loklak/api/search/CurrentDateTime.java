/**
 *  Current Date and Time Service
 *  Copyright 01.08.2016 by Jigyasa Grover, @jig08
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

import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.susi.SusiThought;
import org.loklak.tools.storage.JSONObjectWithDefault;

public class CurrentDateTime extends AbstractAPIHandler implements APIHandler{

	private static final long serialVersionUID = 7187735935382105290L;

	@Override
	public String getAPIPath() {
		return "/api/currentdateandtime.json";
	}

	@Override
	public BaseUserRole getMinimalBaseUserRole() {
		return BaseUserRole.ANONYMOUS;
	}

	@Override
	public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
		return null;
	}

	@Override
	public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights,
			JSONObjectWithDefault permissions) throws APIException {
		return currentDateAndTime();
	}

	public static SusiThought currentDateAndTime() {
		JSONObject currentDateAndTime = new JSONObject();
		
		Date time_and_date = new Date();
		
		String day = time_and_date.toString().substring(0, 3);
		currentDateAndTime.put("day", day);
		
		String month = time_and_date.toString().substring(4, 7);
		currentDateAndTime.put("month", month);
		
		String date = time_and_date.toString().substring(8, 10);
		currentDateAndTime.put("date", date);
		
		String hours = time_and_date.toString().substring(11, 13);
		currentDateAndTime.put("hours", hours);
		
		String minutes = time_and_date.toString().substring(14, 16);
		currentDateAndTime.put("minutes", minutes);
		
		String seconds = time_and_date.toString().substring(17, 19);
		currentDateAndTime.put("seconds", seconds);
		
		String timezone = time_and_date.toString().substring(20, 23);
		currentDateAndTime.put("timezone", timezone);
		
		String year = time_and_date.toString().substring(24, 28);
		currentDateAndTime.put("year", year);
		
		
		JSONArray jsonArray = new JSONArray();
		jsonArray.put(currentDateAndTime);
		
		SusiThought result = new SusiThought();
		result.setData(jsonArray);
		return result;
	}

}

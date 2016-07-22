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

public abstract class TimeAndDateService extends AbstractAPIHandler implements APIHandler {

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

	public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights, JSONObjectWithDefault permissions)
			throws APIException {
		return timeAndDate();
	}

	public static SusiThought timeAndDate() {
		
		JSONObject timeAndDate = new JSONObject();
		
		Date time_and_date = new Date();
		
		String day = time_and_date.toString().substring(0, 3);
		timeAndDate.put("day", day);
		
		String month = time_and_date.toString().substring(4, 7);
		timeAndDate.put("month", month);
		
		String date = time_and_date.toString().substring(8, 10);
		timeAndDate.put("date", date);
		
		String hours = time_and_date.toString().substring(11, 13);
		timeAndDate.put("hours", hours);
		
		String minutes = time_and_date.toString().substring(14, 16);
		timeAndDate.put("minutes", minutes);
		
		String seconds = time_and_date.toString().substring(17, 19);
		timeAndDate.put("seconds", seconds);
		
		String timezone = time_and_date.toString().substring(20, 23);
		timeAndDate.put("timezone", timezone);
		
		String year = time_and_date.toString().substring(24, 28);
		timeAndDate.put("year", year);
		
		
		JSONArray jsonArray = new JSONArray();
		jsonArray.put(timeAndDate);
		
		SusiThought result = new SusiThought();
		result.setData(jsonArray);
		return result;
	}
}

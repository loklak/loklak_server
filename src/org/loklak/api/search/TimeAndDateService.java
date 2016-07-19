/**
 *  Time And Date
 *  Copyright 19.07.2016 by Jigyasa Grover, @jig08
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
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.api.search;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
		// TODO Auto-generated method stub
		return null;
	}

	public JSONObject serviceImpl(Query call, Authorization rights, JSONObjectWithDefault permissions)
			throws APIException {
		String url = call.get("url", "");
		return timeAndDate(url);
	}

	public static SusiThought timeAndDate(String url) {
		Document HTMLPage = null;

		try {
			HTMLPage = Jsoup.connect("http://www.timeanddate.com/").get();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String current_time = HTMLPage.select("a#clk_box").text();
		String day = HTMLPage.select("span#ij1").text();
		String date = HTMLPage.select("span#ij2").text();

		JSONObject result = new JSONObject();
		result.put("current_time", current_time);
		result.put("day", day);
		result.put("date", date);

		JSONArray resultArray = new JSONArray();
		resultArray.put(result);

		SusiThought json = new SusiThought();
		json.setData(resultArray);
		return json;
	}

}

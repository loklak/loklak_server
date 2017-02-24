package org.loklak.api.cms;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.tools.storage.JSONObjectWithDefault;

public class TopMenuService extends AbstractAPIHandler implements APIHandler {

	private static final long serialVersionUID = 1839868262296635665L;

	@Override
	public BaseUserRole getMinimalBaseUserRole() {
		return BaseUserRole.ANONYMOUS;
	}

	@Override
	public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
		return null;
	}

	@Override
	public String getAPIPath() {
		return "/cms/topmenu.json";
	}

	@Override
	public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights,
			final JSONObjectWithDefault permissions) {

		int limited_count = (int) DAO.getConfig("download.limited.count", (long) Integer.MAX_VALUE);

		JSONObject json = new JSONObject(true);
		JSONArray topmenu = new JSONArray().put(new JSONObject().put("Search", "http://loklak.net"))
				.put(new JSONObject().put("Apps", "http://apps.loklak.org"))
				.put(new JSONObject().put("Developers", "http://dev.loklak.org"))
				.put(new JSONObject().put("API", "api.html"));
		if (limited_count > 0)
			topmenu.put(new JSONObject().put("Dumps", "dump.html"));
		topmenu.put(new JSONObject().put("About", "about.html"));
		topmenu.put(new JSONObject().put("Blog", "http://blog.loklak.net"));
		json.put("items", topmenu);

		// modify caching
		json.put("$EXPIRES", 600);
		return json;
	}
}

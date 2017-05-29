/**
 *  AccountServlet
 *  Copyright 27.05.2015 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.api.cms;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.objects.AccountEntry;
import org.loklak.objects.UserEntry;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.tools.storage.JSONObjectWithDefault;


public class AccountService extends AbstractAPIHandler implements APIHandler {

    private static final long serialVersionUID = 8578478303032749879L;

    @Override
    public BaseUserRole getMinimalBaseUserRole() {
        return BaseUserRole.ADMIN;
    }

    @Override
    public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
        return null;
    }

    @Override
    public String getAPIPath() {
        return "/api/account.json";
    }

    @Override
    public JSONObject serviceImpl(
        Query post,
        HttpServletResponse response,
        Authorization rights,
        final JSONObjectWithDefault permissions) throws APIException {

        // parameters
        boolean update = "update".equals(post.get("action", ""));
        String screenName = post.get("screen_name", "");

        String data = post.get("data", "");
        if (update) {
            if (data == null || data.length() == 0) {
                throw new APIException(400, "your request does not contain a data object.");
            }

            JSONObject json = new JSONObject(data);
            Object accountsObj = json.has("accounts") ? json.get("accounts") : null;
            JSONArray accounts;
            if (accountsObj != null && accountsObj instanceof JSONArray) {
                accounts = (JSONArray) accountsObj;
            } else {
                accounts = new JSONArray();
                accounts.put(json);
            }
            for (Object accountObj: accounts) {
                if (accountObj == null) {
                    continue;
                }
                try {
                    AccountEntry a = new AccountEntry((JSONObject) accountObj);
                    DAO.writeAccount(a, true);
                } catch (IOException e) {
                    throw new APIException(400,
                        "submitted data is not well-formed: " + e.getMessage());
                }
            }
            if (accounts.length() == 1) {
                screenName = (String) ((JSONObject) accounts.iterator().next()).get("screen_name");
            }
        }

        UserEntry userEntry = DAO.searchLocalUserByScreenName(screenName);

        // generate json
        JSONObject m = new JSONObject(true);
        JSONObject metadata = new JSONObject(true);
        metadata.put("count", userEntry == null ? "0" : "1");
        metadata.put("client", post.getClientHost());
        m.put("search_metadata", metadata);

        AccountEntry accountEntry = DAO.searchLocalAccount(screenName);

        // create a list of accounts. Why a list? Because the same user may
        // have accounts for several services.
        JSONArray accounts = new JSONArray();
        if (accountEntry == null) {
            if (userEntry != null) {
                accounts.put(AccountEntry.toEmptyAccountJson(userEntry));
            }
        } else {
            accounts.put(accountEntry.toJSON(userEntry));
        }
        m.put("accounts", accounts);

        return m;
    }
}

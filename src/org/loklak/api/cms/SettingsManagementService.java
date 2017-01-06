package org.loklak.api.cms;

import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.server.Authorization;
import org.loklak.server.APIException;
import org.loklak.tools.storage.JSONObjectWithDefault;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class SettingsManagementService extends AbstractAPIHandler implements APIHandler {

    @Override
    public String getAPIPath() {
        return "/api/settings-management.json";
    }

    @Override
    public BaseUserRole getMinimalBaseUserRole() {
        return BaseUserRole.ADMIN;
    }

    @Override
    public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
        JSONObject result = new JSONObject();

        boolean answer = false;

        // Only ADMINs are allowed to modify the settings
        if (baseUserRole == BaseUserRole.ADMIN)
            answer = true;

        result.put("allow_modifications_of_settings", answer);

        return result;
    }

    @Override
    public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights,
                                  JSONObjectWithDefault permissions) throws APIException {
        JSONObject result = new JSONObject();

        if (!DAO.getConfigKeys().containsAll(call.getKeys())) {
            throw new APIException(503, "Invalid configuration key");
        }

        Map<String, String> appliedSettings = new HashMap<>();

        // Apply settings
        for (String key : call.getKeys()) {
            String value = call.get(key, "");
            DAO.setConfig(key, value);
            appliedSettings.put(key, value);
        }

        result.put("settings", appliedSettings);

        result.put("message", "New settings are successfully applied");

        return result;
    }
}

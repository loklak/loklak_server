package org.loklak.api.search;

import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.harvester.TwitterScraper;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.tools.storage.JSONObjectWithDefault;


public class VideoUrlService extends AbstractAPIHandler implements APIHandler {

    /**
     * 
     */
    private static final long serialVersionUID = -6766893925839806345L;
    private static final Pattern twitterUrlPattern = Pattern.compile("https?://(www\\.)?twitter.com(/.*?/status/[0-9]+)/?");
    private static final int maxVideoPerRequest = DAO.getConfig("videoUrlService.maxVideos", 20);

    @Override
    public String getAPIPath() {
        return "/api/videoUrlService.json";
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
    public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights, JSONObjectWithDefault permissions) throws APIException {
        String []ids = call.getRequest().getParameterValues("id");

        // Remove duplicates
        HashSet<String> uniqueIds = new HashSet<String>(Arrays.asList(ids));
        ids = uniqueIds.toArray(new String[uniqueIds.size()]);

        JSONObject retObject = new JSONObject(true);
        retObject.put("metadata", getMetadata(ids));
        JSONObject videos = new JSONObject(true);
        int processed = 0;
        for (String id : ids) {
            if (processed > maxVideoPerRequest) {  // May take lot of time otherwise
                videos.put(id, handleQuotaExhausted());
            }
            Matcher m;
            boolean matched = false;
            m = twitterUrlPattern.matcher(id);
            if (m.find()) {
                matched = true;
                videos.put(id, handleTwitterVideo(m.group(2)));  // Group 2 is "/<user>/status/<id>"
            }
            if (!matched) {
                videos.put(id, handleNoMatchingService());
            } else {
                processed++;
            }
        }
        retObject.put("videos", videos);
        return retObject;
    }

    private static JSONObject getMetadata(String[] ids) {
        JSONArray arr = new JSONArray();
        for (String id : ids) {
            arr.put(id);
        }
        JSONObject meta = new JSONObject(true);
        meta.put("ids", arr);
        return meta;
    }

    private static JSONObject handleTwitterVideo(String id) {
        String[] videos = TwitterScraper.fetchTwitterVideos(id);
        JSONArray links = new JSONArray();
        for (String video : videos) {
            links.put(video);
        }
        return handleGeneric(links, "twitter", "No video found for the given Tweet");
    }

    private static JSONObject handleGeneric(JSONArray links, String service, String noVideoMessage) {
        JSONObject ret = new JSONObject(true);
        ret.put("service", service);
        if (links.length() > 0) {
            ret.put("status", 0);
            ret.put("message", "OK");
            ret.put("links", links);
        } else {
            ret.put("status", 2);
            ret.put("message", noVideoMessage);
        }
        return ret;
    }

    private static JSONObject handleNoMatchingService() {
        JSONObject ret = new JSONObject(true);
        ret.put("status", 1);
        ret.put("message", "No matching service found.");
        return ret;
    }

    private static JSONObject handleQuotaExhausted() {
        JSONObject ret = new JSONObject(true);
        ret.put("status", 3);
        ret.put("message", "Exceeded quota of " + maxVideoPerRequest + " videos for this requests");
        return ret;
    }

}

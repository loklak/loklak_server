package org.loklak.api.iot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.harvester.HarvestingFrequency;
import org.loklak.objects.ImportProfileEntry;
import org.loklak.objects.MessageEntry;
import org.loklak.objects.SourceType;
import org.loklak.objects.Timeline;
import org.loklak.objects.UserEntry;
import org.loklak.server.Query;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class PushServletHelper {

    /* Fields that can be updated */
    public static final String[] FIELDS_TO_COMPARE =
    {
        "link",
        "text" // can embed rich content
    };

    public static PushReport saveMessagesAndImportProfile(
            JSONArray messages, int fileHash, Query post,
            SourceType sourceType, String screenName) throws IOException {
        PushReport report = new PushReport();
        List<String> importedMsgIds = new ArrayList<>();
        for (Object message_obj : messages) {
            JSONObject message = (JSONObject) message_obj;
            message.put("screen_name", screenName);
            JSONObject user = (JSONObject) message.remove("user");
            if (user != null) user.put("screen_name", screenName);
            MessageEntry messageEntry = new MessageEntry(message);
            UserEntry userEntry = new UserEntry(user != null ? user : new JSONObject());
            boolean successful;
            report.incrementRecordCount();
            try {
                DAO.MessageWrapper mw = new DAO.MessageWrapper(messageEntry, userEntry, true);
                successful = DAO.writeMessage(mw);
            } catch (Exception e) {
                e.printStackTrace();
                report.incrementErrorCount();
                continue;
            }
            if (successful) {
                report.incrementNewCount();
                importedMsgIds.add((String) message.get("id_str"));
            } else {
                report.incrementKnownCount((String) message.get("id_str"));
            }
        }

        if (report.getNewCount() > 0 ) {
            ImportProfileEntry importProfileEntry = saveImportProfile(fileHash, post, sourceType, screenName, importedMsgIds);
            report.setImportProfile(importProfileEntry);
        }

        return report;
    }

    protected static ImportProfileEntry saveImportProfile(int fileHash, Query post, SourceType sourceType, String screenName, List<String> importedMsgIds) throws IOException {
        ImportProfileEntry importProfileEntry ;
        JSONObject profile = new JSONObject();
        profile.put("client_host", post.getClientHost());
        profile.put("imported", importedMsgIds);
        profile.put("importer", screenName);
        String harvesting_freq = post.get("harvesting_freq", "");

        // optional parameter 'public' to
        String public_profile = post.get("public", "");
        ImportProfileEntry.PrivacyStatus privacyStatus;
        if ("".equals(public_profile) || !"true".equals(public_profile)){
            privacyStatus = ImportProfileEntry.PrivacyStatus.PRIVATE;
        } else {
            privacyStatus = ImportProfileEntry.PrivacyStatus.PUBLIC;
        }

        if (!"".equals(harvesting_freq)) {
            try {
                profile.put("harvesting_freq", HarvestingFrequency.valueOf(Integer.parseInt(harvesting_freq)).getFrequency());
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                throw new IOException("Unsupported 'harvesting_freq' parameter value : " + harvesting_freq);
            }
        } else {
            profile.put("harvesting_freq", HarvestingFrequency.NEVER.getFrequency());
        }
        String lifetime_str = post.get("lifetime", "");
        if (!"".equals(lifetime_str)) {
            long lifetime;
            try {
                lifetime = Long.parseLong(lifetime_str);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                throw new IOException("Invalid lifetime parameter (must be an integer) : " + lifetime_str);
            }
            profile.put("lifetime", lifetime);
        } else {
            profile.put("lifetime", Integer.MAX_VALUE);
        }
        profile.put("source_url", post.get("url", ""));
        profile.put("source_type", sourceType.name());
        profile.put("source_hash", fileHash);
        profile.put("id_str", computeImportProfileId(profile, fileHash));
        Date currentDate = new Date();
        profile.put("created_at" , currentDate);
        profile.put("last_modified", currentDate);
        profile.put("last_harvested", currentDate);
        profile.put("privacy_status", privacyStatus.name());
        List<String> sharers = new ArrayList<>();
        sharers.add(screenName);
        profile.put("sharers", sharers);
        try {
            importProfileEntry = new ImportProfileEntry(profile);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Unable to create import profile : " + e.getMessage());
        }
        boolean success = DAO.writeImportProfile(importProfileEntry, true);
        if (!success) {
            DAO.log("Error saving import profile from " + post.getClientHost());
            throw new IOException("Unable to save import profile : " + importProfileEntry);
        }
        return importProfileEntry;
    }

    public static String buildJSONResponse(String callback, PushReport pushReport) throws IOException {

        // generate json
        JSONObject json = new JSONObject(true);
        json.put("status", "ok");
        json.put("records", pushReport.getRecordCount());
        json.put("new", pushReport.getNewCount());
        json.put("known", pushReport.getKnownCount());
        json.put("knownIds", pushReport.getKnownMessageIds());
        json.put("error", pushReport.getErrorCount());
        ImportProfileEntry importProfile = pushReport.getImportProfile();
        if (importProfile != null) json.put("importProfile", importProfile.toJSON());
        json.put("message", "pushed");


        // build result
        String result = "";
        boolean jsonp = callback != null && callback.length() > 0;
        if (jsonp) result += callback + "(";
        result += json.toString(2);
        if (jsonp) result += ");";

        return result;
    }

    private static String computeImportProfileId(JSONObject importProfile, int fileHash) {
        String importer = (String) importProfile.get("importer");
        String source_url = (String) importProfile.get("source_url");
        return source_url + "_" + importer + "_" + fileHash;
    }

    public static String checkMessageExistence(JSONObject message) {
        String source_type = (String) message.get("source_type");
        JSONArray location_point = message.getJSONArray("location_point");
        Double latitude = (Double) location_point.get(0);
        Double longitude = (Double) location_point.get(1);
        String query = "/source_type=" + source_type + " /location=" + latitude + "," + longitude;
        // search only latest message
        DAO.SearchLocalMessages search = new DAO.SearchLocalMessages(query, Timeline.Order.CREATED_AT, 0, 1, 0);
        Iterator<MessageEntry> it = search.timeline.iterator();
        while (it.hasNext()) {
            MessageEntry messageEntry = it.next();
            if (compareMessage(messageEntry.toJSON(), message)) {
                return messageEntry.getIdStr();
            }
        }
        return null;
    }

    private static boolean compareMessage(JSONObject m1, JSONObject m2) {
        for (String field : FIELDS_TO_COMPARE) {
            if (!m1.has(field) && m2.has(field)) return false;
            if (m1.has(field) && !m2.has(field)) return false;
            if (!m1.has(field) && !m2.has(field)) continue;
            if (!m1.get(field).equals(m2.get(field))) return false;
        }
        return true;
    }

    public static String computeMessageId(JSONObject message, SourceType sourceType) throws Exception {
        JSONArray location = message.getJSONArray("location_point");
        if (location == null) {
            throw new Exception("location_point not found");
        }

        String longitude, latitude;
        try {
            Object rawLon = location.get(1);
            longitude = rawLon instanceof Integer ? Integer.toString((Integer) rawLon)
                    : (rawLon instanceof Double ? Double.toString((Double) rawLon) : (String) rawLon);
            Object rawLat = location.get(0);
            latitude = rawLat instanceof Integer ? Integer.toString((Integer) rawLat)
                    : (rawLat instanceof Double ? Double.toString((Double) rawLat) : (String) rawLat);
        } catch (ClassCastException e) {
            throw new ClassCastException("Unable to extract lat, lon from location_point " + e.getMessage());
        }
        // Modification time = 'mtime' value. If not found, take current time
        Object mtime = message.get("mtime");
        if (mtime == null) {
            mtime = Long.toString(System.currentTimeMillis());
            message.put("mtime", mtime);
        }

        // Id format : <source_type>_<lat>_<lon>_<mtime>
        return sourceType.name() + "_" + latitude + "_" + longitude + "_" + mtime;
    }

}

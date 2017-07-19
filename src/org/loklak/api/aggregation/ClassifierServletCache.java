package org.loklak.api.aggregation;


import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.data.DAO;

public class ClassifierServletCache {

    private static HashMap<String, JSONObjectWrapper> cacheMap = new HashMap<>();

    private static class JSONObjectWrapper {
        private static long timeout = DAO.getConfig("classifierservlet.cache.timeout", 300000);  // Default 5 mins
        private JSONObject json;

        private long expiry;

        JSONObjectWrapper(JSONObject json) {
            this.json = json;
            this.expiry = System.currentTimeMillis() + timeout;
        }
        private boolean isExpired() {
            return System.currentTimeMillis() > this.expiry;
        }

    }

    private static String getKey(String index, String classifier, String sinceDate, String untilDate) {
        return index + "::::"
            + classifier + "::::"
            + (sinceDate == null ? "" : sinceDate) + "::::"
            + (untilDate == null ? "" : untilDate);
    }

    public static JSONObject getOrCreate(String index, String classifier, String sinceDate, String untilDate, List<String> classes) {
        return getOrCreate(index, classifier, sinceDate, untilDate, classes, null);
    }


    public static JSONObject getOrCreate(String index, String classifier, String sinceDate, String untilDate, List<String> classes, List<String> countries) {
        String key = getKey(index, classifier, sinceDate, untilDate);
        if (cacheMap.keySet().contains(key)) {
            JSONObjectWrapper jw = cacheMap.get(key);
            if (!jw.isExpired()) {
                return filter(jw.json, classes, countries);
            }
        }
        JSONObject freshCache = getFromElasticsearch(index, classifier, sinceDate, untilDate);
        cacheMap.put(key, new JSONObjectWrapper(freshCache));
        return filter(freshCache, classes, countries);
    }

    private static JSONObject getFromElasticsearch(String index, String classifier, String sinceDate, String untilDate) {
        List<String> classes = ClassifierServlet.getAllowedClassifiers().get(classifier);
        HashMap<String, HashMap<String, HashMap<String, Double>>> result = DAO.elasticsearch_client.classifierScoreForCountry(index, "classifier_" + classifier, classes, sinceDate, untilDate);
        return ClassifierServlet.getAggregationsJsonByCountry(result);
    }

    private static JSONObject filter(JSONObject json, List<String> classes, List<String> countries) {
        if (countries == null) {
            return filter(json, classes);
        }
        JSONObject retJson = new JSONObject(true);
        for (String key : json.keySet()) {
            JSONArray value = filterInnerClasses(json.getJSONArray(key), classes);
            if ("GLOBAL".equals(key) || countries.contains(key)) {
                retJson.put(key, value);
            }
        }
        return retJson;
    }

    private static JSONObject filter(JSONObject json, List<String> classes) {
        JSONObject retJson = new JSONObject(true);
        for (String key : json.keySet()) {
            JSONArray value = filterInnerClasses(json.getJSONArray(key), classes);
            retJson.put(key, value);
        }
        return retJson;
    }

    private static JSONArray filterInnerClasses(JSONArray json, List<String> classes) {
        JSONArray retJson = new JSONArray();
        for (int i = 0; i < json.length() ; i++) {
            JSONObject classDetails = (JSONObject) json.get(i);
            if (classes.contains(classDetails.getString("class"))) {
                retJson.put(classDetails);
            }
        }
        return retJson;
    }

}

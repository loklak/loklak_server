package org.loklak.api.aggregation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.loklak.tools.storage.JSONObjectWithDefault;


public class ClassifierServlet extends AbstractAPIHandler implements APIHandler {

    public Map<String, List<String>> getAllowedClassifiers() {

        // Emotion classifier
        List<String> emotionClasses = new ArrayList<>();
        emotionClasses.add("joy");
        emotionClasses.add("trust");
        emotionClasses.add("fear");
        emotionClasses.add("surprise");
        emotionClasses.add("sadness");
        emotionClasses.add("disgust");
        emotionClasses.add("anger");
        emotionClasses.add("anticipation");

        // Profanity classifier
        List<String> profanityClasses = new ArrayList<>();
        profanityClasses.add("swear");
        profanityClasses.add("sex");
        profanityClasses.add("leet");
        profanityClasses.add("troll");

        // Language classifier
        List<String> languageClasses = new ArrayList<>();
        languageClasses.add("english");
        languageClasses.add("german");
        languageClasses.add("french");
        languageClasses.add("spanish");
        languageClasses.add("dutch");

        Map<String, List<String>> classifiers = new HashMap<>();
        classifiers.put("emotion", emotionClasses);
        classifiers.put("profanity", profanityClasses);
        classifiers.put("language", languageClasses);

        return classifiers;
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
        // evaluate get parameters
        String callback = call.get("callback", "");
        boolean jsonp = callback != null && callback.length() > 0;
        boolean minified = call.get("minified", false);

        // Get class details
        HashMap<String, List<String>> classInformation = (HashMap<String, List<String>>) getAllowedClassifiers();

        // Classifier and classes
        String classifier = call.get("classifier", "").toLowerCase();
        String[] classes = call.get("classes", "").toLowerCase().split(",");
        String[] countries = call.get("countries", "").toLowerCase().split(",");
        boolean fetchAllClasses = call.get("all", false);
        if (fetchAllClasses) {
            List<String> allClasses = classInformation.getOrDefault(classifier, new ArrayList<>());
            classes = allClasses.toArray(new String[classes.length]);
        }

        // Dates
        String sinceDate = call.get("since", null);
        String untilDate = call.get("until", null);

        // Build response
        call.setResponse(response, jsonp ? "application/javascript" : "application/json");
        JSONObject retMessage = new JSONObject(true);

        // Put info if not minified
        if (!minified) {
            retMessage.put("info", getCompleteInfo(classInformation));
        }

        // Put metadata
        retMessage.put("metadata", getMetadata(classifier, classes, fetchAllClasses, call));

        try {
            List<String> countryList = Arrays.asList(countries);
            HashMap<String, HashMap<String, HashMap<String, Double>>> result;
            if (countryList.contains("all")) {
                result = DAO.elasticsearch_client.classifierScoreForCountry("messages", "classifier_" + classifier, Arrays.asList(classes), sinceDate, untilDate);
            } else {
                result = DAO.elasticsearch_client.classifierScoreForCountry("messages", "classifier_" + classifier, Arrays.asList(classes), sinceDate, untilDate, countryList);
            }
            // Put aggregation data
            retMessage.put("aggregations", getAggregationsJsonByCountry(result));
        } catch (Exception e) {
            DAO.severe("Unable to handle aggregation request", e);
            throw new APIException(400, "Unable to parse the provided date");
        }

        return retMessage;
    }

    private JSONObject getCompleteInfo(HashMap<String, List<String>> classInformation) {
        JSONObject info = new JSONObject(true);
        info.put("README_0", "This is the aggregation result for your request.");
        info.put("README_1",
            "Parameters: classifier=(emotion|profanity|language), classes=<comma separated class list>,"
            + " all=(true|false) (enables results for all available classes) minified=(true|false)");
        JSONArray classesArray = new JSONArray();
        for (HashMap.Entry<String, List<String>> entry : classInformation.entrySet()) {
            JSONObject classifierJson = new JSONObject(true);
            classifierJson.put("name", entry.getKey());
            JSONArray classesJson = new JSONArray();
            for (String cls: entry.getValue()) {
                classesJson.put(cls);
            }
            classifierJson.put("classes", classesJson);
            classesArray.put(classifierJson);
        }
        info.put("classifiers_allowed", classesArray);
        return info;
    }

    private JSONObject getMetadata(String classifier, String[] classes, boolean fetchAllClasses, Query post) {
        JSONObject metadata = new JSONObject(true);
        metadata.put("classifier", classifier);
        metadata.put("classes", Arrays.toString(classes).substring(1, Arrays.toString(classes).length() - 1));
        metadata.put("since", post.get("since", null));
        metadata.put("until", post.get("until", null));
        metadata.put("all", fetchAllClasses ? "true" : "false");
        metadata.put("client", post.getClientHost());
        metadata.put("time", System.currentTimeMillis() - post.getAccessTime());
        metadata.put("servicereduction", post.isDoS_servicereduction() ? "true" : "false");
        return metadata;
    }

    private JSONObject getAggregationsJsonByCountry(HashMap<String, HashMap<String, HashMap<String, Double>>> result) {
        JSONObject aggregations = new JSONObject();
        for (HashMap.Entry<String, HashMap<String, HashMap<String, Double>>> entry : result.entrySet()) {
            aggregations.put(entry.getKey(), getAggregationsJson(entry.getValue()));
        }
        return aggregations;
    }

    private JSONArray getAggregationsJson(HashMap<String, HashMap<String, Double>> result) {
        JSONArray aggregations = new JSONArray();
        for (HashMap.Entry<String, HashMap<String, Double>> entry: result.entrySet()) {
            JSONObject obj = new JSONObject(true);
            obj.put("class", entry.getKey());
            obj.put("count", entry.getValue().get("count"));
            obj.put("sum", entry.getValue().get("sum"));
            obj.put("avg", entry.getValue().get("avg"));
            aggregations.put(obj);
        }
        return aggregations;
    }

    @Override
    public String getAPIPath() {
        return "/api/classifier.json";
    }
}

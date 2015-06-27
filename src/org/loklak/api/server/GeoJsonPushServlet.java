package org.loklak.api.server;

import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.loklak.data.DAO;
import org.loklak.data.MessageEntry;
import org.loklak.data.ProviderType;
import org.loklak.data.UserEntry;
import org.loklak.harvester.SourceType;
import org.loklak.tools.UTF8;
import twitter4j.JSONException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

public class GeoJsonPushServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.sendError(400, "your must call this with HTTP POST");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        RemoteAccess.Post post = RemoteAccess.evaluate(request);

        // manage DoS
        if (post.isDoS_blackout()) {response.sendError(503, "your request frequency is too high"); return;}

        Map<String, byte[]> m = RemoteAccess.getPostMap(request);
        String url = UTF8.String(m.get("url"));
        String mapType = UTF8.String(m.get("map_type"));
        String callback = UTF8.String(m.get("callback"));
        boolean jsonp = callback != null && callback.length() > 0;

        if (url == null || url.length() == 0) {response.sendError(400, "your request does not contain an url to your data object"); return;}

        // parse json retrieved from url
        List<Map<String, Object>> features;
        try {
            String jsonText = readJsonFromUrl(url);
            XContentParser parser = JsonXContent.jsonXContent.createParser(jsonText);
            Map<String, Object> map = parser == null ? null : parser.map();
            Object features_obj = map.get("features");
            features = features_obj instanceof List<?> ? (List<Map<String, Object>>) features_obj : null;
        } catch (Exception e) {
            response.sendError(400, "error reading json file from url");
            return;
        }
        if (features == null) {
            response.sendError(400, "geojson format error : member 'features' missing.");
            return;
        }

        // parse maptype
        Map<String, List<String>> mapRules = new HashMap<>();
        if (!"".equals(mapType)) {
            try {
                String[] mapRulesArray = mapType.split(",");
                for (String rule : mapRulesArray) {
                    String[] splitted = rule.split(":", 2);
                    if (splitted.length != 2) {
                        throw new Exception("Invalid format");
                    }
                    List<String> valuesList = mapRules.get(splitted[0]);
                    if (valuesList == null) {
                        valuesList = new ArrayList();
                        mapRules.put(splitted[0], valuesList);
                    }
                    valuesList.add(splitted[1]);
                }
            } catch (Exception e) {
                response.sendError(400, "error parsing map_type : " + mapType + ". Please check its format");
                return;
            }
        }

        int recordCount = 0, newCount = 0, knownCount = 0;
        for (Map<String, Object> feature : features) {
            Object properties_obj = feature.get("properties");
            Map<String, Object> properties = properties_obj instanceof Map<?, ?> ? (Map<String, Object>) properties_obj : null;

            if (properties == null) {
                properties = new HashMap<>();
            }

            // add mapped properties
            Map<String, Object> mappedProperties = convertMapRulesProperties(mapRules, properties);
            properties.putAll(mappedProperties);

            properties.put("source_type", SourceType.IMPORT.name());
            properties.put("provider_type", ProviderType.GEOJSON.name());

            // avoid error text not found. TODO: a better strategy, e.g. require text as a mandatory field
            if (properties.get("text") == null) {
                properties.put("text", "");
            }

            // compute unique message id among geojson messages
            try {
                properties.put("id_str", computeGeoJsonId(feature));
                // response.getWriter().println(properties.get("shortname") + ", " + properties.get("screen_name") + ", " + properties.get("name") + " : " + computeGeoJsonId((feature)));
            } catch (Exception e) {
                response.sendError(400, "Error computing id : " + e.getMessage());
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) properties.remove("user");
            MessageEntry messageEntry = new MessageEntry(properties);
            // uncomment this causes NoShardAvailableException
            UserEntry userEntry = new UserEntry(/*(user != null && user.get("screen_name") != null) ? user :*/ new HashMap<String, Object>());
            boolean successful = DAO.writeMessage(messageEntry, userEntry, true, false);
            if (successful) newCount++; else knownCount++;
            recordCount++;
        }

        post.setResponse(response, "application/javascript");

        // generate json
        XContentBuilder json = XContentFactory.jsonBuilder().prettyPrint().lfAtEnd();
        json.startObject();
        json.field("status", "ok");
        json.field("records", recordCount);
        json.field("new", newCount);
        json.field("known", knownCount);
        json.field("message", "pushed");
        json.endObject(); // of root

        // write json
        ServletOutputStream sos = response.getOutputStream();
        if (jsonp) sos.print(callback + "(");
        sos.print(json.string());
        if (jsonp) sos.println(");");
        sos.println();

        DAO.log(request.getServletPath() + " -> records = " + recordCount + ", new = " + newCount + ", known = " + knownCount + ", from host hash " + remoteHash);

    }

    /**
     * For each member m in properties, if it exists in mapRules, perform these conversions :
     *   - m:c -> keep value, change key m to c
     *   - m:c.d -> insert/update json object of key c with a value {d : value}
     * @param mapRules
     * @param properties
     * @return mappedProperties
     */
    private Map<String, Object> convertMapRulesProperties(Map<String, List<String>> mapRules, Map<String, Object> properties) {
        Map<String, Object> root = new HashMap<>();
        Iterator it = properties.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            String key = (String) pair.getKey();
            if (mapRules.containsKey(key)) {
                for (String newField : mapRules.get(key)) {
                    if (newField.contains(".")) {
                        String[] deepFields = newField.split(Pattern.quote("."));
                        System.out.println(Arrays.toString(deepFields));
                        Map<String, Object> currentLevel = root;
                        for (int lvl = 0; lvl < deepFields.length; lvl++) {
                            if (lvl == deepFields.length - 1) {
                                currentLevel.put(deepFields[lvl], pair.getValue());
                            } else {
                                if (currentLevel.get(deepFields[lvl]) == null) {
                                    Map<String, Object> tmp = new HashMap<>();
                                    currentLevel.put(deepFields[lvl], tmp);
                                }
                                currentLevel = (Map<String, Object>) currentLevel.get(deepFields[lvl]);
                            }
                        }
                    } else {
                        root.put(newField, pair.getValue());
                    }
                }
            }
        }
        return root;
    }

    private static String computeGeoJsonId(Map<String, Object> feature) throws Exception {
        Object properties_obj = feature.get("properties");
        Map<String, Object> properties = properties_obj instanceof Map<?, ?> ? (Map<String, Object>) properties_obj : null;
        Object geometry_obj = feature.get("geometry");
        Map<String, Object> geometry = geometry_obj instanceof Map<?, ?> ? (Map<String, Object>) geometry_obj : null;
        String geometryType = (String) geometry.get("type");
        if (!"Point".equals(geometryType)) {
            throw new Exception("Geometry object unsupported : " + geometryType);
        }
        Object mtime_obj = properties.get("mtime");
        if (mtime_obj == null) {
            throw new Exception("geojson format error : member 'mtime' required in feature properties");
        }
        DateTime mtime = new DateTime((String) mtime_obj);

        List<?> coords = (List<?>) geometry.get("coordinates");

        Double longitude = coords.get(0) instanceof Integer ? ((Integer) coords.get(0)).doubleValue() : (Double) coords.get(0);
        Double latitude = coords.get(1) instanceof Integer ? ((Integer) coords.get(1)).doubleValue() : (Double) coords.get(1);

        // longitude and latitude are added to id to a precision of 3 digits after comma
        Long id = (long) Math.floor(1000*longitude) + (long) Math.floor(1000*latitude) + mtime.getMillis();
        return id.toString();
    }

    private static String readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            String jsonText = sb.toString();
            return jsonText;
        } finally {
            is.close();
        }
    }

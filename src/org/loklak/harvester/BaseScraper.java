package org.loklak.harvester;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import javax.servlet.http.HttpServletResponse;
import org.loklak.tools.storage.JSONObjectWithDefault;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;
import org.json.JSONObject;
import org.json.JSONArray;
import org.loklak.objects.ProviderType;
import org.loklak.objects.SourceType;
import org.loklak.objects.PostTimeline;
import org.loklak.server.*;

/**
 * @author vibhcool (Vibhor Verma)
 * @version 0.1
 * @since 07.06.2017
 *
 * This is Base-Interface for Search-Scrappers.
 */
public abstract class BaseScraper extends AbstractAPIHandler {

    /**
     * 
     */
    private static final long serialVersionUID = 2088186173633845169L;
    // a time stamp that is given by loklak upon the arrival of the post which is the current local time
    //TODO: check if UTC time needed
    protected String scraperName;
    protected String html;
    protected String baseUrl;
    protected String midUrl;
    protected String query = null;
    protected String source;
    protected int resultCount = 0;
    // where did the message come from
    protected SourceType sourceType = SourceType.GENERIC;
    // who created the message
    protected ProviderType provider_type;
    protected Map<String, String> extra = null;
    protected Map<String, Map<String, String>> cacheMap = null;
    protected final PostTimeline.Order order = PostTimeline.parseOrder("timestamp");
    protected int hits = 0;
    protected int count = 0;

    @Override
    public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights,
            JSONObjectWithDefault permissions) throws APIException {
        this.setExtra(call);
        this.setParam();
        return this.getData();
    }

    protected void setExtra(Query call) {
        this.setExtra(call.getMap());
    }

    protected void setExtra(Map<String, String> _extra) {
        this.extra = _extra;
        this.query = _extra.get("query");
        try{
            this.resultCount = Integer.parseInt(_extra.get("count"));
        } catch (NumberFormatException e) {
            //TODO: set with config file
            this.resultCount = 10;
        }
        this.source = _extra.get("source");
        this.setParam();
    }

    public String getExtraValue(String key) {
        String value = "";
        if(this.extra.get(key) != null) {
            value = this.extra.get(key).trim();
        }
        return value;
    }

    protected void setExtraValue(String key, String value) {
        if(this.extra == null) {
            this.extra = new HashMap<String, String>();
        }
        this.extra.put(key, value);
    }

    protected void setCacheMap() {
        cacheMap = null;
    }

    protected abstract void setParam();

    protected abstract String prepareSearchUrl(String type);

    public Post getData() {
        Post output = new Post(true);
        Post postArray = new Post(true);

        output.putAll(getDataScraper());
        output.put("metadata", this.getMetadata());

        try {
            postArray.put(this.scraperName, output);
        } catch (Exception e) {
            DAO.severe("check internet connection");
        }

        return output;
    }

    public Post getDataScraper() {
        Post outputOfScraper = null;

        if("cache".equals(this.source)) {
            outputOfScraper = this.getCache();
        } else {
            outputOfScraper = this.getResults();
        }
        return outputOfScraper;
    }

    public Post getCache() {
        this.setCacheMap();
        DAO.SearchLocalMessages indexScrape = new DAO.SearchLocalMessages (this.cacheMap,
                this.order, this.resultCount);
        this.hits = this.hits + indexScrape.postList.getHits();

        // Add scraper name
        Post postArray = new Post(true);
        postArray.put(this.scraperName, indexScrape.postList.toArray());

        return postArray;
    }

    public Post getResults() {
        try {
            return getDataFromConnection();
        } catch (IOException e) {
            DAO.severe("Error on connection to url:" + this.prepareSearchUrl("all"));
            return new Post(true);
        }
    }

    public Post getDataFromConnection(String url, String type) throws IOException {
        // This adds to hits count even if connection fails
        this.hits++;
        ClientConnection connection = new ClientConnection(url);
        BufferedReader br;
        Post postArray = null;
        try {

            // get instance of bufferReader
            br = getHtml(connection);
            postArray = this.scrape(br, type, url);

        } catch (Exception e) {
            DAO.trace(e);
            postArray = new Post(true);
        } finally {
            connection.close();
        }
        return postArray;
    }

    public Post getDataFromConnection(String url) throws IOException {
        return getDataFromConnection(url, "all");
    }

    public Post getDataFromConnection() throws IOException {
        String url = this.prepareSearchUrl("all");
        return getDataFromConnection(url, "all");
    }

    public BufferedReader getHtml(ClientConnection connection) {
        if (connection.getInputStream() == null) {
            return null;
        }
        BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        return br;
    }

    protected abstract Post scrape(BufferedReader br, String type, String url);

    protected Post putData(Post typeArray, String key, PostTimeline postList) {
        if(!"cache".equals(this.source)) {
            //TODO: base it on SourceType
            postList.writeToIndex();
        }
        return this.putData(typeArray, key, postList.toArray());
    }

    protected Post putData(Post typeArray, String key, JSONArray postList) {
        this.count = this.count + postList.length();
        typeArray.put(key, postList);
        return typeArray;
    }

    protected Post getMetadata() {
        Post metadata = new Post(true);

        metadata.put("hits", this.hits);
        metadata.put("count", this.count);
        metadata.put("scraper", this.scraperName);
        metadata.put("input_parameters", this.extra);

        //TODO: implement these
        //metadata.put("provider_type", this.providerType);
        //metadata.put("source_type", this.sourceType);

        return metadata;
    }

    public String bufferedReaderToString(BufferedReader br) throws IOException {
    StringBuilder everything = new StringBuilder();
    String line;
    while( (line = br.readLine()) != null) {
       everything.append(line);
    }
    return everything.toString();
    }
}

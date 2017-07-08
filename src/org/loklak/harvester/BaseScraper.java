package org.loklak.harvester;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.loklak.tools.storage.JSONObjectWithDefault;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;
import org.json.JSONObject;
import org.loklak.objects.ProviderType;
import org.loklak.objects.SourceType;
import org.loklak.objects.Timeline2;
import org.loklak.tools.storage.JSONObjectWithDefault;
import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;
import org.loklak.server.*;

/**
 * @author vibhcool (Vibhor Verma)
 * @version 0.1
 * @since 07.06.2017
 *
 * This is Base-Interface for Search-Scrappers.
 */
public abstract class BaseScraper extends AbstractAPIHandler {

    // a time stamp that is given by loklak upon the arrival of the post which is the current local time
    //TODO: check if UTC time needed
    protected String scraperName;
    protected String html;
    protected String baseUrl;
    protected String midUrl;
    protected String query;
    // where did the message come from
    protected SourceType source_type;
    // who created the message
    protected ProviderType provider_type;
    //TODO: dummy variable, add datastructure for filter, type_of_posts, location, etc
    protected String extra = "";
    protected final Timeline2.Order order = Timeline2.parseOrder("timestamp");

    @Override
    public JSONObject serviceImpl(Query call, HttpServletResponse response, Authorization rights,
            JSONObjectWithDefault permissions) throws APIException {
        this.query = call.get("query", "");

        //TODO: add different extra paramenters. this is dummy variable
        this.extra = call.get("extra", "");
        return this.getData().toJSON(false, "metadata", "posts");
    }

    protected abstract Map<?, ?> getExtra(String _extra);

    public Timeline2 getData() {
        String url = null;
        Post postArray = null;
        Timeline2 tl = new Timeline2(order);
        url = this.baseUrl + this.midUrl + this.query;

        try {
            postArray = getDataFromConnection(url, "all");
        } catch (Exception e) {
            DAO.severe("check internet connection");
            postArray = new Post();
        }
        tl.addPost(postArray);
        return tl;
    }

    public Post getDataFromConnection(String url, String type) throws IOException {
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
/*
    public Post getDataFromConnection(String url) throws IOException {
        return getDataFromConnection(url, "all");
    }
*/
    public BufferedReader getHtml(ClientConnection connection) {
        if (connection.inputStream == null) {
            return null;
        }
        BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.inputStream, StandardCharsets.UTF_8));
        return br;
    }

    protected abstract Post scrape(BufferedReader br, String type, String url);

    public String bufferedReaderToString(BufferedReader br) throws IOException {
    StringBuilder everything = new StringBuilder();
    String line;
    while( (line = br.readLine()) != null) {
       everything.append(line);
    }
    return everything.toString();
    }

}

/**
 *  StatusServlet
 *  Copyright 27.02.2015 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.api.admin;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONObject;
import org.loklak.Caretaker;
import org.loklak.LoklakServer;
import org.loklak.data.DAO;
import org.loklak.data.IncomingMessageBuffer;
import org.loklak.http.ClientConnection;
import org.loklak.objects.QueryEntry;
import org.loklak.server.APIException;
import org.loklak.server.APIHandler;
import org.loklak.server.AbstractAPIHandler;
import org.loklak.server.Authorization;
import org.loklak.server.BaseUserRole;
import org.loklak.server.Query;
import org.loklak.tools.OS;
import org.loklak.tools.UTF8;
import org.loklak.tools.storage.JSONObjectWithDefault;

public class StatusService extends AbstractAPIHandler implements APIHandler {

    private static final long serialVersionUID = 8578478303032749879L;

    @Override
    public String getAPIPath() {
        return "/api/status.json";
    }

    @Override
    public BaseUserRole getMinimalBaseUserRole() {
        return BaseUserRole.ANONYMOUS;
    }

    @Override
    public JSONObject getDefaultPermissions(BaseUserRole baseUserRole) {
        return null;
    }


    public static JSONObject status(final String[] protocolhostportstubs) throws IOException {
        IOException e = null;
        for (String protocolhostportstub: protocolhostportstubs) {
            try {
                return status(protocolhostportstub);
            } catch (IOException ee) {
                e = ee;
            }
        }
        throw e == null ? new IOException("no url given") : e;
    }
    
    public static JSONObject status(final String protocolhostportstub) throws IOException {
        final String urlstring = protocolhostportstub + "/api/status.json";
        byte[] response = ClientConnection.download(urlstring);
        if (response == null || response.length == 0) {
            return new JSONObject();
        }
        JSONObject json = new JSONObject(UTF8.String(response));
        return json;
    }

    public static JSONObject getConfig(Runtime runtime) throws Exception {
        JSONObject system = null;
        JSONObject elasticsearch = null;
        JSONObject searchCount = null;
        JSONObject retrievalForBackend = null;
        JSONObject caretakerProperties = null;
        try{
            system = getSystemConfig(runtime);
            elasticsearch = getElasticsearchConfig();
            searchCount = getSearchCount();
            retrievalForBackend = getRetrievalForBackendConfig();
            caretakerProperties = getCaretakerProperties();
            system.put("elasticsearch", elasticsearch);
            system.put("searchCount", searchCount);
            system.put("retrievalForBackend", retrievalForBackend);
            system.put("caretakerProperties", caretakerProperties);
        } catch(Exception e) {
            DAO.trace(e);
        }
        return system; 
    }

    public static JSONObject getSystemConfig(Runtime runtime) throws Exception {
        JSONObject system = new JSONObject();
        String xmx = DAO.getConfig("Xmx", "");
        system.put("assigned_memory", runtime.maxMemory());
        system.put("used_memory", runtime.totalMemory() - runtime.freeMemory());
        system.put("available_memory", runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory());
        system.put("cores", runtime.availableProcessors());
        system.put("threads", Thread.activeCount());
        system.put("runtime", System.currentTimeMillis() - Caretaker.startupTime);
        system.put("time_to_restart", Caretaker.upgradeTime - System.currentTimeMillis());
        system.put("load_system_average", OS.getSystemLoadAverage());
        Double systemCpuLoad = OS.getSystemCpuLoad();
        system.put("load_system_cpu", systemCpuLoad.isNaN() || systemCpuLoad.isInfinite() ? 0 : systemCpuLoad.doubleValue());
        system.put("load_process_cpu", systemCpuLoad.isNaN() || systemCpuLoad.isInfinite() ? 0 : systemCpuLoad);
        system.put("server_threads", LoklakServer.getServerThreads());
        system.put("server_uri", LoklakServer.getServerURI());
        system.put("Xmx", xmx);
        return system;
    }

    public static JSONObject getIndexConfig() throws Exception {
        final String[] backend = DAO.getBackend();
        final boolean backend_push = DAO.getConfig("backend.push.enabled", false);
        JSONObject backend_status = null;
        JSONObject backend_status_index_sizes = null;
        if (backend.length > 0 && !backend_push) {
            try {
                backend_status = StatusService.status(backend);
                backend_status_index_sizes = backend_status == null ? null : (JSONObject) backend_status.get("index_sizes");
            } catch (IOException e) {}
        }
        long backend_messages = backend_status_index_sizes == null ? 0 : ((Number) backend_status_index_sizes.get("messages")).longValue();
        long backend_users = backend_status_index_sizes == null ? 0 : ((Number) backend_status_index_sizes.get("users")).longValue();
        long local_messages = DAO.countLocalMessages();
        long local_users = DAO.countLocalUsers();

        JSONObject index = new JSONObject(true);

        long countLocalMinMessagesCreated  = DAO.countLocalMessages(60000L, true);
        long countLocalMinMessagesTimestamp  = DAO.countLocalMessages(60000L, false);
        long countLocal10MMessagesCreated  = DAO.countLocalMessages(600000L, true);
        long countLocal10MMessagesTimestamp  = DAO.countLocalMessages(600000L, false);
        long countLocalHourMessagesCreated = DAO.countLocalMessages(3600000L, true);
        long countLocalHourMessagesTimestamp = DAO.countLocalMessages(3600000L, false);
        long countLocalDayMessagesCreated  = DAO.countLocalMessages(86400000L, true);
        long countLocalDayMessagesTimestamp  = DAO.countLocalMessages(86400000L, false);
        long countLocalWeekMessagesCreated = DAO.countLocalMessages(604800000L, true);
        long countLocalWeekMessagesTimestamp = DAO.countLocalMessages(604800000L, false);
        long countLocal10MinDump = DAO.message_dump.objectsPersSecond();
        long countLocal10MinNotInIndex = DAO.message_dump.objectsNotInIndexPerSecond();
        float mps1mC  = countLocalMinMessagesCreated  / 60f;
        float mps1mT  = countLocalMinMessagesTimestamp  / 60f;
        float mps10mC = countLocal10MMessagesCreated  / 600f;
        float mps10mT = countLocal10MMessagesTimestamp  / 600f;
        float mps1hC  = countLocalHourMessagesCreated / 3600f;
        float mps1hT  = countLocalHourMessagesTimestamp / 3600f;
        float mps1dC  = countLocalDayMessagesCreated  / 86400f;
        float mps1dT  = countLocalDayMessagesTimestamp  / 86400f;
        float mps1wC  = countLocalWeekMessagesCreated / 604800f;
        float mps1wT  = countLocalWeekMessagesTimestamp / 604800f;
        index.put("mps1mC", mps1mC);
        index.put("mps1mT", mps1mT);
        index.put("mps10mC", mps10mC);
        index.put("mps10mT", mps10mT);
        index.put("mps1hC", mps1hC);
        index.put("mps1hT", mps1hT);
        index.put("mps1dC", mps1dC);
        index.put("mps1dT", mps1dT);
        index.put("mps1wC", mps1wC);
        index.put("mps1wT", mps1wT);
        index.put("mps10mD", countLocal10MinDump);
        index.put("mps10mNII", countLocal10MinNotInIndex);
        index.put("mps", (int)  // best of 1d, 1h and 10m
                Math.max(
                    Math.max(
                        Math.max(mps1dC, Math.max(mps1hC, Math.max(mps10mC, mps1mC))),
                        Math.max(mps1dT, Math.max(mps1hT, Math.max(mps10mT, mps1mT)))
                    ),
                    countLocal10MinDump
                )
        );
        JSONObject messages = new JSONObject(true);
        messages.put("size", local_messages + backend_messages);
        messages.put("size_local", local_messages);
        messages.put("size_local_minute_created_at", countLocalMinMessagesCreated);
        messages.put("size_local_minute_timestamp", countLocalMinMessagesTimestamp);
        messages.put("size_local_10minutes_created_at", countLocal10MMessagesCreated);
        messages.put("size_local_10minutes_timestamp", countLocal10MMessagesTimestamp);
        messages.put("size_local_hour_created_at", countLocalHourMessagesCreated);
        messages.put("size_local_hour_timestamp", countLocalHourMessagesTimestamp);
        messages.put("size_local_day_created_at", countLocalDayMessagesCreated);
        messages.put("size_local_day_timestamp", countLocalDayMessagesTimestamp);
        messages.put("size_local_week_created_at", countLocalWeekMessagesCreated);
        messages.put("size_local_week_timestamp", countLocalWeekMessagesTimestamp);
        messages.put("size_backend", backend_messages);
        messages.put("stats", DAO.messages.getStats());
        JSONObject queue = new JSONObject(true);
        queue.put("size", IncomingMessageBuffer.getMessageQueueSize());
        queue.put("maxSize", IncomingMessageBuffer.getMessageQueueMaxSize());
        queue.put("clients", IncomingMessageBuffer.getMessageQueueClients());
        messages.put("queue", queue);
        JSONObject users = new JSONObject(true);
        users.put("size", local_users + backend_users);
        users.put("size_local", local_users);
        users.put("size_backend", backend_users);
        users.put("stats", DAO.users.getStats());
        JSONObject queries = new JSONObject(true);
        queries.put("size", DAO.countLocalQueries());
        queries.put("stats", DAO.queries.getStats());
        JSONObject accounts = new JSONObject(true);
        accounts.put("size", DAO.countLocalAccounts());
        JSONObject user = new JSONObject(true);
        user.put("size", DAO.user_dump.size());
        JSONObject followers = new JSONObject(true);
        followers.put("size", DAO.followers_dump.size());
        JSONObject following = new JSONObject(true);
        following.put("size", DAO.following_dump.size());
        index.put("messages", messages);
        index.put("users", users);
        index.put("queries", queries);
        index.put("accounts", accounts);
        index.put("user", user);
        index.put("followers", followers);
        index.put("following", following);
        if (DAO.getConfig("retrieval.queries.enabled", false)) {
            List<QueryEntry> queryList = DAO.SearchLocalQueries("", 1000, "retrieval_next", "date", SortOrder.ASC, null, new Date(), "retrieval_next");
            index.put("queries_pending", queryList.size());
        }
        return index; 
    }

    public static JSONObject getCaretakerProperties() throws Exception {
        JSONObject caretakerProperties = new JSONObject();
        String caretaker_retries = DAO.getConfig("caretaker.backendpush.retries", "");
        String caretaker_backoff = DAO.getConfig("caretaker.backendpush.backoff", "");
        caretakerProperties.put("caretaker_backendpush_retries", caretaker_retries);
        caretakerProperties.put("caretaker_backendpush_backoff", caretaker_backoff);
        return caretakerProperties;
    }

    public static JSONObject getRetrievalForBackendConfig() throws Exception {
        JSONObject retrievalForBackend = new JSONObject();
        String retrieval_forbackend_enabled = DAO.getConfig("retrieval.forbackend.enabled", "");
        String retrieval_forbackend_concurrency = DAO.getConfig("retrieval.forbackend.concurrency", "");
        String retrieval_forbackend_loops = DAO.getConfig("retrieval.forbackend.loops", "");
        String retrieval_forbackend_sleep_base = DAO.getConfig("retrieval.forbackend.sleep.base", "");
        String retrieval_forbackend_sleep_randomoffset = DAO.getConfig("retrieval.forbackend.sleep.randomoffset", "");
        retrievalForBackend.put("retrieval_forbackend_enabled", retrieval_forbackend_enabled);
        retrievalForBackend.put("retrieval_forbackend_concurrency", retrieval_forbackend_concurrency);
        retrievalForBackend.put("retrieval_forbackend_loops", retrieval_forbackend_loops);
        retrievalForBackend.put("retrieval_forbackend_sleep_base", retrieval_forbackend_sleep_base);
        retrievalForBackend.put("retrieval_forbackend_sleep_randomoffset", retrieval_forbackend_sleep_randomoffset);
        return retrievalForBackend;
    }

    public static JSONObject getSearchCount() throws Exception {
        JSONObject searchCount = new JSONObject();
        String search_count_low = DAO.getConfig("search.count.low", "");
        String search_count_default = DAO.getConfig("search.count.default", "");
        String search_count_max_public = DAO.getConfig("search.count.max.public", "");
        String search_count_max_localhost = DAO.getConfig("search.count.max.localhost", "");
        String search_timeout = DAO.getConfig("search.timeout", "");
        searchCount.put("search_count_low", search_count_low);
        searchCount.put("search_count_default", search_count_default);
        searchCount.put("search_count_max_public", search_count_max_public);
        searchCount.put("search_count_max_localhost", search_count_max_localhost);
        searchCount.put("search_timeout", search_timeout);
        return searchCount;
    }

    public static JSONObject getElasticsearchConfig() throws Exception {
        JSONObject json = new JSONObject();
        String watermark_low = DAO.getConfig("elasticsearch.cluster.routing.allocation.disk.watermark.low", "");
        String watermark_high = DAO.getConfig("elasticsearch.cluster.routing.allocation.disk.watermark.high", "");
        json.put("elasticsearch_cluster_routing_allocation_disk_watermark_low", watermark_low);
        json.put("elasticsearch_cluster_routing_allocation_disk_watermark_high", watermark_high);
        return json;
    }

    @Override
    public JSONObject serviceImpl(Query post, HttpServletResponse response, Authorization rights, JSONObjectWithDefault permissions) throws APIException {

        if (post.isLocalhostAccess() && OS.canExecUnix && post.get("upgrade", "").equals("true")) {
            Caretaker.upgrade(); // it's a hack to add this here, this may disappear anytime
        }

        post.setResponse(response, "application/javascript");

        // generate json
        Runtime runtime = Runtime.getRuntime();
        JSONObject json = new JSONObject(true);

        JSONObject system = null;
        JSONObject index = null;
        try{
            system = getConfig(runtime);
            index = getIndexConfig();
        } catch(Exception e) {
            DAO.trace(e);
        }

        JSONObject client_info = new JSONObject(true);
        client_info.put("RemoteHost", post.getClientHost());
        client_info.put("IsLocalhost", post.isLocalhostAccess() ? "true" : "false");

        JSONObject request_header = new JSONObject(true);
        Enumeration<String> he = post.getRequest().getHeaderNames();
        while (he.hasMoreElements()) {
            String h = he.nextElement();
            request_header.put(h, post.getRequest().getHeader(h));
        }
        client_info.put("request_header", request_header);

        json.put("system", system);
        json.put("index", index);
        json.put("client_info", client_info);

        
        String commitHash = System.getenv("COMMIT_HASH");
        String commitComment = System.getenv("COMMIT_COMMENT");
        if (commitComment != null) commitComment = commitComment.replaceAll("^[ \n]+", "").replaceAll("[ \n]+$", "");
        JSONObject commit = new JSONObject(true);
        commit.put("hash", commitHash);
        commit.put("comment", commitComment);
        json.put("commit", commit);

        json.put("stream_enabled", DAO.getConfig("stream.enabled", false));

        return json;
    }

    public static void main(String[] args) {
        try {
            JSONObject json = status("http://api.loklak.org");
            JSONObject index_sizs = (JSONObject) json.get("index_sizes");
            System.out.println(json.toString());
            System.out.println(index_sizs.toString());
        } catch (IOException e) {
            DAO.trace(e);
        }
    }
}

/**
 *  DAO
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
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

package org.loklak.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.google.common.base.Charsets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.logging.slf4j.Slf4jESLoggerFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.loklak.Caretaker;
import org.loklak.api.search.SearchServlet;
import org.loklak.api.search.WordpressCrawlerService;
import org.loklak.geo.GeoNames;
import org.loklak.harvester.Post;
import org.loklak.harvester.TwitterScraper;
import org.loklak.harvester.YoutubeScraper;
import org.loklak.harvester.TwitterScraper.TwitterTweet;
import org.loklak.api.search.GithubProfileScraper;
import org.loklak.api.search.InstagramProfileScraper;
import org.loklak.api.search.QuoraProfileScraper;
import org.loklak.api.search.TweetScraper;
import org.loklak.harvester.BaseScraper;
import org.loklak.http.AccessTracker;
import org.loklak.http.ClientConnection;
import org.loklak.http.RemoteAccess;
import org.loklak.ir.AccountFactory;
import org.loklak.ir.BulkWriteResult;
import org.loklak.ir.ElasticsearchClient;
import org.loklak.ir.ImportProfileFactory;
import org.loklak.ir.MessageFactory;
import org.loklak.ir.QueryFactory;
import org.loklak.ir.UserFactory;
import org.loklak.objects.AbstractObjectEntry;
import org.loklak.objects.AccountEntry;
import org.loklak.objects.ImportProfileEntry;
import org.loklak.objects.Peers;
import org.loklak.objects.QueryEntry;
import org.loklak.objects.ResultList;
import org.loklak.objects.SourceType;
import org.loklak.objects.TwitterTimeline;
import org.loklak.objects.PostTimeline;
import org.loklak.objects.TimelineCache;
import org.loklak.objects.UserEntry;
import org.loklak.server.*;
import org.loklak.stream.MQTTPublisher;
import org.loklak.tools.DateParser;
import org.loklak.tools.IO;
import org.loklak.tools.OS;
import org.loklak.tools.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Data Access Object for the message project.
 * This provides only static methods because the class methods shall be available for
 * all other classes.
 *
 * To debug, call elasticsearch directly i.e.:
 *
 * get statistics
 * curl localhost:9200/_stats?pretty=true
 *
 * get statistics for message index
 * curl -XGET 'http://127.0.0.1:9200/messages?pretty=true'
 *
 * get mappings in message index
 * curl -XGET "http://localhost:9200/messages/_mapping?pretty=true"
 *
 * get search result from message index
 * curl -XGET 'http://127.0.0.1:9200/messages/_search?q=*&pretty=true'
 */
public class DAO {

    public final static com.fasterxml.jackson.core.JsonFactory jsonFactory = new com.fasterxml.jackson.core.JsonFactory();
    public final static com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper(DAO.jsonFactory);
    public final static com.fasterxml.jackson.core.type.TypeReference<HashMap<String,Object>> jsonTypeRef = new com.fasterxml.jackson.core.type.TypeReference<HashMap<String,Object>>() {};

    private static Logger logger = LoggerFactory.getLogger(DAO.class);
    
    public final static String MESSAGE_DUMP_FILE_PREFIX = "messages_";
    public final static String ACCOUNT_DUMP_FILE_PREFIX = "accounts_";
    public final static String USER_DUMP_FILE_PREFIX = "users_";
    public final static String ACCESS_DUMP_FILE_PREFIX = "access_";
    public final static String FOLLOWERS_DUMP_FILE_PREFIX = "followers_";
    public final static String FOLLOWING_DUMP_FILE_PREFIX = "following_";
    private static final String IMPORT_PROFILE_FILE_PREFIX = "profile_";

    public static boolean writeDump;

    public final static int CACHE_MAXSIZE =   10000;
    public final static int EXIST_MAXSIZE = 4000000;

    public  static File data_dir, conf_dir, bin_dir, html_dir;
    private static File external_data, assets, dictionaries;
    public static Settings public_settings, private_settings;
    private static Path message_dump_dir, account_dump_dir, import_profile_dump_dir;
    public static Path push_cache_dir;
    public static JsonRepository message_dump;
    private static JsonRepository account_dump;
    private static JsonRepository import_profile_dump;
    public  static JsonDataset user_dump, followers_dump, following_dump;
    public  static AccessTracker access;
    private static File schema_dir, conv_schema_dir;
    public static ElasticsearchClient elasticsearch_client;
    //private static Node elasticsearch_node;
    //private static Client elasticsearch_client;
    public static UserFactory users;
    private static AccountFactory accounts;
    public static MessageFactory messages;
    public static MessageFactory messages_hour;
    public static MessageFactory messages_day;
    public static MessageFactory messages_week;
    public static QueryFactory queries;
    private static ImportProfileFactory importProfiles;
    private static Map<String, String> config = new HashMap<>();
    public  static GeoNames geoNames = null;
    public static Peers peers = new Peers();
    public static OutgoingMessageBuffer outgoingMessages = new OutgoingMessageBuffer();

    // AAA Schema for server usage
    public static JsonTray authentication;
    public static JsonTray authorization;
    public static JsonTray accounting;
    public static UserRoles userRoles;
    public static JsonTray passwordreset;
    public static Map<String, Accounting> accounting_temporary = new HashMap<>();
    public static JsonFile login_keys;
    public static TimelineCache timelineCache;

    public static MQTTPublisher mqttPublisher = null;
    public static boolean streamEnabled = false;
    public static List<String> randomTerms = new ArrayList<>();

    public static enum IndexName {
    	messages_hour("messages.json"), messages_day("messages.json"), messages_week("messages.json"), messages, queries, users, accounts, import_profiles;
        private String schemaFileName;
    	private IndexName() {
    	    schemaFileName = this.name() + ".json";
    	}
    	private IndexName(String filename) {
            schemaFileName = filename;
        }
    	public String getSchemaFilename() {
    	    return this.schemaFileName;
    	}
    }

    /**
     * initialize the DAO
     * @param configMap
     * @param dataPath the path to the data directory
     */
    public static void init(Map<String, String> configMap, Path dataPath) throws Exception{

        log("initializing loklak DAO");

        config = configMap;
        data_dir = dataPath.toFile();
        conf_dir = new File("conf");
        bin_dir = new File("bin");
        html_dir = new File("html");

        writeDump = DAO.getConfig("dump.write_enabled", true);

        // initialize public and private keys
		public_settings = new Settings(new File("data/settings/public.settings.json"));
		File private_file = new File("data/settings/private.settings.json");
		private_settings = new Settings(private_file);
		OS.protectPath(private_file.toPath());

		if(!private_settings.loadPrivateKey() || !public_settings.loadPublicKey()){
        	log("Can't load key pair. Creating new one");

        	// create new key pair
        	KeyPairGenerator keyGen;
			try {
				String algorithm = "RSA";
				keyGen = KeyPairGenerator.getInstance(algorithm);
				keyGen.initialize(2048);
				KeyPair keyPair = keyGen.genKeyPair();
				private_settings.setPrivateKey(keyPair.getPrivate(), algorithm);
				public_settings.setPublicKey(keyPair.getPublic(), algorithm);
			} catch (NoSuchAlgorithmException e) {
				throw e;
			}
			log("Key creation finished. Peer hash: " + public_settings.getPeerHashAlgorithm() + " " + public_settings.getPeerHash());
        }
        else{
        	log("Key pair loaded from file. Peer hash: " + public_settings.getPeerHashAlgorithm() + " " + public_settings.getPeerHash());
        }

        File datadir = dataPath.toFile();
        // check if elasticsearch shall be accessed as external cluster
        String transport = configMap.get("elasticsearch_transport.enabled");
        if (transport != null && "true".equals(transport)) {
            String cluster_name = configMap.get("elasticsearch_transport.cluster.name");
            String transport_addresses_string = configMap.get("elasticsearch_transport.addresses");
            if (transport_addresses_string != null && transport_addresses_string.length() > 0) {
                String[] transport_addresses = transport_addresses_string.split(",");
                elasticsearch_client = new ElasticsearchClient(transport_addresses, cluster_name);
            }
        } else {
            // use all config attributes with a key starting with "elasticsearch." to set elasticsearch settings

        	ESLoggerFactory.setDefaultFactory(new Slf4jESLoggerFactory());
            org.elasticsearch.common.settings.Settings.Builder settings = org.elasticsearch.common.settings.Settings.builder();
            for (Map.Entry<String, String> entry: config.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("elasticsearch.")) settings.put(key.substring(14), entry.getValue());
            }
            // patch the home path
            settings.put("path.home", datadir.getAbsolutePath());
            settings.put("path.data", datadir.getAbsolutePath());
            settings.build();

            // start elasticsearch
            elasticsearch_client = new ElasticsearchClient(settings);
        }

        // open AAA storage
        Path settings_dir = dataPath.resolve("settings");
        settings_dir.toFile().mkdirs();
        Path authentication_path = settings_dir.resolve("authentication.json");
        authentication = new JsonTray(authentication_path.toFile(), 10000);
        OS.protectPath(authentication_path);
        Path authorization_path = settings_dir.resolve("authorization.json");
        authorization = new JsonTray(authorization_path.toFile(), 10000);
        OS.protectPath(authorization_path);
        Path passwordreset_path = settings_dir.resolve("passwordreset.json");
        passwordreset = new JsonTray(passwordreset_path.toFile(), 10000);
        OS.protectPath(passwordreset_path);
        Path accounting_path = settings_dir.resolve("accounting.json");
        accounting = new JsonTray(accounting_path.toFile(), 10000);
        OS.protectPath(accounting_path);
        Path login_keys_path = settings_dir.resolve("login-keys.json");
        login_keys = new JsonFile(login_keys_path.toFile());
        OS.protectPath(login_keys_path);


        DAO.log("Initializing user roles");

        Path userRoles_path = settings_dir.resolve("userRoles.json");
        userRoles = new UserRoles(new JsonFile(userRoles_path.toFile()));
        OS.protectPath(userRoles_path);

        try{
            userRoles.loadUserRolesFromObject();
            DAO.log("Loaded user roles from file");
        }catch (IllegalArgumentException e){
            DAO.log("Load default user roles");
            userRoles.loadDefaultUserRoles();
        }

        // open index
        Path index_dir = dataPath.resolve("index");
        if (index_dir.toFile().exists()) OS.protectPath(index_dir); // no other permissions to this path

        // define the index factories
        boolean noio = configMap.containsValue("noio") && configMap.get("noio").equals("true");
        messages = new MessageFactory(noio ? null : elasticsearch_client, IndexName.messages.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        messages_hour = new MessageFactory(noio ? null : elasticsearch_client, IndexName.messages_hour.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        messages_day = new MessageFactory(noio ? null : elasticsearch_client, IndexName.messages_day.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        messages_week = new MessageFactory(noio ? null : elasticsearch_client, IndexName.messages_week.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        users = new UserFactory(noio ? null : elasticsearch_client, IndexName.users.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        accounts = new AccountFactory(noio ? null : elasticsearch_client, IndexName.accounts.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        queries = new QueryFactory(noio ? null : elasticsearch_client, IndexName.queries.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);
        importProfiles = new ImportProfileFactory(noio ? null : elasticsearch_client, IndexName.import_profiles.name(), CACHE_MAXSIZE, EXIST_MAXSIZE);

        // create indices and set mapping (that shows how 'elastic' elasticsearch is: it's always good to define data types)
        File mappingsDir = new File(new File(conf_dir, "elasticsearch"), "mappings");
        int shards = Integer.parseInt(configMap.get("elasticsearch.index.number_of_shards"));
        int replicas = Integer.parseInt(configMap.get("elasticsearch.index.number_of_replicas"));
        for (IndexName index: IndexName.values()) {
            log("initializing index '" + index.name() + "'...");
        	try {
        	    elasticsearch_client.createIndexIfNotExists(index.name(), shards, replicas);
        	} catch (Throwable e) {
        		DAO.severe(e);
        	}
            try {
                elasticsearch_client.setMapping(index.name(), new File(mappingsDir, index.getSchemaFilename()));
            } catch (Throwable e) {
            	DAO.severe(e);
            }
        }
        // elasticsearch will probably take some time until it is started up. We do some other stuff meanwhile..

        // create and document the data dump dir
        assets = new File(datadir, "assets").getAbsoluteFile();
        external_data = new File(datadir, "external");
        dictionaries = new File(external_data, "dictionaries");
        dictionaries.mkdirs();

        push_cache_dir = dataPath.resolve("pushcache");
        push_cache_dir.toFile().mkdirs();

        // create message dump dir
        String message_dump_readme =
            "This directory contains dump files for messages which arrived the platform.\n" +
            "There are three subdirectories for dump files:\n" +
            "- own:      for messages received with this peer. There is one file for each month.\n" +
            "- import:   hand-over directory for message dumps to be imported. Drop dumps here and they are imported.\n" +
            "- imported: dump files which had been processed from the import directory are moved here.\n" +
            "You can import dump files from other peers by dropping them into the import directory.\n" +
            "Each dump file must start with the prefix '" + MESSAGE_DUMP_FILE_PREFIX + "' to be recognized.\n";
        message_dump_dir = dataPath.resolve("dump");
        message_dump = new JsonRepository(message_dump_dir.toFile(), MESSAGE_DUMP_FILE_PREFIX, message_dump_readme, JsonRepository.COMPRESSED_MODE, true, Runtime.getRuntime().availableProcessors());

        account_dump_dir = dataPath.resolve("accounts");
        account_dump_dir.toFile().mkdirs();
        OS.protectPath(account_dump_dir); // no other permissions to this path
        account_dump = new JsonRepository(account_dump_dir.toFile(), ACCOUNT_DUMP_FILE_PREFIX, null, JsonRepository.REWRITABLE_MODE, false, Runtime.getRuntime().availableProcessors());

        File user_dump_dir = new File(datadir, "accounts");
        user_dump_dir.mkdirs();
        log("initializing user dump ...");
        user_dump = new JsonDataset(
                user_dump_dir,USER_DUMP_FILE_PREFIX,
                new JsonDataset.Column[]{new JsonDataset.Column("id_str", false), new JsonDataset.Column("screen_name", true)},
                "retrieval_date", DateParser.PATTERN_ISO8601MILLIS,
                JsonRepository.REWRITABLE_MODE, false, Integer.MAX_VALUE);
        log("initializing followers dump ...");
        followers_dump = new JsonDataset(
                user_dump_dir, FOLLOWERS_DUMP_FILE_PREFIX,
                new JsonDataset.Column[]{new JsonDataset.Column("screen_name", true)},
                "retrieval_date", DateParser.PATTERN_ISO8601MILLIS,
                JsonRepository.REWRITABLE_MODE, false, Integer.MAX_VALUE);
        log("initializing following dump ...");
        following_dump = new JsonDataset(
                user_dump_dir, FOLLOWING_DUMP_FILE_PREFIX,
                new JsonDataset.Column[]{new JsonDataset.Column("screen_name", true)},
                "retrieval_date", DateParser.PATTERN_ISO8601MILLIS,
                JsonRepository.REWRITABLE_MODE, false, Integer.MAX_VALUE);

        Path log_dump_dir = dataPath.resolve("log");
        log_dump_dir.toFile().mkdirs();
        OS.protectPath(log_dump_dir); // no other permissions to this path
        access = new AccessTracker(log_dump_dir.toFile(), ACCESS_DUMP_FILE_PREFIX, 60000, 3000);
        access.start(); // start monitor

        timelineCache = new TimelineCache(60000);

        import_profile_dump_dir = dataPath.resolve("import-profiles");
        import_profile_dump = new JsonRepository(import_profile_dump_dir.toFile(), IMPORT_PROFILE_FILE_PREFIX, null, JsonRepository.COMPRESSED_MODE, false, Runtime.getRuntime().availableProcessors());

        // load schema folder
        conv_schema_dir = new File("conf/conversion");
        schema_dir = new File("conf/schema");

        // load dictionaries if they are embedded here
        // read the file allCountries.zip from http://download.geonames.org/export/dump/allCountries.zip
        //File allCountries = new File(dictionaries, "allCountries.zip");
        File cities1000 = new File(dictionaries, "cities1000.zip");
        if (!cities1000.exists()) {
            // download this file
            ClientConnection.download("http://download.geonames.org/export/dump/cities1000.zip", cities1000);
        }

        if(cities1000.exists()){
	        try{
	        	geoNames = new GeoNames(cities1000, new File(conf_dir, "iso3166.json"), 1);
	        }catch(IOException e){
	        	DAO.severe(e.getMessage());
	        	cities1000.delete();
	        	geoNames = null;
	        }
    	}

        // Connect to MQTT message broker
        String mqttAddress = getConfig("stream.mqtt.address", "tcp://127.0.0.1:1883");
        streamEnabled = getConfig("stream.enabled", false);
        if (streamEnabled) {
            mqttPublisher = new MQTTPublisher(mqttAddress);
        }

        // finally wait for healthy status of elasticsearch shards
        ClusterHealthStatus required_status = ClusterHealthStatus.fromString(config.get("elasticsearch_requiredClusterHealthStatus"));
        boolean ok;
        do {
            log("Waiting for elasticsearch " + required_status.name() + " status");
            ok = elasticsearch_client.wait_ready(60000l, required_status);
        } while (!ok);
        /**
        do {
            log("Waiting for elasticsearch green status");
            health = elasticsearch_client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        } while (health.isTimedOut());
        **/
        log("elasticsearch has started up!");

        // start the classifier
        new Thread(){
            public void run() {
                log("initializing the classifier...");
                try {
                    Classifier.init(10000, 1000);
                } catch (Throwable e) {
                	DAO.severe(e);
                }
                log("classifier initialized!");
            }
        }.start();

        log("initializing queries...");
        File harvestingPath = new File(datadir, "queries");
        if (!harvestingPath.exists()) harvestingPath.mkdirs();
        String[] list = harvestingPath.list();
        if (list.length < 10) {
            // use the test data instead
            harvestingPath = new File(new File(datadir.getParentFile(), "test"), "queries");
            list = harvestingPath.list();
        }
        for (String queryfile: list) {
            if (queryfile.startsWith(".") || queryfile.endsWith("~")) continue;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(harvestingPath, queryfile))));
                String line;
                List<IndexEntry<QueryEntry>> bulkEntries = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim().toLowerCase();
                    if (line.length() == 0) continue;
                    if (line.charAt(0) <= '9') {
                        // truncate statistic
                        int p = line.indexOf(' ');
                        if (p < 0) continue;
                        line = line.substring(p + 1).trim();
                    }
                    // write line into query database
                    if (!existQuery(line)) {
                        randomTerms.add(line);
                        bulkEntries.add(
                            new IndexEntry<QueryEntry>(
                                line,
                                SourceType.TWITTER,
                                new QueryEntry(line, 0, 60000, SourceType.TWITTER, false))
                        );
                    }
                    if (bulkEntries.size() > 1000) {
                        queries.writeEntries(bulkEntries);
                        bulkEntries.clear();
                    }
                }
                queries.writeEntries(bulkEntries);
                reader.close();
            } catch (IOException e) {
            	DAO.severe(e);
            }
        }
        log("queries initialized.");

        log("finished DAO initialization");
    }

    public static boolean wait_ready(long maxtimemillis) {
        ClusterHealthStatus required_status = ClusterHealthStatus.fromString(config.get("elasticsearch_requiredClusterHealthStatus"));
        return elasticsearch_client.wait_ready(maxtimemillis, required_status);
    }

    public static String pendingClusterTasks() {
        return elasticsearch_client.pendingClusterTasks();
    }

    public static String clusterStats() {
        return elasticsearch_client.clusterStats();
    }

    public static Map<String, String> nodeSettings() {
        return elasticsearch_client.nodeSettings();
    }

    public static File getAssetFile(String screen_name, String id_str, String file) {
        String letter0 = ("" + screen_name.charAt(0)).toLowerCase();
        String letter1 = ("" + screen_name.charAt(1)).toLowerCase();
        Path storage_path = IO.resolvePath(assets.toPath(), letter0, letter1, screen_name);
        return IO.resolvePath(storage_path, id_str + "_" + file).toFile(); // all assets for one user in one file
    }

    public static Collection<File> getTweetOwnDumps(int count) {
        return message_dump.getOwnDumps(count);
    }

    public static void importAccountDumps(int count) throws IOException {
        Collection<File> dumps = account_dump.getImportDumps(count);
        if (dumps == null || dumps.size() == 0) return;
        for (File dump: dumps) {
            JsonReader reader = account_dump.getDumpReader(dump);
            final JsonReader dumpReader = reader;
            Thread[] indexerThreads = new Thread[dumpReader.getConcurrency()];
            for (int i = 0; i < dumpReader.getConcurrency(); i++) {
                indexerThreads[i] = new Thread() {
                    public void run() {
                        JsonFactory accountEntry;
                        try {
                            while ((accountEntry = dumpReader.take()) != JsonStreamReader.POISON_JSON_MAP) {
                                try {
                                    JSONObject json = accountEntry.getJSON();
                                    AccountEntry a = new AccountEntry(json);
                                    DAO.writeAccount(a, false);
                                } catch (IOException e) {
                                	DAO.severe(e);
                                }
                            }
                        } catch (InterruptedException e) {
                        	DAO.severe(e);
                        }
                    }
                };
                indexerThreads[i].start();
            }
            for (int i = 0; i < dumpReader.getConcurrency(); i++) {
                try {indexerThreads[i].join();} catch (InterruptedException e) {}
            }
            account_dump.shiftProcessedDump(dump.getName());
        }
    }

    /**
     * close all objects in this class
     */
    public static void close() {
        DAO.log("closing DAO");

        // close the dump files
        message_dump.close();
        account_dump.close();
        import_profile_dump.close();
        user_dump.close();
        followers_dump.close();
        following_dump.close();

        // close the tracker
        access.close();

        // close the index factories (flushes the caches)
        messages.close();
        messages_hour.close();
        messages_day.close();
        messages_week.close();
        users.close();
        accounts.close();
        queries.close();
        importProfiles.close();

        // close the index
        elasticsearch_client.close();

        DAO.log("closed DAO");
    }

    /**
     * get values from
     * @param key
     * @param default_val
     * @return
     */
    public static String getConfig(String key, String default_val) {
        String value = config.get(key);
        return value == null ? default_val : value;
    }

    public static String[] getConfig(String key, String[] default_val, String delim) {
        String value = config.get(key);
        return value == null || value.length() == 0 ? default_val : value.split(delim);
    }

    public static long getConfig(String key, long default_val) {
        String value = config.get(key);
        try {
            return value == null ? default_val : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return default_val;
        }
    }

    public static double getConfig(String key, double default_val) {
        String value = config.get(key);
        try {
            return value == null ? default_val : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return default_val;
        }
    }

    public static int getConfig(String key, int default_val) {
        String value = config.get(key);
        try {
            return value == null ? default_val : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return default_val;
        }
    }
/*
    public static void setConfig(String key, String value) {
        config.put(key, value);
    }

    public static void setConfig(String key, long value) {
        setConfig(key, Long.toString(value));
    }

    public static void setConfig(String key, double value) {
        setConfig(key, Double.toString(value));
    }
*/
    public static JsonNode getSchema(String key) throws IOException {
        File schema = new File(schema_dir, key);
        if (!schema.exists()) {
            throw new FileNotFoundException("No schema file with name " + key + " found");
        }
        return JsonLoader.fromFile(schema);
    }

    public static JSONObject getConversionSchema(String key) throws IOException {
        File schema = new File(conv_schema_dir, key);
        if (!schema.exists()) {
            throw new FileNotFoundException("No schema file with name " + key + " found");
        }
        return new JSONObject(com.google.common.io.Files.toString(schema, Charsets.UTF_8));
    }

    public static boolean getConfig(String key, boolean default_val) {
        String value = config.get(key);
        return value == null ? default_val : value.equals("true") || value.equals("on") || value.equals("1");
    }

    public static Set<String> getConfigKeys() {
        return config.keySet();
    }

    public static class MessageWrapper {
        public TwitterTweet t;
        public UserEntry u;
        public boolean dump;
        public MessageWrapper(TwitterTweet t, UserEntry u, boolean dump) {
            this.t = t;
            this.u = u;
            this.dump = dump;
        }
    }

    /**
     * Store a message together with a user into the search index
     * @param mw a message wrapper
     * @return true if the record was stored because it did not exist, false if it was not stored because the record existed already
     */
    public static boolean writeMessage(MessageWrapper mw) {
        if (mw.t == null) return false;
        try {
            synchronized (DAO.class) {
                // record tweet into search index and check if this is a new entry
                // and check if the message exists
                boolean exists = false;
                if (mw.t.getCreatedAt().after(DateParser.oneHourAgo())) {
                    exists = messages_hour.writeEntry(new IndexEntry<Post>(mw.t.getPostId(), mw.t.getSourceType(), mw.t));
                    if (exists) return false;
                }
                if (mw.t.getCreatedAt().after(DateParser.oneDayAgo())) {
                    exists = messages_day.writeEntry(new IndexEntry<Post>(mw.t.getPostId(), mw.t.getSourceType(), mw.t));
                    if (exists) return false;
                }
                if (mw.t.getCreatedAt().after(DateParser.oneWeekAgo())) {
                    exists = messages_week.writeEntry(new IndexEntry<Post>(mw.t.getPostId(), mw.t.getSourceType(), mw.t));
                    if (exists) return false;
                }
                exists = messages.writeEntry(new IndexEntry<Post>(mw.t.getPostId(), mw.t.getSourceType(), mw.t));
                if (exists) return false;

                // write the user into the index
                users.writeEntryAsync(new IndexEntry<UserEntry>(mw.u.getScreenName(), mw.t.getSourceType(), mw.u));

                // record tweet into text file
                if (mw.dump && writeDump) {
                    message_dump.write(mw.t.toJSON(mw.u, false, Integer.MAX_VALUE, ""), true);
                }
                mw.t.publishToMQTT();
             }

            // teach the classifier
            Classifier.learnPhrase(mw.t.getText());
        } catch (IOException e) {
        	DAO.severe(e);
        }
        return true;
    }

    public static Set<String> writeMessageBulk(Collection<MessageWrapper> mws) {
        List<MessageWrapper> noDump = new ArrayList<>();
        List<MessageWrapper> dump = new ArrayList<>();
        for (MessageWrapper mw: mws) {
            if (mw.t == null) continue;
            if (mw.dump) dump.add(mw); else noDump.add(mw);
        }
        Set<String> createdIDs = new HashSet<>();
        createdIDs.addAll(writeMessageBulkNoDump(noDump));
        // does also do an writeMessageBulkNoDump internally
        createdIDs.addAll(writeMessageBulkDump(dump));
        return createdIDs;
    }

    public static Set<String> writeMessageBulk(Set<PostTimeline> postBulk) {
        for (PostTimeline postList: postBulk) {
            if (postList.size() < 1) continue;
            if(postList.dump) {
                writeMessageBulkDump(postList);
            }
            writeMessageBulkNoDump(postList);
        }
        //TODO: return total dumped, or IDs dumped to create hash of IDs
        return new HashSet<>();
    }

    private static Set<String> writeMessageBulkNoDump(PostTimeline postList) {
        if (postList.size() == 0) return new HashSet<>();
        List<Post> messageBulk = new ArrayList<>();

        for (Post post: postList) {
            if (messages.existsCache(post.getPostId())) {
                continue;
            }
            synchronized (DAO.class) {
                messageBulk.add(post);
            }
        }
        BulkWriteResult result = null;
        try {
            Date limitDate = new Date();
            limitDate.setTime(DateParser.oneHourAgo().getTime());
            List<Post> macc = new ArrayList<Post>();
            final Set<String> existed = new HashSet<>();
            long hourLong = limitDate.getTime();
            for(Post post : messageBulk) {
                if(hourLong <= post.getTimestamp()) macc.add(post);
            }
            
            result = messages_hour.writeEntries(macc);
            for (Post i: macc) if (!(result.getCreated().contains(i.getPostId()))) existed.add(i.getPostId());

            limitDate.setTime(DateParser.oneDayAgo().getTime());
            macc = messageBulk.stream().filter(i -> !(existed.contains(i.getPostId()))).filter(i -> i.getCreated().after(limitDate)).collect(Collectors.toList());
            result = messages_day.writeEntries(macc);
            for (Post i: macc) if (!(result.getCreated().contains(i.getPostId()))) existed.add(i.getPostId());

            limitDate.setTime(DateParser.oneWeekAgo().getTime());
            macc = messageBulk.stream().filter(i -> !(existed.contains(i.getPostId())))
                    .filter(i -> i.getCreated().after(limitDate)).collect(Collectors.toList());
            result = messages_week.writeEntries(macc);
            for (Post i: macc) if (!(result.getCreated().contains(i.getPostId()))) existed.add(i.getPostId());

            macc = messageBulk.stream().filter(i -> !(existed.contains(i.getPostId()))).collect(Collectors.toList());
            result = messages.writeEntries(macc);
            for (Post i: macc) if (!(result.getCreated().contains(i.getPostId()))) existed.add(i.getPostId());
        } catch (IOException e) {
        	DAO.severe(e);
        }
        if (result == null) return new HashSet<String>();
        return result.getCreated();
    }

    /**
     * write messages without writing them to the dump file
     * @param mws a collection of message wrappers
     * @return a set of message IDs which had been created with this bulk write.
     */
    private static Set<String> writeMessageBulkNoDump(Collection<MessageWrapper> mws) {
        if (mws.size() == 0) return new HashSet<>();
        List<IndexEntry<UserEntry>> userBulk = new ArrayList<>();
        List<IndexEntry<Post>> messageBulk = new ArrayList<>();
        for (MessageWrapper mw: mws) {
            if (messages.existsCache(mw.t.getPostId())) continue; // we omit writing this again
            synchronized (DAO.class) {
                // write the user into the index
                userBulk.add(new IndexEntry<UserEntry>(mw.u.getScreenName(), mw.t.getSourceType(), mw.u));

                // record tweet into search index
                messageBulk.add(new IndexEntry<Post>(mw.t.getPostId(), mw.t.getSourceType(), mw.t));
             }

            // teach the classifier
            Classifier.learnPhrase(mw.t.getText());
        }
        BulkWriteResult result = null;
        Set<String> created_ids = new HashSet<>();
        try {
            final Date limitDate = new Date();
            List<IndexEntry<Post>> macc;
            final Set<String> existed = new HashSet<>();

            //DAO.log("***DEBUG messages     INIT: " + messageBulk.size());

            limitDate.setTime(DateParser.oneHourAgo().getTime());
            macc = messageBulk.stream().filter(i -> i.getObject().getCreated().after(limitDate)).collect(Collectors.toList());
            //DAO.log("***DEBUG messages for HOUR: " + macc.size());
            result = messages_hour.writeEntries(macc);
            created_ids.addAll(result.getCreated());
            //DAO.log("***DEBUG messages for HOUR: " + result.getCreated().size() + "  created");
            for (IndexEntry<Post> i: macc) if (!(result.getCreated().contains(i.getId()))) existed.add(i.getId());
            //DAO.log("***DEBUG messages for HOUR: " + existed.size() + "  existed");

            limitDate.setTime(DateParser.oneDayAgo().getTime());
            macc = messageBulk.stream().filter(i -> !(existed.contains(i.getObject().getPostId()))).filter(i -> i.getObject().getCreated().after(limitDate)).collect(Collectors.toList());
            //DAO.log("***DEBUG messages for  DAY : " + macc.size());
            result = messages_day.writeEntries(macc);
            created_ids.addAll(result.getCreated());
            //DAO.log("***DEBUG messages for  DAY: " + result.getCreated().size() + " created");
            for (IndexEntry<Post> i: macc) if (!(result.getCreated().contains(i.getId()))) existed.add(i.getId());
            //DAO.log("***DEBUG messages for  DAY: " + existed.size()  + "  existed");

            limitDate.setTime(DateParser.oneWeekAgo().getTime());
            macc = messageBulk.stream().filter(i -> !(existed.contains(i.getObject().getPostId()))).filter(i -> i.getObject().getCreated().after(limitDate)).collect(Collectors.toList());
            //DAO.log("***DEBUG messages for WEEK: " + macc.size());
            result = messages_week.writeEntries(macc);
            created_ids.addAll(result.getCreated());
            //DAO.log("***DEBUG messages for WEEK: " + result.getCreated().size() + "  created");
            for (IndexEntry<Post> i: macc) if (!(result.getCreated().contains(i.getId()))) existed.add(i.getId());
            //DAO.log("***DEBUG messages for WEEK: " + existed.size()  + "  existed");

            macc = messageBulk.stream().filter(i -> !(existed.contains(i.getObject().getPostId()))).collect(Collectors.toList());
            //DAO.log("***DEBUG messages for  ALL : " + macc.size());
            result = messages.writeEntries(macc);
            created_ids.addAll(result.getCreated());
            //DAO.log("***DEBUG messages for  ALL: " + result.getCreated().size() + "  created");
            for (IndexEntry<Post> i: macc) if (!(result.getCreated().contains(i.getId()))) existed.add(i.getId());
            //DAO.log("***DEBUG messages for  ALL: " + existed.size()  + "  existed");

            users.writeEntries(userBulk);

        } catch (IOException e) {
        	DAO.severe(e);
        }
        return created_ids;
    }

    private static Set<String> writeMessageBulkDump(Collection<MessageWrapper> mws) {
        Set<String> created = writeMessageBulkNoDump(mws);

        for (MessageWrapper mw: mws) try {
            mw.t.publishToMQTT();
            if (!created.contains(mw.t.getPostId())) continue;
            synchronized (DAO.class) {
                
                // record tweet into text file
                if (writeDump) {
                    message_dump.write(mw.t.toJSON(mw.u, false, Integer.MAX_VALUE, ""), true);
                }
            }

            // teach the classifier
            if (randomPicker.nextInt(100) == 0) Classifier.learnPhrase(mw.t.getText());
        } catch (IOException e) {
        	DAO.severe(e);
        }

        return created;
    }

    private static Set<String> writeMessageBulkDump(PostTimeline postList) {
        Set<String> created = writeMessageBulkNoDump(postList);

        for (Post post: postList) try {
            if (!created.contains(post.getPostId())) continue;
            synchronized (DAO.class) {
                // record tweet into text file
                if (writeDump) {
                    message_dump.write(post, true);
                }
            }
        } catch (IOException e) {
        	DAO.severe(e);
        }
        return created;
    }

    /**
     * Store an account together with a user into the search index
     * This method is synchronized to prevent concurrent IO caused by this call.
     * @param a an account
     * @param dump
     * @return true if the record was stored because it did not exist, false if it was not stored because the record existed already
     */
    public static boolean writeAccount(AccountEntry a, boolean dump) {
        try {
            // record account into text file
            if (dump && writeDump) {
                account_dump.write(a.toJSON(null), true);
            }

            // record account into search index
            accounts.writeEntryAsync(new IndexEntry<AccountEntry>(a.getScreenName(), a.getSourceType(), a));
        } catch (IOException e) {
        	DAO.severe(e);
        }
        return true;
    }

    /**
     * Store an import profile into the search index
     * @param i an import profile
     * @return true if the record was stored because it did not exist, false if it was not stored because the record existed already
     */
    public static boolean writeImportProfile(ImportProfileEntry i, boolean dump) {
        try {
            // record import profile into text file
            if (dump && writeDump) {
                import_profile_dump.write(i.toJSON(), true);
            }
            // record import profile into search index
            importProfiles.writeEntryAsync(new IndexEntry<ImportProfileEntry>(i.getId(), i.getSourceType(), i));
        } catch (IOException e) {
        	DAO.severe(e);
        }
        return true;
    }

    private static long countLocalHourMessages(final long millis, boolean created_at) {
        if (millis > DateParser.HOUR_MILLIS) return countLocalDayMessages(millis, created_at);
        if (created_at && millis == DateParser.HOUR_MILLIS) return elasticsearch_client.count(IndexName.messages_hour.name());
        return elasticsearch_client.count(
                created_at ? IndexName.messages_hour.name() : IndexName.messages_day.name(),
                created_at ? AbstractObjectEntry.CREATED_AT_FIELDNAME : AbstractObjectEntry.TIMESTAMP_FIELDNAME,
                millis);
    }

    private static long countLocalDayMessages(final long millis, boolean created_at) {
        if (millis > DateParser.DAY_MILLIS) return countLocalWeekMessages(millis, created_at);
        if (created_at && millis == DateParser.DAY_MILLIS) return elasticsearch_client.count(IndexName.messages_day.name());
        return elasticsearch_client.count(
                created_at ? IndexName.messages_day.name() : IndexName.messages_week.name(),
                created_at ? AbstractObjectEntry.CREATED_AT_FIELDNAME : AbstractObjectEntry.TIMESTAMP_FIELDNAME,
                millis);
    }

    private static long countLocalWeekMessages(final long millis, boolean created_at) {
        if (millis > DateParser.WEEK_MILLIS) return countLocalMessages(millis, created_at);
        if (created_at && millis == DateParser.WEEK_MILLIS) return elasticsearch_client.count(IndexName.messages_week.name());
        return elasticsearch_client.count(
                created_at ? IndexName.messages_week.name() : IndexName.messages.name(),
                created_at ? AbstractObjectEntry.CREATED_AT_FIELDNAME : AbstractObjectEntry.TIMESTAMP_FIELDNAME,
                millis);
    }

    /**
     * count the messages in the local index
     * @param millis number of milliseconds in the past
     * @param created_at field selector: true -> use CREATED_AT, the time when the tweet was created; false -> use TIMESTAMP, the time when the tweet was harvested
     * @return the number of messages in that time span
     */
    public static long countLocalMessages(final long millis, boolean created_at) {
        if (millis == 0) return 0;
        if (millis > 0) {
            if (millis <= DateParser.HOUR_MILLIS) return countLocalHourMessages(millis, created_at);
            if (millis <= DateParser.DAY_MILLIS) return countLocalDayMessages(millis, created_at);
            if (millis <= DateParser.WEEK_MILLIS) return countLocalWeekMessages(millis, created_at);
        }
        return elasticsearch_client.count(
                IndexName.messages.name(),
                created_at ? AbstractObjectEntry.CREATED_AT_FIELDNAME : AbstractObjectEntry.TIMESTAMP_FIELDNAME,
                millis == Long.MAX_VALUE ? -1 : millis);
    }

    public static long countLocalMessages() {
        return elasticsearch_client.count(IndexName.messages.name(), AbstractObjectEntry.TIMESTAMP_FIELDNAME, -1);
    }

    public static long countLocalMessages(String provider_hash) {
        return elasticsearch_client.countLocal(IndexName.messages.name(), provider_hash);
    }

    public static long countLocalUsers() {
        return elasticsearch_client.count(IndexName.users.name(), AbstractObjectEntry.TIMESTAMP_FIELDNAME, -1);
    }

    public static long countLocalQueries() {
        return elasticsearch_client.count(IndexName.queries.name(), AbstractObjectEntry.TIMESTAMP_FIELDNAME, -1);
    }

    public static long countLocalAccounts() {
        return elasticsearch_client.count(IndexName.accounts.name(), AbstractObjectEntry.TIMESTAMP_FIELDNAME, -1);
    }

    public static Post readMessage(String id) throws IOException {
        Post m = null;
        return messages_hour != null && ((m = messages_hour.read(id)) != null) ? m :
               messages_day  != null && ((m = messages_day.read(id))  != null) ? m :
               messages_week != null && ((m = messages_week.read(id)) != null) ? m :
               messages.read(id);
    }

    public static boolean existMessage(String id) {
        return messages_hour != null && messages_hour.exists(id) ||
               messages_day  != null && messages_day.exists(id)  ||
               messages_week != null && messages_week.exists(id) ||
               messages      != null && messages.exists(id);
    }

    public static boolean existUser(String id) {
        return users.exists(id);
    }

    public static boolean existQuery(String id) {
        return queries.exists(id);
    }

    public static boolean deleteQuery(String id, SourceType sourceType) {
        return queries.delete(id, sourceType);
    }
    
    public static String getRandomTerm() {
        return randomTerms.size() == 0 ? null : randomTerms.get(randomPicker.nextInt(randomTerms.size()));
    }

    public static boolean deleteImportProfile(String id, SourceType sourceType) {
        return importProfiles.delete(id, sourceType);
    }

    public static int deleteOld(IndexName indexName, Date createDateLimit) {
        RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(AbstractObjectEntry.CREATED_AT_FIELDNAME).to(createDateLimit);
        return elasticsearch_client.deleteByQuery(indexName.name(), rangeQuery);
    }

    public static class SearchLocalMessages {
        public TwitterTimeline timeline;
        public PostTimeline postList;
        public Map<String, List<Map.Entry<String, AtomicLong>>> aggregations;
        public ElasticsearchClient.Query query;

        /**
         * Search the local message cache using a elasticsearch query.
         * @param q - the query, for aggregation this which should include a time frame in the form since:yyyy-MM-dd until:yyyy-MM-dd
         * @param order_field - the field to order the results, i.e. Timeline.Order.CREATED_AT
         * @param timezoneOffset - an offset in minutes that is applied on dates given in the query of the form since:date until:date
         * @param resultCount - the number of messages in the result; can be zero if only aggregations are wanted
         * @param aggregationLimit - the maximum count of facet entities, not search results
         * @param aggregationFields - names of the aggregation fields. If no aggregation is wanted, pass no (zero) field(s)
         * @param filterList - list of filters in String datatype
         */
        public SearchLocalMessages (
                final String q,
                final TwitterTimeline.Order orderField,
                final int timezoneOffset,
                final int resultCount,
                final int aggregationLimit,
                final ArrayList<String> filterList,
                final String... aggregationFields
        ) {
            this.timeline = new TwitterTimeline(orderField);
            QueryEntry.ElasticsearchQuery sq = new QueryEntry.ElasticsearchQuery(q, timezoneOffset, filterList);
            long interval = sq.until.getTime() - sq.since.getTime();
            IndexName resultIndex;
            if (aggregationFields.length > 0 && q.contains("since:")) {
                if (q.contains("since:hour")) {
                    this.query =  elasticsearch_client.query((resultIndex = IndexName.messages_hour).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                } else if (q.contains("since:day")) {
                    this.query =  elasticsearch_client.query((resultIndex = IndexName.messages_day).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                } else if (q.contains("since:week")) {
                    this.query =  elasticsearch_client.query((resultIndex = IndexName.messages_week).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                } else {
                    this.query = elasticsearch_client.query((resultIndex = IndexName.messages).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                }
            } else {
                // use only a time frame that is sufficient for a result
                this.query = elasticsearch_client.query((resultIndex = IndexName.messages_hour).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                if (!q.contains("since:hour") && insufficient(this.query, resultCount, aggregationLimit, aggregationFields)) {
                    ElasticsearchClient.Query aq = elasticsearch_client.query((resultIndex = IndexName.messages_day).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                    this.query.add(aq);
                    if (!q.contains("since:day") && insufficient(this.query, resultCount, aggregationLimit, aggregationFields)) {
                        this.query.add(aq);
                        if (!q.contains("since:week") && insufficient(this.query, resultCount, aggregationLimit, aggregationFields)) {
                            aq = elasticsearch_client.query((resultIndex = IndexName.messages).name(), sq.queryBuilder, orderField.getMessageFieldName(), timezoneOffset, resultCount, interval, AbstractObjectEntry.CREATED_AT_FIELDNAME, aggregationLimit, aggregationFields);
                            this.query.add(aq);
                }}}
            }
                
            timeline.setHits(query.getHitCount());
            timeline.setResultIndex(resultIndex);

            // evaluate search result
            for (Map<String, Object> map: query.getResult()) {
                TwitterTweet tweet = new TwitterTweet(new JSONObject(map));
                try {
                    UserEntry user = users.read(tweet.getScreenName());
                    assert user != null;
                    if (user != null) {
                        timeline.add(tweet, user);
                    }
                } catch (IOException e) {
                	DAO.severe(e);
                }
            }
            this.aggregations = query.getAggregations();
        }

        public SearchLocalMessages (
                final String q,
                final TwitterTimeline.Order orderField,
                final int timezoneOffset,
                final int resultCount,
                final int aggregationLimit,
                final String... aggregationFields
        ) {
            this(
                q,
                orderField,
                timezoneOffset,
                resultCount,
                aggregationLimit,
                new ArrayList<>(),
                aggregationFields
            );
        }

        public SearchLocalMessages (
                Map<String, Map<String, String>> inputMap,
                final PostTimeline.Order orderField,
                final int resultCount
        ) {
            this.postList = new PostTimeline(orderField);
            IndexName resultIndex;
            QueryEntry.ElasticsearchQuery sq = new QueryEntry.ElasticsearchQuery(
                    inputMap.get("get"), inputMap.get("not_get"), inputMap.get("also_get"));

            this.query = elasticsearch_client.query(
                    (resultIndex = IndexName.messages_hour).name(),
                    sq.queryBuilder,
                    orderField.getMessageFieldName(),
                    resultCount
            );

            if (this.query.getHitCount() < resultCount) {
                this.query =  elasticsearch_client.query(
                        (resultIndex = IndexName.messages_day).name(),
                        sq.queryBuilder,
                        orderField.getMessageFieldName(),
                        resultCount
                );
            }

            if (this.query.getHitCount() < resultCount) {
                this.query =  elasticsearch_client.query(
                        (resultIndex = IndexName.messages_week).name(),
                        sq.queryBuilder,
                        orderField.getMessageFieldName(),
                        resultCount
                );
            }

            if (this.query.getHitCount() < resultCount) {
                this.query =  elasticsearch_client.query(
                        (resultIndex = IndexName.messages).name(),
                        sq.queryBuilder,
                        orderField.getMessageFieldName(),
                        resultCount
                );
            }

            // Feed search results to postList
            this.postList.setResultIndex(resultIndex);

            addResults();
        }

        private void addResults() {
            Post outputPost;
            for (Map<String, Object> map: this.query.getResult()) {
                outputPost = new Post(map);
                this.postList.addPost(outputPost);
            }
        }

        private static boolean insufficient(
                ElasticsearchClient.Query query,
                int resultCount,
                int aggregationLimit,
                String... aggregationFields
        ) {
            return query.getHitCount() < resultCount || (aggregationFields.length > 0
                    && getAggregationResultLimit(query.getAggregations()) < aggregationLimit);
        }

        public JSONObject getAggregations() {
            JSONObject json = new JSONObject(true);
            if (aggregations == null) return json;
            for (Map.Entry<String, List<Map.Entry<String, AtomicLong>>> aggregation: aggregations.entrySet()) {
                JSONObject facet = new JSONObject(true);
                for (Map.Entry<String, AtomicLong> a: aggregation.getValue()) {
                    if (a.getValue().equals(query)) continue; // we omit obvious terms that cannot be used for faceting, like search for "#abc" -> most hashtag is "#abc"
                    facet.put(a.getKey(), a.getValue().get());
                }
                json.put(aggregation.getKey(), facet);
            }
            return json;
        }

        private static int getAggregationResultLimit(Map<String, List<Map.Entry<String, AtomicLong>>> agg) {
            if (agg == null) return 0;
            int l = 0;
            for (List<Map.Entry<String, AtomicLong>> a: agg.values()) l = Math.max(l, a.size());
            return l;
        }
    }

    public static LinkedHashMap<String, Long> FullDateHistogram(int timezoneOffset) {
        return elasticsearch_client.fullDateHistogram(IndexName.messages.name(), timezoneOffset, AbstractObjectEntry.CREATED_AT_FIELDNAME);
    }

    /**
     * Search the local user cache using a elasticsearch query.
     * @param screen_name - the user id
     */
    public static UserEntry searchLocalUserByScreenName(final String screen_name) {
        try {
            return users.read(screen_name);
        } catch (IOException e) {
        	DAO.severe(e);
            return null;
        }
    }

    public static UserEntry searchLocalUserByUserId(final String user_id) {
        if (user_id == null || user_id.length() == 0) return null;
        Map<String, Object> map = elasticsearch_client.query(IndexName.users.name(), UserEntry.field_user_id, user_id);
        if (map == null) return null;
        return new UserEntry(new JSONObject(map));
    }

    /**
     * Search the local account cache using an elasticsearch query.
     * @param screen_name - the user id
     */
    public static AccountEntry searchLocalAccount(final String screen_name) {
        try {
            return accounts.read(screen_name);
        } catch (IOException e) {
        	DAO.severe(e);
            return null;
        }
    }

    /**
     * Search the local message cache using a elasticsearch query.
     * @param q - the query, can be empty for a matchall-query
     * @param resultCount - the number of messages in the result
     * @param sort_field - the field name to sort the result list, i.e. "query_first"
     * @param sort_order - the sort order (you want to use SortOrder.DESC here)
     */
    public static ResultList<QueryEntry> SearchLocalQueries(final String q, final int resultCount, final String sort_field, final String default_sort_type, final SortOrder sort_order, final Date since, final Date until, final String range_field) {
        ResultList<QueryEntry> queries = new ResultList<>();
        ResultList<Map<String, Object>> result = elasticsearch_client.fuzzyquery(IndexName.queries.name(), "query", q, resultCount, sort_field, default_sort_type, sort_order, since, until, range_field);
        queries.setHits(result.getHits());
        for (Map<String, Object> map: result) {
            queries.add(new QueryEntry(new JSONObject(map)));
        }
        return queries;
    }

    public static ImportProfileEntry SearchLocalImportProfiles(final String id) {
        try {
            return importProfiles.read(id);
        } catch (IOException e) {
        	DAO.severe(e);
            return null;
        }
    }

    public static Collection<ImportProfileEntry> SearchLocalImportProfilesWithConstraints(final Map<String, String> constraints, boolean latest) throws IOException {
        List<ImportProfileEntry> rawResults = new ArrayList<>();
        List<Map<String, Object>> result = elasticsearch_client.queryWithConstraints(IndexName.import_profiles.name(), "active_status", ImportProfileEntry.EntryStatus.ACTIVE.name().toLowerCase(), constraints, latest);
        for (Map<String, Object> map: result) {
            rawResults.add(new ImportProfileEntry(new JSONObject(map)));
        }

        if (!latest) {
            return rawResults;
        }

        // filter results to display only latest profiles
        Map<String, ImportProfileEntry> latests = new HashMap<>();
        for (ImportProfileEntry entry : rawResults) {
            String uniqueKey;
            if (entry.getImporter() != null) {
                uniqueKey = entry.getSourceUrl() + entry.getImporter();
            } else {
                uniqueKey = entry.getSourceUrl() + entry.getClientHost();
            }
            if (latests.containsKey(uniqueKey)) {
                if (entry.getLastModified().compareTo(latests.get(uniqueKey).getLastModified()) > 0) {
                    latests.put(uniqueKey, entry);
                }
            } else {
                latests.put(uniqueKey, entry);
            }
        }
        return latests.values();
    }

    public static TwitterTimeline scrapeTwitter(
            final Query post,
            final String q,
            final TwitterTimeline.Order order,
            final int timezoneOffset,
            boolean byUserQuery,
            long timeout,
            boolean recordQuery) {

        return scrapeTwitter(post, new ArrayList<>(), q, order, timezoneOffset, byUserQuery, timeout, recordQuery);
    }

    public static TwitterTimeline scrapeTwitter(
            final Query post,
            final ArrayList<String> filterList,
            final String q,
            final TwitterTimeline.Order order,
            final int timezoneOffset,
            boolean byUserQuery,
            long timeout,
            boolean recordQuery) {
        // retrieve messages from remote server

        ArrayList<String> remote = DAO.getFrontPeers();
        TwitterTimeline tl;
        if (remote.size() > 0 && (peerLatency.get(remote.get(0)) == null || peerLatency.get(remote.get(0)).longValue() < 3000)) {
            long start = System.currentTimeMillis();
            tl = searchOnOtherPeers(remote, q, filterList, order, 100, timezoneOffset, "all", SearchServlet.frontpeer_hash, timeout); // all must be selected here to catch up missing tweets between intervals
            // at this point the remote list can be empty as a side-effect of the remote search attempt
            if (post != null && remote.size() > 0 && tl != null) post.recordEvent("remote_scraper_on_" + remote.get(0), System.currentTimeMillis() - start);
            if (tl == null || tl.size() == 0) {
                // maybe the remote server died, we try then ourself
                start = System.currentTimeMillis();

                tl = TwitterScraper.search(q, filterList, order, true, true, 400);
                if (post != null) post.recordEvent("local_scraper_after_unsuccessful_remote", System.currentTimeMillis() - start);
            } else {
                tl.writeToIndex();
            }
        } else {
            if (post != null && remote.size() > 0) post.recordEvent("omitted_scraper_latency_" + remote.get(0), peerLatency.get(remote.get(0)));
            long start = System.currentTimeMillis();

            tl = TwitterScraper.search(q, filterList, order, true, true, 400);
            if (post != null) post.recordEvent("local_scraper", System.currentTimeMillis() - start);
        }

        // record the query
        long start2 = System.currentTimeMillis();
        QueryEntry qe = null;
        try {
            qe = queries.read(q);
        } catch (IOException | JSONException e) {
        	DAO.severe(e);
        }

        if (recordQuery && Caretaker.acceptQuery4Retrieval(q)) {
            if (qe == null) {
                // a new query occurred
                qe = new QueryEntry(q, timezoneOffset, tl.period(), SourceType.TWITTER, byUserQuery);
            } else {
                // existing queries are updated
                qe.update(tl.period(), byUserQuery);
            }
            try {
                queries.writeEntryAsync(new IndexEntry<QueryEntry>(q, qe.source_type == null ? SourceType.TWITTER : qe.source_type, qe));
            } catch (IOException e) {
            	DAO.severe(e);
            }
        } else {
            // accept rules may change, we want to delete the query then in the index
            if (qe != null) queries.delete(q, qe.source_type);
        }
        if (post != null) post.recordEvent("query_recorder", System.currentTimeMillis() - start2);
        //log("SCRAPER: TIME LEFT after recording = " + (termination - System.currentTimeMillis()));

        return tl;
    }


    public static JSONArray scrapeLoklak(
            Map<String, String> inputMap,
            boolean byUserQuery,
            boolean recordQuery,
            JSONObject metadata) {
        PostTimeline.Order order= getOrder(inputMap.get("order"));
        PostTimeline dataSet = new PostTimeline(order);
        List<String> scraperList = Arrays.asList(inputMap.get("scraper").trim().split("\\s*,\\s*"));
        List<BaseScraper> scraperObjList = getScraperObjects(scraperList, inputMap);
        ExecutorService scraperRunner = Executors.newFixedThreadPool(scraperObjList.size());

        try{
            for (BaseScraper scraper : scraperObjList) {
                scraperRunner.execute(() -> {
                    dataSet.add(scraper.getData());
                    
                });
            }
        } finally {
            scraperRunner.shutdown();
            try {
                scraperRunner.awaitTermination(3000L, TimeUnit.SECONDS);
            } catch (InterruptedException e) { }
        }
        dataSet.collectMetadata(metadata);
        return dataSet.toArray();
    }

    public static List<BaseScraper> getScraperObjects(List<String> scraperList, Map<String, String> inputMap) {
        //TODO: use SourceType to get this job done
        List<BaseScraper> scraperObjList = new ArrayList<BaseScraper>();
        BaseScraper scraperObj = null;

        if (scraperList.contains("github") || scraperList.contains("all")) {
            scraperObj = new GithubProfileScraper(inputMap);
            scraperObjList.add(scraperObj);
        }
        if (scraperList.contains("quora") || scraperList.contains("all")) {
            scraperObj = new QuoraProfileScraper(inputMap);
            scraperObjList.add(scraperObj);
        }
        if (scraperList.contains("instagram") || scraperList.contains("all")) {
            scraperObj = new InstagramProfileScraper(inputMap);
            scraperObjList.add(scraperObj);
        }
        if (scraperList.contains("youtube") || scraperList.contains("all")) {
            scraperObj = new YoutubeScraper(inputMap);
            scraperObjList.add(scraperObj);
        }
        if (scraperList.contains("wordpress") || scraperList.contains("all")) {
            scraperObj = new WordpressCrawlerService(inputMap);
            scraperObjList.add(scraperObj);
        }
        if (scraperList.contains("twitter") || scraperList.contains("all")) {
            scraperObj = new TweetScraper(inputMap);
            scraperObjList.add(scraperObj);
        }
        //TODO: add more scrapers
        return scraperObjList;
    }

    public static PostTimeline.Order getOrder(String orderString) {
        //TODO: order set according to input
        return PostTimeline.parseOrder("timestamp");
    }

    public static final Random random = new Random(System.currentTimeMillis());
    private static final Map<String, Long> peerLatency = new HashMap<>();
    private static ArrayList<String> getBestPeers(Collection<String> peers) {
        ArrayList<String> best = new ArrayList<>();
        if (peers == null || peers.size() == 0) return best;
        // first check if any of the given peers has unknown latency
        TreeMap<Long, String> o = new TreeMap<>();
        for (String peer: peers) {
            if (peerLatency.containsKey(peer)) {
                o.put(peerLatency.get(peer) * 1000 + best.size(), peer);
            } else {
                best.add(peer);
            }
        }
        best.addAll(o.values());
        return best;
    }
    public static void healLatency(float factor) {
        for (Map.Entry<String, Long> entry: peerLatency.entrySet()) {
            entry.setValue((long) (factor * entry.getValue()));
        }
    }

    private static Set<String> frontPeerCache = new HashSet<String>();
    private static Set<String> backendPeerCache = new HashSet<String>();

    public static void updateFrontPeerCache(RemoteAccess remoteAccess) {
        if (remoteAccess.getLocalHTTPPort() >= 80) {
            frontPeerCache.add("http://" + remoteAccess.getRemoteHost() + (remoteAccess.getLocalHTTPPort() == 80 ? "" : ":" + remoteAccess.getLocalHTTPPort()));
        } else if (remoteAccess.getLocalHTTPSPort() >= 443) {
            frontPeerCache.add("https://" + remoteAccess.getRemoteHost() + (remoteAccess.getLocalHTTPSPort() == 443 ? "" : ":" + remoteAccess.getLocalHTTPSPort()));
        }
    }

    /**
     * from all known front peers, generate a list of available peers, ordered by the peer latency
     * @return a list of front peers. only the first one shall be used, but the other are fail-over peers
     */
    public static ArrayList<String> getFrontPeers() {
        String[] remote = DAO.getConfig("frontpeers", new String[0], ",");
        ArrayList<String> testpeers = new ArrayList<>();
        if (remote.length > 0) {
            for (String peer: remote) testpeers.add(peer);
            return testpeers;
        }
        if (frontPeerCache.size() == 0) {
            // add dynamically all peers that contacted myself
            for (Map<String, RemoteAccess> hmap: RemoteAccess.history.values()) {
                for (Map.Entry<String, RemoteAccess> peer: hmap.entrySet()) {
                    updateFrontPeerCache(peer.getValue());
                }
            }
        }
        testpeers.addAll(frontPeerCache);
        return getBestPeers(testpeers);
    }

    public static String[] getBackend() {
        return DAO.getConfig("backend", new String[0], ",");
    }
    
    public static List<String> getBackendPeers() {
        List<String> testpeers = new ArrayList<>();
        if (backendPeerCache.size() == 0) {
            final String[] remote = DAO.getBackend();
            for (String peer: remote) backendPeerCache.add(peer);
        }
        testpeers.addAll(backendPeerCache);
        return getBestPeers(testpeers);
    }

    public static TwitterTimeline searchBackend(final String q,final ArrayList<String> filterList, final TwitterTimeline.Order order, final int count, final int timezoneOffset, final String where, final long timeout) {
        List<String> remote = getBackendPeers();

        if (remote.size() > 0 /*&& (peerLatency.get(remote.get(0)) == null || peerLatency.get(remote.get(0)) < 3000)*/) { // condition deactivated because we need always at least one peer
            TwitterTimeline tt = searchOnOtherPeers(remote, q, filterList, order, count, timezoneOffset, where, SearchServlet.backend_hash, timeout);
            if (tt != null) tt.writeToIndex();
            return tt;
        }
        return null;
    }

    private final static Random randomPicker = new Random(System.currentTimeMillis());

    public static TwitterTimeline searchOnOtherPeers(final List<String> remote, final String q, final ArrayList<String> filterList,final TwitterTimeline.Order order, final int count, final int timezoneOffset, final String source, final String provider_hash, final long timeout) {
        // select remote peer
        while (remote.size() > 0) {
            int pick = randomPicker.nextInt(remote.size());
            String peer = remote.get(pick);
            long start = System.currentTimeMillis();
            try {
                TwitterTimeline tl = SearchServlet.search(new String[]{peer}, q, filterList, order, source, count, timezoneOffset, provider_hash, timeout);
                peerLatency.put(peer, System.currentTimeMillis() - start);
                // to show which peer was used for the retrieval, we move the picked peer to the front of the list
                if (pick != 0) remote.add(0, remote.remove(pick));
                tl.setScraperInfo(tl.getScraperInfo().length() > 0 ? peer + "," + tl.getScraperInfo() : peer);
                return tl;
            } catch (IOException e) {
                DAO.log("searchOnOtherPeers: no IO to scraping target: " + e.getMessage());
                // the remote peer seems to be unresponsive, remove it (temporary) from the remote peer list
                peerLatency.put(peer, DateParser.HOUR_MILLIS);
                frontPeerCache.remove(peer);
                backendPeerCache.remove(peer);
                remote.remove(pick);
            }
        }
        return null;
    }

    public final static Set<Number> newUserIds = ConcurrentHashMap.newKeySet();

    public static void announceNewUserId(TwitterTimeline tl) {
        for (TwitterTweet message: tl) {
            UserEntry user = tl.getUser(message);
            assert user != null;
            if (user == null) continue;
            Number id = user.getUser();
            if (id != null) announceNewUserId(id);
        }
    }

    public static void announceNewUserId(Number id) {
        JsonFactory mapcapsule = DAO.user_dump.get("id_str", id.toString());
        JSONObject map = null;
        try {map = mapcapsule == null ? null : mapcapsule.getJSON();} catch (IOException e) {}
        if (map == null) newUserIds.add(id);
    }

    public static Set<Number> getNewUserIdsChunk() {
        if (newUserIds.size() < 100) return null;
        Set<Number> chunk = new HashSet<>();
        Iterator<Number> i = newUserIds.iterator();
        for (int j = 0; j < 100; j++) {
            chunk.add(i.next());
            i.remove();
        }
        return chunk;
    }

    /**
     * For logging informational events
     */
    public static void log(String line) {
        if (DAO.getConfig("flag.log.dao", "true").equals("true")) {
            logger.info(line);
        }
    }

    /**
     * For events serious enough to inform and log, but not fatal.
     */
    public static void severe(String line) {
        if (DAO.getConfig("flag.severe.dao", "true").equals("true")) {
            logger.warn(line);
        }
    }

    public static void severe(String line, Throwable e) {
        if (DAO.getConfig("flag.severe.dao", "true").equals("true")) {
            logger.warn(line, e);
        }
    }

    public static void severe(Throwable e) {
        if (DAO.getConfig("flag.severe.dao", "true").equals("true")) {
            logger.warn("", e);
        }
    }

    /**
     * For Debugging events (very noisy).
     */
    public static void debug(Throwable e) {
        if (DAO.getConfig("flag.debug.dao", "true").equals("true")) {
            DAO.severe(e);
        }
    }

    /**
     * For Stacktracing exceptions (preferred over debug).
     */
    public static void trace(Throwable e) {
        if(DAO.getConfig("flag.trace.dao", "true").equals("true")) {
            e.printStackTrace();
        }
    }
}

package org.loklak.data;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * @author dawei.ma
 * @date 2018/9/6 19:45
 */
public class MongoDBManager {
    private MongoClient mongoClient;
    private String clientId;
    private MongoDatabase database;

    public MongoDBManager(String address, String clientId) {
        this.mongoClient = new MongoClient(new MongoClientURI(address));
        this.clientId = clientId;
        this.database = mongoClient.getDatabase(clientId);
    }

    public MongoDBManager(String address) {
        this(address, "loklak_server");
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public String getClientId() {
        return clientId;
    }

    public void saveChannelMessage(String channel, String message) {
        MongoCollection<Document> collection = database.getCollection(channel);
        Document doc = Document.parse(message);
        collection.insertOne(doc);
    }
}


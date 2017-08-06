package org.loklak.stream;

import net.sf.xenqtt.client.AsyncClientListener;
import net.sf.xenqtt.client.AsyncMqttClient;
import net.sf.xenqtt.client.MqttClient;
import net.sf.xenqtt.client.PublishMessage;
import net.sf.xenqtt.client.Subscription;
import net.sf.xenqtt.message.ConnectReturnCode;
import net.sf.xenqtt.message.QoS;
import org.loklak.data.DAO;

public class MQTTPublisher {

    private AsyncMqttClient mqttClient;

    public MQTTPublisher(String address) {
        AsyncClientListener listener = new AsyncClientListener() {

            @Override
            public void publishReceived(MqttClient client, PublishMessage message) {
                DAO.severe("Received a message when no subscriptions were active. Please check your broker.");
            }

            @Override
            public void disconnected(MqttClient client, Throwable cause, boolean reconnecting) {
                if (cause != null) {
                    DAO.severe("Disconnected from the broker due to an exception.", cause);
                } else {
                    DAO.log("Disconnected from the broker.");
                }
                if (reconnecting) {
                    DAO.log("Attempting to reconnect to the broker.");
                }
            }

            @Override
            public void connected(MqttClient client, ConnectReturnCode returnCode) {
                DAO.log("Connected to client " + client + " with return code " + returnCode);
            }

            @Override
            public void subscribed(MqttClient client, Subscription[] requestedSubscriptions, Subscription[] grantedSubscriptions, boolean requestsGranted) {
                DAO.log("Subscribed to " + grantedSubscriptions.length + "/" + requestedSubscriptions.length + " subscriptions.");
            }

            @Override
            public void unsubscribed(MqttClient client, String[] topics) {
                DAO.log("Unsubscribed from " + topics.length + " topics.");
            }

            @Override
            public void published(MqttClient client, PublishMessage message) {
            }

        };

        this.mqttClient = new AsyncMqttClient(address, listener, 5);
        this.mqttClient.connect("loklak_server", false);
    }

    public void publish(String channel, String message) {
        this.mqttClient.publish(new PublishMessage(channel, QoS.AT_LEAST_ONCE, message));
    }

    public void publish(String channel, String[] messages) {
        for (String message : messages) {
            this.publish(channel, message);
        }
    }

    public void publish(String[] channels, String message) {
        for (String channel : channels) {
            this.publish(channel, message);
        }
    }

    public void publish(String[] channels, String[] messages) {
        for (String channel : channels) {
            this.publish(channel, messages);
        }
    }
}

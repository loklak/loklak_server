package org.loklak.stream;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.loklak.data.DAO;

public class MQTTPublisher {

    private MqttClient client;
    private String clientId;
    private MemoryPersistence persistence = new MemoryPersistence();
    private int qos;

    public MQTTPublisher(String address, String clientId, boolean cleanSession, int qos) throws MqttException {
        this.clientId = clientId;
        this.qos = qos;
        this.client = new MqttClient(address, clientId, persistence);
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(cleanSession);
        this.client.connect();
    }

    public MQTTPublisher(String address) throws MqttException {
        this(address, "loklak_server", true, 0);
    }

    public MemoryPersistence getMemoryPersistence() {
        return this.persistence;
    }

    public String getClientId() {
        return this.clientId;
    }

    public MqttClient getClient() {
        return this.client;
    }

    public void publish(String channel, String message) {
        channel = channel.replaceAll("#", "");
        DAO.log("Publishing " + message.substring(0, 10) + " to " + channel);
        MqttMessage msg = new MqttMessage(message.getBytes());
        msg.setQos(this.qos);
        try {
            this.client.publish(channel, msg);
        } catch (MqttException e) {
            DAO.severe("Failed to publish message to channel " + channel + " due to MQTT exception", e);
        } catch (IllegalArgumentException e) {
            DAO.severe("Failed to publish message to channel " + channel + " because of invalid channel name", e);
        } catch (Exception e) {
            DAO.severe("Failed to publish message to channel " + channel + " due to unknown exception", e);
        }
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

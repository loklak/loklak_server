package org.loklak.stream;

import org.eclipse.jetty.servlets.EventSource;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.loklak.data.DAO;

import java.io.IOException;

public class MqttEventSource implements EventSource, MqttCallback {

    private Emitter emitter;
    private MqttClient mqttClient;
    private String channel;

    MqttEventSource(String channel) {
        this.channel = channel;
    }

    @Override
    public void onOpen(Emitter emitter) throws IOException {
        this.emitter = emitter;
        try {
            this.mqttClient = new MqttClient(DAO.getConfig("stream.mqtt.address", "tcp://127.0.0.1:1883"), "loklak_server_subscriber");
            this.mqttClient.connect();
            this.mqttClient.setCallback(this);
            this.mqttClient.subscribe(this.channel);
        } catch (MqttException e) {
            this.emitter.close();
        }
    }

    @Override
    public void onClose() {
        try {
            this.mqttClient.close();
            this.mqttClient.disconnect();
        } catch (MqttException e) {
            DAO.severe("Error terminating the stream client for API request", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        DAO.severe("Connection lost for Stream request at channel " + this.channel, cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        this.emitter.data(message.toString());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Cool! nothing to do here.
    }
}

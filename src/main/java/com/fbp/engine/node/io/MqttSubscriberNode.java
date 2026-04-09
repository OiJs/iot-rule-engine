package com.fbp.engine.node.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class MqttSubscriberNode extends ProtocolNode{
    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttSubscriberNode(String id, Map<String, Object> config) {
        super(id, config);
        addOutputPort("out");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void connect() throws Exception {
        String brokerUrl = (String) getConfig("brokerUrl");
        String clientId = (String) getConfig("clientId");
        String topic = (String) getConfig("topic");
        Object qosObj = getConfig("qos");
        int qos = (qosObj != null) ? ((Number) qosObj).intValue() : 1;

        client = new MqttClient(brokerUrl, clientId, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setAutomaticReconnect(true);

        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
                System.err.println("[" + getId() + "] MQTT 브로커와 연결이 끊어졌습니다: " + mqttDisconnectResponse.getReasonString());
                reconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {

            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {

            }

            @Override
            public void deliveryComplete(IMqttToken iMqttToken) {

            }

            @Override
            public void connectComplete(boolean b, String s) {

            }

            @Override
            public void authPacketArrived(int i, MqttProperties mqttProperties) {

            }
        });

        client.connect(options);

        client.subscribe(topic, qos, (t, msg) -> {
            String rawPayload = new String(msg.getPayload());
            Map<String, Object> payloadMap;

            try {
                payloadMap = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
            }catch (Exception e) {
                payloadMap = new HashMap<>();
                payloadMap.put("rawPayload", rawPayload);
                payloadMap.put("error", "JSON Parsing Failed");
            }

            payloadMap.put("topic", t);
            payloadMap.put("mqttTimestamp", System.currentTimeMillis());

            Message fbpMessage = new Message(payloadMap);
            send("out", fbpMessage);
        });
    }

    @Override
    protected void disconnect() throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}

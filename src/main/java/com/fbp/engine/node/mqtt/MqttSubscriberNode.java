package com.fbp.engine.node.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
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

public class MqttSubscriberNode extends ProtocolNode {
    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttSubscriberNode(String id, Map<String, Object> config) {
        super(id, config);
        addOutputPort("out");
        this.objectMapper = new ObjectMapper();

        if (!config.containsKey("brokerUrl") || !config.containsKey("clientId") || !config.containsKey("topic")) {
            throw new IllegalArgumentException("MqttSubscriberNode에 필수 설정값이 누락되었습니다.");
        }
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
            public void disconnected(MqttDisconnectResponse response) {
                System.err.println("[" + getId() + "] 연결 끊김: " + response.getReasonString());
                reconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                System.err.println("[" + getId() + "] MQTT 에러: " + e.getMessage());
            }

            @Override
            public void messageArrived(String t, MqttMessage msg) throws Exception {
                String rawPayload = new String(msg.getPayload());
                Map<String, Object> payloadMap;

                try {
                    payloadMap = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    System.err.println("[" + getId() + "] JSON 파싱 실패 원본 데이터: " + rawPayload);
                    payloadMap = new HashMap<>();
                    payloadMap.put("rawPayload", rawPayload);
                    payloadMap.put("error", "JSON Parsing Failed");
                }

                payloadMap.put("topic", t);
                payloadMap.put("mqttTimestamp", System.currentTimeMillis());

                Message fbpMessage = new Message(payloadMap);
                send("out", fbpMessage);

                System.out.println("[" + getId() + "] 메시지 수신 및 전달 완료 (Topic: " + t + ")");
            }

            @Override
            public void deliveryComplete(IMqttToken token) {}

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("[" + getId() + "] 브로커 연결 성공: " + serverURI);
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {}
        });

        client.connect(options);
        client.subscribe(topic, qos);
        System.out.println("[" + getId() + "] '" + topic + "' 구독 시작 (QoS: " + qos + ")");
    }

    @Override
    protected void disconnect() throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            System.out.println("[" + getId() + "] MQTT 클라이언트 종료.");
        }
    }
}
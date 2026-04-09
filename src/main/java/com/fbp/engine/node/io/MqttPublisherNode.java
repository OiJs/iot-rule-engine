package com.fbp.engine.node.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import java.util.Map;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttPersistenceException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class MqttPublisherNode extends ProtocolNode {

    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttPublisherNode(String id, Map<String, Object> config) {
        super(id, config);
        addInputPort("in");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void connect() throws Exception {
        String brokerUrl = (String) getConfig("brokerUrl");
        String clientId = (String) getConfig("clientId");

        client = new MqttClient(brokerUrl, clientId, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setAutomaticReconnect(true);

        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
                System.err.println("[" + getId() + "] 브로커 연결 끊김: " + mqttDisconnectResponse.getReasonString());
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
        client.connect();

    }

    @Override
    protected void disconnect() throws Exception {
        if (client != null && client.isConnected()) {
            client.disconnect();
            client.close();
        }
    }

    @Override
    protected void onProcess(Message message) {
        if(!isConnected()) {
            System.err.println("[" + getId() + "] 연결 끊김. 발행을 취소합니다.");
            return;
        }

        try {
            Map<String, Object> payloadMap = message.getPayload();
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);

            String publishTopic;
            if(payloadMap.containsKey("topic")) {
                publishTopic = payloadMap.get("topic").toString();
            } else {
                publishTopic = (String) getConfig("topic");
            }

            if (publishTopic == null || publishTopic.isEmpty()) {
                throw new IllegalArgumentException("발행할 토픽이 지정되지 않았습니다.");
            }

            Object qosObj = getConfig("qos");
            int qos = (qosObj != null) ? ((Number) qosObj).intValue() : 1;

            Object retainedObj = getConfig("retained");
            boolean retained = (retainedObj != null) && (Boolean) retainedObj;

            MqttMessage mqttMessage = new MqttMessage(jsonPayload.getBytes());
            mqttMessage.setQos(qos);
            mqttMessage.setRetained(retained);

            client.publish(publishTopic, mqttMessage);
            System.out.println("[" + getId() + "] 발행 완료 -> " + publishTopic + " : " + jsonPayload);

        } catch (JsonProcessingException e) {
            System.err.println("[" + getId() + "] JSON 변환 실패: " + e.getMessage());        }
        catch (Exception e) {
            System.err.println("[" + getId() + "] 메시지 발행 중 오류: " + e.getMessage());
        }
    }
}

package com.fbp.engine.flowtest;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import java.nio.charset.StandardCharsets;

/**
 * 과제 2-2: Paho MQTT v5 라이브러리 독립 테스트 프로그램
 * 목적: v5 라이브러리를 이용한 Broker 연결, 구독, 발행 기초 익히기
 */
//TODO Stage2 2-2
public class MqttV5SimpleTest {

    public static void main(String[] args) {
        String broker = "tcp://localhost:1883";
        String clientId = "Junseo_v5_Client";
        String topic = "nhn/test/v5";

        try {
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);
            options.setConnectionTimeout(30);

            // 2. MqttAsyncClient 생성 (StackOverflow 버그 우회를 위해 비동기 클라이언트 사용)
            MqttAsyncClient client = new MqttAsyncClient(broker, clientId, new MemoryPersistence());

            // 3. 브로커 연결 (비동기 방식이므로 waitForCompletion으로 대기)
            System.out.println("Connecting to broker...");
            client.connect(options).waitForCompletion();
            System.out.println("Connected successfully!");

            // 4. 구독(Subscribe)
            // v5 라이브러리 버그를 방지하기 위해 AsyncClient의 subscribe를 직접 호출
            client.subscribe(topic, 1).waitForCompletion();

            // 5. 발행(Publish)
            String content = "Hello MQTT v5 - Fixed Version";
            MqttMessage message = new MqttMessage(content.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);

            System.out.println("[발행] 토픽: " + topic + ", 메시지: " + content);
            client.publish(topic, message).waitForCompletion();

            // 6. 대기 및 종료
            Thread.sleep(2000);
            client.disconnect().waitForCompletion();
            System.out.println("Disconnected.");
            System.exit(0);

        } catch (MqttException | InterruptedException e) {
            System.err.println("에러 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
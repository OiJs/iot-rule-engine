package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.RuleNode;
import com.fbp.engine.node.TimerNode;
import com.fbp.engine.node.modbus.ModbusReaderNode;
import com.fbp.engine.node.modbus.ModbusWriterNode;
import com.fbp.engine.node.mqtt.MqttPublisherNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;

//TODO Stage2 5-1
public class TestScenario {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final int MODBUS_PORT = 5020;
    private static final AtomicInteger processedCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        FlowEngine engine = new FlowEngine();

        ModbusTcpSimulator simulator = new ModbusTcpSimulator(MODBUS_PORT, 20);
        simulator.start();
        Thread.sleep(500);

        setupScenario1(engine);
        setupScenario2(engine, MODBUS_PORT);
        setupScenario3(engine, MODBUS_PORT);

        engine.startFlow("mqtt-only-flow");
        engine.startFlow("modbus-auto-flow");
        engine.startFlow("bridge-flow");
        System.out.println(">>> [INFO] 모든시나리오 가동 중...");

        runPerformanceTest(100, 5);
        runPerformanceTest(500, 5);
        runPerformanceTest(1000, 5);

        // 에러 시뮬레이션
        runErrorSimulation(simulator);

        Thread.sleep(Long.MAX_VALUE);
    }

    public static void setupScenario1(FlowEngine engine) {
        MqttSubscriberNode sub = new MqttSubscriberNode("sub1", Map.of(
                "brokerUrl", BROKER_URL, "topic", "sensor/temp", "clientId", "sub-1"));
        RuleNode rule = new RuleNode("rule1", "value > 30.0");
        MqttPublisherNode pub = new MqttPublisherNode("pub1", Map.of(
                "brokerUrl", BROKER_URL, "topic", "alert/temp", "clientId", "pub-1"));

        Flow flow = new Flow("mqtt-only-flow")
                .addNode(sub).addNode(rule).addNode(pub)
                .connect("sub1", "out", "rule1", "in")
                .connect("rule1", "match", "pub1", "in");
        engine.register(flow);
    }

    public static void setupScenario2(FlowEngine engine, int port) {
        TimerNode timer = new TimerNode("timer2", Map.of("intervalMs", 1000));
        ModbusReaderNode reader = new ModbusReaderNode("reader2", Map.of(
                "host", "localhost", "port", port, "startAddress", 0, "count", 1,
                "registerMapping", Map.of("temp", 0)));
        RuleNode rule = new RuleNode("rule2", "temp > 300"); // 30.0도 기준
        ModbusWriterNode writer = new ModbusWriterNode("writer2", Map.of(
                "host", "localhost", "port", port, "registerAddress", 5, "valueField", "temp"));

        Flow flow = new Flow("modbus-auto-flow")
                .addNode(timer).addNode(reader).addNode(rule).addNode(writer)
                .connect("timer2", "out", "reader2", "trigger")
                .connect("reader2", "out", "rule2", "in")
                .connect("rule2", "match", "writer2", "in");

        engine.register(flow);
    }

    public static void setupScenario3(FlowEngine engine, int port) {
        MqttSubscriberNode sub = new MqttSubscriberNode("sub3", Map.of(
                "brokerUrl", BROKER_URL, "topic", "command/device", "clientId", "sub-3"));
        RuleNode rule = new RuleNode("rule3", "value == 1.0");
        ModbusWriterNode writer = new ModbusWriterNode("writer3", Map.of(
                "host", "localhost", "port", port, "registerAddress", 10, "valueField", "value"));

        Flow flow = new Flow("bridge-flow")
                .addNode(sub).addNode(rule).addNode(writer)
                .connect("sub3", "out", "rule3", "in")
                .connect("rule3", "match", "writer3", "in");

        engine.register(flow);
    }

    //TODO Stage2 5-3
    public static void runPerformanceTest(int tps, int duration) throws Exception {
        MqttClient client = new MqttClient(BROKER_URL, "perf-injector");
        client.connect();

        long intervalNs = 1_000_000_000L / tps;
        int totalMessages = tps * duration;

        System.out.println("\n>>> [PERF] 측정 시작 (Target TPS: " + tps + ")");

        long testStartTime = System.currentTimeMillis();

        for (int i = 0; i < totalMessages; i++) {
            long now = System.nanoTime();
            String msg = String.format("{\"value\": 35.5, \"startTime\": %d}", now);
            client.publish("sensor/temp", new MqttMessage(msg.getBytes()));

            while (System.nanoTime() - now < intervalNs);
        }

        Thread.sleep(2000);

        long totalTimeMs = System.currentTimeMillis() - testStartTime - 2000;

        double throughput = (double) totalMessages / (totalTimeMs / 1000.0);

        System.out.println("--------------------------------------------");
        System.out.println("실측 Throughput : " + String.format("%.2f", throughput) + " msg/s");
        System.out.println("전체 소요 시간   : " + totalTimeMs + " ms");
        System.out.println("--------------------------------------------");

        client.disconnect();
    }

    public static void runErrorSimulation(ModbusTcpSimulator simulator) throws Exception {
        Thread.sleep(2000);
        System.out.println(">>> [ERROR] 시뮬레이션: 시뮬레이터 중단");
        simulator.stop();
        Thread.sleep(3000); // 에러 로그 확인용 대기
        System.out.println(">>> [ERROR] 시뮬레이션: 시뮬레이터 복구");
        simulator.start();
    }
}


//TODO Stage2 5-2
//에러 상황 테스트 1. MQTT Broker 연결 끊김 및 자동 복구

//연결 재시도 로직 동작
//[mqtt-sub] 연결 실패: 서버에 연결할 수 없음
//[pub] 연결 실패: 서버에 연결할 수 없음
//[mqtt-in] 연결 실패: 서버에 연결할 수 없음
//[mqtt-sub] 5000ms 후 재연결을 시도합니다. (2/10)
//[mqtt-in] 5000ms 후 재연결을 시도합니다. (2/10)
//[pub] 5000ms 후 재연결을 시도합니다. (2/10)

//[pub] 외부 시스템 연결 성공
//[mqtt-sub] 브로커 연결 성공: tcp://localhost:1883
//[mqtt-in] 브로커 연결 성공: tcp://localhost:1883
//[mqtt-sub] 'sensor/temp' 구독 시작 (QoS: 1)
//[mqtt-sub] 외부 시스템 연결 성공
//[mqtt-in] 'command/device' 구독 시작 (QoS: 1)
//[mqtt-in] 외부 시스템 연결 성공

// 연결이 끊긴 동안 발생한 메시지들을 메모리에 들고 있다가 연결 직후 발행 할지 버릴지에 대한 정책 필요


//에러 상황 테스트 2.  MODBUS 장비 응답 없음 시뮬레이터 중지
//네트워크 서버 중단 후에도 소켓 세션 유지 및 객체 생존으로 인해 즉각적인 장애 감지가 지연

//에러 상황 테스트 3. 잘못된 데이터 수신
//[mqtt-sub] JSON 파싱 실패 원본 데이터: This is not JSON

//           } catch (Exception e) {
//        System.err.println("[" + getId() + "] JSON 파싱 실패 원본 데이터: " + rawPayload);
//        payloadMap = new HashMap<>();
//        payloadMap.put("rawPayload", rawPayload);
//                    payloadMap.put("error", "JSON Parsing Failed");
//            }
//비정상 데이터로 인해 전체 엔진이 멈추는 상황 방지
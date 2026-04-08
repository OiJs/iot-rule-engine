package com.fbp.engine.node;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TemperatureMonitoringFlowTest {

    private FlowEngine engine;
    private final double THRESHOLD = 30.0;
    private final int TICK_COUNT = 5; // 테스트를 위해 5번만 실행

    private TestCollectorNode alertCollector;
    private TestCollectorNode normalCollector;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();

        TemperatureSensorNode sensor = new TemperatureSensorNode("sensor", 10.0, 50.0);
        ThresholdFilterNode filter = new ThresholdFilterNode("filter", "temperature", THRESHOLD);
        alertCollector = new TestCollectorNode("alert-collector");
        normalCollector = new TestCollectorNode("normal-collector");

        Flow monitoringFlow = new Flow("temp-monitoring")
                .addNode(sensor)
                .addNode(filter)
                .addNode(alertCollector)
                .addNode(normalCollector)
                .connect("sensor", "out", "filter", "in")
                .connect("filter", "alert", "alert-collector", "in")
                .connect("filter", "normal", "normal-collector", "in");

        engine.register(monitoringFlow);
        engine.startFlow("temp-monitoring");

        for (int i = 0; i < TICK_COUNT; i++) {
            sensor.onProcess(new Message(Map.of()));
        }
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("1. alert 경로 검증: 수집된 모든 온도가 임계값을 초과하는가")
    void test1_AlertPathValidation() throws InterruptedException {
        Thread.sleep(1000);

        List<Message> alertMessages = alertCollector.getAllMessages();
        for (Message m : alertMessages) {
            double temp = m.get("temperature");
            assertTrue(temp > THRESHOLD, "alert 경로에 임계값 이하 데이터가 섞여있음: " + temp);
        }
    }

    @Test
    @DisplayName("2. normal 경로 검증: 수집된 모든 온도가 임계값 이하인가")
    void test2_NormalPathValidation() throws InterruptedException {
        Thread.sleep(1000);

        List<Message> normalMessages = normalCollector.getAllMessages();
        for (Message m : normalMessages) {
            double temp = m.get("temperature");
            assertTrue(temp <= THRESHOLD, "normal 경로에 임계값 초과 데이터가 섞여있음: " + temp);
        }
    }

    @Test
    @DisplayName("3. 전체 메시지 수 검증: (Alert + Normal) 수 = 트리거 횟수")
    void test3_TotalMessageCount() throws InterruptedException {
        Thread.sleep(1000);

        int alertSize = alertCollector.getAllMessages().size();
        int normalSize = normalCollector.getAllMessages().size();

        assertEquals(TICK_COUNT, alertSize + normalSize, 
            "분실된 메시지가 있거나 중복 처리되었습니다.");
    }

    private static class TestCollectorNode extends AbstractNode {
        private final List<Message> receivedMessages = new ArrayList<>();

        public TestCollectorNode(String id) {
            super(id);
            addInputPort("in");
        }

        @Override
        protected synchronized void onProcess(Message message) {
            receivedMessages.add(message);
        }

        public synchronized List<Message> getAllMessages() {
            return new ArrayList<>(receivedMessages);
        }
    }
}
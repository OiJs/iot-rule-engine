package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemperatureSensorNodeTest {
    private FlowEngine engine;
    private TemperatureSensorNode sensor;
    private TestSinkNode sink;
    private final String SENSOR_ID = "temp-sensor-1";
    private final double MIN = 15.0;
    private final double MAX = 45.0;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        sensor = new TemperatureSensorNode(SENSOR_ID, MIN, MAX);
        sink = new TestSinkNode("sink");

        Flow flow = new Flow("temp-test-flow")
                .addNode(sensor)
                .addNode(sink)
                .connect(SENSOR_ID, "out", "sink", "in");

        engine.register(flow);
        engine.startFlow("temp-test-flow");
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("test1_온도 범위 및 소수점 정밀도 확인")
    void test1_TemperatureRangeAndPrecision() throws InterruptedException {
        sensor.onProcess(new Message(Map.of()));

        Message received = sink.getNextMessage(2, TimeUnit.SECONDS);

        assertNotNull(received, "메시지 수신 X");
        Double temp = received.get("temperature");

        assertTrue(temp >= MIN && temp <= MAX);
        assertEquals(temp, Math.round(temp * 10.0) / 10.0);
    }

    @Test
    @DisplayName("필수 키(sensorId, temperature, unit, timestamp) 포함 확인")
    void test2_RequiredKeys() throws InterruptedException {
        sensor.onProcess(new Message(Map.of()));
        Message msg = sink.getNextMessage(2, TimeUnit.SECONDS);

        assertAll("페이로드 필수 구성 요소 검증",
                () -> assertTrue(msg.hasKey("sensorId")),
                () -> assertTrue(msg.hasKey("temperature")),
                () -> assertTrue(msg.hasKey("unit")),
                () -> assertNotNull(msg.getTimestamp(), "timestamp가 누락되었습니다.")
        );
    }

    @Test
    @DisplayName("sensorId가 노드 ID와 일치하는지 확인")
    void test3_SensorIdMatch() throws InterruptedException {
        sensor.onProcess(new Message(Map.of()));
        Message msg = sink.getNextMessage(2, TimeUnit.SECONDS);

        assertEquals(SENSOR_ID, msg.get("sensorId"), "메시지의 sensorId가 노드 ID와 다릅니다.");
    }

    @Test
    @DisplayName("트리거 3번 발생 시 메시지 3개 생성 확인")
    void test4_MultipleTriggers() throws InterruptedException {
        int count = 3;
        for (int i = 0; i < count; i++) {
            sensor.onProcess(new Message(Map.of()));
        }

        for (int i = 0; i < count; i++) {
            Message msg = sink.getNextMessage(2, TimeUnit.SECONDS);
            assertNotNull(msg, (i + 1) + "번째 메시지가 누락되었습니다.");
        }
    }

    private static class TestSinkNode extends AbstractNode {
        private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

        public TestSinkNode(String id) {
            super(id);
            addInputPort("in");
        }

        @Override
        protected void onProcess(Message message) {
            queue.offer(message);
        }

        public Message getNextMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }
}

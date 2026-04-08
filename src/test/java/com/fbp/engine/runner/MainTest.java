package com.fbp.engine.runner;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MainTest {

    private FlowEngine engine;
    private final String LOG_PATH = "./log/test-temp-flow.log";
    private final double THRESHOLD = 30.0;
    private final int TICK_COUNT = 10;

    private CollectorNode alertCollector;
    private CollectorNode normalCollector;

    @BeforeAll
    void setUpAll() throws IOException, InterruptedException {
        engine = new FlowEngine();
        Files.createDirectories(Paths.get("./log"));
        Files.deleteIfExists(Paths.get(LOG_PATH));

        TemperatureSensorNode sensor = new TemperatureSensorNode("tempSensor", 15.0, 45.0);
        ThresholdFilterNode filter = new ThresholdFilterNode("filter", "temperature", THRESHOLD);
        alertCollector = new CollectorNode("alert-collector");
        normalCollector = new CollectorNode("normal-collector");
        FileWriteNode fileWriter = new FileWriteNode("file-writer", LOG_PATH);

        Flow flow = new Flow("final-flow")
                .addNode(sensor).addNode(filter)
                .addNode(alertCollector).addNode(normalCollector).addNode(fileWriter)
                .connect("tempSensor", "out", "filter", "in")
                .connect("filter", "alert", "alert-collector", "in")
                .connect("filter", "normal", "normal-collector", "in")
                .connect("filter", "normal", "file-writer", "in");

        engine.register(flow);
        engine.startFlow("final-flow");

        for (int i = 0; i < TICK_COUNT; i++) {
            sensor.process(new Message(Map.of()));
        }

        Thread.sleep(2000);
        engine.shutdown();
    }

    @Test
    @Order(1)
    @DisplayName("Test4: 전체 분기 완전성 (누락 없음)")
    void testTotalMessageCount() {
        int totalCollected = alertCollector.getCollected().size() + normalCollector.getCollected().size();
        assertEquals(TICK_COUNT, totalCollected, "생성된 메시지와 수집된 메시지 총합이 다릅니다.");
    }

    @Test
    @Order(2)
    @DisplayName("Test2: Alert 경로 정확성 (30도 초과)")
    void testAlertPathAccuracy() {
        List<Message> alerts = alertCollector.getCollected();
        for (Message m : alerts) {
            double temp = m.get("temperature");
            assertTrue(temp > THRESHOLD, "Alert 경로에 잘못된 온도 포함: " + temp);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test3: Normal 경로 정확성 (30도 이하)")
    void testNormalPathAccuracy() {
        List<Message> normals = normalCollector.getCollected();
        for (Message m : normals) {
            double temp = m.get("temperature");
            assertTrue(temp <= THRESHOLD, "Normal 경로에 잘못된 온도 포함: " + temp);
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test5 파일 기록 검증 (파일 줄 수 = Normal 메시지 수)")
    void testFileContentMatch() throws IOException {
        List<String> fileLines = Files.readAllLines(Paths.get(LOG_PATH));
        assertEquals(normalCollector.getCollected().size(), fileLines.size(),
                "파일 기록 줄 수와 Normal 수집 데이터 수가 일치하지 않습니다.");
    }

    @Test
    @Order(5)
    @DisplayName("Test6 & 7: 데이터 형식 및 온도 범위 검증")
    void testDataFormatAndRange() {
        alertCollector.getCollected().forEach(this::assertCommonFormat);
        normalCollector.getCollected().forEach(this::assertCommonFormat);
    }

    private void assertCommonFormat(Message m) {
        assertTrue(m.hasKey("sensorId") && m.hasKey("temperature") && m.hasKey("unit"));
        double temp = m.get("temperature");
        assertTrue(temp >= 15.0 && temp <= 45.0);
    }
}
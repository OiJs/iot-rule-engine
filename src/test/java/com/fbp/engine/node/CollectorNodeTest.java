package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectorNodeTest {

    private FlowEngine engine;
    private CollectorNode collector;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        collector = new CollectorNode("test-collector");
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("메시지 수집 및 순서 보증 검증")
    void test1_2_MessageCollectionAndOrder() {
        Message msg1 = new Message(Map.of("index", 1));
        Message msg2 = new Message(Map.of("index", 2));
        Message msg3 = new Message(Map.of("index", 3));

        collector.onProcess(msg1);
        collector.onProcess(msg2);
        collector.onProcess(msg3);

        List<Message> results = collector.getCollected();
        assertEquals(3, results.size());

        assertEquals(1, (Integer) results.get(0).get("index"));
        assertEquals(2, (Integer) results.get(1).get("index"));
        assertEquals(3, (Integer) results.get(2).get("index"));

    }

    @Test
    @DisplayName("초기 상태 빈 리스트 확인")
    void test3_InputPortExists() {
        assertNotNull(collector.getCollected());
        assertTrue(collector.getCollected().isEmpty());
    }

    @Test
    @DisplayName("InputPort 존재 확인")
    void test4_InputPortExists() {
        assertNotNull(collector.getInputPort("in"));
    }

    @Test
    @DisplayName("파이프라인 연결 검증: 엔진을 통한 실제 흐름 수집")
    void test5_PipelineIntegration() throws InterruptedException {
        AbstractNode generator = new AbstractNode("gen") {
            @Override protected void onProcess(Message m) { send("out", m); }
        };
        generator.addOutputPort("out");

        Flow flow = new Flow("test-pipeline")
                .addNode(generator)
                .addNode(collector)
                .connect("gen", "out", "test-collector", "in");

        engine.register(flow);
        engine.startFlow("test-pipeline");

        Message testMsg = new Message(Map.of("data", "hello"));
        generator.onProcess(testMsg);

        Thread.sleep(500);

        List<Message> results = collector.getCollected();
        assertFalse(results.isEmpty(), "엔진을 통한 메시지 수집에 실패했습니다.");
        assertEquals("hello", results.get(0).get("data"));
    }
}

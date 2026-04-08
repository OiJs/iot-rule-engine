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

class ThresholdFilterNodeTest {

    private FlowEngine engine;
    private ThresholdFilterNode filterNode;
    private TestSinkNode alertSink;
    private TestSinkNode normalSink;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        filterNode = new ThresholdFilterNode("filter-1", "temperature", 30.0);
        alertSink = new TestSinkNode("alert-sink");
        normalSink = new TestSinkNode("normal-sink");

        Flow flow = new Flow("filter-test-flow")
                .addNode(filterNode)
                .addNode(alertSink)
                .addNode(normalSink)
                .connect("filter-1", "alert", "alert-sink", "in")
                .connect("filter-1", "normal", "normal-sink", "in");

        engine.register(flow);
        engine.startFlow("filter-test-flow");
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("임계값 초과 시 alert 포트")
    void test1_AlertCondition() throws InterruptedException {
        filterNode.onProcess(new Message(Map.of("temperature", 30.1)));

        Message alertMsg = alertSink.getNextMessage(2, TimeUnit.SECONDS);
        assertNotNull(alertMsg);
        assertEquals(30.1, (Double) alertMsg.get("temperature"));
        assertNull(normalSink.getNextMessage(500, TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("임계값 이하 normal 포트")
    void test2_NormalCondition() throws InterruptedException {
        filterNode.onProcess(new Message(Map.of("temperature", 29.9)));

        Message normalMsg = normalSink.getNextMessage(2, TimeUnit.SECONDS);
        assertNotNull(normalMsg);

        assertNull(alertSink.getNextMessage(500, TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("경계값 처리")
    void test3_BoundaryCondition() throws InterruptedException {
        filterNode.onProcess(new Message(Map.of("temperature", 30.0)));

        Message normalMsg = normalSink.getNextMessage(2, TimeUnit.SECONDS);
        assertNotNull(normalMsg, "경계값(30.0)은 normal 포트로 가야 합니다.");
    }

    @Test
    @DisplayName("4. 키가 없는 메시지 수신 시 예외 없이 무시 확인")
    void test4_MissingKeyHandling() {
        assertDoesNotThrow(() -> {
            filterNode.onProcess(new Message(Map.of("humidity", 50.0)));
        });
    }

    private static class TestSinkNode extends AbstractNode {
        private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
        public TestSinkNode(String id) { super(id); addInputPort("in"); }
        @Override protected void onProcess(Message message) { queue.offer(message); }
        public Message getNextMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }
}

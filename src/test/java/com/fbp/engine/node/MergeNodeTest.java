package com.fbp.engine.node;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MergeNodeTest {
    private FlowEngine engine;
    private MergeNode mergeNode;
    private TestSinkNode sink;
    private Flow flow;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        mergeNode = new MergeNode("merger");
        sink = new TestSinkNode("sink");

        flow = new Flow("merge-test")
                .addNode(mergeNode)
                .addNode(sink)
                .connect("merger", "out", "sink", "in");

        engine.register(flow);
        engine.startFlow("merge-test");
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("4. 포트 구성 확인: in-1, in-2, out 포트가 존재하는가")
    void test4_PortConfiguration() {
        assertNotNull(mergeNode.getInputPort("in-1"));
        assertNotNull(mergeNode.getInputPort("in-2"));
        assertNotNull(mergeNode.getOutputPort("out"));
    }

    @Test
    @DisplayName("1, 2. 양쪽 입력 수신 및 메시지 합치기 검증")
    void test1_2_MergeFunctionality() throws InterruptedException {
        Message msg1 = new Message(Map.of("temperature", 25.5)).withEntry("inputPort", "in-1");
        Message msg2 = new Message(Map.of("humidity", 60.0)).withEntry("inputPort", "in-2");

        mergeNode.process(msg1);
        mergeNode.process(msg2);

        Message merged = sink.getNextMessage(2, TimeUnit.SECONDS);

        assertNotNull(merged, "병합된 메시지가 도착하지 않았습니다.");
        assertEquals(25.5, (Double) merged.get("temperature"));
        assertEquals(60.0, (Double) merged.get("humidity"));
        System.out.println("병합 성공 데이터: " + merged.getPayload());
    }

    @Test
    @DisplayName("3. 한쪽만 도착 시 대기 확인 (매칭 대기)")
    void test3_WaitUntilMatched() throws InterruptedException {
        Message msg1 = new Message(Map.of("data", "first")).withEntry("inputPort", "in-1");
        mergeNode.process(msg1);

        Message shouldBeNull = sink.getNextMessage(1, TimeUnit.SECONDS);
        assertNull(shouldBeNull, "한쪽 데이터만 왔는데 결과가 출력되었습니다.");

        Message msg2 = new Message(Map.of("data2", "second")).withEntry("inputPort", "in-2");
        mergeNode.process(msg2);

        Message merged = sink.getNextMessage(2, TimeUnit.SECONDS);
        assertNotNull(merged, "두 데이터가 모두 도착했는데 병합되지 않았습니다.");
    }

    private static class TestSinkNode extends AbstractNode {
        private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

        public TestSinkNode(String id) { super(id); addInputPort("in"); }

        @Override
        protected void onProcess(Message message) { queue.offer(message); }

        public Message getNextMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }
}
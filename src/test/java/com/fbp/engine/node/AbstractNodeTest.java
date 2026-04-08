package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AbstractNodeTest {

    // 테스트용 최소 구현체
    static class TestNode extends AbstractNode {
        boolean onProcessCalled = false;

        public TestNode(String id) {
            super(id);
            addInputPort("in");
            addOutputPort("out");
        }

        @Override
        protected void onProcess(Message message) {
            onProcessCalled = true;
        }
    }

    // 1. getId 반환
    @Test
    void test1_GetIdReturnsCorrectId() {
        TestNode node = new TestNode("my-node");
        assertEquals("my-node", node.getId());
    }

    // 2. addInputPort 등록
    @Test
    void test2_AddInputPortRegistersPort() {
        TestNode node = new TestNode("node");
        assertNotNull(node.getInputPort("in"));
    }

    // 3. addOutputPort 등록
    @Test
    void test3_AddOutputPortRegistersPort() {
        TestNode node = new TestNode("node");
        assertNotNull(node.getOutputPort("out"));
    }

    // 4. 미등록 포트 조회
    @Test
    void test4_GetUnregisteredPortReturnsNull() {
        TestNode node = new TestNode("node");
        assertNull(node.getInputPort("없는포트"));
        assertNull(node.getOutputPort("없는포트"));
    }

    // 5. process → onProcess 호출
    @Test
    void test5_ProcessCallsOnProcess() {
        TestNode node = new TestNode("node");
        node.process(new Message(Map.of("key", "value")));
        assertTrue(node.onProcessCalled);
    }

    // 6. send로 메시지 전달
    @Test
    void test6_SendDeliversMessageToConnection() throws InterruptedException {
        TestNode sender = new TestNode("sender");
        Connection connection = new Connection("conn");
        sender.getOutputPort("out").connect(connection);

        Message msg = new Message(Map.of("data", "hello"));
        sender.process(msg); // onProcess에서 send 안 하므로 직접 send 호출용 노드 필요

        // send를 직접 호출하는 노드
        AbstractNode senderWithSend = new AbstractNode("sender2") {
            {
                addOutputPort("out");
            }
            @Override
            protected void onProcess(Message message) {
                send("out", message);
            }
        };

        Connection conn2 = new Connection("conn2");
        senderWithSend.getOutputPort("out").connect(conn2);

        AtomicBoolean received = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            Message result = conn2.poll();
            if (result != null) received.set(true);
            latch.countDown();
        });
        consumer.start();

        senderWithSend.process(new Message(Map.of("data", "hello")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(received.get());
    }
}
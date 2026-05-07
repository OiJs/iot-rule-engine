package com.fbp.engine.node;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DelayNodeTest {

    private DelayNode delayNode;
    private LocalConnection outputConn;

    @BeforeEach
    void setUp() {
        delayNode = new DelayNode("delay", 500);
        outputConn = new LocalConnection("out-conn");
        delayNode.getOutputPort("out").connect(outputConn);
    }

    @Test
    void test1_MessageDeliveredAfterDelay() throws InterruptedException {
        AtomicLong receivedAt = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            outputConn.poll();
            receivedAt.set(System.currentTimeMillis());
            latch.countDown();
        });
        consumer.start();

        long sentAt = System.currentTimeMillis();

        Thread delayThread = new Thread(() ->
                delayNode.process(new Message(Map.of("data", "test")))
        );
        delayThread.start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        long elapsed = receivedAt.get() - sentAt;
        assertTrue(elapsed >= 500, "500ms 이상 지연되어야 함, 실제: " + elapsed + "ms");
    }

    @Test
    void test2_MessageContentPreservedAfterDelay() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        Thread delayThread = new Thread(() ->
                delayNode.process(new Message(Map.of("key", "value", "num", 42)))
        );
        delayThread.start();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals("value", received.get().get("key"));
        assertEquals(42, (Integer) received.get().get("num"));
    }
}
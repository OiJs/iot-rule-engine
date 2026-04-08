package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LogNodeTest {

    private LogNode logNode;
    private Connection outputConn;

    @BeforeEach
    void setUp() {
        logNode = new LogNode("logger");
        outputConn = new Connection("out-conn");
        logNode.getOutputPort("out").connect(outputConn);
    }

    @Test
    void test1_MessagePassesThroughUnchanged() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        logNode.process(new Message(Map.of("data", "test")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals("test", received.get().get("data"));
    }

    @Test
    void test2_LogNodeCanBeInsertedInChain() throws InterruptedException {
        Connection nextConn = new Connection("next-conn");
        logNode.getOutputPort("out").connect(nextConn);

        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(nextConn.poll());
            latch.countDown();
        });
        consumer.start();

        logNode.process(new Message(Map.of("key", "value")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals("value", received.get().get("key"));
    }
}
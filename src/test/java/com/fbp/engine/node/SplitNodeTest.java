package com.fbp.engine.node;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SplitNodeTest {

    private SplitNode split;
    private LocalConnection matchConn;
    private LocalConnection mismatchConn;

    @BeforeEach
    void setUp() {
        split = new SplitNode("split", "value", 10.0);
        matchConn = new LocalConnection("match-conn");
        mismatchConn = new LocalConnection("mismatch-conn");
        split.getOutputPort("match").connect(matchConn);
        split.getOutputPort("mismatch").connect(mismatchConn);
    }

    @Test
    void test1_AboveThresholdGoesToMatch() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(matchConn.poll());
            latch.countDown();
        });
        consumer.start();

        split.process(new Message(Map.of("value", 15.0)));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals(0, mismatchConn.getQueueSize());
    }

    @Test
    void test2_BelowThresholdGoesToMismatch() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(mismatchConn.poll());
            latch.countDown();
        });
        consumer.start();

        split.process(new Message(Map.of("value", 5.0)));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals(0, matchConn.getQueueSize());
    }

    // 3. 양쪽 동시 확인
    @Test
    void test3_BothPortsReceiveCorrectMessages() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Message> matchReceived = new AtomicReference<>();
        AtomicReference<Message> mismatchReceived = new AtomicReference<>();

        Thread matchConsumer = new Thread(() -> {
            matchReceived.set(matchConn.poll());
            latch.countDown();
        });
        Thread mismatchConsumer = new Thread(() -> {
            mismatchReceived.set(mismatchConn.poll());
            latch.countDown();
        });

        matchConsumer.start();
        mismatchConsumer.start();

        split.process(new Message(Map.of("value", 15.0))); // match
        split.process(new Message(Map.of("value", 5.0)));  // mismatch

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(matchReceived.get());
        assertNotNull(mismatchReceived.get());
    }

    @Test
    void test4_BoundaryValueGoesToMatch() {
        split.process(new Message(Map.of("value", 10.0)));

        assertEquals(1, matchConn.getQueueSize());
        assertEquals(0, mismatchConn.getQueueSize());
    }
}
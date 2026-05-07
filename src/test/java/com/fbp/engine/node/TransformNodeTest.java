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

class TransformNodeTest {

    private LocalConnection outputConn;

    @BeforeEach
    void setUp() {
        outputConn = new LocalConnection("out-conn");
    }

    @Test
    void test1_TransformerIsAppliedAndResultDelivered() throws InterruptedException {
        TransformNode node = new TransformNode("transform", msg ->
                msg.withEntry("doubled", (int) msg.get("value") * 2)
        );
        node.getOutputPort("out").connect(outputConn);

        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        node.process(new Message(Map.of("value", 5)));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals(10, (Integer) received.get().get("doubled"));
    }

    @Test
    void test2_NullResultIsNotDelivered() throws InterruptedException {
        TransformNode node = new TransformNode("transform", msg -> null);
        node.getOutputPort("out").connect(outputConn);

        node.process(new Message(Map.of("value", 5)));

        Thread.sleep(300);
        assertEquals(0, outputConn.getQueueSize());
    }

    @Test
    void test3_OriginalMessageIsUnchanged() throws InterruptedException {
        TransformNode node = new TransformNode("transform", msg ->
                msg.withEntry("extra", "added")
        );
        node.getOutputPort("out").connect(outputConn);

        Message original = new Message(Map.of("value", 5));

        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        node.process(original);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(original.hasKey("extra"), "원본 메시지에 extra 키가 없어야 함");
        assertTrue(received.get().hasKey("extra"), "변환된 메시지에 extra 키가 있어야 함");
    }
}
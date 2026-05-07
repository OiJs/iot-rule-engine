package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;
import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class GeneratorNodeTest {

    @Test
    void test1_GenerateDeliversMessageToOutputPort() throws InterruptedException {
        GeneratorNode gen = new GeneratorNode("g1");
        LocalConnection conn = new LocalConnection("c1");
        gen.getOutputPort("out").connect(conn);

        assertNotNull(gen.getOutputPort("out"));

        gen.generate("key", "value");

        Message received = pollInThread(conn);
        assertNotNull(received);
    }

    @Test
    void test2_GeneratedMessageContainsKeyValue() throws InterruptedException {
        GeneratorNode gen = new GeneratorNode("g1");
        LocalConnection conn = new LocalConnection("c1");
        gen.getOutputPort("out").connect(conn);

        gen.generate("temperature", 25.5);

        Message received = pollInThread(conn);
        assertNotNull(received);
        assertEquals(25.5, received.get("temperature"));
    }

    @Test
    void test3_GetOutputPortNotNull() {
        GeneratorNode gen = new GeneratorNode("g1");
        assertNotNull(gen.getOutputPort("out"));
    }

    @Test
    void test4_MultipleGeneratesPreserveOrder() throws InterruptedException {
        GeneratorNode gen = new GeneratorNode("g1");
        LocalConnection conn = new LocalConnection("c1");
        gen.getOutputPort("out").connect(conn);

        gen.generate("key", "val1");
        gen.generate("key", "val2");
        gen.generate("key", "val3");

        List<Message> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                received.add(conn.poll());
                latch.countDown();
            }
        });
        consumer.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("val1", received.get(0).get("key"));
        assertEquals("val2", received.get(1).get("key"));
        assertEquals("val3", received.get(2).get("key"));
    }

    private Message pollInThread(LocalConnection conn) throws InterruptedException {
        List<Message> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            result.add(conn.poll());
            latch.countDown();
        });
        t.start();
        latch.await(2, TimeUnit.SECONDS);

        return result.isEmpty() ? null : result.get(0);
    }
}
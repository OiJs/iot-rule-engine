package com.fbp.engine.api;

import com.fbp.engine.metrics.MetricsCollector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class MetricsCollectorTest {
    private MetricsCollector collector;
    private final String FLOW_ID = "flow-1";
    private final String NODE_ID = "node-1";

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }

    @Test
    void testRecordProcessingCount() {
        collector.recordProcessing(FLOW_ID, NODE_ID, true, 100);
        
        var snapshot = collector.getSnapshot(FLOW_ID,NODE_ID);
        Assertions.assertEquals(1, snapshot.processedCount());
    }

    @Test
    void testRecordErrorCount() {
        collector.recordProcessing(FLOW_ID, NODE_ID, false, 100);
        
        var snapshot = collector.getSnapshot(FLOW_ID, NODE_ID);
        Assertions.assertEquals(1, snapshot.errorCount());
        Assertions.assertEquals(1, snapshot.processedCount());
    }

    @Test
    void testAverageProcessingTime() {
        collector.recordProcessing(FLOW_ID, NODE_ID, true, 100);
        collector.recordProcessing(FLOW_ID, NODE_ID, true, 300);
        
        var snapshot = collector.getSnapshot(FLOW_ID, NODE_ID);
        Assertions.assertEquals(200.0, snapshot.avgDuration());
    }

    @Test
    void testMultiThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                for (int j = 0; j < iterations; j++) {
                    collector.recordProcessing(FLOW_ID, NODE_ID, true, 10);
                }
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();

        var snapshot = collector.getSnapshot(FLOW_ID ,NODE_ID);
        Assertions.assertEquals(threadCount * iterations, snapshot.processedCount());
    }

    @Test
    void testNodeIsolation() {
        collector.recordProcessing(FLOW_ID, "node-A", true, 100);
        collector.recordProcessing(FLOW_ID, "node-B", true, 200);

        Assertions.assertEquals(1, collector.getSnapshot(FLOW_ID, "node-A").processedCount());
        Assertions.assertEquals(1, collector.getSnapshot(FLOW_ID, "node-B").processedCount());
        Assertions.assertNotEquals(collector.getSnapshot(FLOW_ID, "node-A").avgDuration(),
                                   collector.getSnapshot(FLOW_ID , "node-B").avgDuration());
    }

    @Test
    void testReset() {
        collector.recordProcessing(FLOW_ID, NODE_ID, true, 100);
        collector.reset();

        Assertions.assertNull(collector.getSnapshot(FLOW_ID, NODE_ID));
    }

    @Test
    void testNonExistentNode() {
        Assertions.assertNull(collector.getSnapshot("unknown-flow", "unknown-node"));
    }
}
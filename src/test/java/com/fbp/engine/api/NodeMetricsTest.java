package com.fbp.engine.api;

import com.fbp.engine.metrics.NodeMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeMetricsTest {
    private NodeMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new NodeMetrics("flow-1");
    }

    @Test
    void testInitialValues() {
        var snapshot = metrics.getSnapshot();
        Assertions.assertEquals(0, snapshot.processedCount());
        Assertions.assertEquals(0, snapshot.errorCount());
        Assertions.assertEquals(0.0, snapshot.avgDuration());
    }

    @Test
    void testIncrement() {
        metrics.record(true, 100);
        metrics.record(false, 200);

        var snapshot = metrics.getSnapshot();
        Assertions.assertEquals(2, snapshot.processedCount());
        Assertions.assertEquals(1, snapshot.errorCount());
    }

    @Test
    void testAverageCalculation() {
        metrics.record(true, 100);
        metrics.record(true, 300);
        metrics.record(false, 200);

        var snapshot = metrics.getSnapshot();
        Assertions.assertEquals(200.0, snapshot.avgDuration());
    }

    @Test
    void testSnapshotImmutability() {
        metrics.record(true, 100);
        var firstSnapshot = metrics.getSnapshot();

        metrics.record(true, 500);
        var secondSnapshot = metrics.getSnapshot();

        Assertions.assertEquals(1, firstSnapshot.processedCount());
        Assertions.assertEquals(2, secondSnapshot.processedCount());
        Assertions.assertNotEquals(firstSnapshot.processedCount(), secondSnapshot.processedCount());
    }
}
package com.fbp.engine.metrics;

import com.fbp.engine.metrics.event.DomainDataEvent;
import com.fbp.engine.metrics.event.FlowEvent;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MetricsScheduler {
    private final MetricsAggregator aggregator;
    private final InfluxBatchWriter writer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-scheduler");
        t.setDaemon(true);
        return t;
    });

    public MetricsScheduler(MetricsAggregator aggregator, InfluxBatchWriter writer) {
        this.aggregator = aggregator;
        this.writer = writer;
        this.aggregator.setWindowFlushListener(this::onWindowFlush);
        this.aggregator.setRawDataListener(this::onRawData);
        this.aggregator.setFlowEventListener(this::onFlowEvent);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 10, 10, TimeUnit.SECONDS);
    }

    private void onRawData(DomainDataEvent e) {
        Point point = Point.measurement("sensor_raw")
                .addTag("flow_id", e.flowId())
                .addTag("node_id", e.nodeId())
                .addTag("sensor_name", e.sensorName());
        
        if (e.tags() != null) {
            e.tags().forEach(point::addTag);
        }
        
        point.addField("value", e.value())
             .time(Instant.ofEpochMilli(e.timestamp()), WritePrecision.MS);
        
        writer.writePoint(point);
    }

    private void onFlowEvent(FlowEvent e) {
        Point point = Point.measurement("flow_events")
                .addTag("flow_id", e.flowId())
                .addTag("event_type", e.eventType())
                .addField("summary", e.summary())
                .time(Instant.ofEpochMilli(e.timestamp()), WritePrecision.MS);
        
        writer.writePoint(point);
    }

    private void onWindowFlush(String flowId, String sensorName, String windowType, TimeWindowBucketer.BucketSnapshot snapshot) {
        Point point = Point.measurement("sensor_stats_" + windowType)
                .addTag("flow_id", flowId)
                .addTag("sensor_name", sensorName)
                .addField("avg", snapshot.avg())
                .addField("min", snapshot.min())
                .addField("max", snapshot.max())
                .addField("count", snapshot.count())
                .time(Instant.ofEpochMilli(snapshot.timestamp()), WritePrecision.MS);
        writer.writePoint(point);
    }

    private void tick() {
        try {
            List<Point> points = new ArrayList<>();
            Instant now = Instant.now();

            // Engine Stats
            long totalNodes = aggregator.getNodeStats().values().stream().mapToLong(m -> m.size()).sum();
            long activeFlows = aggregator.getFlowStats().size();
            points.add(Point.measurement("engine_stats")
                    .addTag("host", java.net.InetAddress.getLocalHost().getHostName())
                    .addField("active_flows", activeFlows)
                    .addField("total_nodes", totalNodes)
                    .addField("heap_used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                    .addField("heap_total", Runtime.getRuntime().totalMemory())
                    .time(now, WritePrecision.NS));

            // Node Stats
            aggregator.getNodeStats().forEach((flowId, nodes) -> {
                nodes.forEach((nodeId, stats) -> {
                    points.add(Point.measurement("node_stats")
                        .addTag("flow_id", flowId)
                        .addTag("node_id", nodeId)
                        .addField("processed", stats.processedCount.sum())
                        .addField("errors", stats.errorCount.sum())
                        .addField("in_bytes", stats.inBytes.sum())
                        .addField("out_bytes", stats.outBytes.sum())
                        .addField("avg_latency_ms", stats.latencyHistogram.getMean() / 1000.0)
                        .addField("p99_latency_ms", stats.latencyHistogram.getValueAtPercentile(99.0) / 1000.0)
                        .time(now, WritePrecision.NS));
                });
            });

            // Wire Stats
            aggregator.getWireStats().forEach((flowId, wires) -> {
                wires.forEach((wireId, stats) -> {
                    points.add(Point.measurement("wire_stats")
                        .addTag("flow_id", flowId)
                        .addTag("wire_id", wireId)
                        .addField("delivered", stats.deliveredCount.sum())
                        .addField("dropped", stats.droppedCount.sum())
                        .addField("bytes", stats.totalBytes.sum())
                        .addField("queue_size", stats.lastQueueSize.get())
                        .time(now, WritePrecision.NS));
                });
            });

            // Flow Stats
            aggregator.getFlowStats().forEach((flowId, stats) -> {
                points.add(Point.measurement("flow_stats")
                    .addTag("flow_id", flowId)
                    .addField("processed", stats.processedCount.sum())
                    .addField("errors", stats.errorCount.sum())
                    .time(now, WritePrecision.NS));
            });

            if (!points.isEmpty()) {
                writer.writePoints(points);
            }
        } catch (Exception e) {
            log.error("Failed to collect or write metrics", e);
        }
    }

    public void stop() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

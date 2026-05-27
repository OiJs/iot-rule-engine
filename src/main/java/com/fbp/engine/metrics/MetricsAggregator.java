package com.fbp.engine.metrics;

import com.fbp.engine.metrics.event.*;
import com.fbp.engine.parser.DomainMetricDefinition;
import com.fbp.engine.parser.FlowDefinition;
import org.HdrHistogram.SynchronizedHistogram;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * MetricsAggregator는 엔진의 모든 비동기 메트릭 이벤트를 수집하여 실제 통계 수치로 변환하는 집계 엔진.
 * Cold Path에서 동작하며, LongAdder와 HdrHistogram을 사용하여 높은 처리량과 낮은 오버헤드로 
 * 엔진, 플로우, 노드, 와이어 및 도메인 레벨의 통계를 유지.
 */
public class MetricsAggregator {
    // flowId -> nodeId -> NodeStats
    private final Map<String, Map<String, NodeStatsInternal>> nodeStats = new ConcurrentHashMap<>();
    // flowId -> wireId -> WireStats
    private final Map<String, Map<String, WireStatsInternal>> wireStats = new ConcurrentHashMap<>();
    // flowId -> FlowStats
    private final Map<String, FlowStatsInternal> flowStats = new ConcurrentHashMap<>();
    // sensorKey (flowId:sensorName) -> windowType -> Bucketer
    private final Map<String, Map<String, TimeWindowBucketer>> sensorBucketers = new ConcurrentHashMap<>();

    private final DomainMetricsExtractor extractor = new DomainMetricsExtractor();
    private final Map<String, List<DomainMetricDefinition>> domainConfigs = new ConcurrentHashMap<>();

    private WindowFlushListener windowFlushListener;
    private java.util.function.Consumer<DomainDataEvent> rawDataListener;
    private java.util.function.Consumer<FlowEvent> flowEventListener;
    private java.util.function.Consumer<MetricEvent> monitorListener;

    /**
     * 텀블링 윈도우가 마감(Flush)될 때 호출되는 리스너 인터페이스.
     */
    @FunctionalInterface
    public interface WindowFlushListener {
        /**
         * 윈도우 마감 시 통계 스냅샷을 전달합니다.
         * @param flowId 플로우 ID
         * @param sensorName 센서 이름
         * @param windowType 윈도우 타입 (1m, 1h, 1d)
         * @param snapshot 계산된 통계 정보
         */
        void onFlush(String flowId, String sensorName, String windowType, TimeWindowBucketer.BucketSnapshot snapshot);
    }

    public void setWindowFlushListener(WindowFlushListener listener) {
        this.windowFlushListener = listener;
    }

    public void setRawDataListener(java.util.function.Consumer<DomainDataEvent> listener) {
        this.rawDataListener = listener;
    }

    public void setFlowEventListener(java.util.function.Consumer<FlowEvent> listener) {
        this.flowEventListener = listener;
    }

    public void setMonitorListener(java.util.function.Consumer<MetricEvent> listener) {
        this.monitorListener = listener;
    }

    /**
     * 새로운 플로우의 메트릭 정의 정보를 등록합니다.
     * @param flow 플로우 설계도 객체
     */
    public void registerFlow(FlowDefinition flow) {
        if (flow.metrics() != null && flow.metrics().domain() != null) {
            domainConfigs.put(flow.id(), flow.metrics().domain());
        }
    }

    /**
     * 메트릭 이벤트를 수신하여 타입에 맞는 통계 정보로 집계합니다.
     * @param event 발생한 메트릭 이벤트
     */
    public void aggregate(MetricEvent event) {
        if (monitorListener != null) {
            monitorListener.accept(event);
        }
        
        if (event instanceof NodeProcessEvent e) {
            updateNodeStats(e);
        } else if (event instanceof WireDeliverEvent e) {
            updateWireStats(e);
        } else if (event instanceof FlowEvent e) {
            updateFlowStats(e);
        } else if (event instanceof DomainDataEvent e) {
            updateDomainStats(e);
        } else if (event instanceof DomainExtractionEvent e) {
            handleDomainExtraction(e);
        }
    }

    /**
     * 노드 처리 관련 통계를 업데이트합니다.
     */
    private void updateNodeStats(NodeProcessEvent e) {
        var stats = nodeStats.computeIfAbsent(e.flowId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.nodeId(), k -> new NodeStatsInternal());
        
        if (!e.success()) stats.errorCount.increment();
        stats.inBytes.add(e.inBytes());
        stats.outBytes.add(e.outBytes());
        // Record latency in microseconds. Max 1 hour.
        stats.latencyHistogram.recordValue(Math.min(e.durationNano() / 1000, 3600000000L)); 
        
        stats.processedCount.increment();

        // Also update flow aggregate
        var fStats = flowStats.computeIfAbsent(e.flowId(), k -> new FlowStatsInternal());
        fStats.processedCount.increment();
        if (!e.success()) fStats.errorCount.increment();
    }

    /**
     * 연결(Wire) 전송 관련 통계를 업데이트합니다.
     */
    private void updateWireStats(WireDeliverEvent e) {
        var stats = wireStats.computeIfAbsent(e.flowId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.wireId(), k -> new WireStatsInternal());
        stats.deliveredCount.increment();
        if (e.dropped()) stats.droppedCount.increment();
        stats.totalBytes.add(e.bytes());
        stats.lastQueueSize.set(e.queueSize());
    }

    private void updateFlowStats(FlowEvent e) {
        if (flowEventListener != null) {
            flowEventListener.accept(e);
        }
    }

    /**
     * 메시지 페이로드로부터 특정 필드 값을 추출하여 도메인 메트릭을 생성합니다.
     */
    private void handleDomainExtraction(DomainExtractionEvent e) {
        List<DomainMetricDefinition> configs = domainConfigs.get(e.flowId());
        if (configs == null) return;

        for (DomainMetricDefinition def : configs) {
            if (def.source().node().equals(e.nodeId()) && def.source().port().equals(e.portName())) {
                Double val = extractor.extractValue(e.message().getPayload(), def.field());
                if (val != null) {
                    var dataEvent = new DomainDataEvent(
                        e.timestamp(),
                        e.flowId(),
                        e.nodeId(),
                        def.name(),
                        val,
                        def.tags()
                    );
                    if (rawDataListener != null) {
                        rawDataListener.accept(dataEvent);
                    }
                    updateDomainStats(dataEvent);
                }
            }
        }
    }

    /**
     * 추출된 데이터 값을 기반으로 텀블링 윈도우(1m, 1h, 1d) 통계를 업데이트합니다.
     * 윈도우 주기가 만료되면 리스너를 통해 스냅샷을 배출합니다.
     */
    private void updateDomainStats(DomainDataEvent e) {
        String sensorKey = e.flowId() + ":" + e.sensorName();
        var bucketers = sensorBucketers.computeIfAbsent(sensorKey, k -> {
            Map<String, TimeWindowBucketer> m = new ConcurrentHashMap<>();
            m.put("1m", new TimeWindowBucketer("1m"));
            m.put("1h", new TimeWindowBucketer("1h"));
            m.put("1d", new TimeWindowBucketer("1d"));
            return m;
        });

        for (TimeWindowBucketer b : bucketers.values()) {
            if (b.isOverdue(e.timestamp())) {
                var snapshot = b.flush(e.timestamp());
                if (windowFlushListener != null) {
                    windowFlushListener.onFlush(e.flowId(), e.sensorName(), b.getWindowType(), snapshot); 
                }
            }
            b.add(e.value());
        }
    }

    public Map<String, List<DomainMetricDefinition>> getDomainConfigs() {
        return domainConfigs;
    }

    public Map<String, Map<String, TimeWindowBucketer>> getSensorBucketers() {
        return sensorBucketers;
    }

    public Map<String, Map<String, NodeStatsInternal>> getNodeStats() {
        return nodeStats;
    }

    public Map<String, Map<String, WireStatsInternal>> getWireStats() {
        return wireStats;
    }

    public Map<String, FlowStatsInternal> getFlowStats() {
        return flowStats;
    }

    /**
     * 노드별 통계 정보를 담는 내부 데이터 구조입니다.
     */
    public static class NodeStatsInternal {
        public final LongAdder processedCount = new LongAdder();
        public final LongAdder errorCount = new LongAdder();
        public final LongAdder inBytes = new LongAdder();
        public final LongAdder outBytes = new LongAdder();
        /** 응답 속도 백분위수 측정을 위한 동기화된 히스토그램 */
        public final SynchronizedHistogram latencyHistogram = new SynchronizedHistogram(3600000000L, 3);
    }

    /**
     * 연결별 통계 정보를 담는 내부 데이터 구조입니다.
     */
    public static class WireStatsInternal {
        public final LongAdder deliveredCount = new LongAdder();
        public final LongAdder droppedCount = new LongAdder();
        public final LongAdder totalBytes = new LongAdder();
        public final AtomicInteger lastQueueSize = new AtomicInteger();
    }

    /**
     * 플로우별 통계 정보를 담는 내부 데이터 구조입니다.
     */
    public static class FlowStatsInternal {
        public final LongAdder processedCount = new LongAdder();
        public final LongAdder errorCount = new LongAdder();
    }
}


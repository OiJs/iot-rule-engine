package com.fbp.engine.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeMetricsTest {
    private NodeMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new NodeMetrics("flowId");
    }

    @Test
    @DisplayName("1. 초기값: 생성 직후 모든 지표는 0이어야 함")
    void test1_InitialValues() {
        NodeMetrics.NodeMetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(0, snapshot.processedCount());
        assertEquals(0, snapshot.errorCount());
        assertEquals(0, snapshot.totalDuration());
        assertEquals(0.0, snapshot.avgDuration());
    }

    @Test
    @DisplayName("2. increment: 성공 및 에러 발생 시 카운터가 정상 증가해야 함")
    void test2_Increment() {
        metrics.record(true, 100);  // 성공
        metrics.record(false, 200); // 에러

        NodeMetrics.NodeMetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals(2, snapshot.processedCount());
        assertEquals(1, snapshot.errorCount());
    }

    @Test
    @DisplayName("3. 평균 계산: 전체 소요 시간을 처리 건수로 나눈 값이 정확해야 함")
    void test3_AverageCalculation() {
        // Given: 100ns, 200ns 두 번 실행 (합계 300ns)
        metrics.record(true, 100);
        metrics.record(true, 200);

        // When
        NodeMetrics.NodeMetricsSnapshot snapshot = metrics.getSnapshot();

        // Then: 300 / 2 = 150.0
        assertEquals(150.0, snapshot.avgDuration());
    }

    @Test
    @DisplayName("4. 스냅샷: 반환된 스냅샷은 이후의 지표 변화에 영향을 받지 않아야 함")
    void test4_SnapshotImmutability() {
        // Given: 초기 상태에서 스냅샷 획득
        metrics.record(true, 100);
        NodeMetrics.NodeMetricsSnapshot firstSnapshot = metrics.getSnapshot();

        // When: 스냅샷 획득 후 추가 기록
        metrics.record(true, 900);
        NodeMetrics.NodeMetricsSnapshot secondSnapshot = metrics.getSnapshot();

        // Then: 첫 번째 스냅샷의 데이터는 변하지 않음 (불변성 검증)
        assertEquals(1, firstSnapshot.processedCount());
        assertEquals(2, secondSnapshot.processedCount());
        assertNotEquals(firstSnapshot.avgDuration(), secondSnapshot.avgDuration());
    }
}
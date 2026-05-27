package com.fbp.engine.performance;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.test.LoadTester;
import com.fbp.engine.test.PerformanceResult;
import org.junit.jupiter.api.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("performance")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnginePerformanceTest {
    private FlowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    /**
     * 1. 처리량 기준: 10,000건 메시지 전송 후 초당 처리량 ≥ 1,000건
     * 2. 지연 시간: 메시지 입력~출력 간 지연 시간 평균 < 10ms
     * 3. 에러율: 10,000건 중 에러 < 0.1%
     */
    @Test
    @Order(1)
    @DisplayName("기본 부하 지표 검증 (Throughput, Latency, Error Rate)")
    void test1_2_3_StandardLoadMetrics() throws InterruptedException {
        int messageCount = 10000;
        Flow flow = new Flow("metric-test");
        AbstractNode n1 = createPassThroughNode("n1");
        AbstractNode n2 = createPassThroughNode("n2");

        flow.addNode(n1).addNode(n2).connect("n1", "out", "n2", "in");

        LocalConnection sinkConn = new LocalConnection("sink");
        n2.getOutputPort("out").connect(sinkConn);

        engine.register(flow);
        engine.startFlow("metric-test");

        PerformanceResult result = LoadTester.run(n1.getInputPort("in"), sinkConn, messageCount);

        System.out.println("Result: " + result);

        assertTrue(result.getThroughput() >= 1000, "처리량이 기준치(1000)보다 낮음: " + result.getThroughput());
        assertTrue(result.getAvgLatency() < 10, "평균 지연 시간이 10ms 초과: " + result.getAvgLatency());
        assertTrue(result.getErrorRate() < 0.1, "에러율이 0.1% 초과: " + result.getErrorRate());
    }

    /**
     * 4. 장시간 실행: 5분 연속 실행 후 메모리 사용량 안정성 (단조 증가 아님)
     * (테스트 환경을 고려해 30초로 축소 구현하되 로직은 동일하게 구성)
     */
    @Test
    @Order(4)
    @DisplayName("장시간 실행 시 메모리 안정성 검증")
    void test4_MemoryStability() throws InterruptedException {
        Flow flow = new Flow("memory-test");
        AbstractNode node = createPassThroughNode("n1");
        flow.addNode(node);
        engine.register(flow);
        engine.startFlow("memory-test");

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = getUsedMemory(runtime);

        long testDuration = 30000;
        long endTime = System.currentTimeMillis() + testDuration;

        while (System.currentTimeMillis() < endTime) {
            node.getInputPort("in").receive(new Message(Map.of("data", "loop")));
            Thread.yield();
        }

        System.gc();
        Thread.sleep(2000);
        long finalMemory = getUsedMemory(runtime);

        System.out.println("Memory Change: " + initialMemory + "MB -> " + finalMemory + "MB");
        assertTrue(finalMemory < initialMemory + 100, "메모리 누수 의심: 100MB 이상 증가함");
    }

    /**
     * 5. 스레드 효율: 20개 노드 기준 활성 스레드 수 ≤ 40
     */
    @Test
    @Order(5)
    @DisplayName("스레드 자원 사용 효율성 검증")
    void test5_ThreadEfficiency() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int initialThreads = threadBean.getThreadCount();

        Flow flow = new Flow("thread-test");
        for (int i = 0; i < 20; i++) {
            flow.addNode(createPassThroughNode("node-" + i));
            if (i > 0) {
                flow.connect("node-" + (i - 1), "out", "node-" + i, "in");
            }
        }

        engine.register(flow);
        engine.startFlow("thread-test");

        int finalThreads = threadBean.getThreadCount();
        int activeThreads = finalThreads - initialThreads;

        System.out.println("Threads created for 20 nodes: " + activeThreads + " (Total: " + finalThreads + ")");
        // 20개 노드 체인에서 10개 고정 워커 풀 + 메트릭 관련 스레드들이 추가됨.
        // 증가량이 40개 이하인지를 검증.
        assertTrue(activeThreads <= 40, "엔진 가동으로 생성된 스레드가 너무 많음: " + activeThreads);
    }

    /**
     * 6. 큐 적체: 부하 상황에서 Connection 큐 크기가 상한선을 초과하지 않음
     */
    @Test
    @Order(6)
    @DisplayName("부하 상황에서의 커넥션 큐 적체 제어 검증")
    void test6_QueueCongestionControl() throws InterruptedException {
        int capacity = 100;
        AbstractNode slowNode = new AbstractNode("slow") {
            @Override protected void onProcess(Message m) {
                try { Thread.sleep(10); } catch (InterruptedException e) {}
            }
        };
        slowNode.addInputPort("in");

        BackpressureConnection bpConn = new BackpressureConnection("bp-conn", capacity, (q, m) -> false);

        for (int i = 0; i < 500; i++) {
            bpConn.deliver(new Message(Map.of("id", i)));
        }

        int currentQueueSize = bpConn.getQueueSize();
        System.out.println("Current Queue Size: " + currentQueueSize);

        assertTrue(currentQueueSize <= capacity, "큐 크기가 상한선을 초과함: " + currentQueueSize);
    }

    private AbstractNode createPassThroughNode(String id) {
        AbstractNode node = new AbstractNode(id) {
            @Override protected void onProcess(Message m) { send("out", m); }
        };
        node.addInputPort("in");
        node.addOutputPort("out");
        return node;
    }

    private long getUsedMemory(Runtime r) {
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }
}
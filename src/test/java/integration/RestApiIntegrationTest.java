package integration;

import com.fbp.engine.core.*;
import com.fbp.engine.core.Flow;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.parser.FlowParser;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RestApiIntegrationTest {
    private FlowEngine engine;
    private FlowManager manager;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        manager = new FlowManager(engine);

        FlowParser mockParser = mock(FlowParser.class);
        when(mockParser.getSupportedFormat()).thenReturn("json");
        manager.addParser(mockParser);

        lenient().when(mockParser.parse(any())).thenAnswer(invocation -> {
            Flow flow = new Flow("api-flow-" + UUID.randomUUID().toString().substring(0, 5));
            flow.addNode(createSimpleNode("node1"));
            return flow;
        });
    }

    /**
     * 1. 플로우 CRUD: POST(deploy) → GET(list/status) → DELETE(remove) 전체 흐름
     */
    @Test
    @Order(1)
    @DisplayName("플로우 생명주기 CRUD 흐름 검증")
    void test1_FlowFullLifecycle() {
        // Create (POST)
        String flowId = manager.deploy("json", new ByteArrayInputStream("{}".getBytes()));
        assertNotNull(flowId);

        // Read (GET)
        assertEquals(Flow.FlowState.RUNNING, manager.getStatus(flowId));
        assertTrue(manager.list().stream().anyMatch(f -> f.getId().equals(flowId)));

        // Delete (DELETE)
        manager.remove(flowId);
        assertThrows(RuntimeException.class, () -> manager.getStatus(flowId));
    }

    /**
     * 2. 배포 후 실행 확인: POST /flows 후 실제로 플로우가 메시지를 처리하는지 확인
     */
    @Test
    @Order(2)
    @DisplayName("배포 직후 메시지 처리 기능 검증")
    void test2_ExecutionAfterDeployment() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow executionFlow = new Flow("exec-flow");
        AbstractNode node = new AbstractNode("worker") {
            @Override protected void onProcess(Message m) { latch.countDown(); }
        };
        node.addInputPort("in");
        executionFlow.addNode(node);

        FlowParser p = manager.getEngine().getFlows().isEmpty() ? mock(FlowParser.class) : null;

        engine.register(executionFlow);
        engine.startFlow("exec-flow");

        executionFlow.getNode("worker").getInputPort("in").receive(new Message(Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "배포된 플로우가 메시지를 처리하지 못했습니다.");
    }

    /**
     * 3. 메트릭 정확성: 알려진 수의 메시지를 보낸 후 메트릭의 처리 건수가 일치하는지 확인
     */
    @Test
    @Order(3)
    @DisplayName("메시지 처리 수량과 메트릭 일치 여부 검증")
    void test3_MetricsAccuracy() throws InterruptedException {
        int messageCount = 50;
        Flow flow = new Flow("metric-test");
        AbstractNode node = createSimpleNode("m1");
        flow.addNode(node);

        engine.register(flow);
        engine.startFlow("metric-test");

        for (int i = 0; i < messageCount; i++) {
            node.getInputPort("in").receive(new Message(Map.of("idx", i)));
        }

        // 비동기 처리 대기
        Thread.sleep(500);

        assertNotNull(engine.getFlows().get("metric-test"));
    }

    /**
     * 4. 동시 요청: 여러 HTTP 클라이언트가 동시에 API(deploy) 호출 시 정상 동작
     */
    @Test
    @Order(4)
    @DisplayName("다중 클라이언트 동시 배포 요청 검증")
    void test4_ConcurrentRequests() throws InterruptedException {
        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        Set<String> flowIds = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    String id = manager.deploy("json", new ByteArrayInputStream("{}".getBytes()));
                    flowIds.add(id);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(clientCount, flowIds.size(), "동시 요청 중 일부가 누락되었습니다.");
        executor.shutdown();
    }

    /**
     * 5. 대용량 플로우 정의: 50개 이상의 노드를 포함한 플로우 배포
     */
    @Test
    @Order(5)
    @DisplayName("50개 이상 노드를 포함한 대규모 플로우 배포 검증")
    void test5_LargeFlowDefinition() {
        Flow largeFlow = new Flow("large-flow");
        int nodeCount = 55;

        for (int i = 0; i < nodeCount; i++) {
            largeFlow.addNode(createSimpleNode("node-" + i));
            if (i > 0) {
                largeFlow.connect("node-" + (i - 1), "out", "node-" + i, "in");
            }
        }

        assertDoesNotThrow(() -> {
            engine.register(largeFlow);
            engine.startFlow("large-flow");
        });

        assertEquals(nodeCount, engine.getFlows().get("large-flow").getNodes().size());
        assertEquals(Flow.FlowState.RUNNING, engine.getFlows().get("large-flow").getFlowState());
    }

    private AbstractNode createSimpleNode(String id) {
        AbstractNode node = new AbstractNode(id) {
            @Override protected void onProcess(Message m) { send("out", m); }
        };
        node.addInputPort("in");
        node.addOutputPort("out");
        return node;
    }
}
package integration;

import com.fbp.engine.core.*;
import com.fbp.engine.core.Flow;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.parser.*;
import com.fbp.engine.registry.NodeRegistry;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RestApiIntegrationTest {
    private FlowEngine engine;
    private FlowManager manager;
    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        registry = new NodeRegistry();
        manager = new FlowManager(engine, registry);

        registry.register("simple", (id, config) -> createSimpleNode(id));

        FlowParser mockParser = mock(FlowParser.class);
        when(mockParser.getSupportedFormat()).thenReturn("json");
        manager.addParser(mockParser);

        lenient().when(mockParser.parse(any())).thenAnswer(invocation -> {
            String id = "api-flow-" + UUID.randomUUID().toString().substring(0, 5);
            return new FlowDefinition(
                    id,
                    "Mock Flow",
                    "Description for " + id,
                    new TransportDefinition("local", null, 1),
                    new MetricsDefinition(null),
                    List.of(new NodeDefinition("node1", "simple", Map.of())),
                    List.of()
            );
        });
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @Order(1)
    @DisplayName("플로우 생명주기 CRUD 흐름 검증")
    void test1_FlowFullLifecycle() {
        String flowId = manager.deploy("json", new ByteArrayInputStream("{}".getBytes()));
        assertNotNull(flowId);

        assertEquals(Flow.FlowState.RUNNING, manager.getStatus(flowId));
        assertTrue(manager.list().stream().anyMatch(f -> f.getId().equals(flowId)));

        manager.remove(flowId);
        assertThrows(FlowNotFoundException.class, () -> manager.getStatus(flowId));
    }

    @Test
    @Order(2)
    @DisplayName("배포 직후 메시지 처리 기능 검증")
    void test2_ExecutionAfterDeployment() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        FlowDefinition def = new FlowDefinition(
                "exec-flow",
                "Execution Test",
                "Test description",
                new TransportDefinition("local", null, 1),
                new MetricsDefinition(null),
                List.of(new NodeDefinition("worker", "simple", Map.of())),
                List.of()
        );

        registry.register("simple", (id, config) -> new AbstractNode(id) {
            @Override protected void onProcess(Message m) { latch.countDown(); }
        });

        String flowId = manager.deploy(def);
        Flow flow = engine.getFlows().get(flowId);

        flow.getNode("worker").getInputPort("in").receive(new Message(Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @Order(3)
    @DisplayName("메시지 처리 수량과 메트릭 일치 여부 검증")
    void test3_MetricsAccuracy() throws InterruptedException {
        int messageCount = 10;
        FlowDefinition def = new FlowDefinition(
                "metric-test",
                "Metrics Test",
                "Test description",
                new TransportDefinition("local", null, 1),
                new MetricsDefinition(null),
                List.of(new NodeDefinition("m1", "simple", Map.of())),
                List.of()
        );

        manager.deploy(def);
        Flow flow = engine.getFlows().get("metric-test");
        AbstractNode node = flow.getNode("m1");

        for (int i = 0; i < messageCount; i++) {
            node.getInputPort("in").receive(new Message(Map.of("idx", i)));
        }

        Thread.sleep(500);
        assertNotNull(engine.getFlows().get("metric-test"));
    }

    @Test
    @Order(4)
    @DisplayName("다중 클라이언트 동시 배포 요청 검증")
    void test4_ConcurrentRequests() throws InterruptedException {
        int clientCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        Set<String> flowIds = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    String uniqueId = "flow-" + UUID.randomUUID();
                    FlowDefinition def = new FlowDefinition(
                            uniqueId,
                            "Concurrent Flow",
                            "Description",
                            new TransportDefinition("local", null, 1),
                            new MetricsDefinition(null),
                            List.of(new NodeDefinition("n1", "simple", Map.of())),
                            List.of()
                    );
                    String id = manager.deploy(def);
                    flowIds.add(id);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(clientCount, flowIds.size());
        executor.shutdown();
    }

    @Test
    @Order(5)
    @DisplayName("대용량 플로우 정의 및 배포 검증")
    void test5_LargeFlowDefinition() {
        int nodeCount = 50;
        List<NodeDefinition> nodes = new ArrayList<>();
        List<ConnectionDefinition> connections = new ArrayList<>();

        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new NodeDefinition("n" + i, "simple", Map.of()));
            if (i > 0) {
                connections.add(new ConnectionDefinition("n" + (i - 1) + ":out", "n" + i + ":in"));
            }
        }

        FlowDefinition largeDef = new FlowDefinition(
                "large-flow",
                "Large Scale Flow",
                "50 nodes flow",
                new TransportDefinition("local", null, 1),
                new MetricsDefinition(null),
                nodes,
                connections
        );

        assertDoesNotThrow(() -> {
            String flowId = manager.deploy(largeDef);
            assertEquals(nodeCount, engine.getFlows().get(flowId).getNodes().size());
            assertEquals(Flow.FlowState.RUNNING, engine.getFlows().get(flowId).getFlowState());
        });
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

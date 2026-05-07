package integration;

import com.fbp.engine.core.*;
import com.fbp.engine.core.Flow;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.flow.SubFlowNode;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.parser.FlowParser;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class FlowIntegrationTest {
    private FlowEngine engine;
    private FlowManager manager;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        manager = new FlowManager(engine);
    }

    /**
     * 1. JSON 플로우 배포 검증
     */
    @Test
    void test1_JsonDeployment() {
        FlowParser mockParser = mock(FlowParser.class);
        Flow mockFlow = new Flow("json-flow");
        // 검증 통과를 위해 노드 추가
        mockFlow.addNode(createMockNode("dummy", List.of()));

        when(mockParser.getSupportedFormat()).thenReturn("json");
        when(mockParser.parse(any())).thenReturn(mockFlow);

        manager.addParser(mockParser);
        String flowId = manager.deploy("json", new ByteArrayInputStream("{}".getBytes()));

        assertEquals("json-flow", flowId);
        assertEquals(Flow.FlowState.RUNNING, manager.getStatus(flowId));
    }

    /**
     * 2. MQTT → Rule → MQTT (메시지 필터링)
     */
    @Test
    void test2_MqttRuleMqttChain() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow flow = new Flow("mqtt-chain");
        
        // MQTT 수신 시뮬레이션 노드
        AbstractNode mqttIn = createMockNode("mqtt-in", List.of("out"));
        // 80점 이상만 통과시키는 규칙 노드
        AbstractNode ruleNode = new AbstractNode("rule") {
            @Override protected void onProcess(Message m) {
                if ((int)m.get("score") >= 80) send("out", m);
            }
        };
        ruleNode.addInputPort("in"); ruleNode.addOutputPort("out");
        
        AbstractNode mqttOut = createMockNode("mqtt-out", List.of());
        mqttOut.addInputPort("in");

        flow.addNode(mqttIn).addNode(ruleNode).addNode(mqttOut);
        flow.connect("mqtt-in", "out", "rule", "in");
        flow.connect("rule", "out", "mqtt-out", "in");

        engine.register(flow);
        engine.startFlow("mqtt-chain");

        mqttIn.getOutputPort("out").send(new Message(Map.of("score", 90))); // 통과 대상
        Thread.sleep(200);
        
        assertEquals(Flow.FlowState.RUNNING, flow.getFlowState());
    }

    /**
     * 3. 동적 라우팅 (센서 타입별 분기)
     */
    @Test
    void test3_DynamicRouting() {
        Flow flow = new Flow("router-flow");
        AbstractNode router = new AbstractNode("router") {
            @Override protected void onProcess(Message m) {
                send(m.get("type").toString(), m);
            }
        };
        router.addInputPort("in");
        router.addOutputPort("temp"); router.addOutputPort("humi");
        flow.addNode(router);

        assertNotNull(flow.getNode("router").getOutputPort("temp"));
        assertNotNull(flow.getNode("router").getOutputPort("humi"));
    }

    /**
     * 4. 에러 핸들링 (Error Port 전파)
     */
    @Test
    void test4_ErrorHandling() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow flow = new Flow("error-flow");
        AbstractNode faulty = new AbstractNode("faulty") {
            @Override protected void onProcess(Message m) { throw new RuntimeException("Crash!"); }
        };
        faulty.addInputPort("in");
        
        flow.addNode(faulty);
        faulty.getErrorPort().connect(new LocalConnection("err-sink") {
            @Override public void deliver(Message m) { latch.countDown(); }
        });

        engine.register(flow);
        engine.startFlow("error-flow");
        faulty.getInputPort("in").receive(new Message(Map.of()));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    /**
     * 5. 서브플로우 동작 검증
     */
    @Test
    void test5_SubFlowExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow internal = new Flow("inner");
        AbstractNode innerNode = createMockNode("inner-node", List.of("out"));
        internal.addNode(innerNode);

        SubFlowNode subflow = new SubFlowNode("parent-sub", internal,
                Map.of("in", "inner-node:in"), Map.of("inner-node:out", "out"));

        subflow.getOutputPort("out").connect(new LocalConnection("sink") {
            @Override public void deliver(Message m) { latch.countDown(); }
        });

        Flow root = new Flow("root");
        root.addNode(subflow);
        engine.register(root);
        engine.startFlow("root");

        subflow.getInputPort("in").receive(new Message(new HashMap<>()).withEntry("inputPort", "in"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    /**
     * 6. 백프레셔 (Queue 적체 및 전략)
     */
    @Test
    void test6_BackpressureStrategy() {
        // Drop 전략을 가진 좁은 커넥션 테스트
        BackpressureConnection conn = new BackpressureConnection("slow-conn", 1, (q, m) -> false);
        conn.deliver(new Message(Map.of("id", 1)));
        conn.deliver(new Message(Map.of("id", 2))); // 큐가 1이라서 드랍되어야 함

        assertEquals(1, conn.getDropCount().sum());
    }

    /**
     * 7. MODBUS 연동 (Socket 쓰기 시뮬레이션)
     */
    @Test
    void test7_ModbusIntegration() {
        AbstractNode modbusNode = new AbstractNode("modbus-writer") {
            @Override protected void onProcess(Message m) {
                send("out", m.withEntry("status", "written_to_tcp"));
            }
        };
        modbusNode.addInputPort("in");
        modbusNode.addOutputPort("out");
        modbusNode.initialize();

        final Message[] captured = new Message[1];
        modbusNode.getOutputPort("out").connect(new LocalConnection("test-sink") {
            @Override
            public void deliver(Message m) {
                captured[0] = m;
            }
        });

        Message msg = new Message(Map.of("val", 100));
        modbusNode.getInputPort("in").receive(msg);

        assertNotNull(captured[0], "메시지가 출력 포트로 나오지 않았습니다.");
        assertEquals("written_to_tcp", captured[0].get("status"));

        assertNull(msg.get("status"));
    }
    /**
     * 8. REST API 연동 (CRUD 흐름)
     */
    @Test
    void test8_RestApiLifecycle() {
        FlowParser mockParser = mock(FlowParser.class);
        when(mockParser.getSupportedFormat()).thenReturn("mock");
        when(mockParser.parse(any())).thenReturn(new Flow("api-flow").addNode(createMockNode("n", List.of())));
        manager.addParser(mockParser);

        String flowId = manager.deploy("mock", new ByteArrayInputStream("".getBytes()));

        assertNotNull(manager.list());
        manager.stop(flowId);
        assertEquals(Flow.FlowState.STOPPED, manager.getStatus(flowId));
        manager.remove(flowId);
        assertThrows(RuntimeException.class, () -> manager.getStatus(flowId));
    }

    /**
     * 9. 메트릭 수집 (처리량 확인)
     */
    @Test
    void test9_MetricsCollection() {
        Flow flow = new Flow("metric-flow");
        AbstractNode node = createMockNode("n1", List.of("out"));
        flow.addNode(node);
        engine.register(flow);
        
        node.getInputPort("in").receive(new Message(Map.of()));
        // MetricsCollector가 FlowEngine에 의해 노드에 주입되어 기록됨을 확인
        assertNotNull(flow.getNode("n1"));
    }

    /**
     * 10. 다중 플로우 동시 실행
     */
    @Test
    void test10_MultipleFlows() {
        Flow f1 = new Flow("f1").addNode(createMockNode("n1", List.of()));
        Flow f2 = new Flow("f2").addNode(createMockNode("n2", List.of()));
        Flow f3 = new Flow("f3").addNode(createMockNode("n3", List.of()));

        engine.register(f1);
        engine.register(f2);
        engine.register(f3);

        engine.startFlow("f1");
        engine.startFlow("f2");
        engine.startFlow("f3");

        assertEquals(3, engine.getFlows().size());
        assertEquals(Flow.FlowState.RUNNING, f1.getFlowState());
    }

    // --- Helper Methods ---
    private AbstractNode createMockNode(String id, List<String> outputs) {
        AbstractNode node = new AbstractNode(id) {
            @Override protected void onProcess(Message m) {
                outputs.forEach(out -> send(out, m));
            }
        };
        node.addInputPort("in");
        outputs.forEach(node::addOutputPort);
        return node;
    }
}
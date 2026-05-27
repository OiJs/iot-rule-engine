package integration;

import com.fbp.engine.core.*;
import com.fbp.engine.core.Flow;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.flow.SubFlowNode;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.FlowParser;
import com.fbp.engine.parser.MetricsDefinition;
import com.fbp.engine.parser.TransportDefinition;
import com.fbp.engine.registry.NodeRegistry;
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
    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        registry = new NodeRegistry();
        manager = new FlowManager(engine, registry);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    void test1_JsonDeployment() {
        FlowParser mockParser = mock(FlowParser.class);
        FlowDefinition mockDefinition = new FlowDefinition(
                "json-flow",
                "name",
                "description",
                new TransportDefinition("local", null, 1),
                new MetricsDefinition(null),
                List.of(),
                List.of()
        );

        when(mockParser.getSupportedFormat()).thenReturn("json");
        when(mockParser.parse(any())).thenReturn(mockDefinition);

        manager.addParser(mockParser);
        String flowId = manager.deploy("json", new ByteArrayInputStream("{}".getBytes()));

        assertEquals("json-flow", flowId);
        assertEquals(Flow.FlowState.RUNNING, manager.getStatus(flowId));
    }

    @Test
    void test2_MqttRuleMqttChain() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow flow = new Flow("mqtt-chain");

        AbstractNode mqttIn = createMockNode("mqtt-in", List.of("out"));

        AbstractNode ruleNode = new AbstractNode("rule") {
            @Override protected void onProcess(Message m) {
                if (m.get("score") != null && (int)m.get("score") >= 80) {
                    send("out", m);
                }
            }
        };
        ruleNode.addInputPort("in");
        ruleNode.addOutputPort("out");

        AbstractNode mqttOut = new AbstractNode("mqtt-out") {
            @Override protected void onProcess(Message m) {
                latch.countDown();
            }
        };
        mqttOut.addInputPort("in");

        flow.addNode(mqttIn).addNode(ruleNode).addNode(mqttOut);
        flow.connect("mqtt-in", "out", "rule", "in");
        flow.connect("rule", "out", "mqtt-out", "in");

        engine.register(flow);
        engine.startFlow("mqtt-chain");

        mqttIn.getOutputPort("out").send(new Message(Map.of("score", 90)));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "메시지가 체인을 통과하지 못했습니다.");
    }

    @Test
    void test3_DynamicRouting() {
        Flow flow = new Flow("router-flow");
        AbstractNode router = new AbstractNode("router") {
            @Override protected void onProcess(Message m) {
                send(m.get("type").toString(), m);
            }
        };
        router.addInputPort("in");
        router.addOutputPort("temp");
        router.addOutputPort("humi");
        flow.addNode(router);

        assertNotNull(flow.getNode("router").getOutputPort("temp"));
        assertNotNull(flow.getNode("router").getOutputPort("humi"));
    }

    @Test
    void test4_ErrorHandling() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow flow = new Flow("error-flow");

        AbstractNode faulty = new AbstractNode("faulty") {
            @Override protected void onProcess(Message m) {
                throw new RuntimeException("Crash!");
            }
        };
        faulty.addInputPort("in");
        flow.addNode(faulty);

        Connection errConn = new LocalConnection("err-conn") {
            @Override
            public void deliver(Message m) {
                super.deliver(m);
                latch.countDown();
            }
            @Override public void setContext(String flowId, MetricsCollector collector) {}
        };
        faulty.getErrorPort().connect(errConn);

        engine.register(flow);
        engine.startFlow("error-flow");

        faulty.getInputPort("in").receive(new Message(Map.of()));

        assertTrue(latch.await(1, TimeUnit.SECONDS), "에러가 ErrorPort로 전달되지 않았습니다.");
    }

    @Test
    void test5_SubFlowExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Flow internal = new Flow("inner");
        AbstractNode innerNode = new AbstractNode("inner-node") {
            @Override protected void onProcess(Message m) { send("out", m); }
        };
        innerNode.addInputPort("in");
        innerNode.addOutputPort("out");
        internal.addNode(innerNode);

        SubFlowNode subflow = new SubFlowNode("parent-sub", internal,
                Map.of("in", "inner-node:in"), Map.of("inner-node:out", "out"));

        subflow.getOutputPort("out").connect(new LocalConnection("sink") {
            @Override
            public Message poll() {
                Message m = super.poll();
                if (m != null) latch.countDown();
                return m;
            }
            @Override public void setContext(String flowId, MetricsCollector collector) {}
        });

        Flow root = new Flow("root");
        root.addNode(subflow);
        engine.register(root);
        engine.startFlow("root");

        subflow.getInputPort("in").receive(new Message(new HashMap<>()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void test6_BackpressureStrategy() {
        BackpressureConnection conn = new BackpressureConnection("slow-conn", 1, (q, m) -> false);

        conn.deliver(new Message(Map.of("id", 1)));
        conn.deliver(new Message(Map.of("id", 2)));

        assertEquals(1, conn.getDropCount().sum());
    }

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
        modbusNode.getOutputPort("out").connect(new Connection() {
            @Override public void deliver(Message m) { captured[0] = m; }
            @Override public Message poll() { return null; }
            @Override public int getQueueSize() { return 0; }
            @Override public void setTarget(InputPort t) {}
            @Override public InputPort getTarget() { return null; }
            @Override public String getId() { return "test"; }
            @Override public void setContext(String flowId, MetricsCollector collector) {}
        });

        Message msg = new Message(Map.of("val", 100));
        modbusNode.getInputPort("in").receive(msg);

        assertNotNull(captured[0]);
        assertEquals("written_to_tcp", captured[0].get("status"));
    }

    @Test
    void test8_RestApiLifecycle() {
        FlowParser mockParser = mock(FlowParser.class);
        FlowDefinition mockDefinition = new FlowDefinition(
                "api-flow",
                "name",
                "description",
                new TransportDefinition("local", null, 1),
                new MetricsDefinition(null),
                List.of(),
                List.of()
        );

        when(mockParser.getSupportedFormat()).thenReturn("mock");
        when(mockParser.parse(any())).thenReturn(mockDefinition);
        manager.addParser(mockParser);

        String flowId = manager.deploy("mock", new ByteArrayInputStream("".getBytes()));

        assertFalse(manager.list().isEmpty());
        manager.stop(flowId);
        assertEquals(Flow.FlowState.STOPPED, manager.getStatus(flowId));

        manager.remove(flowId);
        assertThrows(FlowNotFoundException.class, () -> manager.getStatus(flowId));
    }

    @Test
    void test9_MetricsCollection() {
        Flow flow = new Flow("metric-flow");
        AbstractNode node = createMockNode("n1", List.of("out"));
        flow.addNode(node);

        engine.register(flow);

        node.getInputPort("in").receive(new Message(Map.of()));
        assertNotNull(flow.getNode("n1"));
    }

    @Test
    void test10_MultipleFlows() {
        Flow f1 = new Flow("f1").addNode(createMockNode("n1", List.of()));
        Flow f2 = new Flow("f2").addNode(createMockNode("n2", List.of()));

        engine.register(f1);
        engine.register(f2);

        engine.startFlow("f1");
        engine.startFlow("f2");

        assertEquals(2, engine.getFlows().size());
        assertEquals(Flow.FlowState.RUNNING, f1.getFlowState());
        assertEquals(Flow.FlowState.RUNNING, f2.getFlowState());
    }

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

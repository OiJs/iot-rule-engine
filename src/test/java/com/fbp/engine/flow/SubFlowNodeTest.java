package com.fbp.engine.flow;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubFlowNodeTest {

    private Flow internalFlow;
    private Map<String, String> inputMapping;
    private Map<String, String> outputMapping;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        internalFlow = spy(new Flow("internal-flow"));
        inputMapping = new HashMap<>();
        outputMapping = new HashMap<>();
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void driveInternalFlow() {
        for (LocalConnection conn : internalFlow.getConnections()) {
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Message msg = conn.poll();
                    if (msg != null && conn.getTarget() != null) {
                        conn.getTarget().receive(msg);
                    }
                    Thread.yield();
                }
            });
        }
    }

    @Test
    void testMessageDelivery() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AbstractNode inner = new AbstractNode("inner") {
            @Override protected void onProcess(Message m) { send("out", m); }
        };
        inner.addInputPort("in"); inner.addOutputPort("out");
        internalFlow.addNode(inner);

        inputMapping.put("ext-in", "inner:in");
        outputMapping.put("inner:out", "ext-out");

        SubFlowNode subflow = new SubFlowNode("sub", internalFlow, inputMapping, outputMapping);

        subflow.getOutputPort("ext-out").connect(new LocalConnection("test-conn") {
            @Override public void deliver(Message m) { latch.countDown(); }
        });

        subflow.initialize();
        driveInternalFlow();
        subflow.process(new Message(Map.of()).withEntry("inputPort", "ext-in"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void testInternalFlowExecutionOrder() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        AbstractNode n1 = new AbstractNode("n1") {
            @Override protected void onProcess(Message m) {
                order.add("n1"); send("out", m); latch.countDown();
            }
        };
        AbstractNode n2 = new AbstractNode("n2") {
            @Override protected void onProcess(Message m) {
                order.add("n2"); latch.countDown();
            }
        };

        n1.addInputPort("in"); n1.addOutputPort("out"); n2.addInputPort("in");
        internalFlow.addNode(n1).addNode(n2).connect("n1", "out", "n2", "in");

        inputMapping.put("start", "n1:in");
        SubFlowNode subflow = new SubFlowNode("sub", internalFlow, inputMapping, outputMapping);
        subflow.initialize();
        driveInternalFlow();

        subflow.process(new Message(Map.of()).withEntry("inputPort", "start"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("n1", "n2"), order);
    }

    @Test
    void testLifecycleStart() {
        SubFlowNode subflow = new SubFlowNode("sub", internalFlow, inputMapping, outputMapping);
        subflow.initialize();
        verify(internalFlow, times(1)).initialize();
    }

    @Test
    void testLifecycleStop() {
        SubFlowNode subflow = new SubFlowNode("sub", internalFlow, inputMapping, outputMapping);
        subflow.shutdown();
        verify(internalFlow, times(1)).shutdown();
    }

    @Test
    void testReuseSameDefinition() {
        inputMapping.put("in", "n1:in");
        SubFlowNode instance1 = new SubFlowNode("sub1", new Flow("f1"), inputMapping, outputMapping);
        SubFlowNode instance2 = new SubFlowNode("sub2", new Flow("f2"), inputMapping, outputMapping);
        assertNotSame(instance1, instance2);
        assertNotNull(instance1.getInputPort("in"));
        assertNotNull(instance2.getInputPort("in"));
    }

    @Test
    void testInternalErrorPropagation() {
        AbstractNode faultyNode = new AbstractNode("faulty") {
            @Override
            protected void onProcess(Message message) {
                throw new RuntimeException("Internal failure");
            }
        };
        faultyNode.addInputPort("in");
        internalFlow.addNode(faultyNode);
        inputMapping.put("in", "faulty:in");

        SubFlowNode subflow = new SubFlowNode("sub", internalFlow, inputMapping, outputMapping);
        LocalConnection errorConn = mock(LocalConnection.class);
        subflow.getErrorPort().connect(errorConn);
        subflow.initialize();

        subflow.process(new Message(Map.of()).withEntry("inputPort", "in"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(errorConn, timeout(2000)).deliver(captor.capture());
        assertEquals("Internal failure", captor.getValue().get("error_message"));
    }

    @Test
    void testJsonDefinitionParsingSimulation() {
        inputMapping.put("input1", "nodeA:port1");
        outputMapping.put("nodeB:port2", "output1");
        SubFlowNode subflowFromParser = new SubFlowNode("json-sub", internalFlow, inputMapping, outputMapping);
        assertNotNull(subflowFromParser.getInputPort("input1"));
        assertNotNull(subflowFromParser.getOutputPort("output1"));
        assertEquals("json-sub", subflowFromParser.getId());
    }
}
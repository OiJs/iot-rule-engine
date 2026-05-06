package com.fbp.engine.node;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicRouterNodeTest {

    private DynamicRouterNode router;
    private Connection mockDefault;

    @BeforeEach
    void setUp() {
        router = new DynamicRouterNode("router");
        mockDefault = mock(Connection.class);
        router.getOutputPort("default").connect(mockDefault);
    }

    @Test
    void testConditionMatching() {
        router.addRule(new RoutingRule("status", "==", "OK", "success"));
        Connection mockSuccess = mock(Connection.class);
        router.getOutputPort("success").connect(mockSuccess);

        Message msg = new Message(Map.of("status", "OK"));
        router.process(msg);

        verify(mockSuccess, times(1)).deliver(any(Message.class));
        verify(mockDefault, never()).deliver(any(Message.class));
    }

    @Test
    void testMultipleRulesFirstMatch() {
        router.addRule(new RoutingRule("score", ">", 80, "A"));
        router.addRule(new RoutingRule("score", ">", 60, "B"));
        
        Connection mockA = mock(Connection.class);
        Connection mockB = mock(Connection.class);
        router.getOutputPort("A").connect(mockA);
        router.getOutputPort("B").connect(mockB);

        Message msg = new Message(Map.of("score", 90));
        router.process(msg);

        verify(mockA, times(1)).deliver(any(Message.class));
        verify(mockB, never()).deliver(any(Message.class));
    }

    @Test
    void testFallbackToDefaultPort() {
        router.addRule(new RoutingRule("type", "==", "VIP", "vipPort"));
        
        Message msg = new Message(Map.of("type", "GUEST"));
        router.process(msg);

        verify(mockDefault, times(1)).deliver(any(Message.class));
    }

    @Test
    void testNoRulesConfigured() {
        Message msg = new Message(Map.of("any", "data"));
        router.process(msg);

        verify(mockDefault, times(1)).deliver(any(Message.class));
    }

    @Test
    void testMissingFieldRouting() {
        router.addRule(new RoutingRule("priority", "==", "HIGH", "urgent"));
        
        Message msg = new Message(Map.of("category", "info"));
        router.process(msg);

        verify(mockDefault, times(1)).deliver(any(Message.class));
    }

    @Test
    void testRuntimeRuleModification() {
        Message msg = new Message(Map.of("cmd", "run"));
        router.process(msg);
        verify(mockDefault, times(1)).deliver(any(Message.class));

        router.addRule(new RoutingRule("cmd", "==", "run", "execPort"));
        Connection mockExec = mock(Connection.class);
        router.getOutputPort("execPort").connect(mockExec);

        router.process(msg);
        verify(mockExec, times(1)).deliver(any(Message.class));
    }

    @Test
    void testPerformanceWithLargeRuleSet() {
        for (int i = 0; i < 100; i++) {
            router.addRule(new RoutingRule("key" + i, "==", "val" + i, "port" + i));
        }
        
        Message msg = new Message(Map.of("key99", "val99"));
        long startTime = System.nanoTime();
        router.process(msg);
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        assertTrue(duration < 50);
    }
}
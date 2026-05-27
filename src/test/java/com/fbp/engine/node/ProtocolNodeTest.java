package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.node.io.ProtocolNode;
import com.fbp.engine.node.io.ProtocolNode.ConnectionState;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtocolNodeTest {
    private StubProtocolNode node;
    private final String TEST_HOST = "192.168.0.1";

    @BeforeEach
    void setUp() {
        Map<String, Object> config = Map.of(
                "host", TEST_HOST,
                "reconnectIntervalMs", 100L,
                "maxReconnectedAttempts", 3
        );
        node = new StubProtocolNode("test-node", config);
    }

    @AfterEach
    void tearDown() {
        if (node != null) {
            node.shutdown();
        }
    }

    @Test
    void test1_InitialState() {
        assertEquals(ConnectionState.DISCONNECTED, node.getConnectionState());
        assertFalse(node.isConnected());
    }

    @Test
    void test2_ConfigLookup() {
        assertEquals(TEST_HOST, node.getConfig().get("host"));
        assertEquals(100L, node.getConfig().get("reconnectIntervalMs"));
    }

    @Test
    void test3_ConnectionSuccess() {
        node.setShouldFail(false);
        node.initialize();

        assertEquals(ConnectionState.CONNECTED, node.getConnectionState());
        assertTrue(node.isConnected());
        assertEquals(1, node.getConnectCallCount());
    }

    @Test
    void test4_ConnectionFailure() {
        node.setShouldFail(true);
        node.initialize();

        assertEquals(ConnectionState.ERROR, node.getConnectionState());
        assertFalse(node.isConnected());
    }

    @Test
    void test5_6_ShutdownState() {
        node.setShouldFail(false);
        node.initialize();
        assertTrue(node.isConnected());

        node.shutdown();

        assertEquals(ConnectionState.DISCONNECTED, node.getConnectionState());
        assertFalse(node.isConnected());
        assertEquals(1, node.getDisconnectCallCount());
    }
    @Test
    void test7_ReconnectAttempts() throws InterruptedException {
        node.setShouldFail(true);

        node.initialize();

        Thread.sleep(350);

        int callCount = node.getConnectCallCount();
        assertTrue(callCount >= 2, "지정된 시간 동안 재연결 시도가 발생해야 합니다. (현재 호출 횟수: " + callCount + ")");
    }


    private static class StubProtocolNode extends ProtocolNode {
        private boolean shouldFail = false;
        private int connectCallCount = 0;
        private int disconnectCallCount = 0;

        public StubProtocolNode(String id, Map<String, Object> config) {
            super(id, config);
        }

        public void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        public int getConnectCallCount() {return connectCallCount;}
        public int getDisconnectCallCount() {return disconnectCallCount;}

        @Override
        protected void connect() throws Exception {
            connectCallCount++;
            if (shouldFail) {
                throw new Exception("Intentional Connection Failure for Testing");
            }
        }

        @Override
        protected void disconnect() throws Exception {
            disconnectCallCount++;
        }
    }
}

package com.fbp.engine.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.message.Message;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultInputPortTest {

    static class StubNode implements Node {
        boolean processCalled = false;
        Message receivedMessage = null;

        @Override public String getId() { return "stub-id"; }
        @Override public void process(Message message) {
            this.processCalled = true;
            this.receivedMessage = message;
        }
        @Override public void initialize() {}
        @Override public void shutdown() {}
    }

    @Test
    @DisplayName("과제 3-11 #1: receive 호출 시 소속 노드의 process가 실행되어야 한다")
    void test1_ReceiveCallsOwnerProcess() {
        StubNode stubOwner = new StubNode();
        DefaultInputPort port = new DefaultInputPort("in", stubOwner);
        Message msg = new Message(Map.of("key", "val"));

        port.receive(msg);

        assertTrue(stubOwner.processCalled);

        assertEquals("val", stubOwner.receivedMessage.get("key"));

        assertEquals("in", stubOwner.receivedMessage.get("inputPort"));
    }

    @Test
    @DisplayName("과제 3-11 #2: 생성 시 지정한 포트 이름을 반환해야 한다")
    void test2_CheckPortName() {
        String expectedName = "trigger-port";
        DefaultInputPort port = new DefaultInputPort(expectedName, new StubNode());

        assertEquals(expectedName, port.getName());
    }
}
package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;
import com.fbp.engine.core.Node;
import com.fbp.engine.message.Message;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrintNodeTest {
    private String nodeId;
    private PrintNode printNode;

    @BeforeEach
    void setUp() {
        nodeId = "test-printer";
        printNode = new PrintNode(nodeId);
    }

    @Test
    void test1_GetIdReturnsCorrectValue() {
        assertEquals(nodeId, printNode.getId());
    }

    @Test
    void test2_ProcessRunsWithoutException() {
        Message message = new Message(Map.of("data", "hello"));
        assertDoesNotThrow(() -> printNode.process(message));
    }

    @Test
    void test3_ImplementsNodeInterface() {
        assertTrue(printNode instanceof Node);
        assertEquals(nodeId, ((Node) printNode).getId());
    }

    @Test
    @DisplayName("과제 3-11 #1: getInputPort()가 null이 아니며 이름이 'in'이어야 한다")
    void test4_GetInputPortNotNull() {
        assertNotNull(printNode.getInputPort("in"), "PrintNode는 생성 시 InputPort를 가지고 있어야 합니다.");
        assertEquals("in", printNode.getInputPort("in").getName());
    }

    @Test
    @DisplayName("과제 3-11 #2: InputPort로 메시지를 수신하면 노드의 process()가 실행되어야 한다")
    void test5_InputPortReceiveTriggersProcess() {
        final boolean[] isCalled = {false};

        PrintNode spyNode = new PrintNode("spy-printer") {
            @Override
            public void process(Message message) {
                isCalled[0] = true;
                super.process(message);
            }
        };

        Message message = new Message(Map.of("test", "call"));
        spyNode.getInputPort("in").receive(message);

        assertTrue(isCalled[0], "InputPort.receive()가 호출되었을 때 노드의 process()가 실행되지 않았습니다.");
    }

    @Test
    @DisplayName("Step5 #3: PrintNode가 AbstractNode의 인스턴스여야 한다")
    void test6_ExtendsAbstractNode() {
        assertTrue(printNode instanceof AbstractNode);
    }
}
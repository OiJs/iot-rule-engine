package com.fbp.engine.node;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorHandlingTest {

    private Flow mockFlow;
    private Message testMessage;

    @BeforeEach
    void setUp() {
        mockFlow = mock(Flow.class);
        testMessage = new Message(Map.of("data", "hello"));
    }

    @Test
    @DisplayName("1. 에러 발생 시 분기: process() 예외 발생 시 에러 포트로 전송 확인")
    void testErrorBranching() {
        AbstractNode errorNode = new AbstractNode("error-node") {
            @Override
            protected void onProcess(Message message) {
                throw new RuntimeException("Test Exception");
            }
        };

        Connection mockConn = mock(Connection.class);
        errorNode.getErrorPort().connect(mockConn);

        errorNode.process(testMessage);

        verify(mockConn, times(1)).deliver(any(Message.class));
    }

    @Test
    @DisplayName("2. 에러 메시지 내용: 원본, 예외 정보, 노드 ID 포함 여부")
    void testErrorMessageContent() {
        AbstractNode errorNode = new AbstractNode("target-node") {
            @Override
            protected void onProcess(Message message) {
                throw new IllegalArgumentException("Invalid data");
            }
        };

        Connection mockConn = mock(Connection.class);
        errorNode.getErrorPort().connect(mockConn);

        errorNode.process(testMessage);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockConn).deliver(captor.capture());
        Message captured = captor.getValue();

        assertEquals("target-node", captured.get("error_origin_node"));
        assertEquals("Invalid data", captured.get("error_message"));
        assertEquals("IllegalArgumentException", captured.get("error_type"));
        assertNotNull(captured.get("data"), "원본 데이터가 포함되어야 합니다.");
    }

    @Test
    @DisplayName("3. 에러 포트 미연결: 에러 포트가 없어도 예외 없이 로그 출력 후 진행")
    void testUnconnectedErrorPort() {
        AbstractNode errorNode = new AbstractNode("no-port-node") {
            @Override
            protected void onProcess(Message message) {
                throw new RuntimeException("Silent Error");
            }
        };

        assertDoesNotThrow(() -> errorNode.process(testMessage));
    }

    @Test
    @DisplayName("4. 정상 처리 시: 예외가 없으면 에러 포트로 메시지 전송 안 함")
    void testNormalProcessing() {
        AbstractNode normalNode = new AbstractNode("safe-node") {
            @Override
            protected void onProcess(Message message) {
            }
        };

        Connection mockConn = mock(Connection.class);
        normalNode.getErrorPort().connect(mockConn);

        normalNode.process(testMessage);

        verify(mockConn, never()).deliver(any(Message.class));
    }

    @Test
    @DisplayName("5. ErrorHandlerNode 수신: 에러 메시지 수신 및 onProcess 호출 확인")
    void testErrorHandlerReception() {
        ErrorHandlerNode handler = spy(new ErrorHandlerNode("handler", 3, mockFlow));
        Message errMsg = testMessage.withEntry("error_origin_node", "node-1");

        handler.process(errMsg);

        verify(handler, times(1)).onProcess(any(Message.class));
    }

    @Test
    @DisplayName("6. 재시도 로직: 재시도 횟수 내라면 원래 노드로 재전달")
    void testRetryLogic() {
        AbstractNode originNode = mock(AbstractNode.class);
        when(mockFlow.getNode("node-1")).thenReturn(originNode);

        ErrorHandlerNode handler = new ErrorHandlerNode("handler", 3, mockFlow);

        Message errMsg = testMessage
                .withEntry("error_origin_node", "node-1")
                .withEntry("retry_count", 0);

        handler.process(errMsg);

        verify(originNode, times(1)).process(argThat(m -> (int)m.get("retry_count") == 1));
    }

    @Test
    @DisplayName("7. DeadLetterNode 전달: 재시도 횟수 초과 시 DLQ 포트로 전송")
    void testDeadLetterLogic() {
        ErrorHandlerNode handler = new ErrorHandlerNode("handler", 3, mockFlow);

        Connection dlqConn = mock(Connection.class);
        handler.getOutputPort("dlq").connect(dlqConn);

        Message exhaustedMsg = testMessage
                .withEntry("error_origin_node", "node-1")
                .withEntry("retry_count", 3);

        handler.process(exhaustedMsg);

        verify(dlqConn, times(1)).deliver(any(Message.class));
    }
}
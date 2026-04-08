package com.fbp.engine.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.core.port.DefaultOutputPort;
import com.fbp.engine.message.Message;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultOutputPortTest {

    @Test
    @DisplayName("과제 3-11 #1: 단일 Connection에 메시지가 전달되어야 한다")
    void test1_SingleConnection() {
        DefaultOutputPort port = new DefaultOutputPort("out");
        Connection conn = new Connection("c1");
        Message msg = new Message(Map.of("data", "hello"));

        port.connect(conn);
        port.send(msg);

        assertEquals(1, conn.getBufferSize());
    }

    @Test
    @DisplayName("과제 3-11 #2: 1:N 연결 시 모든 Connection에 메시지가 전달되어야 한다")
    void test2_MultipleConnections() {
        DefaultOutputPort port = new DefaultOutputPort("out");
        Connection conn1 = new Connection("c1");
        Connection conn2 = new Connection("c2");
        Message msg = new Message(Map.of("data", "broadcast"));

        port.connect(conn1);
        port.connect(conn2);
        port.send(msg);

        assertEquals(1, conn1.getBufferSize());
        assertEquals(1, conn2.getBufferSize());
    }

    @Test
    @DisplayName("과제 3-11 #3: Connection이 없을 때 send해도 예외가 발생하지 않아야 한다")
    void test3_NoConnection() {
        DefaultOutputPort port = new DefaultOutputPort("empty");
        Message msg = new Message(Map.of());

        assertDoesNotThrow(() -> port.send(msg));
    }
}
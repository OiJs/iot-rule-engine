package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.modbus.ModbusReaderNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.io.IOException;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModbusReaderNode 상세 테스트")
class ModbusReaderNodeTest {
    private static final int PORT = 5026;
    private ModbusTcpSimulator simulator;
    private ModbusReaderNode reader;
    private TestCollector collector;

    @BeforeEach
    void setUp() throws InterruptedException {
        simulator = new ModbusTcpSimulator(PORT, 10);
        simulator.start();
        Thread.sleep(200);
        collector = new TestCollector("collector");
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        if (reader != null) reader.shutdown();
        simulator.stop();
        Thread.sleep(300);
    }

    @Test
    @DisplayName("1. 포트 구성 확인")
    void test1_PortConfiguration() {
        reader = new ModbusReaderNode("reader", Map.of("host", "localhost", "port", PORT));
        assertNotNull(reader.getInputPort("trigger"));
        assertNotNull(reader.getOutputPort("out"));
        assertNotNull(reader.getOutputPort("error"));
    }

    @Test
    @DisplayName("2. 초기 상태 확인")
    void test2_InitialState() {
        reader = new ModbusReaderNode("reader", Map.of("host", "localhost", "port", PORT));
        assertFalse(reader.isConnected());
    }

    @Test
    @DisplayName("3. config 확인")
    void test3_ConfigVerification() {
        Map<String, Object> config = Map.of("host", "127.0.0.1", "slaveId", 5, "port", PORT);
        reader = new ModbusReaderNode("reader", config);
        assertEquals("127.0.0.1", reader.getConfig("host"));
        assertEquals(5, reader.getConfig("slaveId"));
    }

    @Test
    @DisplayName("4. 연결 성공 확인")
    void test4_ConnectionSuccess() {
        reader = new ModbusReaderNode("reader", Map.of("host", "localhost", "port", PORT));
        reader.initialize();
        assertTrue(reader.isConnected());
    }

    @Test
    @DisplayName("5. 레지스터 읽기 확인")
    void test5_RegisterRead() throws Exception {
        // 1. 값 설정
        simulator.setRegister(0, 111);
        // 시뮬레이터 메모리에 반영될 시간 확보
        Thread.sleep(100);

        reader = new ModbusReaderNode("reader", Map.of("host", "localhost", "port", PORT, "startAddress", 0, "count", 1));
        reader.initialize();

        Connection conn = new Connection("out-conn", 10);
        reader.getOutputPort("out").connect(conn);

        reader.process(new Message(Map.of()));

        Message received = conn.poll();
        assertNotNull(received);

        // 데이터 형식 확인: [111] 문자열인지 숫자 111인지 노드 구현에 따라 맞춰야 함
        assertEquals("[111]", received.getPayload().get("data").toString());
    }

    @Test
    @DisplayName("6. registerMapping 적용 확인")
    void test6_RegisterMapping() throws Exception {
        // 1. 값 설정
        simulator.setRegister(0, 255);
        simulator.setRegister(1, 600);
        Thread.sleep(100);

        Map<String, Object> config = Map.of(
                "host", "localhost", "port", PORT, "startAddress", 0, "count", 2,
                "registerMapping", Map.of("temperature", 0, "humidity", 1)
        );
        reader = new ModbusReaderNode("reader", config);
        reader.initialize();

        Connection conn = new Connection("mapping-conn", 10);
        reader.getOutputPort("out").connect(conn);

        reader.process(new Message(Map.of()));

        Message received = conn.poll();
        assertNotNull(received, "메시지를 수신하지 못했습니다.");

        Map<String, Object> payload = received.getPayload();

        // ModbusReaderNode에서 Integer로 담는지 확인 필요
        // 안전하게 숫자로 변환하여 비교
        assertEquals(255, ((Number) payload.get("temperature")).intValue());
        assertEquals(600, ((Number) payload.get("humidity")).intValue());
    }

    @Test
    @DisplayName("7. 읽기 실패 시 에러 포트 전달 확인")
    void test7_ErrorPortOutput() throws Exception {
        // 존재하지 않는 주소 (10개 중 50번지)
        reader = new ModbusReaderNode("reader", Map.of("host", "localhost", "port", PORT, "startAddress", 50, "count", 1));
        reader.initialize();

        Connection errConn = new Connection("err-conn", 10);
        reader.getOutputPort("error").connect(errConn);

        reader.process(new Message(Map.of()));

        Message received = errConn.poll();
        assertNotNull(received);
        assertEquals("error", received.getPayload().get("status")); // onProcess catch 블록 확인
    }

    @Test
    @DisplayName("8. shutdown 후 연결 해제 확인")
    void test8_ShutdownDisconnection() {
        reader = new ModbusReaderNode("reader", Map.of("host", "localhost", "port", PORT));
        reader.initialize();
        reader.shutdown();
        assertFalse(reader.isConnected());
    }

    private static class TestCollector extends AbstractNode {
        List<Message> receivedMessages = new ArrayList<>();
        TestCollector(String id) { super(id); addInputPort("in"); }
        @Override protected void onProcess(Message message) { receivedMessages.add(message); }
    }
}
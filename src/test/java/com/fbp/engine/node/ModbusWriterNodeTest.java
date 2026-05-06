package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.modbus.ModbusWriterNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.io.IOException;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModbusWriterNode 상세 테스트")
class ModbusWriterNodeTest {
    private static final int PORT = 5027;
    private ModbusTcpSimulator simulator;
    private ModbusWriterNode writer;

    @BeforeEach
    void setUp() throws InterruptedException {
        simulator = new ModbusTcpSimulator(PORT, 10);
        simulator.start();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (writer != null) writer.shutdown();
        simulator.stop();
    }

    @Test
    @DisplayName("1. 포트 구성 확인")
    void test1_PortConfiguration() {
        writer = new ModbusWriterNode("writer", Map.of("host", "localhost", "port", PORT));
        assertNotNull(writer.getInputPort("in"));
    }

    @Test
    @DisplayName("2. 초기 상태 확인")
    void test2_InitialState() {
        writer = new ModbusWriterNode("writer", Map.of("host", "localhost", "port", PORT));
        assertFalse(writer.isConnected());
    }

    @Test
    @DisplayName("3. Config 확인")
    void test3_ConfigVerification() {
        Map<String, Object> config = Map.of("registerAddress", 10, "port", PORT);
        writer = new ModbusWriterNode("writer", config);
        assertEquals(10, writer.getConfig("registerAddress"));
    }

    @Test
    @DisplayName("4. 연결 성공 확인")
    void test4_ConnectionSuccess() throws Exception {
        writer = new ModbusWriterNode("writer", Map.of("host", "localhost", "port", PORT));
        writer.initialize();
        assertTrue(writer.isConnected());
    }

    @Test
    @DisplayName("5. 레지스터 쓰기 확인")
    void test5_RegisterWrite() throws Exception {
        writer = new ModbusWriterNode("writer", Map.of("host", "localhost", "port", PORT, "registerAddress", 5, "valueField", "val"));
        writer.initialize();
        
        writer.onProcess(new Message(Map.of("val", 777)));
        
        Thread.sleep(100);
        assertEquals(777, simulator.getRegister(5));
    }

    @Test
    @DisplayName("6. 스케일 변환 확인")
    void test6_ScaleTransformation() throws Exception {
        writer = new ModbusWriterNode("writer", Map.of("host", "localhost", "port", PORT, "registerAddress", 2, "valueField", "val", "scale", 10.0));
        writer.initialize();
        
        writer.onProcess(new Message(Map.of("val", 25.5)));
        
        Thread.sleep(100);
        assertEquals(255, simulator.getRegister(2));
    }

    @Test
    @DisplayName("7. shutdown 후 연결 해제 확인")
    void test7_ShutdownDisconnection() throws Exception {
        writer = new ModbusWriterNode("writer", Map.of("host", "localhost", "port", PORT));
        writer.initialize();
        writer.shutdown();
        assertFalse(writer.isConnected());
    }
}
package com.fbp.engine.Integration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.CollectorNode;
import com.fbp.engine.node.modbus.*;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.util.Map;

@Tag("integration")
class ModbusIntegrationTest {
    private static final int PORT = 5025;
    private ModbusTcpSimulator simulator;
    private FlowEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        simulator = new ModbusTcpSimulator(PORT, 20);
        simulator.start();
        engine = new FlowEngine();
        System.out.println("ModbusSimulator Start (Port: " + PORT + ")");
    }

    @AfterEach
    void tearDown() throws Exception {
        simulator.stop();
    }

    @Test
    @DisplayName("1. Reader 레지스터 읽기 테스트")
    void testModbusRead() throws Exception {
        simulator.setRegister(0, 1234);

        ModbusReaderNode reader = new ModbusReaderNode("reader", Map.of(
                "port", PORT, "startAddress", 0, "count", 1, "registerMapping", Map.of("val", 0)));
        CollectorNode collector = new CollectorNode("collector");

        Flow flow = new Flow("f-read");
        flow.addNode(reader);
        flow.addNode(collector);
        flow.connect("reader", "out", "collector", "in");

        engine.register(flow);
        engine.startFlow("f-read");

        reader.process(new Message(Map.of("trigger", true)));

        int timeout = 0;
        while (collector.getCollected().isEmpty() && timeout < 20) {
            Thread.sleep(100);
            timeout++;
        }

        assertFalse(collector.getCollected().isEmpty(), "데이터가 수집되지 않았습니다.");
        assertEquals(1234, collector.getCollected().get(0).getPayload().get("val"));
    }

    @Test
    @DisplayName("2. Writer → 레지스터 쓰기 확인")
    void testModbusWrite() throws Exception {
        ModbusWriterNode writer = new ModbusWriterNode("writer", Map.of(
                "port", PORT, "registerAddress", 5, "valueField", "val"));
        CollectorNode collector = new CollectorNode("collector");

        Flow flow = new Flow("f-write");
        flow.addNode(writer);
        flow.addNode(collector);
        flow.connect("writer", "result", "collector", "in");

        engine.register(flow);
        engine.startFlow("f-write");

        writer.process(new Message(Map.of("val", 777)));

        waitForData(collector);

        assertEquals(777, simulator.getRegister(5), "시뮬레이터에 값이 정상적으로 기록되지 않았습니다.");
        assertFalse(collector.getCollected().isEmpty(), "Writer의 result 포트로 결과가 전달되지 않았습니다.");
    }

    @Test
    @DisplayName("3. Reader → Writer 파이프라인 테스트")
    void testModbusReadToWriter() throws Exception {
        simulator.setRegister(0, 999);

        ModbusReaderNode reader = new ModbusReaderNode("reader", Map.of(
                "port", PORT, "startAddress", 0, "registerMapping", Map.of("temp", 0)));
        ModbusWriterNode writer = new ModbusWriterNode("writer", Map.of(
                "port", PORT, "registerAddress", 10, "valueField", "temp"));
        CollectorNode collector = new CollectorNode("collector");

        Flow flow = new Flow("f-pipeline");
        flow.addNode(reader);
        flow.addNode(writer);
        flow.addNode(collector);

        flow.connect("reader", "out", "writer", "in");
        flow.connect("writer", "result", "collector", "in");

        engine.register(flow);
        engine.startFlow("f-pipeline");

        reader.process(new Message(Map.of("trigger", true)));

        waitForData(collector);

        assertEquals(999, simulator.getRegister(10), "파이프라인을 통한 레지스터 복사에 실패했습니다.");
    }

    @Test
    @DisplayName("4. 시뮬레이터 중지 시 에러 포트 동작")
    void testModbusError() throws Exception {
        simulator.stop();

        ModbusReaderNode reader = new ModbusReaderNode("reader-err", Map.of("port", PORT));
        CollectorNode collector = new CollectorNode("collector-err");

        Flow flow = new Flow("f-err");
        flow.addNode(reader);
        flow.addNode(collector);
        flow.connect("reader-err", "error", "collector-err", "in");

        engine.register(flow);
        engine.startFlow("f-err");

        reader.process(new Message(Map.of("trigger", true)));

        int timeout = 0;
        while (collector.getCollected().isEmpty() && timeout < 20) {
            Thread.sleep(100);
            timeout++;
        }

        assertFalse(collector.getCollected().isEmpty(), "에러 메시지가 수집되지 않았습니다.");
        assertEquals("error", collector.getCollected().get(0).getPayload().get("status"));
    }

    private void waitForData(CollectorNode collector) throws InterruptedException {
        int timeout = 0;
        while (collector.getCollected().isEmpty() && timeout < 30) {
            Thread.sleep(100);
            timeout++;
        }
    }
}
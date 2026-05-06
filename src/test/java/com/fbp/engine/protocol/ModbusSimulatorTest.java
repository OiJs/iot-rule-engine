package com.fbp.engine.protocol;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;

@DisplayName("ModbusTcpSimulator 통합 테스트")
class ModbusTcpSimulatorTest {
    private ModbusTcpSimulator simulator;
    private final int PORT = 5022;

    @BeforeEach
    void setUp() throws InterruptedException {
        simulator = new ModbusTcpSimulator(PORT, 10);
        simulator.start();
        Thread.sleep(100); // 서버 바인딩 대기
    }

    @AfterEach
    void tearDown() throws IOException {
        if (simulator != null) {
            simulator.stop();
        }
    }

    @Test
    @DisplayName("1. 시작/종료 검증")
    void test1_StartStop() throws IOException {
        // setUp에서 이미 start됨. stop 후 다시 연결 시도하여 확인
        simulator.stop();
        ModbusTcpClient client = new ModbusTcpClient("localhost", PORT);
        assertThrows(IOException.class, client::connect, "종료 후에는 연결이 거부되어야 함");
    }

    @Test
    @DisplayName("2. 레지스터 초기값 검증")
    void test2_RegisterInitialValues() {
        simulator.setRegister(5, 999);
        assertEquals(999, simulator.getRegister(5), "set한 값이 get으로 정확히 읽혀야 함");
    }

    @Test
    @DisplayName("3. FC 03 응답 검증")
    void test3_FC03Response() throws Exception {
        simulator.setRegister(0, 123);
        ModbusTcpClient client = new ModbusTcpClient("localhost", PORT);
        client.connect();

        int[] data = client.readHoldingRegister(1, 0, 1);
        assertEquals(123, data[0], "클라이언트로 읽은 값이 시뮬레이터 설정값과 일치해야 함");
        client.disconnect();
    }

    @Test
    @DisplayName("4. FC 06 응답 및 에코백 검증")
    void test4_FC06Response() throws Exception {
        ModbusTcpClient client = new ModbusTcpClient("localhost", PORT);
        client.connect();

        client.writeSingleRegister(1, 2, 777); // 쓰기 요청
        assertEquals(777, simulator.getRegister(2), "쓰기 후 시뮬레이터 내부 값이 변경되어야 함");
        // writeSingleRegister 내부에서 에코백이 다르면 IOException을 던지므로, 통과 자체가 에코백 검증임
        client.disconnect();
    }

    @Test
    @DisplayName("5. 잘못된 주소 에러 응답 검증")
    void test5_InvalidAddressError() throws Exception {
        ModbusTcpClient client = new ModbusTcpClient("localhost", PORT);
        client.connect();

        // 범위를 벗어난 15번 주소 요청 (사이즈 10으로 생성함)
        ModbusException ex = assertThrows(ModbusException.class, () -> {
            client.readHoldingRegister(1, 15, 1);
        });
        assertEquals(0x02, ex.getExceptionCode(), "범위 초과 시 0x02 에러 응답 확인");
        client.disconnect();
    }

    @Test
    @DisplayName("6. 다중 클라이언트 접속 검증")
    void test6_MultiClient() throws Exception {
        ModbusTcpClient client1 = new ModbusTcpClient("localhost", PORT);
        ModbusTcpClient client2 = new ModbusTcpClient("localhost", PORT);

        client1.connect();
        client2.connect();

        assertTrue(client1.isConnected());
        assertTrue(client2.isConnected());

        client1.writeSingleRegister(1, 0, 111);
        client2.writeSingleRegister(1, 1, 222);

        assertEquals(111, simulator.getRegister(0));
        assertEquals(222, simulator.getRegister(1));

        client1.disconnect();
        client2.disconnect();
    }
}
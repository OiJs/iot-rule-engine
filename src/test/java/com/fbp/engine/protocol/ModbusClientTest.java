package com.fbp.engine.protocol;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;

@DisplayName("ModbusTcpClient 종합 테스트 (단위+통합)")
class ModbusClientTest {
    private ModbusTcpClient client;
    private ModbusTcpSimulator simulator;
    private final int PORT = 5021;

    @BeforeEach
    void setUp() {
        client = new ModbusTcpClient("localhost", PORT);
    }

    @Test
    @DisplayName("1 & 3. FC 03 요청 프레임 및 MBAP 헤더 구조 검증")
    void test1_3_FC03FrameAssembly() {
        int tid = 1, uid = 1, addr = 10, qty = 5;
        byte[] frame = client.buildReadRequest(tid, uid, addr, qty);

        assertEquals((byte)0x00, frame[0]); assertEquals((byte)0x01, frame[1]);
        assertEquals((byte)0x00, frame[2]); assertEquals((byte)0x00, frame[3]);
        assertEquals((byte)0x00, frame[4]); assertEquals((byte)0x06, frame[5]);
        assertEquals((byte)uid, frame[6]);

        assertEquals((byte)0x03, frame[7]);
        assertEquals((byte)0x00, frame[8]); assertEquals((byte)0x0A, frame[9]);
        assertEquals((byte)0x00, frame[10]); assertEquals((byte)0x05, frame[11]);
    }

    @Test
    @DisplayName("2. FC 06 요청 프레임 조립 검증")
    void test2_FC06FrameAssembly() {
        int tid = 2, uid = 1, addr = 5, val = 100;
        byte[] frame = client.buildWriteRequest(tid, uid, addr, val);

        assertEquals((byte)0x06, frame[7]);
        assertEquals((byte)0x00, frame[8]); assertEquals((byte)0x05, frame[9]);
        assertEquals((byte)0x00, frame[10]); assertEquals((byte)0x64, frame[11]);
    }

    @Test
    @DisplayName("4. Transaction ID 증가 검증")
    void test4_TransactionIdIncrement() {
        int first = client.getNextTransactionId();
        int second = client.getNextTransactionId();
        assertEquals(first + 1, second, "TID는 연속 호출 시 1씩 증가해야 함");
    }

    @Test
    @DisplayName("5. 초기 상태 검증")
    void test5_InitialState() {
        assertFalse(client.isConnected(), "생성 직후 연결 상태는 false여야 함");
    }

    @Nested
    @DisplayName("시뮬레이터 통합 테스트")
    class IntegrationTests {

        @BeforeEach
        void startSimulator() {
            simulator = new ModbusTcpSimulator(PORT, 10);
            simulator.start();
        }

        @AfterEach
        void stopSimulator() throws IOException {
            client.disconnect();
            simulator.stop();
        }

        @Test
        @DisplayName("6. 연결 및 해제 검증")
        void test6_ConnectDisconnect() throws IOException, InterruptedException {
            client.connect();
            Thread.sleep(100);
            assertTrue(client.isConnected());
            client.disconnect();
            Thread.sleep(100);
            assertFalse(client.isConnected());
        }

        @Test
        @DisplayName("7 & 8. Holding Register 읽기 (단일 및 다수)")
        void test7_8_ReadRegisters() throws Exception {
            client.connect();
            simulator.setRegister(0, 500);
            simulator.setRegister(1, 600);
            simulator.setRegister(2, 700);
            simulator.setRegister(3, 800);
            simulator.setRegister(4, 900);

            int[] res = client.readHoldingRegister(1, 0, 5);
            assertEquals(5, res.length);
            assertEquals(500, res[0]);
            assertEquals(900, res[4]);
        }

        @Test
        @DisplayName("9 & 10. Single Register 쓰기 및 읽기 반영 검증")
        void test9_10_WriteAndRead() throws Exception {
            client.connect();
            client.writeSingleRegister(1, 2, 1234);

            assertEquals(1234, simulator.getRegister(2));
            int[] res = client.readHoldingRegister(1, 2, 1);
            assertEquals(1234, res[0]);
        }

        @Test
        @DisplayName("11. 에러 응답 처리 (존재하지 않는 주소)")
        void test11_ErrorResponse() throws IOException {
            client.connect();
            ModbusException ex = assertThrows(ModbusException.class, () -> {
                client.readHoldingRegister(1, 20, 1);
            });
            assertEquals(0x02, ex.getExceptionCode(), "ILLEGAL_DATA_ADDRESS(0x02) 확인");
        }

        @Test
        @DisplayName("12. 소켓 타임아웃/연결 실패 검증")
        void test12_SocketTimeout() throws IOException {
            simulator.stop(); // 시뮬레이터 중지
            assertThrows(IOException.class, () -> {
                client.connect();
                client.readHoldingRegister(1, 0, 1);
            });
        }
    }
}
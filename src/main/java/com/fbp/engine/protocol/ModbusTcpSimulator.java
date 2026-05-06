package com.fbp.engine.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
//TODO Stage2 3-4
/**
 * Modbus TCP Slave(Server) 역할을 수행하는 시뮬레이터 클래스입니다.
 * 클라이언트의 요청을 받아 Holding Register를 읽거나 쓰는 기능을 제공하며,
 * 다중 클라이언트 접속을 지원하기 위해 멀티 스레딩 방식으로 동작합니다.
 */
public class ModbusTcpSimulator {
    private final int port;
    private final int[] registers;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    /**
     * 시뮬레이터 생성자
     * * @param port 수신 대기할 포트 번호 (표준 Modbus 포트는 502)
     * @param registerCount 시뮬레이터가 보유할 전체 Holding Register 개수
     */
    public ModbusTcpSimulator(int port, int registerCount) {
        this.port = port;
        this.registers = new int[registerCount];
    }

    /**
     * 시뮬레이터 서버를 시작합니다.
     * 별도의 스레드에서 클라이언트의 연결 요청(Accept)을 무한 루프로 대기합니다.
     */
    public void start() {
        running = true;

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("ModbusSimulator Start (Port: " + port + ")");

                while(running) {
                    Socket clientSocket = serverSocket.accept();
                    // 각 클라이언트를 독립적인 스레드에서 처리하여 동시성 확보
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                if(running) e.printStackTrace();
            }
        }).start();
    }

    /**
     * 개별 클라이언트 소켓의 통신을 처리합니다.
     * MBAP 헤더를 파싱하고 Function Code(03, 06)에 따라 로직을 수행합니다.
     * * @param socket 연결된 클라이언트 소켓
     */
    private void handleClient(Socket socket) {
        try(DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            while(running && !socket.isClosed()) {
                // 1. MBAP 헤더 수신 (7바이트)
                int tid = in.readUnsignedShort(); // Transaction ID
                int pid = in.readUnsignedShort(); // Protocol ID
                int length = in.readUnsignedShort(); // Remaining Length
                int uid = in.readUnsignedByte();  // Unit ID

                // 2. Function Code 및 PDU 수신
                int fc = in.readUnsignedByte();

                try {
                    if(fc == 0x03) { // Read Holding Registers
                        int addr = in.readUnsignedShort();
                        int qty = in.readUnsignedShort();

                        // 레지스터 범위 검증
                        if(addr + qty > registers.length) {
                            throw new ModbusException(fc, 0x02); // ILLEGAL_DATA_ADDRESS
                        }

                        // 응답 전송: Header(7) + FC(1) + ByteCount(1) + Data(qty*2)
                        sendMbapHeader(out, tid, 3 + (qty * 2), uid);
                        out.writeByte(fc);
                        out.writeByte(qty * 2);
                        for (int i = 0; i < qty; i++) {
                            out.writeShort(registers[addr + i]);
                        }
                    } else if(fc == 0x06) { // Write Single Register
                        int addr = in.readUnsignedShort();
                        int val = in.readUnsignedShort();

                        if (addr >= registers.length) {
                            throw new ModbusException(fc, 0x02);
                        }
                        registers[addr] = val;

                        // 에코백(Echo-back) 응답 전송
                        sendMbapHeader(out, tid, 6, uid);
                        out.writeByte(fc);
                        out.writeShort(addr);
                        out.writeShort(val);
                    } else {
                        // 지원하지 않는 Function Code 요청 시
                        throw new ModbusException(fc, 0x01); // ILLEGAL_FUNCTION
                    }
                    out.flush();
                } catch (ModbusException me) {
                    // 에러 발생 시 예외 프레임 전송
                    sendErrorResponse(out, tid, uid, fc, me.getExceptionCode());
                }
            }
        } catch (IOException e) {
            System.out.println("클라이언트 연결 종료");
        }
    }

    /**
     * MBAP 헤더(7바이트)를 생성하여 출력 스트림으로 전송합니다.
     */
    private void sendMbapHeader(DataOutputStream out, int tid, int len, int uid) throws IOException {
        out.writeShort(tid);
        out.writeShort(0);
        out.writeShort(len);
        out.writeByte(uid);
    }

    /**
     * Modbus 예외 응답 프레임을 전송합니다.
     * Function Code의 MSB를 1로 설정(FC | 0x80)하여 에러임을 나타냅니다.
     */
    private void sendErrorResponse(DataOutputStream out, int tid, int uid, int fc, int excCode) throws IOException {
        sendMbapHeader(out, tid, 3, uid);
        out.writeByte(fc | 0x80);
        out.writeByte(excCode);
        out.flush();
    }

    /**
     * 외부(MQTT 엔진 등)에서 시뮬레이터의 레지스터 값을 직접 설정합니다.
     */
    public void setRegister(int address, int value) {
        registers[address] = value;
    }

    /**
     * 특정 레지스터 번지에 저장된 값을 조회합니다.
     */
    public int getRegister(int address) {
        return registers[address];
    }

    /**
     * 서버 실행을 중단하고 사용 중인 자원을 해제합니다.
     */
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null) {
            serverSocket.close();
        }
    }
}
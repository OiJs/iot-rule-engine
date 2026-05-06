package com.fbp.engine.flowtest;

import com.fbp.engine.protocol.ModbusTcpClient;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.util.Arrays;

//TODO Stage2 3-5
public class ModbusIntegrationTest {
    public static void main(String[] args) {
        int port = 5020;
        int registerCount = 10;

        ModbusTcpSimulator simulator = new ModbusTcpSimulator(port, registerCount);

        simulator.setRegister(0, 250);
        simulator.setRegister(1, 600);
        simulator.setRegister(2, 1);

        simulator.start();
        System.out.println("[서버] 시뮬레이터가 시작되었습니다.");

        ModbusTcpClient client = new ModbusTcpClient("localhost", port);

        try {
            System.out.println("클라이언트 시뮬레이터 연결");
            client.connect();

            if(client.isConnected()) {
                System.out.println("연결 성공");

                System.out.println("초기 레지스터 값 읽기");
                int[] initialValues = client.readHoldingRegister(1, 0, 3);
                System.out.println("값: " + Arrays.toString(initialValues));

                System.out.println("주소 2번 값 100 쓰기");
                client.writeSingleRegister(1, 2, 100);

                System.out.println("변경 된 값 읽기");
                int[] updatedValues = client.readHoldingRegister(1, 0, 3);
                System.out.println("값: " + Arrays.toString(updatedValues));

                if (updatedValues[2] == 100) {
                    System.out.println("\n[결과] 검증 성공: 주소 2번의 값이 100으로 정상 변경되었습니다.");
                } else {
                    System.out.println("\n[결과] 검증 실패: 값이 변경되지 않았습니다.");
                }
            }

            client.disconnect();
            simulator.stop();
        }catch (Exception e) {
            System.err.println("[에러 발생] 테스트 중 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }
}

package com.fbp.engine.protocol;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModbusException 단위 테스트")
class ModbusExceptionTest {

    @Test
    @DisplayName("1. getMessage 포맷 검증")
    void test1_GetMessageFormat() {
        int functionCode = 0x03;
        int exceptionCode = 0x02;
        ModbusException ex = new ModbusException(functionCode, exceptionCode);
        
        String message = ex.getMessage();
        assertTrue(message.contains(String.valueOf(functionCode)), "메시지에 functionCode가 포함되어야 함");
        assertTrue(message.contains(String.valueOf(exceptionCode)), "메시지에 exceptionCode가 포함되어야 함");
    }

    @Test
    @DisplayName("2. getExceptionCode 반환 검증")
    void test2_GetExceptionCode() {
        int expectedCode = 0x01;
        ModbusException ex = new ModbusException(0x06, expectedCode);
        
        assertEquals(expectedCode, ex.getExceptionCode(), "지정한 exceptionCode가 정확히 반환되어야 함");
    }

    @Test
    @DisplayName("3. 상수 값 검증")
    void test3_ConstantValues() {
        assertEquals(0x01, ModbusException.ILLEGAL_FUNCTION);
        assertEquals(0x02, ModbusException.ILLEGAL_DATA_ADDRESS);
        assertEquals(0x03, ModbusException.ILLEGAL_DATA_VALUE);
    }
}
package com.fbp.engine.message;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

class MessageTest {
    private Map<String, Object> sourceMap;
    private Message message;

    @BeforeEach
    void setUp() {
        sourceMap = new HashMap<>();
        sourceMap.put("temperature", 25.5);
        message = new Message(sourceMap);
    }

    @Test
    void test1_IdAutoAssignment() {
        assertNotNull(message.getId());
        assertFalse(message.getId().isBlank());
    }

    @Test
    void test2_TimestampAutoRecording() {
        assertNotNull(message.getTimestamp());
    }

    @Test
    void test3_PayloadLookup() {
        assertEquals(25.5, message.get("temperature"));
    }

    @Test
    void test4_GenericGetCasting() {
        Double temp = message.get("temperature");
        assertEquals(25.5, temp);
    }

    @Test
    void test5_GetNonExistentKey() {
        assertNull(message.get("non-existent"));
    }

    @Test
    void test6_PayloadImmutabilityExternalModification() {
        assertThrows(UnsupportedOperationException.class, () -> {
            message.getPayload().put("newKey", "value");
        });
    }

    @Test
    void test7_PayloadImmutabilitySourceMapModification() {
        sourceMap.put("temperature", 100.0);
        assertEquals(25.5, message.get("temperature"));
    }

    @Test
    void test8_WithEntryReturnsNewObject() {
        Message newMessage = message.withEntry("humidity", 60);
        assertNotSame(message, newMessage);
    }

    @Test
    void test9_WithEntryOriginalUnchanged() {
        message.withEntry("humidity", 60);
        assertFalse(message.hasKey("humidity"));
    }

    @Test
    void test10_WithEntryNewValueExists() {
        Message newMessage = message.withEntry("humidity", 60);
        assertEquals(60, (Integer) newMessage.get("humidity"));
    }

    @Test
    void test11_HasKeyExisting() {
        assertTrue(message.hasKey("temperature"));
    }

    @Test
    void test12_HasKeyNonExisting() {
        assertFalse(message.hasKey("humidity"));
    }

    @Test
    void test13_WithoutKeyRemoval() {
        Message newMessage = message.withoutKey("temperature");
        assertFalse(newMessage.hasKey("temperature"));
    }

    @Test
    void test14_WithoutKeyOriginalUnchanged() {
        message.withoutKey("temperature");
        assertTrue(message.hasKey("temperature"));
    }

    @Test
    void test15_ToStringFormat() {
        String str = message.toString();
        assertNotNull(str);
        assertTrue(str.contains("temperature=25.5"));
    }
}
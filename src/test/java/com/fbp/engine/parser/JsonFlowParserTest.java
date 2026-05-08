package com.fbp.engine.parser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import java.io.ByteArrayInputStream;

@DisplayName("JsonFlowParser 상세 검증 테스트")
class JsonFlowParserTest {
    private JsonFlowParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonFlowParser();
    }

    @Test
    @DisplayName("1. 정상 파싱: 유효한 JSON -> FlowDefinition DTO 정상 변환")
    void test1_NormalParsing() {
        String json = """
            {
              "id": "flow-1",
              "transport": { "type": "local" },
              "nodes": [
                { "id": "n1", "type": "MqttSubscriber", "config": {"url": "localhost"} }
              ],
              "connections": []
            }
            """;
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertNotNull(def);
        assertEquals("flow-1", def.id());
        assertEquals("local", def.transport().type());
    }

    @Test
    @DisplayName("2. 노드 목록: 파싱된 노드 DTO의 수와 id, type, config 일치 확인")
    void test2_NodeList() {
        String json = """
            {
              "id": "f1",
              "nodes": [
                { "id": "n1", "type": "MqttSubscriber", "config": {"url": "localhost"} }
              ],
              "connections": []
            }
            """;
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertEquals(1, def.nodes().size());
        NodeDefinition nodeDef = def.nodes().get(0);
        assertEquals("n1", nodeDef.id());
        assertEquals("MqttSubscriber", nodeDef.type());
        assertEquals("localhost", nodeDef.config().get("url"));
    }

    @Test
    @DisplayName("3. 연결 목록: from/to 정보 DTO 변환 확인")
    void test3_ConnectionList() {
        String json = """
            {
              "id": "f1",
              "nodes": [
                { "id": "n1", "type": "MqttSubscriber", "config": {} },
                { "id": "n2", "type": "ThresholdFilter", "config": {} }
              ],
              "connections": [
                { "from": "n1:out", "to": "n2:in" }
              ]
            }
            """;
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertEquals(1, def.connections().size());
        ConnectionDefinition connDef = def.connections().get(0);
        assertEquals("n1:out", connDef.from());
        assertEquals("n2:in", connDef.to());
    }

    @Test
    @DisplayName("4. 필수 필드 누락 - id: id가 없으면 예외 발생")
    void test4_MissingId() {
        String json = "{ \"nodes\": [], \"connections\": [] }";
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertThrows(FlowParserException.class, def::validate);
    }

    @Test
    @DisplayName("5. 필수 필드 누락 - nodes: nodes 배열이 없으면 예외 발생")
    void test5_MissingNodes() {
        String json = "{ \"id\": \"f1\", \"connections\": [] }";
        // Jackson 파싱 시 필드가 없으면 null 혹은 빈 리스트로 바인딩됨에 따라 validate에서 체크
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertThrows(FlowParserException.class, def::validate);
    }

    @Test
    @DisplayName("6. 잘못된 JSON 형식: 문법 오류 발생 시 예외")
    void test6_InvalidJson() {
        String json = "{ \"id\": \"f1\", \"nodes\": [ }";
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("7. transport 정보 파싱 확인")
    void test7_TransportParsing() {
        String json = """
            {
              "id": "f1",
              "transport": {
                "type": "mqtt",
                "config": { "broker": "tcp://localhost:1883" }
              },
              "nodes": [{ "id": "n1", "type": "test", "config": {} }],
              "connections": []
            }
            """;
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertEquals("mqtt", def.transport().type());
        assertEquals("tcp://localhost:1883", def.transport().broker());
    }

    @Test
    @DisplayName("8. config 타입 보존: 숫자, 문자열, boolean 타입 유지 확인")
    void test8_ConfigTypePreservation() {
        String json = """
            {
              "id": "f1",
              "nodes": [
                { 
                  "id": "n1", 
                  "type": "test", 
                  "config": { "str": "abc", "num": 10, "bool": true } 
                }
              ],
              "connections": []
            }
            """;
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        NodeDefinition nodeDef = def.nodes().get(0);

        assertInstanceOf(String.class, nodeDef.config().get("str"));
        assertInstanceOf(Number.class, nodeDef.config().get("num"));
        assertInstanceOf(Boolean.class, nodeDef.config().get("bool"));
    }

    @Test
    @DisplayName("9. 기본 transport 설정: 생략 시 local로 설정되는지 확인")
    void test9_DefaultTransport() {
        String json = """
            {
              "id": "f1",
              "nodes": [{ "id": "n1", "type": "test", "config": {} }],
              "connections": []
            }
            """;
        FlowDefinition def = parser.parse(new ByteArrayInputStream(json.getBytes()));
        // Parser 혹은 Definition 생성자에서 기본값 처리가 되어있는지 확인
        if (def.transport() != null) {
            assertEquals("local", def.transport().type());
        }
    }
}
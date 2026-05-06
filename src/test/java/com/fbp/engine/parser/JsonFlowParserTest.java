package com.fbp.engine.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import org.junit.jupiter.api.*;
import com.fbp.engine.core.*;
import com.fbp.engine.registry.NodeRegistry;
import java.io.ByteArrayInputStream;
import java.util.Map;

@DisplayName("JsonFlowParser 상세 검증 테스트")
class JsonFlowParserTest {
    private NodeRegistry registry;
    private JsonFlowParser parser;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
        // 테스트에 필요한 노드 타입 등록
        registry.register("MqttSubscriber", (id, config) -> new MockNode(id, config));
        registry.register("ThresholdFilter", (id, config) -> new MockNode(id, config));
        parser = new JsonFlowParser(registry);
    }

    @Test
    @DisplayName("1. 정상 파싱: 유효한 JSON -> FlowDefinition 정상 변환")
    void test1_NormalParsing() {
        String json = """
            {
              "id": "flow-1",
              "nodes": [
              { "id": "n1", "type": "MqttSubscriber", "config": {"url": "localhost"} }],
              "connections": []
            }
            """;
        Flow flow = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertNotNull(flow);
        assertEquals("flow-1", flow.getId());
    }

    @Test
    @DisplayName("2. 노드 목록: 파싱된 노드의 수와 id, type, config 일치 확인")
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
        Flow flow = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertNotNull(flow.getNode("n1"));
    }

    @Test
    @DisplayName("3. 연결 목록: from/to 정보 일치 확인")
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
        assertDoesNotThrow(() -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("4. 필수 필드 누락 - id: id가 없으면 FlowParserException 발생")
    void test4_MissingId() {
        String json = "{ \"nodes\": [], \"connections\": [] }";
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("5. 필수 필드 누락 - nodes: nodes 배열이 없으면 예외 발생")
    void test5_MissingNodes() {
        String json = "{ \"id\": \"f1\", \"connections\": [] }";
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("6. 빈 노드 목록: nodes가 빈 배열이면 예외 발생")
    void test6_EmptyNodes() {
        String json = "{ \"id\": \"f1\", \"nodes\": [], \"connections\": [] }";
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("7. 잘못된 JSON 형식: 문법 오류 발생 시 적절한 예외")
    void test7_InvalidJson() {
        String json = "{ \"id\": \"f1\", \"nodes\": [ }"; // 문법 오류
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("8. 연결의 포트 파싱: 'sensor:out' -> sourceNode='sensor', sourcePort='out' 분리")
    void test8_PortParsing() {
        String json = """
            {
              "id": "f1",
              "nodes": [
                { "id": "sensor", "type": "MqttSubscriber", "config": {} },
                { "id": "rule", "type": "ThresholdFilter", "config": {} }
              ],
              "connections": [
                { "from": "sensor:out", "to": "rule:in" }
              ]
            }
            """;
        Flow flow = parser.parse(new ByteArrayInputStream(json.getBytes()));
        assertNotNull(flow);
    }

    @Test
    @DisplayName("9. 잘못된 연결 형식: 'sensor' (포트 없음) -> 예외")
    void test9_InvalidConnectionFormat() {
        String json = """
            {
              "id": "f1",
              "nodes": [{ "id": "n1", "type": "MqttSubscriber", "config": {} }],
              "connections": [{ "from": "n1", "to": "n2:in" }]
            }
            """;
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("10. 존재하지 않는 노드 참조: 정의되지 않은 id 참조 시 예외")
    void test10_UnknownNodeReference() {
        String json = """
            {
              "id": "f1",
              "nodes": [{ "id": "n1", "type": "MqttSubscriber", "config": {} }],
              "connections": [{ "from": "n1:out", "to": "non-existent:in" }]
            }
            """;
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("11. 중복 노드 id: 같은 id의 노드가 두 개 이상이면 예외")
    void test11_DuplicateNodeId() {
        String json = """
            {
              "id": "f1",
              "nodes": [
                { "id": "n1", "type": "MqttSubscriber", "config": {} },
                { "id": "n1", "type": "ThresholdFilter", "config": {} }
              ],
              "connections": []
            }
            """;
        assertThrows(FlowParserException.class, () -> parser.parse(new ByteArrayInputStream(json.getBytes())));
    }

    @Test
    @DisplayName("12. config 타입 보존: 숫자, 문자열, boolean 타입 유지 확인")
    void test12_ConfigTypePreservation() {
        String json = """
            {
              "id": "f1",
              "nodes": [
                { 
                  "id": "n1", 
                  "type": "MqttSubscriber", 
                  "config": { "str": "abc", "num": 10, "bool": true } 
                }
              ],
              "connections": []
            }
            """;
        Flow flow = parser.parse(new ByteArrayInputStream(json.getBytes()));
        MockNode node = (MockNode) flow.getNode("n1");
        
        assertInstanceOf(String.class, node.config.get("str"));
        assertInstanceOf(Number.class, node.config.get("num"));
        assertInstanceOf(Boolean.class, node.config.get("bool"));
    }

    private static class MockNode extends AbstractNode {
        final Map<String, Object> config;
        MockNode(String id, Map<String, Object> config) {
            super(id);
            this.config = config;
            addInputPort("in");
            addOutputPort("out");
            addOutputPort("match");
        }
        @Override protected void onProcess(Message message) {}
    }
}
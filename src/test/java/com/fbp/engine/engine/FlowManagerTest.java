package com.fbp.engine.engine;

import com.fbp.engine.core.*;
import com.fbp.engine.core.Flow.FlowState;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.parser.FlowParser;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FlowManagerTest {
    private FlowManager manager;
    private FlowEngine engine;
    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        registry = new NodeRegistry();
        registry.register("MockType", (id, config) -> new MockNode(id, config));
        
        manager = new FlowManager(engine);
        manager.addParser(new JsonFlowParser(registry));
    }

    @Test
    @DisplayName("1. deploy: 플로우 배포 후 엔진 등록 및 RUNNING 상태 확인")
    void test1_Deploy() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        
        assertEquals(FlowState.RUNNING, manager.getStatus("f1"));
    }

    @Test
    @DisplayName("2. list: 배포된 플로우 목록의 정확한 개수와 포함 여부 조회")
    void test2_List() {
        String json1 = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        String json2 = "{\"id\":\"f2\", \"nodes\":[{\"id\":\"n2\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        
        manager.deploy("json", new ByteArrayInputStream(json1.getBytes()));
        manager.deploy("json", new ByteArrayInputStream(json2.getBytes()));
        
        List<Flow> flows = manager.list();
        assertEquals(2, flows.size());
    }

    @Test
    @DisplayName("3. getStatus: 특정 플로우의 현재 실행 상태 조회")
    void test3_GetStatus() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        
        assertNotNull(manager.getStatus("f1"));
    }

    @Test
    @DisplayName("4. stop: RUNNING 상태의 플로우를 STOPPED 상태로 변경")
    void test4_Stop() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        
        manager.stop("f1");
        assertEquals(FlowState.STOPPED, manager.getStatus("f1"));
    }

    @Test
    @DisplayName("5. restart: STOPPED 상태의 플로우를 다시 RUNNING 상태로 변경")
    void test5_Restart() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        manager.stop("f1");
        
        manager.restart("f1");
        assertEquals(FlowState.RUNNING, manager.getStatus("f1"));
    }

    @Test
    @DisplayName("6. remove: 관리 목록에서 플로우 제거 확인")
    void test6_Remove() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        
        manager.remove("f1");
        assertThrows(FlowNotFoundException.class, () -> manager.getStatus("f1"));
    }

    @Test
    @DisplayName("7. 실행 중 삭제: RUNNING 상태 삭제 시 자동 정지 후 제거")
    void test7_RemoveRunningFlow() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        
        assertDoesNotThrow(() -> manager.remove("f1"));
        assertEquals(0, manager.list().size());
    }

    @Test
    @DisplayName("8. 존재하지 않는 id 조작: 없는 ID 시 FlowNotFoundException 발생")
    void test8_InvalidIdOperation() {
        assertThrows(FlowNotFoundException.class, () -> manager.stop("non-existent"));
        assertThrows(FlowNotFoundException.class, () -> manager.restart("non-existent"));
        assertThrows(FlowNotFoundException.class, () -> manager.remove("non-existent"));
    }

    @Test
    @DisplayName("9. 중복 id 배포: 동일 ID 재배포 시 기존 플로우 교체")
    void test9_DuplicateIdDeployment() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"MockType\",\"config\":{}}], \"connections\":[]}";
        manager.deploy("json", new ByteArrayInputStream(json.getBytes()));

        assertDoesNotThrow(() -> manager.deploy("json", new ByteArrayInputStream(json.getBytes())));
        assertEquals(1, manager.list().size());
    }

    @Test
    @DisplayName("10. 미등록 노드 타입: Registry에 없는 타입 포함 시 배포 실패")
    void test10_UnknownNodeType() {
        String json = "{\"id\":\"f1\", \"nodes\":[{\"id\":\"n1\",\"type\":\"UnknownType\",\"config\":{}}], \"connections\":[]}";
        
        assertThrows(RuntimeException.class, () -> {
            manager.deploy("json", new ByteArrayInputStream(json.getBytes()));
        });
    }

    private static class MockNode extends AbstractNode {
        MockNode(String id, Map<String, Object> config) { super(id); }
        @Override protected void onProcess(Message message) {}
    }
}
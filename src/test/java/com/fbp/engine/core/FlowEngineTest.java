package com.fbp.engine.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.core.Flow.FlowState;
import com.fbp.engine.core.FlowEngine.State;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowEngineTest {
    private FlowEngine engine;
    private Flow validFlow;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();

        validFlow = new Flow("flow1")
                .addNode(new TimerNode("t1", 500))
                .addNode(new PrintNode("p1"))
                .connect("t1", "out", "p1", "in");
    }

    @Test
    @DisplayName("엔진 초기 상태 확인")
    void test1_InitialState() {
        assertEquals(State.INITIALIZED, engine.getState());
    }

    @Test
    @DisplayName("플로우 등록 확인")
    void test2_RegisterFlow() {
        engine.register(validFlow);
        engine.startFlow("flow1");

        assertTrue(engine.getFlows().containsKey("flow1"));
        assertEquals(validFlow, engine.getFlows().get("flow1"));
    }

    @Test
    @DisplayName("startFlow 정상 동작 확인")
    void test3_StartFlowNormal() {
        engine.register(validFlow);
        engine.startFlow("flow1");

        assertEquals(State.RUNNING, engine.getState());
        assertEquals(FlowState.RUNNING, validFlow.getFlowState());
    }

    @Test
    @DisplayName("startFlow - 유효성 검사 실패")
    void test5_StartFlowValidateFail() {
        Flow emptyFlow = new Flow("empty");
        engine.register(emptyFlow);

        assertThrows(IllegalStateException.class, () -> {
            engine.startFlow("empty");
        });
    }

    @Test
    @DisplayName("stopFlow 정상 동작 확인")
    void test6_StopFlowNormal() {
        engine.register(validFlow);
        engine.startFlow("flow1");
        engine.stopFlow("flow1");

        assertEquals(FlowState.STOPPED, validFlow.getFlowState());
    }

    @Test
    @DisplayName("다중 플로우 동작 확인")
    void test8_MultipleFlowIndependence() {
        Flow flow2 = new Flow("flow2")
                .addNode(new TimerNode("t2", 1000))
                .addNode(new PrintNode("p2"))
                .connect("t2", "out", "p2", "in");

        engine.register(validFlow);
        engine.register(flow2);

        engine.startFlow(validFlow.getId());
        engine.startFlow(flow2.getId());

        engine.stopFlow(validFlow.getId());

        assertEquals(Flow.FlowState.STOPPED, validFlow.getFlowState());
        assertEquals(Flow.FlowState.RUNNING, flow2.getFlowState());
    }

    @Test
    @DisplayName("listFlows 출력 및 조회 확인")
    void test9_ListFlows() {
        engine.register(validFlow);

        List<Flow> flowList = engine.listFlows();
        assertEquals(1, flowList.size());
        assertEquals("flow1", flowList.getFirst().getId());
    }
}

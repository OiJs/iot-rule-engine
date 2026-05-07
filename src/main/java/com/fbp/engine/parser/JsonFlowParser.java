package com.fbp.engine.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

public class JsonFlowParser implements FlowParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FlowDefinition parse(InputStream inputStream) {
        try {
            // 1. JSON을 FlowDefinition record(설계도)로 바로 변환
            FlowDefinition def = objectMapper.readValue(inputStream, FlowDefinition.class);

            // 2. 기초적인 데이터 검증 (필수 값 확인)
            if (def.id() == null || def.id().isBlank()) {
                throw new FlowParserException("Flow ID가 누락되었습니다.");
            }

            if (def.nodes() == null || def.nodes().isEmpty()) {
                throw new FlowParserException("노드 정의가 비어있습니다.");
            }

            // 3. 설계도만 반환 (조립은 FlowManager가 수행)
            return def;

        } catch (IOException e) {
            throw new FlowParserException("플로우 파일(JSON) 읽기 실패: " + e.getMessage());
        }
    }

    @Override
    public String getSupportedFormat() {
        return "json";
    }
}
package com.fbp.engine.parser;

import java.io.InputStream;

public interface FlowParser {
    FlowDefinition parse(InputStream inputStream);
    String getSupportedFormat();
}

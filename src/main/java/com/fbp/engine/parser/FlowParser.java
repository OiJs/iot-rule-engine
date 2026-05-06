package com.fbp.engine.parser;

import com.fbp.engine.core.Flow;
import java.io.InputStream;

public interface FlowParser {
    Flow parse(InputStream inputStream);
    String getSupportedFormat();
}

package com.fbp.engine.core.port;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;

public interface OutputPort {
    String getName();
    void send(Message message);
    void connect(Connection connection);
}

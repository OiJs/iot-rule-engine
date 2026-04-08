package com.fbp.engine.core;

import com.fbp.engine.message.Message;

public interface OutputPort {
    String getName();
    void send(Message message);
    void connect(Connection connection);
}

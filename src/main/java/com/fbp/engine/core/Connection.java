package com.fbp.engine.core;

import com.fbp.engine.message.Message;

public interface Connection {
    String getId();
    void deliver(Message message);
    Message poll() throws InterruptedException;
    void setTarget(InputPort target);
    int getQueueSize();
    default void close() {};
}

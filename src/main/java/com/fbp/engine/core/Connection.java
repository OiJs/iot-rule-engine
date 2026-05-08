package com.fbp.engine.core;

import com.fbp.engine.message.Message;

public interface Connection {
    String getId();
    void deliver(Message message);
    InputPort getTarget();
    void setTarget(InputPort target);
    default Message poll() throws InterruptedException{return null;}
    default int getQueueSize() {return 0;}
    default void close() {}
}

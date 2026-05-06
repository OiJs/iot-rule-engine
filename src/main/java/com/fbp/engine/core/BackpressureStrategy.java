package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.concurrent.BlockingQueue;

public interface BackpressureStrategy {
    /**
     * @return 메시지 전송 성공 여부 (드롭 시 false)
     */
    boolean handleOverflow(BlockingQueue<Message> queue, Message newMessage);
}
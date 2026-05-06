package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.concurrent.BlockingQueue;

public class DropOldestStrategy implements BackpressureStrategy {
    @Override
    public boolean handleOverflow(BlockingQueue<Message> queue, Message newMessage) {
        queue.poll();
        return queue.offer(newMessage);
    }
}
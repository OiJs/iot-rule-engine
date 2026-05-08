package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Getter;
import lombok.Setter;

public class LocalConnection implements Connection{
    @Getter
    private final String id;
    private final BlockingQueue<Message> queue;
    @Setter
    @Getter
    private InputPort target;

    public LocalConnection(String id) {
        this.id = id;
        this.queue = new LinkedBlockingQueue<>(1000);

    }

    public LocalConnection(String id, int capacity) {
        this.id = id;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void deliver(Message message) {
        queue.offer(message);

        if(this.target != null) {
            this.target.receive(message);
        }
    }

    @Override
    public Message poll() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
    @Override
    public int getQueueSize() {
        return queue.size();
    }
}

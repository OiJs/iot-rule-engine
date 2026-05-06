package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.List;

public class ErrorPort extends DefaultOutputPort {
    public ErrorPort(String name) {
        super(name);
    }

    public boolean hasConnection() {
        List<Connection> connections = getConnectionList();
        return connections != null && !connections.isEmpty();
    }

    public void sendError(Message message) {
        super.send(message);
    }
}
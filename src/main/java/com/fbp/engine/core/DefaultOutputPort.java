package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class DefaultOutputPort implements OutputPort{
    @Getter
    private final String name;
    @Getter
    private final List<Connection> connectionList;

    public DefaultOutputPort(String name) {
        this.name = name;
        this.connectionList = new ArrayList<>();
    }

    @Override
    public void send(Message message) {
        for(Connection connection : connectionList) {
            connection.deliver(message);
        }
    }

    @Override
    public void connect(Connection connection) {
        if (connection != null && !connectionList.contains(connection)) {
            connectionList.add(connection);
        }
    }
}

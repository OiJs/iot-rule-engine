package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultInputPort implements InputPort{
    @Getter
    private final String name;
    private final Node owner;

    @Override
    public void receive(Message message) {
        Message messageWithPort = message.withEntry("inputPort", this.name);
        if(Objects.nonNull(message)) {
            owner.process(messageWithPort);
        }
    }
}

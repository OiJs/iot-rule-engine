package com.fbp.engine.core.port;

import com.fbp.engine.core.Node;
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
        if(Objects.nonNull(message)) {
            owner.process(message);
        }
    }
}

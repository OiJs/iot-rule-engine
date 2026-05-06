package com.fbp.engine.plugin;

import com.fbp.engine.core.Node;
import com.fbp.engine.registry.NodeFactory;

public record NodeDescriptor(
    String typeName,
    String description,
    Class<? extends Node> nodeClass,
    NodeFactory factory
) {}
package com.seggellion.britannia_mod.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ServiceDialogueSetDefinition(
        String key,
        String entryNodeKey,
        List<ServiceDialogueNodeDefinition> nodes,
        long definitionRevision
) {
    public ServiceDialogueSetDefinition {
        nodes = List.copyOf(nodes);
    }

    public Map<String, ServiceDialogueNodeDefinition> nodesByKey() {
        Map<String, ServiceDialogueNodeDefinition> indexed = new LinkedHashMap<>();
        for (ServiceDialogueNodeDefinition node : nodes) {
            indexed.put(node.key(), node);
        }
        return Collections.unmodifiableMap(indexed);
    }
}

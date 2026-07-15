package com.seggellion.britannia_mod.service;

import java.util.List;

public record ServiceDialogueNodeDefinition(
        String key,
        String body,
        List<ServiceDialogueOptionDefinition> options
) {
    public ServiceDialogueNodeDefinition {
        options = List.copyOf(options);
    }
}

package com.seggellion.britannia_mod.service;

public record ServiceDialogueOptionDefinition(
        String id,
        String label,
        DialogueActionType actionType,
        String targetNodeKey,
        String serviceKey
) {
}

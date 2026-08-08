package com.seggellion.britannia_mod.dialogue;

import java.util.List;

public record DialogueViewModel(
        String npcName,
        String npcGender,
        String professionLabel,
        String title,
        String body,
        String nodeType,
        boolean completed,
        List<DialogueOptionViewModel> options
) {
    public DialogueViewModel {
        npcName = valueOrEmpty(npcName);
        npcGender = valueOrEmpty(npcGender);
        professionLabel = valueOrEmpty(professionLabel);
        title = valueOrEmpty(title);
        body = valueOrEmpty(body);
        nodeType = valueOrEmpty(nodeType);
        options = List.copyOf(options);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

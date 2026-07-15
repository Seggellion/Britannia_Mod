package com.seggellion.britannia_mod.dialogue;

import com.seggellion.britannia_mod.quest.network.QuestModels.QuestChoice;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;

import java.util.List;
import java.util.Objects;

public final class QuestDialogueAdapter {
    private QuestDialogueAdapter() {
    }

    public static DialogueViewModel from(QuestResponse response, String npcName, String npcGender) {
        Objects.requireNonNull(response, "response");

        String title = response.currentNode != null ? response.currentNode.title : "Quest Update";
        String body = response.currentNode != null ? response.currentNode.body : "No dialogue.";
        String nodeType = response.currentNode != null ? response.currentNode.nodeType : "";
        List<DialogueOptionViewModel> options = response.choices == null
                ? List.of()
                : response.choices.stream().map(QuestDialogueAdapter::adaptChoice).toList();

        return new DialogueViewModel(
                npcName,
                npcGender,
                "",
                title,
                body,
                nodeType,
                response.completed,
                options
        );
    }

    private static DialogueOptionViewModel adaptChoice(QuestChoice choice) {
        if (choice == null) {
            return new DialogueOptionViewModel("", "", true);
        }
        return new DialogueOptionViewModel(choice.id, choice.text, choice.isLocked);
    }
}

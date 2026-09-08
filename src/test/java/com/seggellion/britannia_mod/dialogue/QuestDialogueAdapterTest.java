package com.seggellion.britannia_mod.dialogue;

import com.seggellion.britannia_mod.quest.network.QuestModels.QuestChoice;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestNode;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDialogueAdapterTest {
    @Test
    void preservesQuestTextChoiceOrderLockingAndBlankProfession() {
        QuestResponse response = new QuestResponse();
        response.currentNode = new QuestNode();
        response.currentNode.title = "A Request";
        response.currentNode.body = "Will you help?";
        response.currentNode.nodeType = "decision";
        response.choices = List.of(choice("accept", "I will help.", false), choice("reject", "No.", true));
        response.completed = false;

        DialogueViewModel view = QuestDialogueAdapter.from(response, "Iolo", "male");

        assertEquals("Iolo", view.npcName());
        assertEquals("male", view.npcGender());
        assertEquals("", view.professionLabel());
        assertEquals("A Request", view.title());
        assertEquals("Will you help?", view.body());
        assertEquals("decision", view.nodeType());
        assertEquals(List.of("accept", "reject"), view.options().stream().map(DialogueOptionViewModel::id).toList());
        assertFalse(view.options().getFirst().locked());
        assertTrue(view.options().getLast().locked());
        assertFalse(view.completed());

        DialogueLayout legacyLayout = DialogueLayout.calculate(800, 2, 2, 9, false, true);
        assertEquals(21, legacyLayout.portraitY());
        assertEquals(163, legacyLayout.textX());
        assertEquals(640, legacyLayout.buttonStartX());
    }

    @Test
    void preservesTheExistingNoDialogueFallback() {
        QuestResponse response = new QuestResponse();
        response.choices = null;
        response.completed = true;

        DialogueViewModel view = QuestDialogueAdapter.from(response, null, null);

        assertEquals("No dialogue.", view.body());
        assertEquals("Quest Update", view.title());
        assertTrue(view.options().isEmpty());
        assertTrue(view.completed());
    }

    private static QuestChoice choice(String id, String text, boolean locked) {
        QuestChoice choice = new QuestChoice();
        choice.id = id;
        choice.text = text;
        choice.isLocked = locked;
        return choice;
    }
}

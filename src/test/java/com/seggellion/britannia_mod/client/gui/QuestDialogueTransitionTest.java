package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.quest.network.QuestModels.QuestNode;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rule that decides whether a transition's answer advances the dialogue or ends it.
 *
 * <p>Live defect this pins: every "I will return with the ..." choice in the Rowan questline points
 * back at the node it is offered from, so Rails answers 200 with the same node. The screen used to
 * re-open an identical copy of itself, which reads as a dead button.
 */
class QuestDialogueTransitionTest {

    private static QuestResponse at(long nodeId) {
        QuestResponse response = new QuestResponse();
        QuestNode node = new QuestNode();
        node.id = nodeId;
        response.currentNode = node;
        return response;
    }

    private static QuestResponse withoutNode() {
        return new QuestResponse();
    }

    @Test
    void anAnswerThatLandsBackOnTheSameNodeIsADismissal() {
        // "I will return with the dung." -- Dung Duty's `later` choice, destination Dung Duty.
        assertTrue(QuestDialogueTransition.isDismissal(1836L, at(1836L)),
            "a transition that ends where it began must close the dialogue");
    }

    @Test
    void anAnswerThatMovesOnIsNotADismissal() {
        // Dung Duty -> The Dung Delivered, once the trigger fires.
        assertFalse(QuestDialogueTransition.isDismissal(1836L, at(1837L)),
            "a real step must still open the node it moved to");
    }

    @Test
    void missingInformationNeverCloses() {
        // Closing on an unknown id would turn a parsing gap into a dialogue that simply vanishes,
        // which is worse than showing whatever came back.
        assertFalse(QuestDialogueTransition.isDismissal(-1L, at(1836L)),
            "a screen that never knew its own node must not dismiss");
        assertFalse(QuestDialogueTransition.isDismissal(1836L, withoutNode()),
            "an answer carrying no node must not dismiss");
        assertFalse(QuestDialogueTransition.isDismissal(1836L, null),
            "a null answer is handled by the error path, not by dismissal");
    }

    @Test
    void nodeIdReadsWhatIsThereAndMinusOneOtherwise() {
        assertEquals(1836L, QuestDialogueTransition.nodeId(at(1836L)));
        assertEquals(-1L, QuestDialogueTransition.nodeId(withoutNode()));
        assertEquals(-1L, QuestDialogueTransition.nodeId(null));
    }

    @Test
    void everyRowanStageOffersExactlyThisShapeOfChoice() {
        // The five `later` choices are the only self-loops in the whole content set, so the rule
        // above is narrow by construction: it can only ever fire on a choice that already means
        // "I am leaving". Node ids stand in for the five stages.
        for (long node : new long[] {1836L, 1841L, 1846L, 1851L, 1856L}) {
            assertTrue(QuestDialogueTransition.isDismissal(node, at(node)),
                "stage node " + node + " must dismiss on its own `later` choice");
            assertFalse(QuestDialogueTransition.isDismissal(node, at(node + 1)),
                "stage node " + node + " must still advance to its delivered node");
        }
    }
}

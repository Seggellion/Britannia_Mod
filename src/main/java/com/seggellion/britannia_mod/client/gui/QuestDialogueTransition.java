package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;

/**
 * What a transition's answer means for the screen that asked for it.
 *
 * <h2>Why this is not on the screen</h2>
 * {@code QuestDecisionScreen} extends {@code Screen}, so a test that touched this rule there would
 * have to stand up Minecraft to reach it. The rule is pure -- two node ids -- so it lives here
 * beside the layout classes, which are client-free for the same reason.
 *
 * <h2>The rule</h2>
 * A questline step that the player cannot finish yet still has to offer a way out of the dialogue.
 * The content spells that as a choice pointing back at the node it was offered from -- "I will
 * return with the dung." Rails answers it exactly as authored: the objective is still live, there is
 * nowhere else to go, so it returns the same node with HTTP 200.
 *
 * <p>Re-opening an identical screen on that answer is what makes the button look broken: the player
 * clicks, the pending notice flickers, and the same words stay on the parchment. Reading the answer
 * as a dismissal and closing is what the choice's own text promises.
 *
 * <p>Keyed on the node that came back rather than on the choice's id, because the wire carries no
 * destination -- {@code QuestChoice} is {@code id}, {@code text} and {@code is_locked} and nothing
 * else. Presentation choices such as the help guide never reach a transition at all; the screen
 * opens their view without sending anything.
 */
public final class QuestDialogueTransition {

    private QuestDialogueTransition() {}

    /** The id of the node a response is showing, or {@code -1} when it carries none. */
    public static long nodeId(QuestResponse response) {
        return response == null || response.currentNode == null ? -1L : response.currentNode.id;
    }

    /**
     * Whether an answer landed back on the node it was asked from.
     *
     * <p>Unknown ids never dismiss. A response carrying no node, or a screen that never knew its
     * own, keeps the old behaviour of showing whatever came back: closing on missing information
     * would turn a parsing gap into a dialogue that vanishes, which is the worse failure of the two.
     */
    public static boolean isDismissal(long askedFromNodeId, QuestResponse answer) {
        if (askedFromNodeId < 0) return false;
        // A strict item hand-in answers from the node it was asked at by design -- Rails does not
        // advance until a shard confirms a removal -- so it looks exactly like a self-loop and is
        // the opposite of a dismissal: the player is short of something, or the transaction could
        // not finish, and the dialogue is the only place either can be said. Closing here would put
        // this feature's entire failure vocabulary back into a screen that silently vanishes.
        if (QuestHandinPresentation.from(answer).keepsDialogueOpen()) return false;
        long landedOn = nodeId(answer);
        return landedOn >= 0 && landedOn == askedFromNodeId;
    }
}

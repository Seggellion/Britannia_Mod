package com.seggellion.britannia_mod.client.gui;

/**
 * Rowan farming questline M8 item 4: what a quest trigger result does to the player's screen, and
 * whose name is on it.
 *
 * <h2>The auto-popup</h2>
 * A farming objective completing used to open a full dialogue screen. That is the wrong shape for
 * this questline: the objectives fire while the player is hoeing, fertilizing, planting and
 * watering, so a screen appears over the plot they are standing on, takes the mouse, and has to be
 * dismissed before the next step. The apprenticeship's reward is claimed at Rowan, not in a popup
 * at the plot.
 *
 * <p>So a result the player did not ask for is now a quiet notice, and a result they did ask for --
 * one that arrived because they are talking to a quest giver -- still opens the dialogue.
 * {@link #decide} is that rule, as a pure function, because it is the difference between an
 * interface that interrupts and one that does not.
 *
 * <h2>The attribution</h2>
 * {@code ClientNetworkHandler} named every unattributed trigger result "The Guardian" -- a
 * hardcoded string, in a questline whose quest giver is a farmer called Rowan. The name is
 * available: the response carries {@code quest_giver_name}, and the journal entry for the quest
 * carries one too. {@link #attribution} takes them in order of authority and returns blank only
 * when nothing at all names a giver, which is the signal to use
 * {@link QuestScreenText#GIVER_UNKNOWN} rather than to invent somebody.
 */
public final class QuestTriggerResultPresentation {

    private QuestTriggerResultPresentation() {
    }

    /** What to do with a trigger result. */
    public enum Outcome {
        /**
         * Open the dialogue screen. The player is in a conversation, so a screen is what they
         * expect and it is already covering the world.
         */
        OPEN_DIALOGUE,
        /**
         * An action-bar line and a toast, and nothing that takes the mouse. The player is in the
         * middle of doing something.
         */
        QUIET_NOTICE,
        /** The response carried no node; there is nothing to show. */
        NOTHING
    }

    /**
     * @param hasNode            whether the response carried a current node to show
     * @param fromConversation   whether a quest giver's presentation matched this result -- true
     *                           exactly when the player is talking to somebody
     */
    public static Outcome decide(boolean hasNode, boolean fromConversation) {
        if (!hasNode) return Outcome.NOTHING;
        return fromConversation ? Outcome.OPEN_DIALOGUE : Outcome.QUIET_NOTICE;
    }

    /**
     * The quest giver's display name for a result, best source first.
     *
     * @param conversationName the name of the giver the player is talking to, if any
     * @param responseName     {@code quest_giver_name} from the Rails response
     * @param journalName      the name on the journal entry for this quest
     * @return a display name, or {@code ""} when nothing names a giver
     */
    public static String attribution(String conversationName, String responseName, String journalName) {
        String fromConversation = displayName(conversationName);
        if (!fromConversation.isEmpty()) return fromConversation;
        String fromResponse = displayName(responseName);
        if (!fromResponse.isEmpty()) return fromResponse;
        return displayName(journalName);
    }

    /** Which of the two quiet-notice sentences a result gets. */
    public enum NoticeKind {
        /** An objective completed and there are more to do: name the one that is next. */
        ADVANCED,
        /** The last objective is done: send the player back to the giver. */
        READY_TO_CLAIM
    }

    /**
     * The quiet notice, as a translation key and the one argument it takes.
     *
     * @param kind     which sentence
     * @param argument the objective for {@link NoticeKind#ADVANCED}, the giver's display name for
     *                 {@link NoticeKind#READY_TO_CLAIM}. Blank means the caller must substitute a
     *                 translated stand-in rather than print nothing.
     */
    public record Notice(NoticeKind kind, String argument) {

        public Notice {
            argument = argument == null ? "" : argument.trim();
        }

        public String translationKey() {
            return kind == NoticeKind.READY_TO_CLAIM
                    ? QuestScreenText.OBJECTIVE_READY_TO_CLAIM
                    : QuestScreenText.OBJECTIVE_ADVANCED;
        }
    }

    /**
     * What a quiet objective notice should say.
     *
     * <p>Pure, and separate from the handler, because the two things worth asserting about it are
     * behavioural rather than cosmetic: a <i>second</i> objective must produce a <i>different</i>
     * line from the first, and the last one must switch to the return-to-giver sentence. Both
     * depend entirely on the caller having refreshed the journal before composing this -- the
     * defect being fixed is that it had not, so every farming step printed the same line and the
     * claim sentence never fired at the moment it exists for.
     *
     * @param objective    the journal's next action, after the advance
     * @param questTitle   the quest's own title, used only when there is no objective at all
     * @param claimPending whether the journal says the quest is waiting to be claimed
     * @param giverName    the quest giver, raw; trimmed of its api-id suffix here
     */
    public static Notice objectiveNotice(String objective, String questTitle, boolean claimPending,
                                         String giverName) {
        if (claimPending) {
            return new Notice(NoticeKind.READY_TO_CLAIM, displayName(giverName));
        }
        String line = objective == null ? "" : objective.trim();
        if (line.isEmpty()) {
            line = questTitle == null ? "" : questTitle.trim();
        }
        return new Notice(NoticeKind.ADVANCED, line);
    }

    /**
     * Trims a raw quest-giver name to what a player should read.
     *
     * <p>Quest giver names reach the client as {@code "Rowan"} or as {@code "Rowan:<api id>"}; the
     * suffix is an identity, not a name, and the journal already strips it the same way. Doing it
     * here too means the dialogue header and the journal row never disagree about who somebody is.
     */
    public static String displayName(String rawName) {
        if (rawName == null) return "";
        String cleaned = rawName.trim();
        if (cleaned.isEmpty()) return "";
        if (!cleaned.contains(":")) return cleaned;
        return cleaned.split(":", 2)[0].trim();
    }
}

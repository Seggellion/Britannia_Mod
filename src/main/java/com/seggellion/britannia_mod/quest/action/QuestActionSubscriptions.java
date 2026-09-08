package com.seggellion.britannia_mod.quest.action;

import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Decides whether a farming action the server just observed is worth telling Rails about
 * (protocol section 2.2: "the mod only enqueues events that match a subscribed
 * {@code triggers.action}/{@code triggers.steps} in the server journal ... so the outbox never
 * carries every farming action of every player").
 *
 * <p>This is a filter, not an authority. Section 2.1 is explicit that "the watcher uses these only
 * to decide <em>whether to send</em> an event; Rails re-evaluates everything". A match here never
 * grants progress, and a subject that satisfies nothing simply produces no event.
 *
 * <p>Nothing in it reads a client packet: the journal is the server's copy, and the subject is
 * built at the authoritative success point from what the server itself did.
 */
public final class QuestActionSubscriptions {
    private QuestActionSubscriptions() {}

    /**
     * One thing to report. {@code advancing} distinguishes an {@code action_trigger} (which
     * advances the node) from an {@code action_step} (which records progress) -- the mod does not
     * act on the difference, but it is worth logging.
     */
    public record Match(String questStateId, long questId, String triggerKey, boolean advancing) {
        public Match {
            Objects.requireNonNull(questStateId, "questStateId");
            Objects.requireNonNull(triggerKey, "triggerKey");
        }
    }

    /**
     * Every subscription across this player's journal that the action satisfies: at most one
     * advancing trigger and at most one step per quest.
     */
    public static List<Match> matches(UUID playerUuid, List<ClientQuestEntry> journal,
                                      QuestAction action, QuestActionSubject subject) {
        if (playerUuid == null || journal == null || journal.isEmpty() || action == null || subject == null) {
            return List.of();
        }

        List<Match> found = new ArrayList<>();
        for (ClientQuestEntry entry : journal) {
            if (entry == null || entry.questStateId().isBlank()) continue;
            QuestObjectiveTriggers triggers = entry.triggers();
            if (triggers == null || !triggers.hasActionSubscriptions()) continue;
            long questId = questId(entry);

            QuestObjectiveTriggers.ActionTrigger actionTrigger = triggers.action();
            if (actionTrigger != null && action.wireName().equals(actionTrigger.action())
                && satisfies(playerUuid, subject, actionTrigger.match(), actionTrigger.requirePlanter(),
                    actionTrigger.requireBound())) {
                found.add(new Match(entry.questStateId(), questId, actionTrigger.triggerKey(), true));
            }

            QuestObjectiveTriggers.ActionStep step = firstMatchingStep(playerUuid, subject, action, triggers.steps());
            if (step != null) {
                found.add(new Match(entry.questStateId(), questId, step.key(), false));
            }
        }
        return List.copyOf(found);
    }

    /**
     * The step to report, or null. The first step that is not yet {@code done} wins, because steps
     * are ordered and Rails rejects one applied out of order. When every matching step is already
     * done the FIRST of them is reported anyway: section 2.1 makes an earlier step restart the
     * sequence, which is exactly how a player whose plot was reclaimed recovers by hoeing again.
     */
    private static QuestObjectiveTriggers.ActionStep firstMatchingStep(
            UUID playerUuid, QuestActionSubject subject, QuestAction action,
            List<QuestObjectiveTriggers.ActionStep> steps) {
        QuestObjectiveTriggers.ActionStep firstDone = null;
        for (QuestObjectiveTriggers.ActionStep step : steps) {
            if (!action.wireName().equals(step.action())) continue;
            if (!satisfies(playerUuid, subject, step.match(), step.requirePlanter(), step.requireBound())) continue;
            if (!step.done()) return step;
            if (firstDone == null) firstDone = step;
        }
        return firstDone;
    }

    /**
     * Section 2.1's comparison rules: equality for every listed {@code match} key against the
     * event's subject, {@code require_planter} against the acting player, and every published
     * {@code require_bound} value against the same subject. A key the subject does not carry never
     * matches -- absence is not equality.
     */
    private static boolean satisfies(UUID playerUuid, QuestActionSubject subject, Map<String, String> match,
                                     boolean requirePlanter, Map<String, String> requireBound) {
        if (!allEqual(subject, match)) return false;
        if (!allEqual(subject, requireBound)) return false;
        if (requirePlanter) {
            String planter = subject.comparable("planter_uuid");
            return planter != null && planter.equalsIgnoreCase(playerUuid.toString());
        }
        return true;
    }

    private static boolean allEqual(QuestActionSubject subject, Map<String, String> expected) {
        for (Map.Entry<String, String> condition : expected.entrySet()) {
            String actual = subject.comparable(condition.getKey());
            if (actual == null || !actual.equals(condition.getValue())) return false;
        }
        return true;
    }

    private static long questId(ClientQuestEntry entry) {
        try {
            return Long.parseLong(entry.questId());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}

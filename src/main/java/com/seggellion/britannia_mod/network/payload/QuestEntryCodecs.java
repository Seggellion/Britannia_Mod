package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.quest.ClientQuestEntry;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What a journal entry looks like on the wire.
 *
 * <h2>The rule this file exists to enforce</h2>
 * It writes an <b>explicit field list</b>, never the whole record. {@code ClientQuestEntry} carries
 * {@code triggers} -- the match criteria, the bound plot key, the crop-cycle UUID, the exact volume
 * that completes a delivery -- and the explicit list is what keeps it out of <i>this</i> packet. A
 * reflective or "just serialize the record" codec here would put quest solutions on the wire the
 * first time somebody added a field, which is exactly why this is written out by hand.
 *
 * <h2>What this codec does not claim</h2>
 * It is <b>the sync payload's</b> field list, and it is the only thing it speaks for. It has never
 * been a statement about the mod as a whole, because until M11 trigger data did reach a client by
 * routes that do not come through here:
 * <ul>
 *   <li>{@code QuestActionDispatcher} sent the raw Rails body in
 *       {@code QuestTriggerResultS2CPayload}, {@code accepted_quest.triggers} and its resolved
 *       bound values included;</li>
 *   <li>{@code QuestObjectiveWatcher} and {@code QuestEventHandlers} sent the response with
 *       {@code node.metadata} intact, which carries {@code action_trigger} and
 *       {@code action_steps}.</li>
 * </ul>
 * M11 closed those: all three now serialize through {@code QuestClientPayload}, which removes
 * {@code triggers} and the five node observer keys at every depth. Milestone 6's finding Q-05 -- a
 * modified client asserting any objective it likes -- was already closed by the <i>server</i>
 * deciding, which is true regardless of what a client can read; what changed in M11 is that the
 * client is no longer handed the answer either. What this file guarantees remains narrower and
 * still worth having: adding a field to {@code ClientQuestEntry} does not silently widen the
 * journal sync, whatever any other path does.
 *
 * <h2>Milestone 8: what was added, and why triggers still does not travel</h2>
 * The journal screen has to show the quest number, the next action, the ordered progress list, the
 * reward and keep previews, and whether the quest is waiting to be claimed. None of that is
 * derivable from the eight strings this used to write, so
 * {@link ClientQuestEntry.JournalDetail} is now written too: {@code stage}, the giver's
 * {@code profession}, {@code objective}, {@code progress[]}, {@code rewards_preview}
 * ({@code on_accept}, {@code on_complete}, {@code achievements}), {@code keep_items} and
 * {@code claim_pending}. Protocol section 3.1 names exactly this set.
 *
 * <p>{@code triggers} is still not in the list, and the two are separate fields on the record
 * rather than one blob so that they cannot be confused. The distinction is not "journal fields are
 * safe": it is that these fields describe <i>what the player has already done and what they were
 * promised</i>, while {@code triggers} describes <i>what would satisfy the server</i>. A
 * {@code progress} entry says a step is done, which the player can already see on the plot in
 * front of them; the matching {@code steps} entry in {@code triggers} says which plot key and
 * which crop cycle would make it done, which is a solution.
 */
final class QuestEntryCodecs {
    private QuestEntryCodecs() {}

    /**
     * Upper bound on any list read from the wire. The server writes these, but a decoder that
     * allocates whatever a packet asks for only behaves while the sender does. Well past the
     * longest authored quest (five steps and a handful of items).
     */
    private static final int MAX_LIST = 64;

    static void writeEntry(FriendlyByteBuf buf, ClientQuestEntry entry) {
        buf.writeUtf(entry.questStateId());
        buf.writeUtf(entry.questId());
        buf.writeUtf(entry.questKey());
        buf.writeUtf(entry.questGiverName());
        buf.writeUtf(entry.name());
        buf.writeUtf(entry.briefDescription());
        buf.writeUtf(entry.acceptedAt());
        buf.writeUtf(entry.status());
        writeDetail(buf, entry.detail());
    }

    static ClientQuestEntry readEntry(FriendlyByteBuf buf) {
        String questStateId = buf.readUtf();
        String questId = buf.readUtf();
        String questKey = buf.readUtf();
        String questGiverName = buf.readUtf();
        String name = buf.readUtf();
        String briefDescription = buf.readUtf();
        String acceptedAt = buf.readUtf();
        String status = buf.readUtf();
        ClientQuestEntry.JournalDetail detail = readDetail(buf);
        return new ClientQuestEntry(questStateId, questId, questKey, questGiverName, name,
                briefDescription, acceptedAt, status).withDetail(detail);
    }

    // ------------------------------------------------------------ M8 detail

    private static void writeDetail(FriendlyByteBuf buf, ClientQuestEntry.JournalDetail detail) {
        ClientQuestEntry.JournalDetail safe =
                detail == null ? ClientQuestEntry.JournalDetail.NONE : detail;

        ClientQuestEntry.Stage stage = safe.stage();
        buf.writeUtf(stage.questlineKey());
        buf.writeVarInt(stage.index());
        buf.writeVarInt(stage.count());
        buf.writeUtf(stage.label());

        buf.writeUtf(safe.questGiverProfession());
        buf.writeUtf(safe.objective());

        List<ClientQuestEntry.ProgressStep> progress = capped(safe.progress());
        buf.writeVarInt(progress.size());
        for (ClientQuestEntry.ProgressStep step : progress) {
            buf.writeUtf(step.key());
            buf.writeUtf(step.label());
            buf.writeBoolean(step.done());
        }

        writeItems(buf, safe.rewardsOnAccept());
        writeItems(buf, safe.rewardsOnComplete());
        writeItems(buf, safe.keepItems());

        List<ClientQuestEntry.Achievement> achievements = capped(safe.achievements());
        buf.writeVarInt(achievements.size());
        for (ClientQuestEntry.Achievement achievement : achievements) {
            buf.writeUtf(achievement.key());
            buf.writeUtf(achievement.title());
        }

        buf.writeBoolean(safe.claimPending());
    }

    private static ClientQuestEntry.JournalDetail readDetail(FriendlyByteBuf buf) {
        ClientQuestEntry.Stage stage = new ClientQuestEntry.Stage(
                buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        String profession = buf.readUtf();
        String objective = buf.readUtf();

        int progressCount = readSize(buf);
        List<ClientQuestEntry.ProgressStep> progress = new ArrayList<>(progressCount);
        for (int i = 0; i < progressCount; i++) {
            progress.add(new ClientQuestEntry.ProgressStep(
                    buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }

        List<ClientQuestEntry.RewardItem> onAccept = readItems(buf);
        List<ClientQuestEntry.RewardItem> onComplete = readItems(buf);
        List<ClientQuestEntry.RewardItem> keepItems = readItems(buf);

        int achievementCount = readSize(buf);
        List<ClientQuestEntry.Achievement> achievements = new ArrayList<>(achievementCount);
        for (int i = 0; i < achievementCount; i++) {
            achievements.add(new ClientQuestEntry.Achievement(buf.readUtf(), buf.readUtf()));
        }

        boolean claimPending = buf.readBoolean();
        return new ClientQuestEntry.JournalDetail(stage, profession, objective, progress,
                onAccept, onComplete, achievements, keepItems, claimPending);
    }

    private static void writeItems(FriendlyByteBuf buf, List<ClientQuestEntry.RewardItem> items) {
        List<ClientQuestEntry.RewardItem> capped = capped(items);
        buf.writeVarInt(capped.size());
        for (ClientQuestEntry.RewardItem item : capped) {
            buf.writeUtf(item.id());
            buf.writeVarInt(item.count());
        }
    }

    private static List<ClientQuestEntry.RewardItem> readItems(FriendlyByteBuf buf) {
        int count = readSize(buf);
        List<ClientQuestEntry.RewardItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new ClientQuestEntry.RewardItem(buf.readUtf(), buf.readVarInt()));
        }
        return items;
    }

    private static <T> List<T> capped(List<T> source) {
        if (source == null) return List.of();
        return source.size() <= MAX_LIST ? source : source.subList(0, MAX_LIST);
    }

    private static int readSize(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_LIST) {
            throw new IllegalArgumentException("Quest journal list size out of range: " + size);
        }
        return size;
    }
}

package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Durability for the hand-in ledger, and the one place the ordering that makes a removal
 * recoverable is written down.
 *
 * <h2>The problem</h2>
 * A removal touches two files that are written at different moments: the player's own
 * {@code playerdata/&lt;uuid&gt;.dat}, which holds the inventory, and this {@code SavedData}, which holds
 * the transaction. A crash between them leaves them disagreeing, and only one direction of
 * disagreement is safe.
 *
 * <ul>
 *   <li><b>Player file ahead of the ledger</b> -- items gone, ledger does not know. Recoverable, as
 *       long as the player file itself carries enough to finish: it does, because the proof is
 *       written into the player's persistent data in the same in-memory step as the shrink.</li>
 *   <li><b>Ledger ahead of the player file</b> -- ledger says "removed", player reloads still
 *       holding the items. Unrecoverable in the dangerous direction: confirming would buy a
 *       completed quest, or a refund, for items the player never gave up.</li>
 * </ul>
 *
 * <p>So the ordering is chosen to make the second direction impossible <i>by construction</i>, and
 * this is the same conclusion {@code BankTransferPlayerDurability} reached for a deposit: on a
 * removal the player save comes first and the durable record second. Receipt-first is a real,
 * exploitable duplication, and it is the mistake this class exists to not make.
 *
 * <h2>The sequence</h2>
 * <ol>
 *   <li>{@link #record} the transaction as {@code PREPARED} and flush. Nothing is owed.</li>
 *   <li>Resolve every requirement and count the carried inventory, mutating nothing.</li>
 *   <li>{@link #transition} to {@code REMOVAL_INTENT} carrying the concrete proof, and flush. Still
 *       nothing is owed -- an intent entitles nobody to anything -- but the transaction that is
 *       about to mutate is now named on disk.</li>
 *   <li>On the server thread, in one uninterrupted step: shrink the stacks <b>and</b> append the
 *       proof to the player's persistent data.</li>
 *   <li>Force that player's own file to disk. If it reports a failure, put the stacks back and
 *       return to {@code PREPARED}; nothing was owed and nothing was lost.</li>
 *   <li>{@link #transition} to {@code REMOVED_LOCAL} and flush.</li>
 *   <li>Confirm with Rails, carrying the stored proof.</li>
 *   <li>Apply the completion, or wait for the refund the reward-delivery ledger will bring.</li>
 *   <li>Only then mark the row settled and flush.</li>
 * </ol>
 *
 * <h2>Why a crash cannot strand an item</h2>
 * Step 4 is the mutation, and it is why the marker lives in the player's own file rather than only
 * here. The shrink and the marker are two adjacent in-memory changes to the same {@code
 * ServerPlayer} with no I/O, yield or thread handoff between them, so no save -- autosave or the
 * forced one -- can observe one without the other. Vanilla writes that file to a temporary path and
 * atomically replaces the real one, so the file on disk holds <b>both the removal and its proof, or
 * neither</b>.
 *
 * <table>
 *   <caption>Crash points and their outcomes</caption>
 *   <tr><th>Crash</th><th>Player file</th><th>Ledger</th><th>Recovery</th></tr>
 *   <tr><td>after 3, before 4</td><td>items present, no marker</td><td>{@code REMOVAL_INTENT}</td>
 *       <td>Marker absent: the mutation never happened. The row returns to {@code PREPARED} and the
 *           player still has everything.</td></tr>
 *   <tr><td>inside 4</td><td>items present, no marker</td><td>{@code REMOVAL_INTENT}</td>
 *       <td>Identical to the row above -- the file cannot have half of step 4.</td></tr>
 *   <tr><td>after 5, before 6</td><td>items gone, marker present</td><td>{@code REMOVAL_INTENT}</td>
 *       <td>Marker present: the removal is durable. The row is promoted to {@code REMOVED_LOCAL}
 *           <b>from the marker's own proof</b> and confirmed. This is the case the marker exists
 *           for, and the reason it carries the whole proof rather than an id.</td></tr>
 *   <tr><td>after 6, before 7</td><td>items gone, marker present</td><td>{@code REMOVED_LOCAL}</td>
 *       <td>Confirm with the stored proof. Byte-identical to the attempt that was lost.</td></tr>
 *   <tr><td>after Rails consumed, before its reply</td><td>items gone</td><td>{@code CONFIRMING}</td>
 *       <td>Confirm again; Rails answers {@code duplicate} with the stored completion, applied
 *           once.</td></tr>
 *   <tr><td>ledger file lost entirely</td><td>items gone, marker present</td><td>row absent</td>
 *       <td>The marker is self-sufficient: the row is rebuilt from it as {@code REMOVED_LOCAL} and
 *           confirmed.</td></tr>
 * </table>
 *
 * <h2>What this does not claim</h2>
 * Two honest limits, both inherited and both already stated by the machinery this follows.
 *
 * <p>First, {@code PlayerDataStorage#save} catches its own {@code IOException} and only logs, so a
 * forced player save that <i>reports</i> success is not proof the bytes reached the disk. If that
 * silent failure happens, step 6 writes {@code REMOVED_LOCAL} for a player whose file still has the
 * items -- the dangerous direction. It is not left to chance: on the next start the marker is
 * absent while the ledger says removed, which is a contradiction this server refuses to act on. The
 * row is stranded rather than confirmed, so the player keeps the items and nobody is paid twice,
 * and an operator sees it. That is the correct failure direction, and it is the only outcome of
 * that window.
 *
 * <p>Second, {@code SavedData#save(File, ...)} swallows its own {@code IOException} too, so a
 * failed ledger flush is invisible here as well. Every state it could fail to write is one whose
 * loss is recovered from the player file, which is written first for exactly this reason.
 *
 * <p>Neither covers a power loss defeating {@code fsync} on the storage device itself -- the same
 * limitation the blessed-item and banking receipts state plainly for themselves.
 */
public final class QuestHandinLedger {

    private QuestHandinLedger() {}

    public static QuestHandinLedgerStore store(MinecraftServer server) {
        return QuestHandinLedgerStore.get(server);
    }

    /** Durably records a new transaction. Flushes only when a row was actually created. */
    public static QuestHandinLedgerStore.RecordOutcome record(MinecraftServer server,
                                                              QuestHandinLedgerEntry entry) {
        QuestHandinLedgerStore.RecordOutcome outcome = store(server).record(entry);
        if (outcome == QuestHandinLedgerStore.RecordOutcome.CREATED) forceSynchronousFlush(server);
        return outcome;
    }

    /**
     * Applies {@code change} and flushes when the row actually changed.
     *
     * <p>Every state transition in the sequence above goes through here, so "durable before the next
     * step" is a property of the method rather than of each caller remembering.
     */
    public static Optional<QuestHandinLedgerEntry> transition(MinecraftServer server, UUID handinUuid,
                                                              UnaryOperator<QuestHandinLedgerEntry> change) {
        Optional<QuestHandinLedgerEntry> changed = store(server).update(handinUuid, change);
        changed.ifPresent(entry -> forceSynchronousFlush(server));
        return changed;
    }

    /**
     * Applies {@code change} without forcing a flush, for bookkeeping whose loss costs one extra
     * retry and never an item: attempt counts and the last transport error.
     */
    public static Optional<QuestHandinLedgerEntry> note(MinecraftServer server, UUID handinUuid,
                                                        UnaryOperator<QuestHandinLedgerEntry> change) {
        return store(server).update(handinUuid, change);
    }

    public static Optional<QuestHandinLedgerEntry> find(MinecraftServer server, UUID handinUuid) {
        return store(server).find(handinUuid);
    }

    /**
     * Writes every dirty store on the overworld to disk, synchronously.
     *
     * <p>Whole-storage scoped: this also flushes anything else dirty on the overworld. That is the
     * same trade {@code BlessedDeliveryReceipts} and {@code QuestRewardDeliveryLedger} accept, and
     * it is the only forced-flush API {@code DimensionDataStorage} offers.
     */
    public static void forceSynchronousFlush(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }
}

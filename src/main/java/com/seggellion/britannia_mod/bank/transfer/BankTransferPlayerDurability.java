package com.seggellion.britannia_mod.bank.transfer;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.mixin.PlayerListInvokerMixin;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Closes the standing cross-milestone gap the M10 currency-deposit crash-window verification
 * pass identified: {@code BankTransferReceiptStore}'s own writes are durable (fsync'd) far
 * sooner than the player's own inventory state is -- vanilla only persists a player's NBT on the
 * periodic autosave tick, on disconnect, or at shutdown, which can lag a receipt's own
 * durability by minutes. In that lag window, a process kill produces a state where the receipt
 * (or Rails' own confirmed state) and the player's actual, reloaded inventory disagree about
 * whether a removal/insertion really happened -- exactly the duplication/loss pair traced in
 * that verification pass, not a new defect but a real one, present in every bank-transfer flow
 * this program has shipped since Milestone 9.
 *
 * <p>{@link #forceSave(ServerPlayer)} forces exactly this one player's own data durably to disk,
 * synchronously, via {@link PlayerListInvokerMixin}'s bridge to the real (protected) {@code
 * PlayerList#save(ServerPlayer)} -- confirmed by reading {@code PlayerDataStorage#save}
 * directly: a temp-file write opened with {@code StandardOpenOption.SYNC} (durable to storage
 * before the call returns) followed by a same-directory atomic rename. No other player, no
 * chunk, nothing else is touched. {@code PlayerDataStorage#save} itself already catches every
 * exception internally and only logs (it never rethrows) -- the try/catch here exists purely as
 * this codebase's own established defensive posture around every other side-effecting call
 * (mirroring how {@code client.prepare}/{@code client.cancel} calls are wrapped elsewhere), not
 * because a throw is expected.
 *
 * <h2>Failure handling (reconsidered): detectable failures change the caller's own action</h2>
 * {@link #forceSave(ServerPlayer)} returns {@code false} when the save could not be performed --
 * every call site below now branches on that, rather than treating "logged a warning" as good
 * enough. What "log and proceed" used to mean concretely: the transaction would carry on to
 * write a receipt and dispatch to Rails as if the player's on-disk state now reflected the
 * physical change, when it provably might not -- precisely the duplication/loss window this
 * whole fix exists to close, reopened by the failure handler itself, and, per this program's own
 * established finding, one the receipt/Rails machinery cannot detect on its own (Rails only ever
 * sees "prepared"/"confirmed", never "was this player's file actually written"). That
 * characterization was verified against this method's own real callers, not assumed.
 *
 * <p><b>Deposit (item and currency):</b> a detected failure aborts. At the exact point both
 * deposit flows call this method, the physical removal is still purely in-memory (nothing
 * durable or Rails-facing exists yet -- no receipt, no confirm), so undoing it is free: the
 * caller restores the shrunk stack and calls Rails' {@code cancel} on the just-prepared
 * operation, exactly mirroring the already-established removal-revalidation-mismatch branch
 * each proxy service already has. Nothing is left for a human to review because nothing
 * happened.
 *
 * <p><b>Withdrawal:</b> abort is not available -- the receipt was already durably written
 * <i>before</i> insertion (Section A.6's ordering, unchanged), so by the time this method runs
 * the system has already durably committed to "this insertion happened." Undoing the insertion
 * now would create a fresh, worse inconsistency (a receipt claiming a physical action that this
 * process then reversed), not restore a clean prior state. The withdrawal call site instead
 * proceeds exactly as before but marks the eventual confirm outcome for mandatory reconciliation
 * review (escalates instead of resolving, reusing the exact {@code
 * BankTransferReceiptStore}/{@code RECONCILIATION_REQUIRED} machinery Rails' own {@code
 * ReconciliationRequired} confirm response already uses) rather than letting a clean Rails
 * confirm silently delete the receipt and erase the only trace that local doubt existed.
 *
 * <p>Retrying the save itself was considered and rejected: every call site here runs on the
 * single-threaded main server tick with no concurrent access to this player's data, so the one
 * exception class this method can actually observe (see below) reflects a real data-shape
 * problem, not a transient race -- an immediate identical retry would almost certainly throw
 * again, adding latency and complexity without a plausible mechanism by which it would succeed
 * differently.
 *
 * <h2>The honest limit of what "returns false" can detect</h2>
 * Traced directly from {@code PlayerDataStorage#save}: its entire body, including {@code
 * saveWithoutId} and the file write/rename, is wrapped in its own {@code catch (Exception)} that
 * only logs -- it never rethrows, so an I/O failure there is already fully invisible to this
 * method and returns {@code true} regardless. What this method's own {@code catch} actually
 * guards is {@code PlayerList#save(ServerPlayer)}'s further calls to {@code
 * ServerStatsCounter#save} and, in particular, {@code PlayerAdvancements#save}, whose {@code
 * DataResult#getOrThrow()} encode step is <b>not</b> wrapped in any try/catch and can propagate a
 * real exception up through the mixin bridge into this method. So {@code forceSave} returning
 * {@code false} is a real, actionable signal for that class of failure; returning {@code true}
 * is not proof the underlying file write actually happened, only that nothing in this
 * observable path threw. Closing that remaining silent gap would require reading the just-
 * written file back from disk to verify it, which was considered and deliberately not done here:
 * it adds a full synchronous NBT round-trip to every deposit/withdrawal, and doing it safely
 * requires the exact same live-instance-hijack guard this program's own GameTest fix for {@code
 * loadFreshFromDisk} needed (constructing a second same-UUID {@code ServerPlayer} to read it
 * back re-points the live player's cached {@code PlayerAdvancements} tracker via {@code
 * PlayerList#getPlayerAdvancements}) -- a real option, not implemented here, and explicitly not
 * claimed to be covered by the policy above.
 *
 * <h2>Ordering: save-then-receipt for deposits, insert-then-save-then-confirm for withdrawal</h2>
 * For a deposit (item or currency), this is called after the in-memory removal but before
 * {@code BankTransferReceipts.record} -- deliberately the opposite of "record first": if the
 * receipt were written first, a crash between the receipt's durability and this save's
 * durability would leave a resumable {@code PENDING_LOCAL_ACTION} receipt (confirmed and
 * resumed on restart, crediting the balance/bank item) sitting alongside a player reload that
 * still has the coins/item back (the removal was never durably saved) -- a real, exploitable
 * duplication, the exact failure mode Section A.6 and this program's own invariants
 * ("a physical coin amount and the Rails balance are never both credited by a retry") forbid.
 * Ordering the save first means any crash before the receipt exists leaves no resumable
 * evidence at all -- Rails' own abandoned "prepared" operation simply expires
 * (BankTransferOperations::Expire's already-proven, accepted behavior) -- narrowing the
 * remaining risk to a rare, non-duplicating loss, the same class of "regrettable but never
 * duplicated" outcome this program's own Expire documentation already accepts for a deposit's
 * plain, unconfirmed expiry.
 *
 * <p>For withdrawal, the receipt is already durably written BEFORE insertion (Section A.6's own
 * withdrawal-specific ordering, unchanged by this fix). This call instead goes immediately
 * after a successful insertion and before confirm is dispatched: the insertion is the one
 * physical action left un-persisted to the player's own file, and forcing it to disk before
 * Rails is told to confirm means a crash in the remaining, now-adjacent window leaves the item
 * durably present (this save already completed) with, at worst, an ambiguous
 * reconciliation-required Rails-side operation for an admin to review -- never a state where
 * the item is durably gone from the player's own file while Rails already confirmed it
 * withdrawn.
 *
 * <h2>What "sequential, uninterrupted task" actually proves</h2>
 * Both the forced save and the receipt write are calls this codebase already established as
 * synchronous and durable-on-return (no callback, no awaited future, no thread handoff). Placed
 * as two adjacent statements inside the same {@code server.execute} task with no I/O, wait, or
 * yield between them, there is no JVM/OS scheduling event that can occur strictly between them
 * -- no other thread can run, no async signal delivery boundary exists for this process to
 * observe, and no legitimate crash-recovery scenario in a real deployment can land "between"
 * two adjacent bytecode-level statements in any way distinguishable from "before both ran" or
 * "after both ran." This is the identical proof style already used to establish the original
 * shrink-then-receipt window as airtight, applied to the pair this class now guards.
 * Precisely, honestly, and no more than this: it eliminates the schedulable, exploitable,
 * reproducible version of the residual gap. It does not, and cannot, prove literal
 * zero-probability against an arbitrary-instant hardware kill (a genuine power-loss landing at
 * an exact CPU cycle between two adjacent machine instructions) -- the same well-known,
 * inherent limitation {@link BankTransferReceipts}'s own fsync-durability claim already states
 * plainly for itself. No software-level ordering fix, this one included, can close that last
 * residual off; nothing about this fix pretends otherwise.
 */
public final class BankTransferPlayerDurability {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The real, production save call -- mirrors exactly what {@link #forceSave} always invoked
     * inline before this seam existed. Substitutable only via {@link #useSaveDelegateForTesting},
     * following the same {@code useClientForTesting}/{@code resetClientForTesting} pattern this
     * codebase already uses everywhere else a real side-effecting dependency needs a test double
     * (e.g. {@code BankingDepositProxyService}'s own {@code client} field). This exists because
     * {@code forceSave}'s own {@code catch (RuntimeException)} already mechanically guarantees
     * any underlying failure is caught and turned into {@code false} -- that is a fact of Java's
     * type system, not something that needs a contrived real-game-state reproduction to prove.
     * What was never actually tested is whether the CALLING code (the three proxy services)
     * correctly responds to {@code forceSave} returning {@code false}; this seam lets a test
     * install a delegate that throws on demand, isolating exactly that boundary.
     */
    private static java.util.function.Consumer<ServerPlayer> saveDelegate = BankTransferPlayerDurability::invokeRealSave;

    private BankTransferPlayerDurability() {
    }

    private static void invokeRealSave(ServerPlayer player) {
        ((PlayerListInvokerMixin) player.server.getPlayerList()).britannia$invokeSave(player);
    }

    /** Test-only seam: substitutes {@link #forceSave}'s underlying save call for the duration of a test. */
    public static void useSaveDelegateForTesting(java.util.function.Consumer<ServerPlayer> testDelegate) {
        saveDelegate = testDelegate;
    }

    /** Test-only seam: restores the real production save call. Always call this in test teardown. */
    public static void resetSaveDelegateForTesting() {
        saveDelegate = BankTransferPlayerDurability::invokeRealSave;
    }

    /**
     * @return {@code true} if the save ran without this method's own catch observing a failure;
     *         {@code false} if it did. See the class docs for exactly what each caller does with
     *         this and the honest limit of what {@code false} vs {@code true} actually proves.
     */
    public static boolean forceSave(ServerPlayer player) {
        try {
            saveDelegate.accept(player);
            return true;
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Failed to force an immediate save of player {}'s own data during a bank transfer",
                    player.getStringUUID(), failure
            );
            return false;
        }
    }
}

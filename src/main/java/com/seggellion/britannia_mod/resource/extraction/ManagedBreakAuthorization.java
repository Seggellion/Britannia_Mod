package com.seggellion.britannia_mod.resource.extraction;

import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.structure.HouseBuildRights;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The prerequisites every managed destructive resource path shares, in one place.
 *
 * <h2>The hazard this exists to close</h2>
 * A handler that cancels a {@code BreakEvent} and then mutates the world itself is an
 * <b>authority</b>, not a participant. A cancelled event never reaches later listeners, so
 * {@code StructureProtectionHandler} — the class that looks like the house authority — simply does
 * not run for any break a managed handler has taken over. Three separate defects came from that
 * one fact: ore, sediment and wood could each be taken out of a stranger's house by whoever
 * reached the block first, and each was fixed by hand, separately, after the damage was found.
 *
 * <p>So the rule is: <b>security correctness must never depend on a downstream listener seeing an
 * event that has already been cancelled.</b> Event priority stays useful for orchestration — the
 * Mining gate still runs at HIGH so a denial lands before {@code CustomBlockBreakHandler} mutates
 * — but it is no longer what makes the system safe. Every cancel-and-mutate handler carries its
 * own complete authorization path, and this class is the shared part of it.
 *
 * <h2>What it owns, and what it deliberately does not</h2>
 * It owns only what is genuinely common to every managed destructive path:
 *
 * <ul>
 *   <li><b>Actor validity</b> — a real player's server-side entity, never a {@link
 *       net.neoforged.neoforge.common.util.FakePlayer} and never a non-player, because automation
 *       has no skill identity to earn against. Typed by {@link ManagedExtractionPolicy}, so there
 *       is one definition of "fake player" for every family.</li>
 *   <li><b>Location and ownership</b> — the canonical {@link HouseBuildRights} decision, the same
 *       one the break rule, the place rule and the housing lease all consume. No foundation
 *       geometry is recomputed here and no alternative ownership test exists.</li>
 * </ul>
 *
 * <p>Everything that varies by domain stays with its domain: the Mining requirement, the
 * extraction tag, the resource's own state, wood and leaf rules, deposit restoration, commodity
 * mapping, creative policy. Creative in particular is deliberately absent — the Mining ladder
 * grants operators an explicit bypass so a misplaced block can be removed, the deposit rule lets
 * {@code instabuild} through to its own creative guard, and collapsing those into one answer here
 * would change behaviour rather than protect it.
 *
 * <p>Nothing in this class mutates: it reads a decision and, at most, tells the player why. It is
 * safe to call from a preflight and again at completion, and both is exactly right — a house
 * boundary or its ownership can change during a dig, so the completed break re-asks rather than
 * trusting a permission remembered from the first click.
 */
public final class ManagedBreakAuthorization {

    private ManagedBreakAuthorization() {
    }

    /** Why a managed break may not proceed, or that it may. */
    public enum Reason {
        ALLOWED,
        /** Not a real player's server entity: a dispenser, an explosion, worldgen, a command. */
        NOT_A_PLAYER,
        /** Automation. A fake player is a real {@code ServerPlayer}, so this is typed, not assumed. */
        AUTOMATION,
        /** A house stands here and this player is not entitled to change what is in it. */
        HOUSE_PROTECTED
    }

    /** The answer, plus the sentence to show the player when there is one worth showing. */
    public record Decision(Reason reason, @Nullable String message) {

        public boolean allowed() {
            return reason == Reason.ALLOWED;
        }
    }

    private static final Decision ALLOWED = new Decision(Reason.ALLOWED, null);

    /**
     * The common prerequisites for working a managed resource at this position, mutating nothing.
     *
     * <p>Order matters and is fixed: an actor is refused for <em>what it is</em> before anything
     * is asked about where it is standing, so a quarry mod never reaches the house question at
     * all.
     */
    public static Decision evaluate(@Nullable ServerLevel level, @Nullable BlockPos pos,
            @Nullable Player actor) {
        ManagedExtractionPolicy.Actor type = ManagedExtractionPolicy.actorOf(actor);
        if (type == ManagedExtractionPolicy.Actor.NON_PLAYER) {
            return new Decision(Reason.NOT_A_PLAYER, null);
        }
        if (type == ManagedExtractionPolicy.Actor.FAKE_PLAYER) {
            return new Decision(Reason.AUTOMATION, null);
        }
        if (level == null || pos == null) {
            // Nothing to ask a house about. Callers always have both in practice; failing closed
            // on the location question would refuse legitimate work for a missing argument.
            return ALLOWED;
        }
        HouseBuildRights.Decision house =
                HouseBuildRights.evaluateBreak(level, pos, (ServerPlayer) actor);
        if (!house.permitted()) {
            return new Decision(Reason.HOUSE_PROTECTED, house.message());
        }
        return ALLOWED;
    }

    /**
     * The call a cancel-and-mutate handler makes: {@code true} when it must stop.
     *
     * <p>Named for the refusal rather than the permission so the guard reads as the early return
     * it is, and so a handler that forgets to act on the result fails visibly rather than
     * silently proceeding.
     *
     * <pre>
     *   if (ManagedBreakAuthorization.refuses(level, pos, player)) {
     *       event.setCanceled(true);
     *       return;                       // nothing mutated, nothing awarded
     *   }
     * </pre>
     */
    public static boolean refuses(@Nullable ServerLevel level, @Nullable BlockPos pos,
            @Nullable Player actor) {
        Decision decision = evaluate(level, pos, actor);
        if (decision.allowed()) {
            return false;
        }
        if (decision.message() != null && actor instanceof ServerPlayer player) {
            // Throttled, because a held dig re-raises the attempt every few ticks and the same
            // refusal must not rewrite the action bar continuously.
            MiningBreakGate.sendThrottledDenial(player,
                    "MANAGED_BREAK|" + decision.reason().name(),
                    Component.literal(decision.message()));
        }
        return true;
    }
}

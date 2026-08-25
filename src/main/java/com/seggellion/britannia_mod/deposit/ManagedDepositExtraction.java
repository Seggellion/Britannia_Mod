package com.seggellion.britannia_mod.deposit;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.extraction.ManagedBreakAuthorization;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import com.seggellion.britannia_mod.structure.HouseBuildRights;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Working one deposit: check, remember, empty, yield — in that order.
 *
 * <h2>Order</h2>
 * Everything that can refuse the extraction refuses before anything is written, and the
 * restoration record is taken from the state that is still standing. A rule that mutated first
 * and validated afterwards is exactly the housing privacy defect this programme already fixed
 * once; it is not repeated here.
 *
 * <p>House authority is asked first, before the tool. A deposit inside somebody's house is
 * theirs, whatever is in the hand reaching for it, and the answer comes from
 * {@link HouseBuildRights} rather than from a copy of the region rules living here. That also
 * settles the ordering hazard the clay pass reported: this runs at HIGH priority and so precedes
 * {@code StructureProtectionHandler}, which means it cannot rely on that handler to protect
 * anything and has to ask the same question itself.
 *
 * <p>After the tool, the Mining skill (skill-progression remediation). The question is answered
 * by {@link MiningBreakGate} — the same server-authoritative gate the ore ladder uses, reading
 * the bed's {@code required_mining} from the one Mining catalogue — so a bed and an ore can never
 * disagree about what a Mining requirement means, and the client's opinion of its own skill is
 * consulted exactly nowhere.
 *
 * <h2>Exhaustion and regeneration</h2>
 * The bed is replaced by whatever fluid occupies its cell — air on a bank, water in a shallow —
 * which is how {@code CustomBlockBreakHandler} empties a worked stone or ore block, and it means
 * a bed dug out under water does not leave a bubble. It is exhausted from that instant: the
 * block is gone, so the next left click resolves no deposit and yields nothing.
 *
 * <p>Coming back is not this class's business. {@code BrokenBlockTracker} records position,
 * state and player in the level's {@code broken_blocks} saved data, and
 * {@code BlockRestoreHandler} puts the bed back six hours later — wall clock, so the delay runs
 * while the server is down, and only into a cell that is still free of blocks and entities. That
 * is the same store, the same timer and the same refusal-to-overwrite the ore veins have always
 * used, and {@code /brokenblocks list} shows a pending clay bed among them.
 */
public final class ManagedDepositExtraction {

    private ManagedDepositExtraction() {
    }

    /** What happened, so callers can explain it rather than guess. */
    public enum Result {
        /** The player worked the deposit and holds its yield. */
        EXTRACTED,
        /**
         * Every check passed but nothing was written: {@link #preflight} answering "this break
         * may proceed". Never returned by {@link #extract}, which commits instead.
         */
        ELIGIBLE,
        /** Not a deposit at all; the caller should leave the world's own rules alone. */
        NOT_A_DEPOSIT,
        /** A deposit, but not with that in hand. */
        WRONG_TOOL,
        /** A deposit standing inside a house this player has no right to change. */
        PROTECTED,
        /**
         * A deposit reached by something that does not earn: a fake player, or anything else the
         * managed extraction policy refuses. Milestone 6.
         */
        DENIED_ACTOR,
        /**
         * A deposit whose Mining requirement this player has not reached. The bed stands, nothing
         * is yielded, no restoration debt is created and no tool wear is charged — a refused
         * extraction never started.
         */
        INSUFFICIENT_SKILL,
        /**
         * The player's authoritative skill data is not loaded, so the requirement cannot be
         * answered. Fail closed, exactly as the ore ladder's break gate does: an unknown skill is
         * refused, never assumed to be enough.
         */
        SKILL_DATA_UNAVAILABLE;

        public boolean extracted() {
            return this == EXTRACTED;
        }

        /** Whether a {@link #preflight} with this answer should let the break proceed. */
        public boolean permitsBreak() {
            return this == ELIGIBLE || this == EXTRACTED;
        }

        /** True when a deposit was involved either way, which is when the caller must intervene. */
        public boolean concernsADeposit() {
            return this != NOT_A_DEPOSIT;
        }
    }

    /**
     * Attempt to work the deposit at {@code pos} with {@code tool}.
     *
     * <p>Deliberately indifferent to game mode. Adventure is how a player normally meets a
     * deposit and the vanilla break is how they work it, but the rule the owner asked for is
     * about the tool and the skill, not the mode, so an operator in survival with the wrong tool
     * is refused for the same reason and with the same message as anybody else.
     */
    public static Result extract(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        ResourceDefinition deposit = ManagedDeposits.resolve(state).orElse(null);
        if (deposit == null) {
            return Result.NOT_A_DEPOSIT;
        }
        Result decision = evaluate(level, pos, state, deposit, player, tool);
        if (decision != Result.ELIGIBLE) {
            return decision;
        }

        // Remembered before it is removed, and from the state that is still there.
        BrokenBlockTracker.recordBrokenBlock(level, pos, state, player.getUUID());
        level.setBlock(pos, Resources.depletedState(deposit, level, pos), 3);

        ItemStack yield = ManagedDeposits.yieldStack(deposit);
        Block.popResource(level, pos, yield);
        ManagedExtractionPolicy.chargeExtractionTool(player);
        player.displayClientMessage(
                Component.translatable("message.britannia_mod.deposit.extracted", yield.getHoverName()), true);
        return Result.EXTRACTED;
    }

    /**
     * The full refusal chain with nothing written: would {@link #extract} commit here, right now?
     *
     * <p>This is what the Adventure left-click consults <em>before</em> the vanilla break is
     * allowed to begin, so an under-skilled or unauthorised digger is told at the first swing
     * rather than after holding the dig to completion. It shares {@link #evaluate} with
     * {@code extract}, so the two answers cannot drift: whatever this permits, the completed
     * break's extraction re-checks with the same code before anything mutates.
     */
    public static Result preflight(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        ResourceDefinition deposit = ManagedDeposits.resolve(state).orElse(null);
        if (deposit == null) {
            return Result.NOT_A_DEPOSIT;
        }
        return evaluate(level, pos, state, deposit, player, tool);
    }

    /**
     * Every question that can refuse, in refusal order, mutating nothing.
     *
     * <p>Actor first (a machine is refused for what it is), then ground (a house refuses whoever
     * is not its owner), then tool ("not with that"), then Mining ("not yet"). The Mining answer
     * comes from {@link MiningBreakGate}, the same server-authoritative gate the ore ladder uses,
     * reading the bed's {@code required_mining} from the one Mining catalogue — the deposit path
     * holds no skill number and no comparison of its own. The gate also owns the denial feedback,
     * so a bed and an ore refuse an under-skilled digger with the identical throttled message.
     */
    private static Result evaluate(ServerLevel level, BlockPos pos, BlockState state,
            ResourceDefinition deposit, ServerPlayer player, ItemStack tool) {
        // The prerequisites every managed destructive path shares, in the order they have always
        // been asked here: a machine is refused for what it is before anything is asked about
        // where it is standing or what it is holding (milestone 6 -- a fake player is a real
        // ServerPlayer, so nothing above this line excludes one), and then the ground is asked
        // whose it is, so an administrator dropping a bed into somebody's house cannot hand
        // strangers a way in.
        //
        // Routed through ManagedBreakAuthorization rather than kept as two local checks: it is
        // the same pair of questions the Mining flow and the wood handler must answer, and this
        // was the path that got them right first. Nothing is written either way -- the caller
        // cancels the break, so the bed is still standing afterwards, with no yield, no
        // restoration debt and no tool wear.
        ManagedBreakAuthorization.Decision common =
                ManagedBreakAuthorization.evaluate(level, pos, player);
        if (!common.allowed()) {
            if (common.reason() == ManagedBreakAuthorization.Reason.HOUSE_PROTECTED) {
                if (common.message() != null) {
                    player.displayClientMessage(Component.literal(common.message()), true);
                }
                return Result.PROTECTED;
            }
            return Result.DENIED_ACTOR;
        }

        if (!ManagedDeposits.isAuthorizedTool(deposit, tool)) {
            player.displayClientMessage(
                    Component.translatable("message.britannia_mod.deposit.wrong_tool"), true);
            return Result.WRONG_TOOL;
        }

        // The skill-progression remediation: the beds are in the Mining catalogue now, so the
        // established break gate answers the skill question — inclusive threshold, fail-closed
        // when skill data is unavailable, admin bypass, provenance. Mining 0.0 against a bed
        // that requires more is INSUFFICIENT_SKILL here, never an extraction. The gate judges
        // the tool THIS method was asked about, not whatever the main hand happens to hold.
        MiningBreakGate.Evaluation gate = MiningBreakGate.evaluate(player, state, level, pos, tool);
        if (!gate.permitsBreak()) {
            MiningBreakGate.sendDenialFeedback(player, gate);
            return switch (gate.type()) {
                case SKILL_DATA_UNAVAILABLE -> Result.SKILL_DATA_UNAVAILABLE;
                // Unreachable after this method's own checks, but never misreported as skill.
                case WRONG_TOOL -> Result.WRONG_TOOL;
                case NON_PLAYER_POLICY -> Result.DENIED_ACTOR;
                default -> Result.INSUFFICIENT_SKILL;
            };
        }
        return Result.ELIGIBLE;
    }
}

package com.seggellion.britannia_mod.deposit;

import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
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
        /** Not a deposit at all; the caller should leave the world's own rules alone. */
        NOT_A_DEPOSIT,
        /** A deposit, but not with that in hand. */
        WRONG_TOOL,
        /** A deposit standing inside a house this player has no right to change. */
        PROTECTED;

        public boolean extracted() {
            return this == EXTRACTED;
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
     * deposit and the left click is how they work it, but the rule the owner asked for is about
     * the tool, not the mode, so an operator in survival with the wrong tool is refused for the
     * same reason and with the same message as anybody else.
     */
    public static Result extract(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        ResourceDefinition deposit = ManagedDeposits.resolve(state).orElse(null);
        if (deposit == null) {
            return Result.NOT_A_DEPOSIT;
        }

        // Whose ground is this? Outside any house the world's own rules apply and this passes;
        // inside one, only the owner may work what stands in it. An administrator dropping a
        // deposit into somebody's house therefore cannot hand strangers a way in.
        HouseBuildRights.Decision house = HouseBuildRights.evaluateBreak(level, pos, player);
        if (!house.permitted()) {
            String refusal = house.message();
            if (refusal != null) {
                player.displayClientMessage(Component.literal(refusal), true);
            }
            return Result.PROTECTED;
        }

        if (!ManagedDeposits.isAuthorizedTool(deposit, tool)) {
            player.displayClientMessage(
                    Component.translatable("message.britannia_mod.deposit.wrong_tool"), true);
            return Result.WRONG_TOOL;
        }

        // Remembered before it is removed, and from the state that is still there.
        BrokenBlockTracker.recordBrokenBlock(level, pos, state, player.getUUID());
        level.setBlock(pos, Resources.depletedState(deposit, level, pos), 3);

        ItemStack yield = ManagedDeposits.yieldStack(deposit);
        Block.popResource(level, pos, yield);
        player.displayClientMessage(
                Component.translatable("message.britannia_mod.deposit.extracted", yield.getHoverName()), true);
        return Result.EXTRACTED;
    }
}

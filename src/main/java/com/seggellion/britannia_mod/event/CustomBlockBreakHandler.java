package com.seggellion.britannia_mod.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.chat.Component;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import com.seggellion.britannia_mod.util.BlockBreakUtils;
import com.seggellion.britannia_mod.util.PickaxeMiningRules;

public class CustomBlockBreakHandler {
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

      if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;


        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ItemStack heldItem = player.getMainHandItem();

        // Mining milestone 7: a mineable the player placed themselves is construction, not a
        // deposit. It breaks with ordinary vanilla behaviour -- no graded/purity drop, no
        // restoration, no Mining award -- which is what closes the place-break loop.
        if (com.seggellion.britannia_mod.mining.MiningProvenance.isPlayerPlaced(serverLevel, pos)) {
            returnPlacedConstruction(serverLevel, pos, state, player);
            return;
        }

        // OreVein milestone 6. Only an actor the extraction policy says may earn performs an
        // economic extraction. Everything else leaves this handler untouched, which means no
        // depletion, no yield, no restoration debt, no skill and no tool wear -- there is no
        // partial transaction to unwind because none is started.
        //
        // Creative is decided by the attacking hand (ManagedExtractionPolicy). Without the
        // Britannia pickaxe a creative player is administering: the gate has already stood aside
        // at HIGH, this handler stands aside here, and the break is an ordinary creative removal
        // that drops nothing -- which is exactly what a creative break of a clay bed has always
        // done. Attacking WITH the Britannia pickaxe a creative player is a tester, the policy
        // answers ALLOWED, and the whole flow below runs for them exactly as for a survival miner:
        // that is how mining is exercised without leaving creative. (Operator permission bypasses
        // nothing anywhere -- see MiningBreakGate; op is administration, not progression.)
        //
        // Fake players are refused here too, though the gate already cancels them upstream. Two
        // independent refusals of automation is the correct amount for the path that mints money.
        if (!ManagedExtractionPolicy.mayExtract(player)) {
            return;
        }

        // The shared prerequisites, asked here rather than assumed from upstream. This handler
        // cancels the event and empties the cell itself, which makes it an authority: it must not
        // depend on MiningGateHandler having run at HIGH and refused first, because a cancelled
        // event never reaches anyone and a priority change would silently reopen the hole that
        // let strangers mine out of other people's houses. Costs one HouseBuildRights lookup on a
        // path that is already about to rewrite the world.
        if (com.seggellion.britannia_mod.resource.extraction.ManagedBreakAuthorization
                .refuses(serverLevel, pos, player)) {
            event.setCanceled(true);
            return;
        }

        boolean isStone = PickaxeMiningRules.isAllowedStoneBlock(state);
        boolean isOre = PickaxeMiningRules.isAllowedOreBlock(state);
        boolean isMineral = PickaxeMiningRules.isAllowedMineralBlock(state);

        if (com.seggellion.britannia_mod.mining.MiningExtractionTool.isAuthorized(state, heldItem)) {
            event.setCanceled(true);

            // The one Mining check for this activation, before anything is mutated. It answers two
            // independent questions -- did the resource come out, and did the skill move -- and the
            // world may only change on the first. A qualified miner who fails the roll has still
            // used the skill and may well have learned from it; what they must not do is deplete a
            // cell, mint an item or enrol restoration debt for an attempt that produced nothing.
            MiningSkill.AttemptResult attempt = MiningSkill.checkMiningAttempt(player, state, pos);
            if (!attempt.extracted()) {
                // The event is already cancelled, so the block stands. Resynchronise: the client
                // has been animating a dig it was fully entitled to attempt, and unlike a gate
                // refusal there was no denial message on the way in to explain the outcome.
                com.seggellion.britannia_mod.mining.MiningBreakGate
                        .synchronizeDeniedBreak(player, serverLevel, pos);
                player.displayClientMessage(
                        Component.translatable("message.britannia_mod.mining.extraction_failed"), true);
                return;
            }

            if (isStone) {
                handleStoneBreaking(serverLevel, pos, state, player);
            } else if (isOre) {
                handleOreBreaking(serverLevel, pos, state, player);
            } else if (isMineral) {
                handleMineralBreaking(serverLevel, pos, state, player);
            } else {
                // Authorized for something this handler does not yield -- a sediment bed reached
                // with its own shovel, which the deposit handler has already taken. Nothing was
                // extracted here, so nothing is charged for it.
                return;
            }

         BrokenBlockTracker.recordBrokenBlock(serverLevel, pos, state, player.getUUID());

            // Milestone 6: the single durability charge, at the end of the one path that commits.
            // One ordinary hurtAndBreak, so Unbreaking behaves here as it does everywhere else.
            ManagedExtractionPolicy.chargeExtractionTool((net.minecraft.server.level.ServerPlayer) player);
        } else if (Resources.resolve(state).isPresent()) {
            // A live managed cell that this handler was not able to take over, because the tool
            // cannot work it. Defence in depth: the gate already answers WRONG_TOOL and cancels at
            // HIGH, and a cancelled event reaches no later listener, so nothing should arrive here
            // now that operator permission no longer bypasses the gate. It stays because the cost
            // of being wrong is unrecoverable -- a priority change, a new bypass, or any future
            // caller that reaches this handler directly would otherwise destroy a sited deposit.
            //
            // Letting the event continue would hand a sited deposit to the vanilla break
            // lifecycle, and these blocks ship no loot table: the cell would disappear, drop
            // nothing, and file no restoration debt, permanently deleting a planned resource that
            // the six-hour restoration would otherwise bring back. The managed pipeline owns the
            // removal of managed cells, so a break it cannot commit is a break that does not
            // happen. Creative removal is unaffected: it returns above, at mayExtract.
            event.setCanceled(true);
        }
    }

    // Milestone 2: the tool question is asked directly of MiningExtractionTool above, which
    // resolves the resource's configured extraction tag. The private isBritanniaPickaxe predicate
    // that used to live here is gone -- it was the second definition of the rule, and there is now
    // exactly one, in data.

    /**
     * Hands back a mineable the player placed themselves.
     *
     * <p>Milestone 7 rules that player-placed construction "breaks with ordinary vanilla
     * behaviour", which is what closes the place-break loop: no purity ore, no restoration debt
     * and no Mining award, so stacking a block and re-breaking it trains nothing. For the vanilla
     * blocks in the catalogue -- stone, deepslate, iron ore -- ordinary vanilla behaviour is
     * already exactly right, and this method leaves them entirely alone.
     *
     * <p>The project's own ore and rock blocks ship no loot table, though, so for them "ordinary
     * vanilla behaviour" silently evaluated to annihilation: a player who placed one and changed
     * their mind lost it outright, which reads in game as "I broke it and got nothing". Returning
     * the block when vanilla would return nothing restores the intended behaviour without
     * reopening the loop, because a block handed back is not a resource extracted -- still no ore,
     * still no skill, still no restoration debt.
     */
    private static void returnPlacedConstruction(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        // Creative keeps vanilla's own no-drop convention, the same one the extraction policy
        // already applies everywhere else on this path.
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || ManagedExtractionPolicy.isCreativeGameMode(serverPlayer)) {
            return;
        }
        // Only compensate where vanilla genuinely yields nothing; anything with a working loot
        // table drops through untouched, so this can never become a second drop.
        if (!Block.getDrops(state, level, pos, level.getBlockEntity(pos), player,
                player.getMainHandItem()).isEmpty()) {
            return;
        }
        ItemStack returned = new ItemStack(state.getBlock().asItem());
        if (returned.isEmpty()) {
            return;
        }
        Block.popResource(level, pos, returned);
        // Say which path this was. A placed block handing itself back looks, in the drop pile,
        // exactly like a managed deposit paying out its BlockItem -- and reading it that way is
        // what sent a live investigation after a "mining now drops ore blocks" defect that did not
        // exist. A managed extraction announces "You mined ... Purity=N"; this announces that
        // nothing was extracted, so the two are never confused again.
        serverPlayer.displayClientMessage(Component.literal(
                "You recovered the " + returned.getHoverName().getString()
                        + " you placed. This is construction, not a deposit: no ore, no Mining."),
                true);
    }

    /** The declared depleted state, falling back to the historical behaviour for unmanaged blocks. */
    private static BlockState depletedState(ServerLevel level, BlockPos pos, BlockState state) {
        return Resources.resolve(state)
                .map(definition -> Resources.depletedState(definition, level, pos))
                .orElseGet(() -> level.getFluidState(pos).createLegacyBlock());
    }

    private void handleStoneBreaking(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        // Milestone 2: the cell becomes the resource's declared depleted state. That is still the
        // fluid-aware air this always wrote -- the behaviour is unchanged -- but it is now the
        // definition saying so rather than this line assuming it.
        level.setBlock(pos, depletedState(level, pos, state), 2);

        String stoneType = BlockBreakUtils.deduceStoneType(state);
        int grade = BlockBreakUtils.generateStoneGrade();

        ItemStack stoneStack = new ItemStack(ItemRegistry.GRADE_STONE_ITEM.get());
        GradeStoneItem stoneItem = (GradeStoneItem) stoneStack.getItem();
        stoneItem.setStoneType(stoneStack, stoneType);
        stoneItem.setGradeValue(stoneStack, grade);

        // Drop custom item
        ItemEntity drop = new ItemEntity(level,
                                         pos.getX() + 0.5,
                                         pos.getY() + 0.5,
                                         pos.getZ() + 0.5,
                                         stoneStack);
        level.addFreshEntity(drop);

        player.displayClientMessage(
            Component.literal(String.format("You mined %s stone. Grade: %s",
                                            stoneType, stoneItem.getGradeName(stoneStack))),
            true
        );
    }

    /**
     * A Mining-governed non-metal: coal today.
     *
     * <p>Unlike stone and ore this mints nothing. The yield is whatever the resource's definition
     * says it is, built by {@link Resources#yieldStack} -- the same call the sediment beds use --
     * so managed coal hands over ordinary {@code minecraft:coal} and every furnace, campfire and
     * torch recipe that already accepts coal keeps working with no registration of ours.
     *
     * <p>The definition is the authority for the yield, not the block being broken and not the
     * vanilla loot table, which is never consulted: the break is cancelled above, so the only way
     * material leaves this cell is the stack built here.
     */
    private void handleMineralBreaking(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        level.setBlock(pos, depletedState(level, pos, state), 2);

        ItemStack yield = Resources.resolve(state)
                .map(Resources::yieldStack)
                .orElse(ItemStack.EMPTY);
        if (yield.isEmpty()) {
            // Reachable only if the catalogue and the block registry disagree, which the catalogue's
            // load-time validation already refuses. Dropping nothing is the safe half of that.
            return;
        }

        level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, yield));

        player.displayClientMessage(
                Component.literal(String.format("You mined %s.", yield.getHoverName().getString())),
                true);
    }

    private void handleOreBreaking(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        level.setBlock(pos, depletedState(level, pos, state), 2);

        String oreType = BlockBreakUtils.deduceOreType(state);
        int purity = BlockBreakUtils.generateRandomPurity();

        ItemStack oreStack = new ItemStack(ItemRegistry.PURITY_ORE_ITEM.get());
        PurityOreItem oreItem = (PurityOreItem) oreStack.getItem();
        oreItem.setOreType(oreStack, oreType);
        oreItem.setPurity(oreStack, purity);

        // Drop custom item
        ItemEntity drop = new ItemEntity(level,
                                         pos.getX() + 0.5,
                                         pos.getY() + 0.5,
                                         pos.getZ() + 0.5,
                                         oreStack);
        level.addFreshEntity(drop);

        player.displayClientMessage(
            Component.literal(String.format("You mined %s ore. Purity=%d", oreType, purity)),
            true
        );
    }

}

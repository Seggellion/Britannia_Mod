package com.seggellion.britannia_mod.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
            return;
        }

        // OreVein milestone 6. Only a real player in a survival-like mode performs an economic
        // extraction. Everything else leaves this handler untouched, which means no depletion, no
        // yield, no restoration debt, no skill and no tool wear -- there is no partial transaction
        // to unwind because none is started.
        //
        // Creative is the case this closes. The Mining gate deliberately permits an operator's
        // break so a misplaced block can be removed, and that permission was arriving here as a
        // fully accounted extraction: an administrator clearing a vein was minting purity ore and
        // enrolling restoration debt in their own name. The break still happens; it is now an
        // ordinary creative removal that drops nothing, which is exactly what a creative break of
        // a clay bed has always done.
        //
        // Fake players are refused here too, though the gate already cancels them upstream. Two
        // independent refusals of automation is the correct amount for the path that mints money.
        if (!ManagedExtractionPolicy.mayExtract(player)) {
            return;
        }

        boolean isStone = PickaxeMiningRules.isAllowedStoneBlock(state);
        boolean isOre = PickaxeMiningRules.isAllowedOreBlock(state);

        if (com.seggellion.britannia_mod.mining.MiningExtractionTool.isAuthorized(state, heldItem)) {
            event.setCanceled(true);

            if (isStone) {
                handleStoneBreaking(serverLevel, pos, state, player);
            } else if (isOre) {
                handleOreBreaking(serverLevel, pos, state, player);
            } else {
                // Authorized for something this handler does not yield -- a sediment bed reached
                // with its own shovel, which the deposit handler has already taken. Nothing was
                // extracted here, so nothing is charged for it.
                return;
            }

         BrokenBlockTracker.recordBrokenBlock(serverLevel, pos, state, player.getUUID());

            // Mining milestone 4: the success boundary of the managed Mining flow -- the resource
            // was extracted and its restoration scheduled -- so this is exactly one qualifying
            // activation. Invalid-tool breaks never reach here (design 9.1 excludes them), and the
            // award itself re-checks the break gate, so bypasses and automation award nothing.
            MiningSkill.awardForBreak(player, state, pos);

            // Milestone 6: the single durability charge, at the end of the one path that commits.
            // One ordinary hurtAndBreak, so Unbreaking behaves here as it does everywhere else.
            ManagedExtractionPolicy.chargeExtractionTool((net.minecraft.server.level.ServerPlayer) player);
        }
    }

    // Milestone 2: the tool question is asked directly of MiningExtractionTool above, which
    // resolves the resource's configured extraction tag. The private isBritanniaPickaxe predicate
    // that used to live here is gone -- it was the second definition of the rule, and there is now
    // exactly one, in data.

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

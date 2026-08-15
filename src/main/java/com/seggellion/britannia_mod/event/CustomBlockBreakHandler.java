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
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.GradeStoneItem;

import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import com.seggellion.britannia_mod.mining.MiningSkill;
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

        boolean isStone = PickaxeMiningRules.isAllowedStoneBlock(state);
        boolean isOre = PickaxeMiningRules.isAllowedOreBlock(state);

        if (isBritanniaPickaxe(heldItem)) {
            event.setCanceled(true);

            if (isStone) {
                handleStoneBreaking(serverLevel, pos, state, player);
            } else if (isOre) {
                handleOreBreaking(serverLevel, pos, state, player);
            } else {
                return;
            }

         BrokenBlockTracker.recordBrokenBlock(serverLevel, pos, state, player.getUUID());

            // Mining milestone 4: the success boundary of the managed Mining flow -- the resource
            // was extracted and its restoration scheduled -- so this is exactly one qualifying
            // activation. Invalid-tool breaks never reach here (design 9.1 excludes them), and the
            // award itself re-checks the break gate, so bypasses and automation award nothing.
            MiningSkill.awardForBreak(player, state, pos);
        }
    }

    private boolean isBritanniaPickaxe(ItemStack stack) {
        return stack.getItem() == ToolRegistry.PICKAXE.get()
                || stack.getItem() instanceof QualityToolItem
                || stack.getItem() instanceof BritanniaPickaxeItem;
    }

    private void handleStoneBreaking(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        // Replace the block with its fluid state
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), 2);

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
        // Replace the block with its fluid state
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), 2);

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

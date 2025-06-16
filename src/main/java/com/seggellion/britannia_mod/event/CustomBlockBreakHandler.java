package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.util.BlockBreakUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import org.slf4j.Logger;

import java.util.UUID;

public class CustomBlockBreakHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

      if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;


        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ItemStack heldItem = player.getMainHandItem();

        boolean isStone = state.is(BlockTags.BASE_STONE_OVERWORLD);
        boolean isOre   = state.is(Blocks.IRON_ORE) ||
                        state.is(Blocks.DEEPSLATE_IRON_ORE) ||
                        state.is(Blocks.GOLD_ORE) ||
                        state.is(BlockRegistry.COPPER_ORE.get()) ||
                        state.is(BlockRegistry.TIN_ORE.get()) ||
                        state.is(BlockRegistry.SILVER_ORE.get()) ||
                        state.is(BlockRegistry.GOLD_ORE.get()) ||
                        state.is(BlockRegistry.SHADOW_IRON_ORE.get()) ||
                        state.is(BlockRegistry.AGAPITE_ORE.get()) ||
                        state.is(BlockRegistry.VERITE_ORE.get()) ||
                        state.is(BlockRegistry.VALORITE_ORE.get()) ||
                        state.is(BlockRegistry.HIGH_PURITY_SILVER_ORE.get());
        boolean isLog   = state.is(BlockTags.LOGS);

        // Using IronPickaxe
        if (heldItem.getItem() == ToolRegistry.PICKAXE.get()) {
            if (isStone) {
                handleStoneBreaking(serverLevel, pos, state, player);
            } else if (isOre) {
                handleOreBreaking(serverLevel, pos, state, player);
            }
            // else: already blocked in onBreakSpeed

            // Example: if you want to record the break:
         BrokenBlockTracker.recordBrokenBlock(serverLevel, pos, state, player.getUUID());
        }
        // Using TwoHandedAxe
        else if (heldItem.getItem() instanceof TwoHandedAxeItem) {
            if (isLog) {
                handleLogBreaking(serverLevel, pos, state, player);
            }
            // else: already blocked in onBreakSpeed
        }
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

    private void handleLogBreaking(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        // Example: do vanilla-like behavior
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        TreeKarmaHandler.treeCutTimestamps.put(playerId, currentTime);
        player.sendSystemMessage(Component.literal("You cut down a tree. Replant a sapling to avoid karma loss!"));
        level.destroyBlock(pos, true, player);
        player.displayClientMessage(
            Component.literal("You chopped some logs with your Two-Handed Axe!"),
            true
        );
    }
}

package com.seggellion.britannia_mod.event;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem;
import com.seggellion.britannia_mod.util.AxeHarvestRules;
import com.seggellion.britannia_mod.util.PickaxeMiningRules;

public class ToolInteractionHandler {
    /**
     * Plays a chopping sound when left-clicking logs with a TwoHandedAxe.
     */
    @SubscribeEvent
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof TwoHandedAxeItem) {
                BlockPos pos = event.getPos();
                Level level = player.getCommandSenderWorld();
                BlockState state = level.getBlockState(pos);

                if (AxeHarvestRules.isAllowedLogBlock(state)) {
                    level.playSound(
                        null,
                        pos,
                        ModSounds.CHOP_TREE.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F
                    );
                }
            }
        }
    }

    /**
     * Restricts which blocks can be broken with specific tools.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockState state = event.getState();
            ItemStack heldItem = player.getMainHandItem();
            Item item = heldItem.getItem();

            // TwoHandedAxe → logs only
            if (item instanceof TwoHandedAxeItem) {
                if (!AxeHarvestRules.isAllowedAxeHarvestBlock(state)) {
                    event.setCanceled(true);
                } else {
                    event.setNewSpeed(AxeHarvestRules.isAllowedLeafBlock(state) ? 4.0F : 2.0F);
                }
            }
            // IronPickaxe → stone/ore only
            else if (item instanceof BritanniaPickaxeItem || item instanceof QualityToolItem) {
                if (!PickaxeMiningRules.isAllowedMineableBlock(state)) {
                    event.setCanceled(true);
                } else {
                    float speed = item instanceof QualityToolItem ? 2.0F + (QualityToolItem.getQuality(heldItem) * 0.5F) : 2.0F;
                    event.setNewSpeed(speed);
                }
            }
        }
    }
}

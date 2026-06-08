package com.seggellion.britannia_mod.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem; 
import com.seggellion.britannia_mod.util.AxeHarvestRules;
import com.seggellion.britannia_mod.util.PickaxeMiningRules;

public class BreakSpeedHandler {
    @SubscribeEvent
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof TwoHandedAxeItem) {
                BlockPos pos = event.getPos();
                Level level = player.getCommandSenderWorld();
                BlockState state = level.getBlockState(pos);

                if (AxeHarvestRules.isAllowedLogBlock(state)) {
                    level.playSound(null, pos, ModSounds.CHOP_TREE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        }
    }

    // [IMPORTANT] Ensure onPlayerTick is DELETED from this file entirely.

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockState state = event.getState();
            ItemStack heldItem = player.getMainHandItem();
            Item item = heldItem.getItem();

            if (item instanceof TwoHandedAxeItem) {
                if (!AxeHarvestRules.isAllowedAxeHarvestBlock(state)) {
                    event.setCanceled(true); 
                } else {
                    event.setNewSpeed(AxeHarvestRules.isAllowedLeafBlock(state) ? 4.0F : 2.0F);
                }
            }
            
            if (item instanceof QualityToolItem || item instanceof BritanniaPickaxeItem) {
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

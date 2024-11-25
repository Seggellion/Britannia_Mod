package com.seggellion.britannia_mod.features;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;


public class MobSpawnControl {
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
               // Remove bows from all Skeletons
        if (entity instanceof Skeleton skeleton) {
            skeleton.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (entity instanceof Creeper || entity instanceof EnderMan || entity instanceof Phantom) {
            event.setCanceled(true);
        }
    }
}

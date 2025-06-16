package com.seggellion.britannia_mod.features;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public class MobSpawnControl {
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        // Remove bows from all Skeletons
        if (entity instanceof Skeleton skeleton) {
            skeleton.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        // Prevent spawning of specified mobs
        if (entity instanceof Creeper ||
            entity instanceof EnderMan ||
            entity instanceof Witch ||
            entity instanceof Phantom ||
            entity instanceof WanderingTrader ||
            entity instanceof TraderLlama) {
            event.setCanceled(true);
        }
    }
}


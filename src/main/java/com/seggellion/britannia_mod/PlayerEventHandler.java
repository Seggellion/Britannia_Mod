package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.magic.Spell;
import com.seggellion.britannia_mod.magic.SpellRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public class PlayerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Cancel physical attack with spell items
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        Spell spell = SpellRegistry.getSpell(itemStack);

        // Cancel physical attack if using a spell item
        if (spell != null) {
            LOGGER.info("Preventing physical damage from spell item.");
            event.setCanceled(true);
        }
    }

    // Handle right-click to apply spell effect to the caster
    @SubscribeEvent
    public void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack itemStack = player.getItemInHand(event.getHand());
        Spell spell = SpellRegistry.getSpell(itemStack);

        if (spell != null && player instanceof ServerPlayer serverPlayer) {
            LOGGER.info("Casting spell on self.");
            if (!spell.castSelf(serverPlayer)) {
                player.sendSystemMessage(Component.literal("Failed to cast spell on self!"));
            }
            event.setCanceled(true); // Prevent default right-click action if spell is cast
        }
    }
}

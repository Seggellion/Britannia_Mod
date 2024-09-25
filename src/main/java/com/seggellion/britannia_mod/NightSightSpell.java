package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component; 
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class NightSightSpell {
    private static final int MANA_COST = 4;
    private static final Logger LOGGER = LogUtils.getLogger();

    // Check if the item is the spell item
    public boolean isSpellItem(ItemStack itemStack) {
        boolean result = itemStack.getItem() == BritanniaMod.NIGHT_SIGHT_ITEM.get();
        LOGGER.info("isSpellItem check: {} for item {}", result, itemStack.getItem());
        return result;
    }

    // Cast spell on self
    public boolean castSelf(ServerPlayer caster) {
        if (hasReagents(caster) && ManaHandler.useMana(caster, MANA_COST)) {
            consumeReagents(caster);  // Consume the reagents
            applyNightSight(caster);
            LOGGER.info("Night Sight spell cast successfully on self");
            return true;
        } else {
            LOGGER.info("Not enough reagents or mana for Night Sight");
            caster.sendSystemMessage(Component.literal("Not enough reagents or mana!"));
        }
        return false;
    }

    // Cast spell on target player
    public boolean castTarget(ServerPlayer caster, Player target) {
        if (hasReagents(caster) && ManaHandler.useMana(caster, MANA_COST)) {
            consumeReagents(caster);  // Consume the reagents
            applyNightSight(target);
            LOGGER.info("Night Sight spell cast successfully on target: {}", target.getName().getString());
            return true;
        } else {
            LOGGER.info("Not enough reagents or mana for Night Sight");
            caster.sendSystemMessage(Component.literal("Not enough reagents or mana!"));
        }
        return false;
    }

    // Apply night sight effect to a player
    private void applyNightSight(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 24000));  // Apply night vision for 20 minutes (day/night cycle)
    }

    // Check if the caster has enough reagents in their inventory
    private boolean hasReagents(Player player) {
        return hasItemInInventory(player, new ItemStack(BritanniaMod.SPIDERS_SILK.get())) 
            && hasItemInInventory(player, new ItemStack(BritanniaMod.SULPHUROUS_ASH.get()));
    }

    // Helper method to check if player has a specific item in their inventory
    private boolean hasItemInInventory(Player player, ItemStack reagent) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == reagent.getItem() && itemStack.getCount() >= 1) {
                return true;
            }
        }
        return false;
    }

    // Consume the required reagents from the player's inventory
    private void consumeReagents(Player player) {
        consumeItem(player, new ItemStack(BritanniaMod.SPIDERS_SILK.get()));
        consumeItem(player, new ItemStack(BritanniaMod.SULPHUROUS_ASH.get()));
        LOGGER.info("Consumed reagents for Night Sight spell");
    }

    // Helper method to remove 1 item from player's inventory
    private void consumeItem(Player player, ItemStack reagent) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == reagent.getItem() && itemStack.getCount() >= 1) {
                itemStack.shrink(1);  // Reduce the item count by 1
                return;
            }
        }
    }
}

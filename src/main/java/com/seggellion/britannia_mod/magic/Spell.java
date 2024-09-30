package com.seggellion.britannia_mod.magic;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public abstract class Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

    protected abstract int getManaCost();
    protected abstract ItemStack[] getReagents();

    public boolean castSelf(ServerPlayer caster) {
        if (hasReagents(caster) && ManaHandler.useMana(caster, getManaCost())) {
            consumeReagents(caster);
            applyEffect(caster);
            LOGGER.info("Spell cast successfully on self");
            return true;
        } else {
            LOGGER.info("Not enough reagents or mana to cast spell");
            caster.sendSystemMessage(Component.literal("Not enough reagents or mana!"));
            return false;
        }
    }

    public boolean castTarget(ServerPlayer caster, Player target) {
        if (hasReagents(caster) && ManaHandler.useMana(caster, getManaCost())) {
            consumeReagents(caster);
            applyEffect(target);
            LOGGER.info("Spell cast successfully on target: {}", target.getName().getString());
            return true;
        } else {
            LOGGER.info("Not enough reagents or mana to cast spell");
            caster.sendSystemMessage(Component.literal("Not enough reagents or mana!"));
            return false;
        }
    }

    protected abstract void applyEffect(Player target);

    private boolean hasReagents(Player player) {
        for (ItemStack reagent : getReagents()) {
            if (!hasItemInInventory(player, reagent)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItemInInventory(Player player, ItemStack reagent) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == reagent.getItem() && itemStack.getCount() >= 1) {
                return true;
            }
        }
        return false;
    }

    private void consumeReagents(Player player) {
        for (ItemStack reagent : getReagents()) {
            consumeItem(player, reagent);
        }
    }

    private void consumeItem(Player player, ItemStack reagent) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == reagent.getItem() && itemStack.getCount() >= 1) {
                itemStack.shrink(1);  // Reduce item count by 1
                return;
            }
        }
    }
}

package com.seggellion.britannia_mod.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public abstract class Spell {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<LivingEntity, Long> cooldowns = new HashMap<>();

    protected abstract int getManaCost();
    protected abstract ItemStack[] getReagents();
    protected abstract int getCooldownTime();
    protected abstract void applyEffect(ServerPlayer caster, LivingEntity target);
    protected abstract void applySelfEffect(ServerPlayer caster);
    protected abstract void applyTargetEffect(ServerPlayer caster, LivingEntity target);

    public boolean castSelf(ServerPlayer caster) {
        if (hasReagents(caster) && ManaHandler.useMana(caster, getManaCost())) {
            consumeReagents(caster);
            applyEffectWithCooldown(caster, caster);  // Pass caster as both the caster and target
            LOGGER.info("Spell cast successfully on self");
            return true;
        } else {
            LOGGER.info("Not enough reagents or mana to cast spell");
            caster.sendSystemMessage(Component.literal("Not enough reagents or mana!"));
            return false;
        }
    }

    public boolean castTarget(ServerPlayer caster, LivingEntity target) {
        if (hasReagents(caster) && ManaHandler.useMana(caster, getManaCost())) {
            consumeReagents(caster);
            applyEffectWithCooldown(caster, target);  // Pass caster and the target
            LOGGER.info("Spell cast successfully on target: {}", target.getName().getString());
            return true;
        } else {
            LOGGER.info("Not enough reagents or mana to cast spell");
            caster.sendSystemMessage(Component.literal("Not enough reagents or mana!"));
            return false;
        }
    }

    private void applyEffectWithCooldown(ServerPlayer caster, LivingEntity target) {
        if (isOnCooldown(target)) {
            if (target instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Spell is on cooldown!"));
            }
            return;
        }
        applyEffect(caster, target);  // Pass both caster and target to applyEffect
        startCooldown(target);
    }

    private boolean isOnCooldown(LivingEntity entity) {
        if (!cooldowns.containsKey(entity)) return false;
        long lastCastTime = cooldowns.get(entity);
        return (System.currentTimeMillis() - lastCastTime) < getCooldownTime();
    }

    private void startCooldown(LivingEntity entity) {
        cooldowns.put(entity, System.currentTimeMillis());
    }

    private boolean hasReagents(ServerPlayer player) {
        for (ItemStack reagent : getReagents()) {
            if (!hasItemInInventory(player, reagent)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItemInInventory(ServerPlayer player, ItemStack reagent) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == reagent.getItem() && itemStack.getCount() >= 1) {
                return true;
            }
        }
        return false;
    }

    private void consumeReagents(ServerPlayer player) {
        for (ItemStack reagent : getReagents()) {
            consumeItem(player, reagent);
        }
    }

    private void consumeItem(ServerPlayer player, ItemStack reagent) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == reagent.getItem() && itemStack.getCount() >= 1) {
                itemStack.shrink(1);
                return;
            }
        }
    }
}

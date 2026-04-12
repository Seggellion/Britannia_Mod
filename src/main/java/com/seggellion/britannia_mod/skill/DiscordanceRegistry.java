package com.seggellion.britannia_mod.systems.skills;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.seggellion.britannia_mod.skill.SkillManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordanceRegistry {
    private static final Map<UUID, UUID> DISCORDED_MOBS = new ConcurrentHashMap<>();
    
    // 1.21 uses ResourceLocations instead of UUIDs for Attribute Modifiers
    private static final ResourceLocation DAMAGE_MOD = ResourceLocation.fromNamespaceAndPath("britannia_mod", "discordance_damage");
    private static final ResourceLocation SPEED_MOD = ResourceLocation.fromNamespaceAndPath("britannia_mod", "discordance_speed");
    private static final ResourceLocation ARMOR_MOD = ResourceLocation.fromNamespaceAndPath("britannia_mod", "discordance_armor");

    public static void apply(Mob mob, ServerPlayer bard, float skill) {
        // Broadsword Meta: ~20% reduction at 100 skill
        double reduction = -(skill / 500.0); // 100 skill = -0.2 (20% reduction)

        applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_MOD, reduction);
        applyModifier(mob, Attributes.MOVEMENT_SPEED, SPEED_MOD, reduction);
        applyModifier(mob, Attributes.ARMOR, ARMOR_MOD, reduction);

        DISCORDED_MOBS.put(mob.getUUID(), bard.getUUID());
    }

    private static void applyModifier(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id); // Clear existing
            // 1.21 Operation change: MULTIPLY_TOTAL -> ADD_MULTIPLIED_TOTAL
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static void tick(Mob mob) {
        UUID bardUuid = DISCORDED_MOBS.get(mob.getUUID());
        if (bardUuid == null) return;

        ServerPlayer bard = (ServerPlayer) mob.getServer().getPlayerList().getPlayer(bardUuid);
        if (bard == null || !bard.isAlive() || mob.distanceToSqr(bard) > Math.pow(PeacemakingSystem.getBardRange(SkillManager.getSkill(bard, "discordance")), 2)) {
            remove(mob);
        }
    }

    public static void remove(Mob mob) {
        if (DISCORDED_MOBS.remove(mob.getUUID()) != null) {
            AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);

            if (damage != null) damage.removeModifier(DAMAGE_MOD);
            if (speed != null) speed.removeModifier(SPEED_MOD);
            if (armor != null) armor.removeModifier(ARMOR_MOD);
        }
    }
}
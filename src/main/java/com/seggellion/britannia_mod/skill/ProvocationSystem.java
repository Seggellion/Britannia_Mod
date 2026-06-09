package com.seggellion.britannia_mod.systems.skills;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProvocationSystem {
    private static final Map<UUID, Mob> PENDING_PROVOCATION = new ConcurrentHashMap<>();

    public static void setPending(ServerPlayer player, Mob firstTarget) {
        PENDING_PROVOCATION.put(player.getUUID(), firstTarget);
    }

    public static Mob getPending(ServerPlayer player) {
        return PENDING_PROVOCATION.get(player.getUUID());
    }

    public static void clearPending(ServerPlayer player) {
        PENDING_PROVOCATION.remove(player.getUUID());
    }

    public static boolean resolveProvocation(ServerPlayer player, Mob first, Mob second, float musicSkill, float provSkill) {
        double musicChance = SkillManager.catchChance(musicSkill);
        boolean musicSuccess = player.getRandom().nextDouble() < musicChance;
        SkillManager.trySkillGain(player, "musicianship", musicSuccess);

        if (!musicSuccess) {
            first.setTarget(player);
            clearPending(player);
            return false; // Return false on music failure
        }

        double provChance = SkillManager.catchChance(provSkill);
        boolean provSuccess = player.getRandom().nextDouble() < provChance;
        SkillManager.trySkillGain(player, "provocation", provSuccess);

        if (provSuccess) {
            first.setTarget(second);
            second.setTarget(first);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        first.getX(), first.getY() + 1, first.getZ(), 5, 0.2, 0.2, 0.2, 0.0);
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        second.getX(), second.getY() + 1, second.getZ(), 5, 0.2, 0.2, 0.2, 0.0);
            }
        } else {
            first.setTarget(player);
            second.setTarget(player);
        }

        clearPending(player);
        return provSuccess; // Return true if provocation succeeded, false if it failed
    }
}
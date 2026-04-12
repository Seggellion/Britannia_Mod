package com.seggellion.britannia_mod.systems.skills;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PeacemakingRegistry {
    private static final Map<UUID, UUID> CALMED_MOBS = new ConcurrentHashMap<>();

    public static void calm(Mob mob, ServerPlayer bard) {
        CALMED_MOBS.put(mob.getUUID(), bard.getUUID());
    }

    public static void breakPeace(Mob mob) {
        if (mob != null && mob.getUUID() != null) {
            CALMED_MOBS.remove(mob.getUUID());
        }
    }

    public static boolean isCalmed(Mob mob) {
        UUID bardUuid = CALMED_MOBS.get(mob.getUUID());
        if (bardUuid == null) return false;

        ServerPlayer bard = (ServerPlayer) mob.getServer().getPlayerList().getPlayer(bardUuid);
        
        if (bard == null || !bard.isAlive()) {
            CALMED_MOBS.remove(mob.getUUID());
            return false;
        }

        float skill = SkillManager.getSkill(bard, "peacemaking");
        double range = PeacemakingSystem.getBardRange(skill);

        if (mob.distanceToSqr(bard) > (range * range)) {
            CALMED_MOBS.remove(mob.getUUID());
            return false;
        }

        return true;
    }
}
package com.seggellion.britannia_mod.systems.skills;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class PeacemakingSystem {

    public static int getBardRange(float skill) {
        return 8 + (int)(skill / 15f);
    }

    // CHANGE: 'void' is now 'boolean'
    public static boolean performAreaPeacemaking(ServerPlayer player, float musicSkill, float peaceSkill) {
        double musicChance = SkillManager.catchChance(musicSkill);
        boolean musicSuccess = player.getRandom().nextDouble() < musicChance;
        SkillManager.trySkillGain(player, "musicianship", musicSuccess);

        if (!musicSuccess) return false;

        double peaceChance = SkillManager.catchChance(peaceSkill);
        boolean peaceSuccess = player.getRandom().nextDouble() < peaceChance;
        SkillManager.trySkillGain(player, "peacemaking", peaceSuccess);

        if (peaceSuccess) {
            int range = getBardRange(peaceSkill);
            AABB area = player.getBoundingBox().inflate(range); // inflate() replaces expand()
            
            List<Mob> entities = player.serverLevel().getEntitiesOfClass(Mob.class, area, e -> true);

            for (Mob mob : entities) {
                mob.setTarget(null);
                PeacemakingRegistry.calm(mob, player);
                if (mob.getLastHurtByMob() != null) { // getAttacker() -> getLastHurtByMob()
                    mob.setLastHurtByMob(null);
                }
            }
            return true; 
        }
        return false;
    }
}
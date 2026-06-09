package com.seggellion.britannia_mod.systems.skills;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

public class DiscordanceSystem {

    public static void performDiscordance(ServerPlayer player, Mob target, float musicSkill, float discordSkill) {
        double musicChance = SkillManager.catchChance(musicSkill);
        boolean musicSuccess = player.getRandom().nextDouble() < musicChance;
        SkillManager.trySkillGain(player, "musicianship", musicSuccess);

        if (!musicSuccess) {
            target.setTarget(player);
            return;
        }

        double discordChance = SkillManager.catchChance(discordSkill);
        boolean discordSuccess = player.getRandom().nextDouble() < discordChance;
        SkillManager.trySkillGain(player, "discordance", discordSuccess);

        if (discordSuccess) {
            DiscordanceRegistry.apply(target, player, discordSkill);

            // 1.21 Particle handling
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF800080),
                        target.getX(), target.getY() + 1, target.getZ(), 15, 0.5, 0.5, 0.5, 0.0);
            }
        } else {
            target.setTarget(player);
        }
    }
}
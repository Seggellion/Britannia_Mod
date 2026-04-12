package com.seggellion.britannia_mod.systems.skills;

import com.seggellion.britannia_mod.item.InstrumentItem;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = "britannia_mod")
public class BardEvents {

    @SubscribeEvent
    public static void onPlayerAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack stack = serverPlayer.getMainHandItem();
            
            // Sneaking + Holding Instrument + Targeting a Mob
            if (serverPlayer.isCrouching() && stack.getItem() instanceof InstrumentItem instrument) {
                if (event.getTarget() instanceof Mob targetedMob) {
                    
                    float musicSkill = SkillManager.getSkill(serverPlayer, "musicianship");
                    float discordSkill = SkillManager.getSkill(serverPlayer, "discordance");

                    // 1. Musicianship Check
                    double musicChance = SkillManager.catchChance(musicSkill);
                    boolean musicSuccess = serverPlayer.getRandom().nextDouble() < musicChance;
                    SkillManager.trySkillGain(serverPlayer, "musicianship", musicSuccess);

                    if (musicSuccess) {
                        // 2. Perform Discordance
                        DiscordanceSystem.performDiscordance(serverPlayer, targetedMob, musicSkill, discordSkill);
                        instrument.playInstrumentSound(serverPlayer.level(), serverPlayer, true);
                    } else {
                        instrument.sendGray(serverPlayer, "You play poorly and disturb the peace.");
                        instrument.playInstrumentSound(serverPlayer.level(), serverPlayer, false);
                    }

                    // Standard 2-second cooldown
                    serverPlayer.getCooldowns().addCooldown(instrument, 40);

                    // Cancel the event so the player doesn't actually punch/damage the mob
                    event.setCanceled(true);
                }
            }
        }
    }
}
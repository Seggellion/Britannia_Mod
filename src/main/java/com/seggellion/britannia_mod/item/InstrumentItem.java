package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.systems.skills.PeacemakingSystem;
import com.seggellion.britannia_mod.systems.skills.ProvocationSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Optional;

public class InstrumentItem extends Item {

    private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final TextColor GRAY_848484 = TextColor.fromRgb(0x848484);

    private final DeferredHolder<SoundEvent, SoundEvent> successSound;
    private final DeferredHolder<SoundEvent, SoundEvent> failSound;

    public InstrumentItem(Properties properties,
                          DeferredHolder<SoundEvent, SoundEvent> successSound,
                          DeferredHolder<SoundEvent, SoundEvent> failSound) {
        super(properties);
        this.successSound = successSound;
        this.failSound = failSound;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            float musicSkill = SkillManager.getSkill(serverPlayer, "musicianship");
            Mob targetedMob = getTargetedMob(serverPlayer);

            // --- BRANCH 1: PROVOCATION (Sneak + Right Click Target) ---
            if (targetedMob != null && serverPlayer.isCrouching()) { // isSneaking() -> isCrouching() in Mojmap
                Mob firstMob = ProvocationSystem.getPending(serverPlayer);

                if (firstMob == null) {
                    ProvocationSystem.setPending(serverPlayer, targetedMob);
                    sendGray(serverPlayer, "Incite whom to fight?");
                    playInstrumentSound(level, serverPlayer, true);
                    serverPlayer.getCooldowns().addCooldown(this, 20);
                } else {
                    if (firstMob == targetedMob || !firstMob.isAlive()) {
                        sendGray(serverPlayer, "You cannot incite a creature against itself.");
                        ProvocationSystem.clearPending(serverPlayer);
                    } else {
                        float provSkill = SkillManager.getSkill(serverPlayer, "provocation");
                        ProvocationSystem.resolveProvocation(serverPlayer, firstMob, targetedMob, musicSkill, provSkill);
                        playInstrumentSound(level, serverPlayer, true);
                        serverPlayer.getCooldowns().addCooldown(this, 40);
                    }
                }
                return InteractionResultHolder.success(stack);
            }

            // --- BRANCH 2: PEACEMAKING (Right Click Target - No Sneak) ---
            if (targetedMob != null && !serverPlayer.isCrouching()) {
                float peaceSkill = SkillManager.getSkill(serverPlayer, "peacemaking");
                
                // Capture the boolean result
                boolean peaceSuccess = PeacemakingSystem.performAreaPeacemaking(serverPlayer, musicSkill, peaceSkill);
                
                if (peaceSuccess) {
                    playInstrumentSound(level, serverPlayer, true);
                    sendGray(serverPlayer, "You play a calming melody."); // Optional success message
                } else {
                    playInstrumentSound(level, serverPlayer, false);
                    sendGray(serverPlayer, "You attempt to calm everyone, but fail.");
                }
                
                serverPlayer.getCooldowns().addCooldown(this, 40);
                return InteractionResultHolder.success(stack);
            }

            // --- BRANCH 3: MUSICIANSHIP (Right Click Air) ---
            if (targetedMob == null) {
                double chance = SkillManager.catchChance(musicSkill);
                boolean success = level.random.nextDouble() < chance;
                SkillManager.trySkillGain(serverPlayer, "musicianship", success);

                sendGray(serverPlayer, success ? "You play a lovely melody." : "You play poorly and disturb the peace.");
                playInstrumentSound(level, serverPlayer, success);
                serverPlayer.getCooldowns().addCooldown(this, 40);
                return InteractionResultHolder.success(stack);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private Mob getTargetedMob(ServerPlayer player) {
        double reach = 15.0;
        Vec3 cameraPos = player.getEyePosition();
        Vec3 rotation = player.getViewVector(1.0F);
        Vec3 endPos = cameraPos.add(rotation.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(rotation.scale(reach)).inflate(1.0D);

        for (Entity entity : player.level().getEntities(player, box)) {
            if (entity instanceof Mob mob) {
                AABB entityBox = mob.getBoundingBox().inflate(mob.getPickRadius());
                Optional<Vec3> hit = entityBox.clip(cameraPos, endPos);
                if (hit.isPresent()) return mob;
            }
        }
        return null;
    }

    public void playInstrumentSound(Level level, ServerPlayer player, boolean success) {
        SoundEvent soundEvent = success ? successSound.get() : failSound.get();
        float pitch = success ? 1.0F : 0.8F;
        level.playSound(null, player.blockPosition(), soundEvent, SoundSource.PLAYERS, 1.0F, pitch);
    }

    public void sendGray(Player player, String msg) {
        Style style = Style.EMPTY.withFont(FONT_UO_CLASSIC).withColor(GRAY_848484);
        player.sendSystemMessage(Component.literal(msg).withStyle(style));
    }
}
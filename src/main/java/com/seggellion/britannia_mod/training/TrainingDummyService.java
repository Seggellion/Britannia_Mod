package com.seggellion.britannia_mod.training;

import com.seggellion.britannia_mod.skill.SkillManager;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative cooldown and skill-gain boundary for an accepted dummy strike. */
public final class TrainingDummyService {
    public static final float WEAPON_SKILL_CAP = 25.0F;
    public static final int COOLDOWN_TICKS = 60;
    public static final double TACTICS_GAIN_CHANCE = 0.10D;
    public static final String TACTICS_SKILL_SLUG = "tactics";
    static final String NEXT_ALLOWED_TICK_TAG = "britannia_mod:training_dummy_next_tick";

    private TrainingDummyService() {
    }

    public static AttemptResult attempt(ServerPlayer player, ItemStack mainHandWeapon) {
        if (player == null || player.isSpectator()) {
            return AttemptResult.rejected(Rejection.INVALID_PLAYER);
        }
        Optional<TrainingWeaponSkill> classification = TrainingWeaponClassifier.classify(mainHandWeapon);
        if (classification.isEmpty()) {
            return AttemptResult.rejected(Rejection.UNSUPPORTED_WEAPON);
        }

        long now = player.server.overworld().getGameTime();
        CompoundTag persistentData = player.getPersistentData();
        long nextAllowed = persistentData.getLong(NEXT_ALLOWED_TICK_TAG);
        if (!isReady(now, nextAllowed)) {
            return AttemptResult.rejected(Rejection.COOLDOWN);
        }
        persistentData.putLong(NEXT_ALLOWED_TICK_TAG, nextAllowedTick(now));

        TrainingWeaponSkill weaponSkill = classification.orElseThrow();
        float weaponGain = SkillManager.trySkillGainCapped(
                player, weaponSkill.skillSlug(), true, WEAPON_SKILL_CAP);
        boolean tacticsAttempted = player.getRandom().nextDouble() < TACTICS_GAIN_CHANCE;
        if (tacticsAttempted) {
            SkillManager.trySkillGain(player, TACTICS_SKILL_SLUG, true);
        }
        return AttemptResult.accepted(weaponSkill, weaponGain, tacticsAttempted);
    }

    public static boolean isReady(long now, long nextAllowed) {
        return now >= nextAllowed;
    }

    public static long nextAllowedTick(long now) {
        return now + COOLDOWN_TICKS;
    }

    public enum Rejection { NONE, INVALID_PLAYER, UNSUPPORTED_WEAPON, COOLDOWN }

    public record AttemptResult(
            boolean accepted,
            Rejection rejection,
            TrainingWeaponSkill weaponSkill,
            float weaponGain,
            boolean tacticsAttempted) {
        static AttemptResult accepted(
                TrainingWeaponSkill skill, float weaponGain, boolean tacticsAttempted) {
            return new AttemptResult(true, Rejection.NONE, skill, weaponGain, tacticsAttempted);
        }

        static AttemptResult rejected(Rejection rejection) {
            return new AttemptResult(false, rejection, null, 0.0F, false);
        }
    }
}

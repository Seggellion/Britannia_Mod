package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmingSkill {
    public static final String SKILL_ID = "farming";
    private static final float BASE_GAIN = 0.0143f;
    private static final Map<UUID, FarmingSkillData> DATA = new ConcurrentHashMap<>();

    private FarmingSkill() {
    }

    public static float award(ServerPlayer player, FarmingActionType actionType, int cropTier, float extraModifier) {
        if (player == null) {
            return 0.0f;
        }

        float current = SkillManager.getSkill(player, SKILL_ID);
        if (current >= 100.0f) {
            return 0.0f;
        }

        FarmingSkillData data = DATA.computeIfAbsent(player.getUUID(), id -> new FarmingSkillData());
        data.record(actionType);

        float gain = BASE_GAIN
                * difficultyModifier(current)
                * cropTierModifier(cropTier)
                * data.diversityModifier()
                * data.repetitionModifier()
                * Math.max(0.0f, extraModifier);

        return SkillManager.awardSkillGain(player, SKILL_ID, gain);
    }

    public static String titleFor(float value) {
        if (value >= 100.0f) return "Grandmaster Farmer";
        if (value >= 80.0f) return "Master Farmer";
        if (value >= 60.0f) return "Agriculturist";
        if (value >= 40.0f) return "Horticulturist";
        if (value >= 20.0f) return "Grower";
        return "Farmhand";
    }

    private static float difficultyModifier(float current) {
        if (current >= 100.0f) return 0.0f;
        if (current >= 90.0f) return 0.25f;
        if (current >= 75.0f) return 0.50f;
        if (current >= 50.0f) return 0.75f;
        return 1.0f;
    }

    private static float cropTierModifier(int tier) {
        return switch (tier) {
            case 2 -> 1.10f;
            case 3 -> 1.20f;
            case 4 -> 1.35f;
            default -> 1.0f;
        };
    }

    private static final class FarmingSkillData {
        private FarmingActionType lastAction;
        private int consecutiveActions;
        private final Deque<FarmingActionType> recentActions = new ArrayDeque<>();

        private void record(FarmingActionType actionType) {
            if (lastAction == actionType) {
                consecutiveActions++;
            } else {
                lastAction = actionType;
                consecutiveActions = 0;
            }

            recentActions.addLast(actionType);
            while (recentActions.size() > 5) {
                recentActions.removeFirst();
            }
        }

        private float repetitionModifier() {
            return (float) Math.pow(0.95d, consecutiveActions);
        }

        private float diversityModifier() {
            return recentActions.size() == 5 && EnumSet.copyOf(recentActions).size() == 5 ? 1.25f : 1.0f;
        }
    }
}

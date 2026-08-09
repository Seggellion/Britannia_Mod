package com.seggellion.britannia_mod.farming;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

/** Server-authoritative Farming eligibility policy for new planting transactions. */
public final class FarmingCultivationGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String GRAPE_SPECIES_ID = "grapes";

    private FarmingCultivationGate() {
    }

    public enum ResultType {
        ELIGIBLE,
        INSUFFICIENT_SKILL,
        NOT_APPLICABLE,
        UNRESOLVED_SPECIES,
        APPROVED_BYPASS,
        NON_PLAYER_POLICY,
        SKILL_DATA_UNAVAILABLE
    }

    public enum MaterialCategory {
        CROP_SEEDS("message.britannia_mod.farming.cultivation.insufficient.crop"),
        FLOWER_SEEDS("message.britannia_mod.farming.cultivation.insufficient.flower"),
        PLANTING_MATERIAL("message.britannia_mod.farming.cultivation.insufficient.material");

        private final String insufficientSkillKey;

        MaterialCategory(String insufficientSkillKey) {
            this.insufficientSkillKey = insufficientSkillKey;
        }

        public String insufficientSkillKey() {
            return insufficientSkillKey;
        }
    }

    enum ActorType {
        PLAYER,
        AUTOMATION,
        NON_PLAYER
    }

    record Subject(
            ActorType actorType,
            boolean creativeMode,
            int permissionLevel,
            SkillManager.SkillDataState skillDataState,
            float farmingSkill
    ) {
        Subject {
            Objects.requireNonNull(actorType, "Cultivation actor type is required");
            Objects.requireNonNull(skillDataState, "Farming skill-data state is required");
        }

        static Subject loadedPlayer(float farmingSkill) {
            return new Subject(ActorType.PLAYER, false, 0,
                    SkillManager.SkillDataState.AVAILABLE, farmingSkill);
        }
    }

    public record Evaluation(
            ResultType type,
            Optional<FarmingSkillRequirementResolver.ResolvedRequirement> resolvedRequirement,
            MaterialCategory materialCategory,
            float currentFarmingSkill,
            float requiredFarmingSkill
    ) {
        public Evaluation {
            Objects.requireNonNull(type, "Cultivation result type is required");
            resolvedRequirement = Objects.requireNonNull(resolvedRequirement, "Resolved requirement optional is required");
            Objects.requireNonNull(materialCategory, "Cultivation material category is required");
        }

        public boolean permitsPlanting() {
            return type == ResultType.ELIGIBLE
                    || type == ResultType.APPROVED_BYPASS
                    || type == ResultType.NOT_APPLICABLE;
        }

        public String feedbackTranslationKey() {
            return switch (type) {
                case INSUFFICIENT_SKILL -> materialCategory.insufficientSkillKey();
                case SKILL_DATA_UNAVAILABLE -> "message.britannia_mod.farming.cultivation.skill_unavailable";
                case NON_PLAYER_POLICY -> "message.britannia_mod.farming.cultivation.automation_blocked";
                case UNRESOLVED_SPECIES -> "message.britannia_mod.farming.cultivation.unresolved";
                default -> "";
            };
        }
    }

    public static Evaluation evaluate(@Nullable Player actor, Item plantingItem) {
        FarmingSkillRequirementResolver.ResolvedRequirement resolved;
        try {
            resolved = FarmingSkillRequirementResolver.resolve(plantingItem).orElse(null);
        } catch (RuntimeException exception) {
            LOGGER.error("Rejected planting because its Farming species mapping could not be resolved", exception);
            resolved = null;
        }
        if (resolved == null) {
            return new Evaluation(ResultType.UNRESOLVED_SPECIES, Optional.empty(),
                    MaterialCategory.PLANTING_MATERIAL, Float.NaN, Float.NaN);
        }
        return evaluateResolved(resolved, subject(actor), plantingItem);
    }

    static Evaluation evaluateResolved(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            Subject subject
    ) {
        return evaluateResolved(resolved, subject, null);
    }

    static Evaluation evaluateResolved(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            Subject subject,
            @Nullable Item plantingItem
    ) {
        Objects.requireNonNull(resolved, "Resolved Farming requirement is required");
        Objects.requireNonNull(subject, "Cultivation subject is required");
        MaterialCategory material = materialCategory(resolved, plantingItem);
        float required = resolved.minimumFarmingSkill();
        Optional<FarmingSkillRequirementResolver.ResolvedRequirement> resolution = Optional.of(resolved);

        if (GRAPE_SPECIES_ID.equals(resolved.speciesId())) {
            return new Evaluation(ResultType.NOT_APPLICABLE, resolution, material,
                    subject.farmingSkill(), required);
        }
        if (subject.actorType() != ActorType.PLAYER) {
            return new Evaluation(ResultType.NON_PLAYER_POLICY, resolution, material,
                    subject.farmingSkill(), required);
        }
        if (FlowerProtectionService.isAdministrator(subject.creativeMode(), subject.permissionLevel())) {
            return new Evaluation(ResultType.APPROVED_BYPASS, resolution, material,
                    subject.farmingSkill(), required);
        }
        if (subject.skillDataState() != SkillManager.SkillDataState.AVAILABLE) {
            return new Evaluation(ResultType.SKILL_DATA_UNAVAILABLE, resolution, material,
                    Float.NaN, required);
        }
        ResultType type = subject.farmingSkill() >= required
                ? ResultType.ELIGIBLE
                : ResultType.INSUFFICIENT_SKILL;
        return new Evaluation(type, resolution, material, subject.farmingSkill(), required);
    }

    private static MaterialCategory materialCategory(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            @Nullable Item plantingItem
    ) {
        if (resolved.requirement() instanceof FlowerDefinition) {
            return MaterialCategory.FLOWER_SEEDS;
        }
        if (plantingItem == Items.POTATO
                || plantingItem == Items.BROWN_MUSHROOM
                || plantingItem == Items.RED_MUSHROOM) {
            return MaterialCategory.PLANTING_MATERIAL;
        }
        return MaterialCategory.CROP_SEEDS;
    }

    static Subject subject(@Nullable Player actor) {
        if (!(actor instanceof ServerPlayer serverPlayer)) {
            return new Subject(ActorType.NON_PLAYER, false, 0,
                    SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        }
        if (serverPlayer instanceof FakePlayer) {
            return new Subject(ActorType.AUTOMATION, serverPlayer.isCreative(),
                    FlowerProtectionService.effectivePermissionLevel(serverPlayer),
                    SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        }
        SkillManager.SkillSnapshot snapshot = SkillManager.getSkillSnapshot(
                serverPlayer.getUUID(), FarmingSkill.SKILL_ID
        );
        return new Subject(ActorType.PLAYER, serverPlayer.isCreative(),
                FlowerProtectionService.effectivePermissionLevel(serverPlayer),
                snapshot.state(), snapshot.value());
    }

    public static void sendDenialFeedback(@Nullable Player actor, Evaluation evaluation) {
        Objects.requireNonNull(evaluation, "Cultivation evaluation is required");
        if (!(actor instanceof ServerPlayer serverPlayer) || evaluation.permitsPlanting()) {
            return;
        }
        Component message = evaluation.type() == ResultType.INSUFFICIENT_SKILL
                ? Component.translatable(
                        evaluation.feedbackTranslationKey(),
                        formatSkill(evaluation.currentFarmingSkill()),
                        formatSkill(evaluation.requiredFarmingSkill())
                )
                : Component.translatable(evaluation.feedbackTranslationKey());
        serverPlayer.displayClientMessage(message.copy().withStyle(ChatFormatting.YELLOW), true);
    }

    public static void synchronizeDeniedInteraction(@Nullable Player actor, Level level, BlockPos pos) {
        if (!(actor instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.getInventory().setChanged();
        serverPlayer.containerMenu.broadcastChanges();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
    }

    static String formatSkill(float value) {
        if (!Float.isFinite(value)) {
            return "?";
        }
        return value == Math.rint(value)
                ? Integer.toString((int) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

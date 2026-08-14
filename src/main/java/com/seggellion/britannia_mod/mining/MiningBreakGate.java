package com.seggellion.britannia_mod.mining;

import com.seggellion.britannia_mod.farming.FlowerProtectionService;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-authoritative Mining eligibility policy for block-break attempts.
 *
 * <p>Mining milestone 3. Deliberately shaped after {@code FarmingCultivationGate}, the
 * repository-standard skill gate: a pure, unit-testable decision core ({@link #evaluateResolved})
 * fed by a {@link Subject} snapshot of authoritative server state, with actor typing, the
 * repo-standard admin bypass, and the skill-data-unavailable denial policy. The client never
 * supplies a skill value anywhere on this path.
 *
 * <p>Decision order mirrors Farming: resolution → actor policy → admin bypass → data
 * availability → inclusive threshold ({@code current >= required}, design §10.2).
 */
public final class MiningBreakGate {

    /** Slug of the Mining skill row Rails already seeds (UoSkillRoster). */
    public static final String SKILL_ID = "mining";

    private MiningBreakGate() {
    }

    public enum ResultType {
        ELIGIBLE,
        INSUFFICIENT_SKILL,
        NOT_APPLICABLE,
        APPROVED_BYPASS,
        NON_PLAYER_POLICY,
        SKILL_DATA_UNAVAILABLE
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
            float miningSkill
    ) {
        Subject {
            Objects.requireNonNull(actorType, "Mining actor type is required");
            Objects.requireNonNull(skillDataState, "Mining skill-data state is required");
        }

        static Subject loadedPlayer(float miningSkill) {
            return new Subject(ActorType.PLAYER, false, 0,
                    SkillManager.SkillDataState.AVAILABLE, miningSkill);
        }
    }

    public record Evaluation(
            ResultType type,
            Optional<MineableDefinition> definition,
            float currentMining,
            float requiredMining
    ) {
        public Evaluation {
            Objects.requireNonNull(type, "Mining result type is required");
            definition = Objects.requireNonNull(definition, "Mineable definition optional is required");
        }

        public boolean permitsBreak() {
            return type == ResultType.ELIGIBLE
                    || type == ResultType.APPROVED_BYPASS
                    || type == ResultType.NOT_APPLICABLE;
        }

        public String feedbackTranslationKey() {
            return switch (type) {
                case INSUFFICIENT_SKILL -> "message.britannia_mod.mining.insufficient";
                case SKILL_DATA_UNAVAILABLE -> "message.britannia_mod.mining.skill_unavailable";
                case NON_PLAYER_POLICY -> "message.britannia_mod.mining.automation_blocked";
                default -> "";
            };
        }
    }

    /** Full server-side evaluation of one break attempt against the live catalogue. */
    public static Evaluation evaluate(@Nullable Player actor, BlockState state) {
        return evaluateResolved(Mineables.resolve(state), subject(actor));
    }

    /** Pure decision core; package-visible so unit tests drive it without Minecraft bootstrap. */
    static Evaluation evaluateResolved(Optional<MineableDefinition> resolved, Subject subject) {
        Objects.requireNonNull(subject, "Mining subject is required");
        if (resolved.isEmpty()) {
            return new Evaluation(ResultType.NOT_APPLICABLE, Optional.empty(), Float.NaN, Float.NaN);
        }
        float required = resolved.get().requiredMining();
        if (subject.actorType() != ActorType.PLAYER) {
            return new Evaluation(ResultType.NON_PLAYER_POLICY, resolved, subject.miningSkill(), required);
        }
        if (FlowerProtectionService.isAdministrator(subject.creativeMode(), subject.permissionLevel())) {
            return new Evaluation(ResultType.APPROVED_BYPASS, resolved, subject.miningSkill(), required);
        }
        if (subject.skillDataState() != SkillManager.SkillDataState.AVAILABLE) {
            return new Evaluation(ResultType.SKILL_DATA_UNAVAILABLE, resolved, Float.NaN, required);
        }
        ResultType type = subject.miningSkill() >= required
                ? ResultType.ELIGIBLE
                : ResultType.INSUFFICIENT_SKILL;
        return new Evaluation(type, resolved, subject.miningSkill(), required);
    }

    static Subject subject(@Nullable Player actor) {
        if (!(actor instanceof ServerPlayer serverPlayer)) {
            return new Subject(ActorType.NON_PLAYER, false, 0,
                    SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        }
        if (serverPlayer instanceof FakePlayer) {
            return new Subject(ActorType.AUTOMATION, isCreativeGameMode(serverPlayer),
                    FlowerProtectionService.effectivePermissionLevel(serverPlayer),
                    SkillManager.SkillDataState.NOT_LOADED, Float.NaN);
        }
        SkillManager.SkillSnapshot snapshot = SkillManager.getSkillSnapshot(
                serverPlayer.getUUID(), SKILL_ID);
        return new Subject(ActorType.PLAYER, isCreativeGameMode(serverPlayer),
                FlowerProtectionService.effectivePermissionLevel(serverPlayer),
                snapshot.state(), snapshot.value());
    }

    /**
     * The server-side game mode is the authority for the creative bypass, matching
     * {@code StructureProtectionHandler}'s own break-path convention. Deliberately NOT
     * {@link Player#isCreative()}: that is an overridable derived view — GameTestHelper's mock
     * players hard-code it to {@code true} whatever their real game mode — while
     * {@code gameMode.getGameModeForPlayer()} is the same state the vanilla break pipeline itself
     * consults.
     */
    private static boolean isCreativeGameMode(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    public static void sendDenialFeedback(@Nullable Player actor, Evaluation evaluation) {
        Objects.requireNonNull(evaluation, "Mining evaluation is required");
        if (!(actor instanceof ServerPlayer serverPlayer) || evaluation.permitsBreak()) {
            return;
        }
        Component message = evaluation.type() == ResultType.INSUFFICIENT_SKILL
                ? Component.translatable(
                        evaluation.feedbackTranslationKey(),
                        formatSkill(evaluation.currentMining()),
                        formatSkill(evaluation.requiredMining()),
                        evaluation.definition().map(MineableDefinition::displayName).orElse("?"))
                : Component.translatable(evaluation.feedbackTranslationKey());
        serverPlayer.displayClientMessage(message.copy().withStyle(ChatFormatting.YELLOW), true);
    }

    /** Farming's denied-interaction resync: keep the denying client's world view honest. */
    public static void synchronizeDeniedBreak(@Nullable Player actor, Level level, BlockPos pos) {
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

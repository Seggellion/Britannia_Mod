package com.seggellion.britannia_mod.mining;

import com.seggellion.britannia_mod.farming.FlowerProtectionService;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-authoritative Mining eligibility policy for block-break attempts.
 *
 * <p>Mining milestone 3. Deliberately shaped after {@code FarmingCultivationGate}, the
 * repository-standard skill gate: a pure, unit-testable decision core ({@link #evaluateResolved})
 * fed by a {@link Subject} snapshot of authoritative server state, with actor typing, the
 * creative bypass, and the skill-data-unavailable denial policy. The client never supplies a skill
 * value anywhere on this path.
 *
 * <p>Decision order mirrors Farming: resolution → actor policy → <b>creative</b> bypass →
 * <b>extraction tool</b> → data availability → inclusive threshold ({@code current >= required},
 * design §10.2). The bypass is creative-mode only, and only for a creative player who is
 * <em>not</em> attacking with the Britannia pickaxe — see {@link ManagedExtractionPolicy}, whose
 * rule this is. Operator permission is an administrative capability and never stands in for
 * gameplay progression.
 *
 * <p>The tool step is milestone 1 of the OreVein remediation. Without it the gate answered "yes"
 * to any sufficiently skilled player whatever they held, and the block then fell through to
 * vanilla breaking because {@code CustomBlockBreakHandler} only acts for a project pickaxe — which
 * destroyed the resource outside the managed transaction.
 *
 * <p>Milestone 2 made that step data-driven. The answer comes from the resource definition's
 * configured item tag through {@link MiningExtractionTool}, not from a class check, so this gate
 * and the yield handler cannot hold different opinions and neither holds an opinion of its own.
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
        /**
         * A creative player not attacking with the Britannia pickaxe. The break is permitted and
         * is not an extraction: the handlers stand aside for it before they ever ask this gate,
         * and this answer is what the gate says if anything asks it anyway — the diagnostic
         * command, the skill award (which treats it as no attempt), the deposit service.
         */
        APPROVED_BYPASS,
        NON_PLAYER_POLICY,
        SKILL_DATA_UNAVAILABLE,
        /**
         * A managed resource reached with something that cannot work it (milestone 1). Denied
         * whatever the skill: skill answers "may you", the tool answers "with that", and the
         * second question was previously never asked on this path.
         */
        WRONG_TOOL
    }

    enum ActorType {
        PLAYER,
        AUTOMATION,
        NON_PLAYER
    }

    /**
     * Snapshot of the facts the decision is made over. {@code attacksWithBritanniaPickaxe} is the
     * registered-identity fact behind the creative bypass; {@code authorizedTool} is the separate,
     * per-resource tag fact behind WRONG_TOOL. They agree for the ore ladder today (the tag holds
     * exactly that one item) and differ for the sediment beds, which the pickaxe is not authorised
     * for — so a creative tester with the pickaxe on a clay bed is held to WRONG_TOOL like anyone.
     */
    record Subject(
            ActorType actorType,
            boolean creativeMode,
            int permissionLevel,
            SkillManager.SkillDataState skillDataState,
            float miningSkill,
            boolean authorizedTool,
            boolean attacksWithBritanniaPickaxe
    ) {
        Subject {
            Objects.requireNonNull(actorType, "Mining actor type is required");
            Objects.requireNonNull(skillDataState, "Mining skill-data state is required");
        }

        /** A loaded survival player holding the Britannia pickaxe: the subject skill questions are about. */
        static Subject loadedPlayer(float miningSkill) {
            return new Subject(ActorType.PLAYER, false, 0,
                    SkillManager.SkillDataState.AVAILABLE, miningSkill, true, true);
        }

        /** The same player with something that cannot work the resource. */
        static Subject loadedPlayerWithWrongTool(float miningSkill) {
            return new Subject(ActorType.PLAYER, false, 0,
                    SkillManager.SkillDataState.AVAILABLE, miningSkill, false, false);
        }

        /** Whether the creative bypass is in effect for this subject — one rule, owned elsewhere. */
        boolean creativeBypass() {
            return ManagedExtractionPolicy.creativeBypasses(creativeMode, attacksWithBritanniaPickaxe);
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
                case WRONG_TOOL -> "message.britannia_mod.mining.wrong_tool";
                default -> "";
            };
        }
    }

    /** Full server-side evaluation of one break attempt against the live catalogue. */
    public static Evaluation evaluate(@Nullable Player actor, BlockState state) {
        return evaluate(actor, state, null, null);
    }

    /**
     * Position-aware evaluation. A mineable a player placed themselves is construction, not a
     * deposit (milestone 7): it resolves NOT_APPLICABLE, so the gate never stops someone dismantling
     * their own granite wall and the place-break loop awards nothing.
     */
    public static Evaluation evaluate(
            @Nullable Player actor,
            BlockState state,
            @Nullable ServerLevel level,
            @Nullable BlockPos pos) {
        return evaluate(actor, state, level, pos, null);
    }

    /**
     * The same evaluation with the working stack named by the caller.
     *
     * <p>The break flow always reads the server-side main hand — that is state the client cannot
     * assert, and passing null here keeps that behaviour. The managed deposit service, though, has
     * always taken the tool as an explicit parameter, and its answer must be about the stack it
     * was actually asked about; judging a different slot than the caller named is how this gate
     * and the deposit's own tool check briefly held two different opinions of one attempt.
     */
    public static Evaluation evaluate(
            @Nullable Player actor,
            BlockState state,
            @Nullable ServerLevel level,
            @Nullable BlockPos pos,
            @Nullable net.minecraft.world.item.ItemStack tool) {
        if (MiningProvenance.isPlayerPlaced(level, pos)) {
            return new Evaluation(ResultType.NOT_APPLICABLE, Optional.empty(), Float.NaN, Float.NaN);
        }
        return evaluateResolved(Mineables.resolve(state), subject(actor, state, tool));
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
        // The creative bypass, before the tool and before the skill. A creative player who is not
        // attacking with the Britannia pickaxe is administering, not mining: the block breaks as
        // any block does in creative, and none of the questions below is asked of them. A creative
        // player attacking with the Britannia pickaxe is a tester and gets every question, ladder
        // included -- that is the whole point of the exception, which exists so the managed flow
        // can be exercised without leaving creative.
        //
        // What is deliberately NOT a bypass: operator permission. Op is an administrative
        // capability and the ladder is gameplay progression; because every GameTest builds a
        // permission-0 player that bypass was invisible to the whole suite while reproducing
        // instantly on a live client, and it stays gone. Nor is creative alone a bypass for the
        // pickaxe holder: "if the player's Mining skill is below the hard requirement for a
        // resource, the resource block must not break" still holds for anyone actually mining.
        if (subject.creativeBypass()) {
            return new Evaluation(ResultType.APPROVED_BYPASS, resolved, subject.miningSkill(), required);
        }
        // Milestone 1. Before skill, because the tool is a fact the player can see and fix, while
        // skill data is transient -- "you cannot mine this with that" is the more useful answer
        // when both are wrong.
        if (!subject.authorizedTool()) {
            return new Evaluation(ResultType.WRONG_TOOL, resolved, subject.miningSkill(), required);
        }
        if (subject.skillDataState() != SkillManager.SkillDataState.AVAILABLE) {
            return new Evaluation(ResultType.SKILL_DATA_UNAVAILABLE, resolved, Float.NaN, required);
        }
        ResultType type = subject.miningSkill() >= required
                ? ResultType.ELIGIBLE
                : ResultType.INSUFFICIENT_SKILL;
        return new Evaluation(type, resolved, subject.miningSkill(), required);
    }

    /**
     * Snapshot of authoritative server state for one actor.
     *
     * <p>The held item is read here, from the server's own copy of the main hand, for the same
     * reason the game mode is: it is state the client cannot assert. Reading it inside the
     * snapshot keeps every caller -- the break gate, the skill award, and the read-only diagnostic
     * command -- asking the identical question about the identical actor, so none of them can
     * drift into a second tool policy.
     *
     * <p>The block is a parameter because milestone 2 made authorization per resource: the tool is
     * checked against the tag that particular resource names, not against a class. A null state
     * therefore authorises nothing, which is the correct fail-closed answer and never reached in
     * practice because an unresolved block answers NOT_APPLICABLE first.
     */
    static Subject subject(@Nullable Player actor, @Nullable BlockState state) {
        return subject(actor, state, null);
    }

    /** The same snapshot judging an explicit stack; null means the server-side main hand. */
    static Subject subject(@Nullable Player actor, @Nullable BlockState state,
            @Nullable net.minecraft.world.item.ItemStack tool) {
        if (!(actor instanceof ServerPlayer serverPlayer)) {
            return new Subject(ActorType.NON_PLAYER, false, 0,
                    SkillManager.SkillDataState.NOT_LOADED, Float.NaN, false, false);
        }
        // Both tool facts are read off the same stack, so the gate cannot say "bypassing" about
        // one slot and "wrong tool" about another.
        net.minecraft.world.item.ItemStack judged = tool != null ? tool : serverPlayer.getMainHandItem();
        boolean authorizedTool = MiningExtractionTool.isAuthorized(state, judged);
        boolean britanniaPickaxe = ManagedExtractionPolicy.isBritanniaPickaxe(judged);
        if (ManagedExtractionPolicy.actorOf(serverPlayer) == ManagedExtractionPolicy.Actor.FAKE_PLAYER) {
            return new Subject(ActorType.AUTOMATION, isCreativeGameMode(serverPlayer),
                    FlowerProtectionService.effectivePermissionLevel(serverPlayer),
                    SkillManager.SkillDataState.NOT_LOADED, Float.NaN, authorizedTool, britanniaPickaxe);
        }
        SkillManager.SkillSnapshot snapshot = SkillManager.getSkillSnapshot(
                serverPlayer.getUUID(), SKILL_ID);
        return new Subject(ActorType.PLAYER, isCreativeGameMode(serverPlayer),
                FlowerProtectionService.effectivePermissionLevel(serverPlayer),
                snapshot.state(), snapshot.value(), authorizedTool, britanniaPickaxe);
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
        return ManagedExtractionPolicy.isCreativeGameMode(player);
    }

    /**
     * How long an identical denial stays quiet. A held left-click re-completes the dig every few
     * ticks, so without this the same action-bar line is rewritten continuously (milestone 9).
     */
    static final long DENIAL_FEEDBACK_COOLDOWN_TICKS = 40L;

    private static final String DENIAL_TICK_TAG = "britannia_mod:mining_denial_tick";
    private static final String DENIAL_KEY_TAG = "britannia_mod:mining_denial_key";

    /**
     * Whether a denial should be shown now. A <em>different</em> message is always shown
     * immediately — looking at a tougher ore must say so at once — while an identical repeat waits
     * out the cooldown. Pure, so the policy is unit-testable without a player.
     */
    static boolean shouldSendDenial(String previousKey, long previousTick, String key, long now) {
        if (!key.equals(previousKey)) {
            return true;
        }
        // A rewound clock (world swap, restored backup) must not mute feedback forever.
        return now < previousTick || now - previousTick >= DENIAL_FEEDBACK_COOLDOWN_TICKS;
    }

    public static void sendDenialFeedback(@Nullable Player actor, Evaluation evaluation) {
        Objects.requireNonNull(evaluation, "Mining evaluation is required");
        if (!(actor instanceof ServerPlayer serverPlayer) || evaluation.permitsBreak()) {
            return;
        }
        String key = evaluation.type().name() + '|'
                + evaluation.definition().map(MineableDefinition::id).orElse("-") + '|'
                + formatSkill(evaluation.currentMining()) + '|'
                + formatSkill(evaluation.requiredMining());
        Component message = evaluation.type() == ResultType.INSUFFICIENT_SKILL
                ? Component.translatable(
                        evaluation.feedbackTranslationKey(),
                        formatSkill(evaluation.currentMining()),
                        formatSkill(evaluation.requiredMining()),
                        evaluation.definition().map(MineableDefinition::displayName).orElse("?"))
                : Component.translatable(evaluation.feedbackTranslationKey());
        sendThrottledDenial(serverPlayer, key, message);
    }

    /**
     * One throttled action-bar denial, shared by every Mining refusal path.
     *
     * <p>The first-swing preflight made sharing necessary rather than tidy: a denial can now be
     * spoken at the left click <em>and</em> at a completed break, and a held button re-raises the
     * click every few ticks. One throttle state — riding on the player's own persistent data, the
     * same place {@code TrainingDummyService} keeps its cooldown, so it cannot leak past logout —
     * means the same refusal is voiced once wherever it fires from, while a <em>different</em>
     * refusal still speaks immediately.
     */
    public static void sendThrottledDenial(ServerPlayer player, String key, Component message) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        long now = player.serverLevel().getGameTime();
        if (!shouldSendDenial(data.getString(DENIAL_KEY_TAG), data.getLong(DENIAL_TICK_TAG), key, now)) {
            return;
        }
        data.putString(DENIAL_KEY_TAG, key);
        data.putLong(DENIAL_TICK_TAG, now);
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.YELLOW), true);
    }

    /**
     * The key of the last denial voiced to this player, or the empty string. Test seam: a bypassing
     * creative break must leave this untouched, and that is the cheapest honest proof that no
     * Mining refusal was spoken or throttled on its way through.
     */
    public static String lastDenialKey(ServerPlayer player) {
        return player.getPersistentData().getString(DENIAL_KEY_TAG);
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

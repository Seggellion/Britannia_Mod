package com.seggellion.britannia_mod.mining;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mining skill awards for authorized Mining activations (milestone 4).
 *
 * <p>Reuses the existing skill engine end to end: the authoritative value, its Rails persistence,
 * the client sync and the cap all come from {@link SkillManager#awardSkillGain}, exactly as
 * {@code FarmingSkill} does. Nothing here stores progression state — a failed roll leaves no
 * residue, so there is no second Mining XP ledger to keep in step with the skill.
 *
 * <p><b>Gain shape.</b> One activation awards either nothing or a whole {@link #GAIN_UNIT} of
 * 0.1, matching the engine's own gain unit and keeping the "your skill has increased by 0.1%"
 * feedback honest. The roll lives here rather than in {@code SkillManager.trySkillGain} because
 * that path exposes only a per-skill Rails modifier and no per-material input, while this project
 * requires the RunUO separation of <em>access requirement</em> from <em>progression difficulty</em>
 * (design §4). Global skill-gain behaviour is untouched: no other skill's math changes, and Rails'
 * Mining row is not edited.
 *
 * <pre>
 *   chance(current, challenge) = BASE_CHANCE
 *                              × (100 − current) / 100        // engine-shaped: harder as you rise
 *                              × materialFactor(current, challenge)
 * </pre>
 *
 * <p>{@code materialFactor} decays as a resource falls behind the miner's skill, so appropriate
 * material is the better progression path, while never reaching zero — regular Stone must stay a
 * valid training source at any skill (design §9.2).
 */
public final class MiningSkill {

    /** The Rails-seeded Mining skill slug, shared with the break gate. */
    public static final String SKILL_ID = MiningBreakGate.SKILL_ID;

    /** One successful activation advances Mining by the engine's own 0.1 gain unit. */
    public static final float GAIN_UNIT = 0.1f;

    /**
     * Sole Mining-specific balance constant, calibrated so the recommended progression route needs
     * roughly 18,000 qualifying activations for 0.0 → 100.0. Because it is a pure multiplier on
     * every roll, expected activations scale exactly as {@code 1 / BASE_CHANCE}; see
     * {@code docs/mining/MINING_SKILL_CALIBRATION_REPORT.md}.
     */
    public static final float BASE_CHANCE = 0.4252f;

    /** Skill distance over which an out-tier resource decays to {@link #MINIMUM_MATERIAL_FACTOR}. */
    public static final float MATERIAL_DECAY_SPAN = 100.0f;

    /** Floor that keeps Stone (and any other trailing resource) trainable forever. */
    public static final float MINIMUM_MATERIAL_FACTOR = 0.25f;

    /**
     * Last accepted activation per player, so one block break can never be counted twice if more
     * than one callback reaches this class. Holds a single small record per player seen, mirroring
     * {@code FarmingSkill}'s in-memory per-player map; it carries no progression value and is
     * safe to lose at any time.
     */
    private static final Map<UUID, Activation> LAST_ACTIVATION = new ConcurrentHashMap<>();

    private record Activation(long gameTime, long packedPos) {
    }

    private MiningSkill() {
    }

    /** Outcome of one Mining attempt: did it extract, and did the skill move. */
    public record AttemptResult(boolean extracted, float skillGained) {
        /** Nothing legitimate happened; no check was made and no world mutation is permitted. */
        public static final AttemptResult NO_ATTEMPT = new AttemptResult(false, 0.0f);
    }

    /**
     * Resolves one Mining attempt: the single skill check, and the gain evaluation that rides with
     * it. The caller mutates the world only when {@link AttemptResult#extracted()} is true.
     *
     * <h2>Why this replaced awardForBreak</h2>
     * The old entry point took a completed break and decided whether to pay for it, which welded
     * progression to successful extraction and made "a failed attempt still trains you" impossible
     * to express. RunUO's shape is the other way round: the check <em>is</em> the attempt, its
     * result decides whether the resource comes out, and gain is evaluated either way.
     *
     * <h2>The two independent outcomes</h2>
     * Success and gain are separate rolls, exactly as in {@code SkillCheck.cs}. A successful
     * extraction may teach nothing; a failed one may teach. What never happens is a gain below the
     * hard requirement: RunUO's {@code skillBase >= resource.ReqSkill && from.CheckSkill(...)}
     * short-circuits, so an unqualified miner never reaches the check at all and the resource
     * teaches them nothing. That is deliberate — you train on what you can actually work.
     *
     * <h2>The beginner ramp</h2>
     * {@code SkillCheck.CheckSkill} ends with {@code || skill.Base < 10.0}, forcing a gain below
     * 10 whatever the roll said. That is what makes an entry window survivable: ordinary stone is
     * 0/0/100, so a miner at 0 has a 0% chance of extracting anything, and without the ramp they
     * would be relying on an ordinary gain roll to escape a dead stop. Reproduced here.
     */
    public static AttemptResult checkMiningAttempt(
            @Nullable Player actor, BlockState state, BlockPos pos) {
        if (!(actor instanceof ServerPlayer player)) {
            return AttemptResult.NO_ATTEMPT;
        }
        MiningBreakGate.Evaluation evaluation =
                MiningBreakGate.evaluate(player, state, player.serverLevel(), pos);
        if (evaluation.type() != MiningBreakGate.ResultType.ELIGIBLE) {
            return AttemptResult.NO_ATTEMPT;
        }
        MineableDefinition definition = evaluation.definition().orElse(null);
        if (definition == null) {
            return AttemptResult.NO_ATTEMPT;
        }
        float current = SkillManager.getSkill(player, SKILL_ID);
        boolean extracted =
                player.getRandom().nextFloat() < MiningProgression.successChance(current, definition);

        // The duplicate guard belongs to the GAIN, not to the extraction. Whether the resource
        // comes out is a property of this break, and the break pipeline can legitimately resolve
        // more than one of them in a single tick; whether the skill moves is a property of the
        // activation, and a second callback for the same (player, position, tick) must not pay
        // twice. Gating extraction on it as well silently refused honest work.
        float gained = 0.0f;
        if (acceptActivation(player.getUUID(), pos.asLong(), player.serverLevel().getGameTime())) {
            float gainChance = gainChance(current, definition.challenge());
            if (current < BEGINNER_GAIN_FLOOR
                    || (gainChance > 0.0f && player.getRandom().nextFloat() < gainChance)) {
                gained = SkillManager.awardSkillGain(player, SKILL_ID, GAIN_UNIT);
            }
        }
        return new AttemptResult(extracted, gained);
    }

    /**
     * Below this the skill always advances on a qualified attempt, from RunUO's
     * {@code || skill.Base < 10.0}. It is what carries a brand-new miner across the part of the
     * curve where their extraction chance is still effectively zero.
     */
    public static final float BEGINNER_GAIN_FLOOR = 10.0f;

    /** Probability that one activation against {@code challenge} advances Mining by 0.1. */
    public static float gainChance(float current, float challenge) {
        if (Float.isNaN(current) || Float.isNaN(challenge) || current >= 100.0f) {
            return 0.0f;
        }
        float chance = BASE_CHANCE * ((100.0f - current) / 100.0f) * materialFactor(current, challenge);
        return Math.max(0.0f, Math.min(1.0f, chance));
    }

    /**
     * How much progression value a resource still carries for a miner of this skill: full value
     * while the resource is at or above them, decaying as they outgrow it, never below
     * {@link #MINIMUM_MATERIAL_FACTOR}.
     */
    public static float materialFactor(float current, float challenge) {
        float delta = current - challenge;
        if (delta <= 0.0f) {
            return 1.0f;
        }
        return Math.max(MINIMUM_MATERIAL_FACTOR, 1.0f - (delta / MATERIAL_DECAY_SPAN));
    }

    /** Rejects a repeat of the same (player, position, tick) activation. */
    static boolean acceptActivation(UUID playerId, long packedPos, long gameTime) {
        Activation previous = LAST_ACTIVATION.put(playerId, new Activation(gameTime, packedPos));
        return previous == null
                || previous.gameTime() != gameTime
                || previous.packedPos() != packedPos;
    }

    /** Test seam: forgets activation history so ordering between tests cannot leak. */
    static void clearActivationHistory() {
        LAST_ACTIVATION.clear();
    }
}

package com.seggellion.britannia_mod.mining;

/**
 * The one authority for a resource's three RunUO progression numbers.
 *
 * <h2>The three numbers</h2>
 * <ul>
 *   <li><b>ReqSkill</b> — absolute eligibility. Below it there is no harvest and, deliberately, no
 *       skill check at all, so the resource teaches nothing.</li>
 *   <li><b>MinSkill / MaxSkill</b> — the difficulty window a qualified attempt rolls against.
 *       Success is {@code (skill - min) / (max - min)}, clamped: certain failure at or below the
 *       floor, certain success at or above the ceiling.</li>
 * </ul>
 *
 * <p>RunUO stores all three per resource. This project's catalogue stores only
 * {@code required_mining}, so the window is <em>derived</em> here rather than added to the data —
 * one formula in one class, so no handler can grow a second opinion. The derivation is RunUO's own
 * arithmetic, not an invention: across its entire ore table MinSkill is uniformly
 * {@code ReqSkill - 40} and the window is 80 wide (Dull Copper 65/25/105, Shadow Iron 70/30/110,
 * Copper 75/35/115, Bronze 80/40/120, Gold 85/45/125, Agapite 90/50/130, Verite 95/55/135,
 * Valorite 99/59/139). Iron is RunUO's one special case at 0/0/100, and every resource this
 * project puts at requirement 0 — stone and cobblestone included — inherits that same beginner
 * window.
 *
 * <p>Changing a hard requirement stays a data edit in {@code mineables.json}; the window follows
 * automatically and cannot drift out of step with it.
 */
public final class MiningProgression {

    /** RunUO's uniform MinSkill offset below the requirement. */
    public static final float WINDOW_OFFSET = 40.0f;

    /** Width of an ordinary difficulty window. */
    public static final float WINDOW_SPAN = 80.0f;

    /** Width of the entry-tier window, from RunUO's Iron at 0/0/100. */
    public static final float ENTRY_WINDOW_SPAN = 100.0f;

    private MiningProgression() {
    }

    /** Absolute eligibility: below this the resource cannot be worked or learned from. */
    public static float reqSkill(MineableDefinition definition) {
        return definition.requiredMining();
    }

    /** Floor of the difficulty window; at or below it a qualified attempt always fails. */
    public static float minSkill(MineableDefinition definition) {
        return Math.max(0.0f, reqSkill(definition) - WINDOW_OFFSET);
    }

    /** Ceiling of the difficulty window; at or above it a qualified attempt always succeeds. */
    public static float maxSkill(MineableDefinition definition) {
        float required = reqSkill(definition);
        float span = required <= 0.0f ? ENTRY_WINDOW_SPAN : WINDOW_SPAN;
        return minSkill(definition) + span;
    }

    /** Whether this miner is allowed to attempt the resource at all. */
    public static boolean qualifies(float miningSkill, MineableDefinition definition) {
        return !Float.isNaN(miningSkill) && miningSkill >= reqSkill(definition);
    }

    /**
     * Probability that a qualified attempt extracts, exactly as RunUO's {@code CheckSkill} bounds
     * it: certain failure at or below {@code MinSkill}, certain success at or above
     * {@code MaxSkill}, linear in between.
     *
     * <p>A beginner working ordinary stone (0/0/100) therefore starts at 0% and climbs as the skill
     * does. That is the intended shape, not a defect to be smoothed away: the progression comes
     * from failed attempts still being able to train, which is what makes early Mining feel like
     * work rather than a formality.
     */
    public static float successChance(float miningSkill, MineableDefinition definition) {
        if (Float.isNaN(miningSkill) || !qualifies(miningSkill, definition)) {
            return 0.0f;
        }
        float min = minSkill(definition);
        float max = maxSkill(definition);
        if (miningSkill <= min) {
            return 0.0f;
        }
        if (miningSkill >= max) {
            return 1.0f;
        }
        return (miningSkill - min) / (max - min);
    }
}

package com.seggellion.britannia_mod.mining;

/**
 * The one authority for a resource's three RunUO progression numbers.
 *
 * <h2>The three numbers</h2>
 * <ul>
 *   <li><b>ReqSkill</b> — absolute eligibility. Below it there is no harvest and, deliberately, no
 *       skill check at all, so the resource teaches nothing.</li>
 *   <li><b>MinSkill / MaxSkill</b> — the difficulty window a qualified attempt rolls against,
 *       for the families that roll at all. Success is {@code (skill - min) / (max - min)},
 *       clamped: certain failure at or below the floor, certain success at or above the ceiling.
 *       The stone family does not roll — see {@link ExtractionMode}.</li>
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

    /**
     * How a qualified attempt turns into a removed block.
     *
     * <p>The two modes are a deliberate Minecraft adaptation, not an inconsistency.
     */
    public enum ExtractionMode {
        /**
         * Meeting the requirement is enough; the block always comes out.
         *
         * <p>For the stone family, which is the terrain itself. RunUO harvests a static mountain
         * face backed by a resource bank, so a failed check simply yields nothing and the world is
         * unchanged. Minecraft stone is the medium the player physically tunnels through, and a
         * miner at 0.4 rolling {@code (0.4 - 0) / 100} would be refused better than 99 times in a
         * hundred -- they could not dig at all. Excavation is therefore guaranteed once the hard
         * requirement is met, and the renewable-resource behaviour that RunUO got from static
         * terrain comes instead from depletion plus timed restoration.
         */
        DETERMINISTIC_AFTER_REQUIREMENT,

        /**
         * Meeting the requirement buys a roll against the difficulty window.
         *
         * <p>For ores, where RunUO's model transfers intact: the vein is a prize rather than a
         * path, refusing it costs the player nothing but time, and the climb from ReqSkill to
         * MaxSkill is the progression.
         */
        SKILL_CHECKED
    }

    /**
     * Which mode this resource extracts under, taken from its catalogue family.
     *
     * <p>Family is the right discriminator and not merely a convenient one: in the shipped
     * catalogue the {@code stone} category and the {@code graded_stone} yield coincide exactly --
     * all 17 stone resources produce {@link com.seggellion.britannia_mod.item.GradeStoneItem} and
     * nothing else does -- so "is this the terrain" and "does this pay graded stone" are the same
     * question. {@code MiningExtractionModeTest} pins that agreement, so a future resource cannot
     * quietly land in one set without the other.
     */
    public static ExtractionMode extractionMode(MineableDefinition definition) {
        return definition.category() == MineableDefinition.Category.STONE
                ? ExtractionMode.DETERMINISTIC_AFTER_REQUIREMENT
                : ExtractionMode.SKILL_CHECKED;
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
     * <p>Returns 1.0 for a qualified {@link ExtractionMode#DETERMINISTIC_AFTER_REQUIREMENT}
     * resource, so the whole pipeline reads one number and no handler has to know which mode a
     * resource is in. An ore climbs from its floor to its ceiling; the terrain does not resist.
     */
    public static float successChance(float miningSkill, MineableDefinition definition) {
        if (Float.isNaN(miningSkill) || !qualifies(miningSkill, definition)) {
            return 0.0f;
        }
        if (extractionMode(definition) == ExtractionMode.DETERMINISTIC_AFTER_REQUIREMENT) {
            // The terrain does not resist a qualified miner. Everything else about the attempt --
            // whether it trains, and what grade it yields -- is decided elsewhere and independently.
            return 1.0f;
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

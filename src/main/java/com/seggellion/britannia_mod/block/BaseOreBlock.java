package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The mod's own ore and rock blocks.
 *
 * <p>These exist only where {@code /populateores} put one, so like a {@link ManagedDepositBlock}
 * their coordinates are their identity — the restoration record and the deposit's whole economic
 * meaning are tied to the position. Milestone 1 of the OreVein remediation made them
 * {@link PushReaction#BLOCK} accordingly: a piston could otherwise move a vein cell away from the
 * record that was scheduled to restore it, leaving a debt pointing at empty stone and an ore block
 * standing somewhere unaccounted for.
 */
public class BaseOreBlock extends Block {

    /**
     * The destroy time every managed ore shipped with before the ladder existed, and still the
     * blast resistance of all of them.
     */
    public static final float DEFAULT_STRENGTH = 3.0f;

    public BaseOreBlock() {
        this(DEFAULT_STRENGTH);
    }

    /**
     * A managed ore with its own destroy time, and therefore its own real hold-to-break duration.
     *
     * <p>Break time is vanilla's own arithmetic — destroy progress per tick is the tool's speed
     * over {@code destroyTime * 30} — so this single number is the whole per-resource timing knob:
     * a higher-ranked ore registers a larger figure and genuinely takes longer under the same
     * pickaxe, with no bespoke timer and no interaction-handler involvement. The ladder itself is
     * written at the registrations in {@code BlockRegistry}, ordered by the Mining rank the
     * catalogue already states.
     *
     * <p><b>Blast resistance deliberately does not follow it.</b> {@code strength(a, b)} sets
     * destroy time and explosion resistance together, and every ore has always had 3.0 for both;
     * letting the ladder drag resistance along would quietly make valorite twice as blast-proof as
     * silver, which is a different game rule than the one being asked for. Timing is the only
     * thing that varies here.
     */
    public BaseOreBlock(float destroyTime) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(destroyTime, DEFAULT_STRENGTH)
                .pushReaction(PushReaction.BLOCK));
    }
}

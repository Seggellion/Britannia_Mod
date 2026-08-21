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
    public BaseOreBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(3.0f, 3.0f)
                .pushReaction(PushReaction.BLOCK));
    }
}

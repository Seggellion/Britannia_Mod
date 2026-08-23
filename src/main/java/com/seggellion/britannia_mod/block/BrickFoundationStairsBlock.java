package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The house foundation masonry, cut as stairs.
 *
 * <p>An ordinary {@link StairBlock} in every way a builder can see -- the same shape, collision,
 * facing, upside-down placement and waterlogging as {@link CustomStoneStairsBlock}, which is the
 * stair this is copied from -- wearing the three-variant light brick the normal foundation family
 * already paints on its sides. No new art: the models name
 * {@code block/structure/brick_foundation_01..03} directly.
 *
 * <h2>Permanent to players, removable by an operator</h2>
 * This is a structural housing-boundary block, so no ordinary player takes one out. A misplaced one
 * still has to be removable by whoever placed it, and creative is how that is done here. The whole
 * rule is one declaration:
 *
 * <p>{@code strength(-1.0F, 3600000.0F)} -- bedrock's own hardness and blast resistance. A negative
 * hardness makes {@code getDestroyProgress} return zero, so a survival or adventure player
 * accumulates no progress however long they hold and the break never completes; no tool changes
 * that, TNT and creepers do nothing, and a piston refuses to push it. It is the same declaration
 * the moongate blocks use.
 *
 * <p>Creative is deliberately not covered, and hardness is precisely the right instrument for that:
 * {@code ServerPlayerGameMode.destroyBlock} skips straight past the break gate for a creative player
 * and calls {@code removeBlock}, so hardness is never consulted on that path. Bedrock behaves the
 * same way for the same reason. There is therefore no {@code onDestroyedByPlayer} override here --
 * that method is the one question asked in <em>every</em> game mode, so refusing there would take
 * the operator's removal away along with everybody else's.
 *
 * <p>Nothing drops on the way out. A creative removal never drops and no other removal exists, so
 * the block is explicitly drop-free rather than carrying a loot table.
 *
 * <p>None of this is a new protection system, and none of it replaces the housing one: the block is
 * also in {@code britannia_mod:house_foundation} beside the other perimeter courses, so a player who
 * reaches for one inside a house is told why by {@code StructureProtectionHandler} exactly as they
 * are for {@code cobblestone_foundation}. That rule exempts creative as well -- the same operator
 * carve-out arriving from the other direction.
 */
public class BrickFoundationStairsBlock extends StairBlock {

    public BrickFoundationStairsBlock() {
        super(
            Blocks.STONE.defaultBlockState(), // base shape, as CustomStoneStairsBlock does
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 3600000.0F) // no player break, no explosion, no piston
                .sound(SoundType.STONE)
                .noLootTable() // the only removal is a creative one, and those never drop
                .noOcclusion()
        );
    }
}

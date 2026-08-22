package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
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
 * <h2>Why nobody breaks it</h2>
 * This is a structural housing-boundary block, so it is permanent in every game mode. Two
 * independent things say so, because the usual one is not enough on its own:
 *
 * <ul>
 *   <li>{@code strength(-1.0F, 3600000.0F)} -- bedrock's own hardness and blast resistance. A
 *       negative hardness makes {@code getDestroyProgress} return zero, so a survival or adventure
 *       player never finishes a break however long they hold, no tool helps, TNT and creepers do
 *       nothing, and a piston refuses to push it. This is the same declaration the moongate blocks
 *       use, and like them the block is explicitly drop-free rather than carrying a loot table.</li>
 *   <li>{@link #onDestroyedByPlayer} refusing outright. Hardness does not bind creative:
 *       {@code ServerPlayerGameMode.destroyBlock} skips straight past the break gate for a creative
 *       player and calls {@code removeBlock}, whose only question is this method. Returning false
 *       is the one answer that is asked in every game mode.</li>
 * </ul>
 *
 * <p>Neither of those is a new protection system, and neither replaces the housing one: the block
 * is also in {@code britannia_mod:house_foundation} beside the other perimeter courses, so a player
 * who reaches for it inside a house is told why by {@code StructureProtectionHandler} exactly as
 * they are for {@code cobblestone_foundation}. The difference is that the housing rule is scoped to
 * registered houses and deliberately exempts creative, and these two are not.
 */
public class BrickFoundationStairsBlock extends StairBlock {

    public BrickFoundationStairsBlock() {
        super(
            Blocks.STONE.defaultBlockState(), // base shape, as CustomStoneStairsBlock does
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 3600000.0F) // unbreakable and explosion-proof
                .sound(SoundType.STONE)
                .noLootTable() // nothing to drop, because nothing may break it
                .noOcclusion()
        );
    }

    /**
     * The last word on removal, and the only one a creative player passes through.
     *
     * <p>Returning false leaves the block where it is. {@code destroyBlock} still returns true to
     * its caller in creative -- that is vanilla's shape, not ours -- so anything checking this must
     * look at the world rather than at that boolean.
     */
    @Override
    public boolean onDestroyedByPlayer(
            BlockState state, Level level, BlockPos pos, Player player,
            boolean willHarvest, FluidState fluid) {
        return false;
    }
}

package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Three-block-tall lamp post.
 *
 * <p>Built on {@link CandelabraBlock} so it inherits the established light-emitting behaviour: the
 * block entity, the light re-check on removal, and interior-decorator nudging.
 *
 * <p>The model spans 48 voxels from {@code y=-16} to {@code y=32}, because Minecraft rejects any
 * model element outside -16..32 and a post starting at 0 could only reach 32. It is placed at the
 * MIDDLE of its three blocks, reaching one block down and one up - the same arrangement
 * {@code candelabra_tall} uses.
 *
 * <p>Collision is the 4x4 shaft rather than the full cube {@code CandelabraBlock} would otherwise
 * give it, so players can walk past a lamp post instead of being blocked by an invisible box.
 */
public class VillaLampPostBlock extends CandelabraBlock {

    private static final VoxelShape SHAFT = Block.box(6, 0, 6, 10, 16, 10);

    public VillaLampPostBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAFT;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAFT;
    }
}

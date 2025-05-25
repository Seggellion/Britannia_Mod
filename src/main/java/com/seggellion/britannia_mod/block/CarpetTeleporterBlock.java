// CarpetTeleporterBlock.java
package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CarpetTeleporterBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.server.level.ServerPlayer;


public class CarpetTeleporterBlock extends Block implements EntityBlock {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered"); // redstone toggle
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 4);

    private static final VoxelShape SLAB =
        Shapes.box(0, 0, 0, 1, 1f / 16f, 1);

    public CarpetTeleporterBlock() {
        super(BlockBehaviour.Properties
                .of()
                .mapColor(MapColor.WOOL)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .instabreak());
        registerDefaultState(stateDefinition.any().setValue(POWERED, true));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(POWERED, STYLE);
    }

    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override public VoxelShape getShape(BlockState s, net.minecraft.world.level.BlockGetter l,
                                         BlockPos p,
                                         net.minecraft.world.phys.shapes.CollisionContext c) {
        return SLAB;
    }

    /* teleport players who step on it (one‑way) */
        @Override
        public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (level.isClientSide) return;
            if (!(entity instanceof ServerPlayer player)) return;
            if (!state.getValue(POWERED)) return;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CarpetTeleporterBlockEntity teleporter) {
                teleporter.teleport(player); // Only center tile runs this
            }
        }


    /* destroy all eight dummy slices if the centre is removed */
    @Override public void onRemove(BlockState s, Level lvl, BlockPos pos,
                                   BlockState newState, boolean moving) {
        if (s.getBlock() == newState.getBlock()) return;
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                if (!(dx == 0 && dz == 0))
                    lvl.destroyBlock(pos.offset(dx, 0, dz), false);
        super.onRemove(s, lvl, pos, newState, moving);
    }

    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return BlockEntityRegistry.CARPET_TELEPORTER_BLOCK_ENTITY_TYPE.get().create(p, s);
    }
}

// CarpetTeleporterItem.java
package com.seggellion.britannia_mod.item;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.CarpetDummyBlock;
import com.seggellion.britannia_mod.block.CarpetPart;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class CarpetTeleporterItem extends BlockItem {

    private static final Logger LOGGER = LogUtils.getLogger();

    public CarpetTeleporterItem(Properties props) {
        super(BlockRegistry.CARPET_TELEPORTER_BLOCK.get(), props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level lvl = ctx.getLevel();
        BlockPos origin = ctx.getClickedPos().above();
        Direction face = ctx.getClickedFace();
        if (face != Direction.UP) return InteractionResult.FAIL;

        /* ensure 3×3 area is clear */
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                if (!lvl.getBlockState(origin.offset(dx, 0, dz)).canBeReplaced())
                    return InteractionResult.FAIL;

        BlockState teleporter = BlockRegistry.CARPET_TELEPORTER_BLOCK.get().defaultBlockState();
        lvl.setBlock(origin, teleporter, 3);

        BlockState dummyBase = BlockRegistry.CARPET_DUMMY_BLOCK.get().defaultBlockState();
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                if (!(dx == 0 && dz == 0)) {
                    BlockPos p = origin.offset(dx, 0, dz);
                    CarpetPart part = CarpetPart.fromGrid(dx + 1, dz + 1);
                        lvl.setBlock(p, dummyBase.setValue(CarpetDummyBlock.PART, part), 3);

                }

        if (!lvl.isClientSide) {
            ctx.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(lvl.isClientSide);
    }
}

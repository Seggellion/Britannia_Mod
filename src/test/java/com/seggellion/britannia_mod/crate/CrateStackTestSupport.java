package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.GameData;

/**
 * A crate column with no world behind it.
 *
 * <p>The packing, identity and persistence rules are all pure state, so they are worth testing as
 * plain JUnit rather than as GameTests: no server, no chunk, no player, and a whole suite that runs
 * in the time one GameTest takes to place a block. Only promotion and container validity need a real
 * level, and those are tested where they belong.
 */
final class CrateStackTestSupport {

    private static CrateStackBlock block;
    private static BlockEntityType<CrateStackBlockEntity> type;

    private CrateStackTestSupport() {
    }

    static synchronized void bootstrap() {
        if (block != null) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        block = new CrateStackBlock(BlockBehaviour.Properties.of());
        @SuppressWarnings("unchecked")
        BlockEntityType<CrateStackBlockEntity>[] holder =
                (BlockEntityType<CrateStackBlockEntity>[]) new BlockEntityType<?>[1];
        holder[0] = BlockEntityType.Builder
                .of((pos, state) -> new CrateStackBlockEntity(holder[0], pos, state), block)
                .build(null);
        type = holder[0];
    }

    /** An empty column, detached from any level. */
    static CrateStackBlockEntity emptyStack() {
        bootstrap();
        return new CrateStackBlockEntity(type, BlockPos.ZERO, block.defaultBlockState());
    }

    /** A column already holding these variants, bottom first, all facing north. */
    static CrateStackBlockEntity stackOf(CrateVariant... variants) {
        CrateStackBlockEntity stack = emptyStack();
        for (CrateVariant variant : variants) {
            stack.appendCrate(variant, net.minecraft.core.Direction.NORTH);
        }
        return stack;
    }
}

package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.block.entity.MoongateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server structure and migration checks for the Milestone 10 city moongate. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsMoongateGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsMoongateGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void cityMoongateUsesOneCellAndCleansLegacyTop(GameTestHelper helper) {
        Block gate = BlockRegistry.MOONGATE_BLOCK.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 3, 2));
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;
        helper.getLevel().setBlock(anchor, gate.defaultBlockState(), flags);

        check(helper.getLevel().getBlockState(anchor).is(gate), "city moongate root was not placed");
        check(helper.getLevel().getBlockState(anchor.above()).isAir(),
                "city moongate placed a second logical cell");
        check(helper.getLevel().getBlockEntity(anchor) instanceof MoongateBlockEntity,
                "random-city moongate lacks its client billboard render anchor");
        check(helper.getLevel().getBlockState(anchor).getCollisionShape(helper.getLevel(), anchor).isEmpty(),
                "city moongate stopped being pass-through");

        helper.getLevel().setBlock(anchor.above(), BlockRegistry.MOONGATE_TOP.get().defaultBlockState(), flags);
        check(helper.getLevel().getBlockState(anchor.above()).isAir(),
                "legacy city-moongate top did not remove itself");
        check(helper.getLevel().getBlockState(anchor).is(gate),
                "legacy-top cleanup removed the final moongate cell");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

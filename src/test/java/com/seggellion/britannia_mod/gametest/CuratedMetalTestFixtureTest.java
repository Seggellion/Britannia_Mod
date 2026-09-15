package com.seggellion.britannia_mod.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CuratedMetalTestFixtureTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final BlockPos FAILED_PLACEMENT = new BlockPos(11491855, -60, 10223103);

    @Test
    void reproducesTheReportedIronChunkEdgeFailureAndChoosesAnOwnedInteriorCell() {
        BlockPos oldOrigin = FAILED_PLACEMENT.offset(1, 1, 1);
        var oldPlan = CuratedDepositTestRows.of("iron", oldOrigin, 20).plan(DIMENSION);
        assertTrue(oldPlan.positionsIn(new ChunkPos(oldOrigin)).stream()
                .noneMatch(cell -> cell.getY() > -60 && cell.getY() < 319),
                "The recorded old fixture must reproduce its actual missing-cell failure");
        var fixture = verify("iron", FAILED_PLACEMENT);
        var repeat = verify("iron", FAILED_PLACEMENT);
        assertEquals(fixture.row(), repeat.row());
        assertEquals(fixture.cell(), repeat.cell());
        assertEquals(fixture.deposit().positions(), repeat.deposit().positions());
    }

    @Test
    void allThreeMetalsFitEveryChunkAlignmentWithoutLeavingTheTestInterior() {
        int checked = 0;
        int baseX = (FAILED_PLACEMENT.getX() >> 4) << 4;
        int baseZ = (FAILED_PLACEMENT.getZ() >> 4) << 4;
        for (String metal : List.of("iron", "gold", "copper")) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    verify(metal, new BlockPos(baseX + x, -60, baseZ + z));
                    checked++;
                }
            }
            verify(metal, new BlockPos(-11491855, -60, -10223103));
        }
        assertEquals(768, checked);
    }

    @Test
    void impossibleFixtureBoundsFailInsteadOfSelectingAnotherChunkOrSkippingTheLifecycle() {
        assertThrows(IllegalArgumentException.class, () -> CuratedMetalTestFixture.select("iron",
                DIMENSION, new AABB(100, -58, 100, 105, -55, 105), new ChunkPos(0, 0), -64, 320));
    }

    private static CuratedMetalTestFixture.Fixture verify(String path, BlockPos structureBlock) {
        // GameTest's 7 x 5 x 7 template starts one block above its structure block.
        AABB interior = new AABB(structureBlock.getX(), structureBlock.getY() + 1, structureBlock.getZ(),
                structureBlock.getX() + 7, structureBlock.getY() + 6, structureBlock.getZ() + 7).deflate(1);
        ChunkPos chunk = new ChunkPos(structureBlock.offset(1, 1, 1));
        var fixture = CuratedMetalTestFixture.select(path, DIMENSION, interior, chunk, -64, 320);
        assertTrue(interior.contains(Vec3.atCenterOf(fixture.cell())), path + " escaped its structure");
        assertEquals(chunk, new ChunkPos(fixture.cell()), path + " escaped its loaded chunk");
        assertTrue(fixture.cell().getY() > -60 && fixture.cell().getY() < 319);
        assertTrue(fixture.deposit().positionsIn(chunk).contains(fixture.cell()), "cell must belong to its real plan");
        assertEquals(fixture.row().identity(DIMENSION), fixture.deposit().config().seed());
        return fixture;
    }
}

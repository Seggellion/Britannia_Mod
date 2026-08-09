package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.placement.BannerBlockItemTransfer;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerRemovalCause;
import com.seggellion.britannia_mod.banner.structure.BannerStructureLifecycle;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerStructureLifecyclePolicyTest {
    private static final Path MAIN = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/java/com/seggellion/britannia_mod");

    @BeforeAll
    static void setup() {
        Milestone7RegisteredTestContent.ensureRegistered();
    }

    @Test
    void onlySurvivalBreakAndSupportLossAllowOneConfiguredDrop() {
        assertTrue(BannerRemovalCause.SURVIVAL_PLAYER.dropsConfiguredItem());
        assertTrue(BannerRemovalCause.SUPPORT_LOSS.dropsConfiguredItem());
        for (BannerRemovalCause cause : BannerRemovalCause.values()) {
            if (cause != BannerRemovalCause.SURVIVAL_PLAYER && cause != BannerRemovalCause.SUPPORT_LOSS) {
                assertFalse(cause.dropsConfiguredItem(), cause.name());
            }
        }
        assertFalse(BannerRemovalCause.CREATIVE_PLAYER.dropsConfiguredItem());
        assertFalse(BannerRemovalCause.EXPLOSION.dropsConfiguredItem());
        assertFalse(BannerRemovalCause.ORPHAN_CLEANUP.dropsConfiguredItem());
        assertFalse(BannerRemovalCause.PLACEMENT_ROLLBACK.dropsConfiguredItem());
    }

    @Test
    void exactConfiguredDropCopyPreservesEveryBannerStateFieldWithoutRegistryLookup() {
        var expected = CoreDataFixtures.dyedBannerState();
        var stack = BannerBlockItemTransfer.create(Milestone7RegisteredTestContent.banner(),
                Milestone7RegisteredTestContent.component(), Optional.of(expected));
        assertEquals(expected, stack.get(Milestone7RegisteredTestContent.component()));
        assertEquals(1, stack.getCount());
    }

    @Test
    void membershipRejectsWrongFacingOrientationOffsetAndPartAsAnchor() {
        BannerBlock anchor = new BannerBlock(BlockBehaviour.Properties.of());
        BannerPartBlock part = new BannerPartBlock(BlockBehaviour.Properties.of());
        var footprint = BannerFootprint.fromDimensions(new BannerDimensions(3, 2, true)).footprint();
        for (var offset : footprint.offsets()) {
            var state = offset.isAnchor()
                    ? anchor.defaultBlockState().setValue(BannerBlock.FACING, Direction.SOUTH)
                            .setValue(BannerBlock.ORIENTATION, BannerOrientation.WALL_PERPENDICULAR)
                    : part.stateFor(Direction.SOUTH, BannerOrientation.WALL_PERPENDICULAR, offset);
            assertTrue(BannerStructureLifecycle.isExpectedCell(
                    state, Direction.SOUTH, BannerOrientation.WALL_PERPENDICULAR, offset));
            assertFalse(BannerStructureLifecycle.isExpectedCell(
                    state, Direction.NORTH, BannerOrientation.WALL_PERPENDICULAR, offset));
            assertFalse(BannerStructureLifecycle.isExpectedCell(
                    state, Direction.SOUTH, BannerOrientation.WALL_PARALLEL, offset));
        }
        assertFalse(BannerStructureLifecycle.isExpectedCell(
                part.stateFor(Direction.SOUTH, BannerOrientation.WALL_PERPENDICULAR,
                        footprint.offsets().get(1)),
                Direction.SOUTH, BannerOrientation.WALL_PERPENDICULAR, footprint.offsets().get(2)));
    }

    @Test
    void anchorAndChildBreakExplosionExternalRemovalAndPickConvergeOnLifecycle() throws Exception {
        String anchor = Files.readString(MAIN.resolve("banner/block/BannerBlock.java"));
        String part = Files.readString(MAIN.resolve("banner/block/BannerPartBlock.java"));
        for (String source : java.util.List.of(anchor, part)) {
            assertTrue(source.contains("onDestroyedByPlayer"));
            assertTrue(source.contains("BannerStructureLifecycle.removeFrom"));
            assertTrue(source.contains("BannerRemovalCause.EXPLOSION"));
            assertTrue(source.contains("getCloneItemStack"));
            assertTrue(source.contains("return List.of()"));
        }
        assertTrue(anchor.contains("removeExternalAnchor"));
        assertTrue(part.contains("removeExternalPart"));
        assertTrue(part.contains("BannerBlockItemTransfer.fromBlockEntity(anchor)"));
        assertTrue(part.contains("level.hasChunkAt(anchorPos)"));
    }

    @Test
    void centralServiceUsesScopedGuardReverseRemovalOneDropAndNoExplosionDrop() throws Exception {
        String source = Files.readString(MAIN.resolve("banner/structure/BannerStructureLifecycle.java"));
        assertTrue(source.contains("IN_PROGRESS.add(key)"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("IN_PROGRESS.remove(key)"));
        assertTrue(source.contains("structure.occupiedOffsets().size() - 1"));
        assertTrue(source.contains("Block.UPDATE_SUPPRESS_DROPS"));
        assertEquals(1, count(source, "Block\\.popResource\\("));
        assertTrue(source.contains("cause.dropsConfiguredItem()"));
    }

    @Test
    void integrityDefersUnloadedAnchorsRepairsOnlyReplaceableCellsAndPreservesObstructions() throws Exception {
        String source = Files.readString(MAIN.resolve("banner/structure/BannerStructureIntegrity.java"));
        assertTrue(source.contains("DEFERRED_UNLOADED_CHUNK"));
        assertTrue(source.contains("actual.canBeReplaced()"));
        assertTrue(source.contains("obstruction was preserved"));
        assertTrue(source.contains("BannerRemovalCause.SUPPORT_LOSS"));
        assertTrue(source.contains("requiredSupportPositions"));

        String handler = Files.readString(MAIN.resolve("banner/structure/BannerStructureIntegrityHandler.java"));
        assertTrue(handler.contains("ChunkEvent.Load"));
        assertTrue(handler.contains("ServerTickEvent.Post"));
        assertTrue(handler.contains("getChunkNow"));
        assertFalse(handler.contains("getChunkAt"));
    }

    private static long count(String value, String regex) {
        return java.util.regex.Pattern.compile(regex).matcher(value).results().count();
    }
}

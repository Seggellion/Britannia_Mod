package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementFailure;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlanner;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlanningResult;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementWorld;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerMultiBlockPlacementPlannerTest {
    private static RegistrySnapshot production;
    private static BannerItem item;
    private static BannerItemFactory factory;
    private static BannerBlock anchor;
    private static BannerPartBlock part;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        item = Milestone7RegisteredTestContent.banner();
        factory = new BannerItemFactory(item, item.stateAccess());
        anchor = new BannerBlock(BlockBehaviour.Properties.of());
        part = new BannerPartBlock(BlockBehaviour.Properties.of());
    }

    @Test
    void allThirtyThreeDefinitionsPlanThroughDimensionsWithExpectedAnchorAndPartCounts() {
        int planned = 0;
        for (var definition : production.banners().activeDefinitions()) {
            FakeWorld world = new FakeWorld();
            ItemStack stack = natural(definition.id());
            BannerPlacementPlanningResult result = plan(stack, BlockPos.ZERO, Direction.NORTH, world);
            assertTrue(result.successful(), definition.id() + ": " + result.failure());
            var plan = result.plan().orElseThrow();
            int expectedCells = definition.dimensions().widthBlocks() * definition.dimensions().heightBlocks();
            assertEquals(expectedCells, plan.cells().size());
            assertEquals(1, plan.cells().stream().filter(cell -> cell.role() == BannerCellRole.ANCHOR).count());
            assertEquals(expectedCells - 1,
                    plan.cells().stream().filter(cell -> cell.role() == BannerCellRole.PART).count());
            assertEquals(definition.dimensions().widthBlocks(), plan.requiredSupportPositions().size());
            assertEquals(stack.get(Milestone7RegisteredTestContent.component()), plan.bannerState());
            assertEquals(1, stack.getCount());
            assertEquals(0, world.mutations);
            planned++;
        }
        assertEquals(33, planned);
    }

    @Test
    void oneByTwoTwoByTwoAndThreeByTwoPlansUseOneThreeAndFiveParts() {
        assertPartCount("tournament_medium", 1);
        assertPartCount("joined_wards", 3);
        assertPartCount("large_01", 5);
    }

    @Test
    void blockedMiddleAndFinalCellsFailBeforeMutationAndConsumption() {
        ItemStack stack = natural(BannerDefinitionId.parse("britannia_mod:large_01"));
        var successful = plan(stack, BlockPos.ZERO, Direction.NORTH, new FakeWorld()).plan().orElseThrow();
        for (int index : new int[] {2, 5}) {
            FakeWorld world = new FakeWorld();
            world.occupied.add(successful.cells().get(index).worldPosition());
            BannerPlacementPlanningResult failed = plan(stack, BlockPos.ZERO, Direction.NORTH, world);
            assertEquals(BannerPlacementFailure.TARGET_OCCUPIED, failed.failure());
            assertEquals(1, stack.getCount());
            assertEquals(0, world.mutations);
        }
    }

    @Test
    void unloadedOccupiedOrSupportChunkFailsWithoutForceLoading() {
        ItemStack stack = natural(BannerDefinitionId.parse("britannia_mod:large_01"));
        BlockPos clicked = new BlockPos(15, 70, 0);
        FakeWorld world = new FakeWorld();
        world.unloadedChunks.add("1,0");
        BannerPlacementPlanningResult result = plan(stack, clicked, Direction.SOUTH, world);
        assertEquals(BannerPlacementFailure.REQUIRED_CHUNK_UNLOADED, result.failure());
        assertFalse(world.forceLoaded);
        assertEquals(0, world.blockStateReads);
        assertEquals(0, world.mutations);
        assertEquals(1, stack.getCount());
    }

    @Test
    void xZAndNegativeChunkBoundaryPlansRemainDeterministicWhenChunksAreLoaded() {
        assertCrossesChunks(new BlockPos(15, 70, 0), Direction.SOUTH);
        assertCrossesChunks(new BlockPos(0, 70, 15), Direction.WEST);
        assertCrossesChunks(new BlockPos(-1, 70, 0), Direction.SOUTH);
    }

    @Test
    void onlyTopRowRequiresSupport() {
        ItemStack stack = natural(BannerDefinitionId.parse("britannia_mod:large_01"));
        BlockPos middleSupport = plan(stack, BlockPos.ZERO, Direction.NORTH, new FakeWorld())
                .plan().orElseThrow().requiredSupportPositions().get(1);
        FakeWorld world = new FakeWorld();
        world.invalidSupports.add(middleSupport);
        assertEquals(BannerPlacementFailure.INVALID_WALL_SUPPORT,
                plan(stack, BlockPos.ZERO, Direction.NORTH, world).failure());
        assertFalse(world.supportChecks.contains(new BlockPos(0, 69, 0)));
    }

    private static void assertPartCount(String path, int expectedParts) {
        var plan = plan(natural(BannerDefinitionId.parse("britannia_mod:" + path)),
                BlockPos.ZERO, Direction.NORTH, new FakeWorld()).plan().orElseThrow();
        assertEquals(expectedParts,
                plan.cells().stream().filter(cell -> cell.role() == BannerCellRole.PART).count());
    }

    private static void assertCrossesChunks(BlockPos clicked, Direction facing) {
        var plan = plan(natural(BannerDefinitionId.parse("britannia_mod:large_01")),
                clicked, facing, new FakeWorld()).plan().orElseThrow();
        assertTrue(plan.cells().stream().map(cell -> (cell.worldPosition().getX() >> 4) + ","
                + (cell.worldPosition().getZ() >> 4)).distinct().count() >= 2);
    }

    private static BannerPlacementPlanningResult plan(
            ItemStack stack, BlockPos clicked, Direction face, FakeWorld world) {
        return BannerPlacementPlanner.plan(item, stack, production, true, clicked, face, anchor, part, world);
    }

    private static ItemStack natural(BannerDefinitionId id) {
        return factory.naturalCottonAdminBanner(id, production, true).stack().orElseThrow();
    }

    private static final class FakeWorld implements BannerPlacementWorld {
        final Set<BlockPos> occupied = new HashSet<>();
        final Set<BlockPos> invalidSupports = new HashSet<>();
        final Set<BlockPos> supportChecks = new HashSet<>();
        final Set<String> unloadedChunks = new HashSet<>();
        int mutations;
        int blockStateReads;
        boolean forceLoaded;

        @Override public BlockState blockState(BlockPos pos) {
            blockStateReads++;
            return Blocks.SHORT_GRASS.defaultBlockState();
        }
        @Override public boolean targetReplaceable(BlockPos pos) { return !occupied.contains(pos); }
        @Override public boolean inWorldBounds(BlockPos pos) { return true; }
        @Override public boolean chunkLoaded(BlockPos pos) {
            return !unloadedChunks.contains((pos.getX() >> 4) + "," + (pos.getZ() >> 4));
        }
        @Override public boolean unrelatedBannerCell(BlockPos pos) { return false; }
        @Override public boolean validWallSupport(BlockPos pos, Direction facing) {
            supportChecks.add(pos);
            return !invalidSupports.contains(pos);
        }
        @Override public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack) { return true; }
        @Override public boolean canCreateBannerBlockEntity(BlockState state) { return true; }
        @Override public boolean canEncodePart(BlockState state) { return state.getBlock() instanceof BannerPartBlock; }
        @Override public boolean canAcceptState(BannerInstanceState state) { return true; }
    }
}

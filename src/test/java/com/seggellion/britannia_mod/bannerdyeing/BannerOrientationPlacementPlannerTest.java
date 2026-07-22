package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementFailure;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlanner;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlanningResult;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementWorld;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerOrientationPlacementPlannerTest {
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static final MountId IRON = MountId.parse("britannia_mod:iron");
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
    void allDefinitionsBothOrientationsFourFacingsPlanWithoutDuplicates() {
        int plans = 0;
        for (BannerDefinition definition : production.banners().activeDefinitions()) {
            for (MountId mount : List.of(BRASS, IRON)) {
                for (BannerOrientation orientation : BannerOrientation.values()) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        BannerPlacementPlanningResult result = plan(
                                natural(definition.id(), mount), production, orientation,
                                new BlockPos(-16, 80, 15), facing, new FakeWorld());
                        assertTrue(result.successful(), definition.id() + " " + mount + " "
                                + orientation + " " + facing + " " + result.failure());
                        var planned = result.plan().orElseThrow();
                        assertEquals(mount, planned.bannerState().mountId());
                        assertEquals(orientation, planned.orientation());
                        assertEquals(orientation, planned.placedStructure().orientation());
                        assertEquals(orientation, planned.anchorBlockState().getValue(BannerBlock.ORIENTATION));
                        assertEquals(planned.cells().size(), planned.cells().stream()
                                .map(cell -> cell.worldPosition()).distinct().count());
                        planned.cells().stream().skip(1).forEach(cell -> assertEquals(
                                orientation, cell.placedState().getValue(BannerPartBlock.ORIENTATION)));
                        assertEquals(orientation == BannerOrientation.WALL_PARALLEL
                                        ? definition.dimensions().widthBlocks() : 1,
                                planned.requiredSupportPositions().size());
                        plans++;
                    }
                }
            }
        }
        assertEquals(33 * 2 * 2 * 4, plans);
    }

    @Test
    void perpendicularProjectsOutwardWhileParallelSpansViewerRight() {
        ItemStack stack = natural(BannerDefinitionId.parse("britannia_mod:large_01"), BRASS);
        var parallel = plan(stack, production, BannerOrientation.WALL_PARALLEL,
                BlockPos.ZERO, Direction.NORTH, new FakeWorld()).plan().orElseThrow();
        var perpendicular = plan(stack, production, BannerOrientation.WALL_PERPENDICULAR,
                BlockPos.ZERO, Direction.NORTH, new FakeWorld()).plan().orElseThrow();
        assertNotEquals(parallel.cells().get(1).worldPosition(), perpendicular.cells().get(1).worldPosition());
        assertEquals(Direction.WEST, BannerStructureTransform.spanAxis(
                Direction.NORTH, BannerOrientation.WALL_PARALLEL));
        assertEquals(Direction.NORTH, BannerStructureTransform.spanAxis(
                Direction.NORTH, BannerOrientation.WALL_PERPENDICULAR));
        assertEquals(perpendicular.anchorPos().north(), perpendicular.cells().get(1).worldPosition());
    }

    @Test
    void perpendicularRequiresOnlyAnchorSupportAndFailsBeforeMutation() {
        ItemStack stack = natural(BannerDefinitionId.parse("britannia_mod:large_01"), BRASS);
        var valid = plan(stack, production, BannerOrientation.WALL_PERPENDICULAR,
                BlockPos.ZERO, Direction.EAST, new FakeWorld()).plan().orElseThrow();
        assertEquals(List.of(valid.anchorPos().west()), valid.requiredSupportPositions());
        FakeWorld unsupported = new FakeWorld();
        unsupported.invalidSupports.add(valid.requiredSupportPositions().getFirst());
        assertEquals(BannerPlacementFailure.INVALID_WALL_SUPPORT,
                plan(stack, production, BannerOrientation.WALL_PERPENDICULAR,
                        BlockPos.ZERO, Direction.EAST, unsupported).failure());
        assertEquals(1, stack.getCount());
        assertEquals(0, unsupported.mutations);
    }

    @Test
    void supportBoundsFailBeforeAnyBlockStateCapture() {
        ItemStack stack = natural(BannerDefinitionId.parse("britannia_mod:large_01"), BRASS);
        var valid = plan(stack, production, BannerOrientation.WALL_PERPENDICULAR,
                BlockPos.ZERO, Direction.EAST, new FakeWorld()).plan().orElseThrow();
        FakeWorld outside = new FakeWorld();
        outside.outOfBounds.add(valid.requiredSupportPositions().getFirst());
        assertEquals(BannerPlacementFailure.WORLD_BOUND_FAILURE,
                plan(stack, production, BannerOrientation.WALL_PERPENDICULAR,
                        BlockPos.ZERO, Direction.EAST, outside).failure());
        assertEquals(0, outside.blockStateReads);
    }

    @Test
    void definitionRestrictionsAreEnforcedBeforeAnyWorldRead() {
        BannerDefinition source = production.banners().require(
                BannerDefinitionId.parse("britannia_mod:large_01"));
        BannerDefinition parallelOnly = copy(source, List.of(BannerOrientation.WALL_PARALLEL));
        FakeWorld world = new FakeWorld();
        assertEquals(BannerPlacementFailure.UNSUPPORTED_ORIENTATION,
                plan(natural(source.id(), BRASS), RegistrySnapshotTestFactory.replaceBanner(production, parallelOnly),
                        BannerOrientation.WALL_PERPENDICULAR, BlockPos.ZERO, Direction.NORTH, world).failure());
        assertEquals(0, world.reads);

        BannerDefinition perpendicularOnly = copy(source, List.of(BannerOrientation.WALL_PERPENDICULAR));
        assertTrue(plan(natural(source.id(), BRASS),
                RegistrySnapshotTestFactory.replaceBanner(production, perpendicularOnly),
                BannerOrientation.WALL_PERPENDICULAR, BlockPos.ZERO, Direction.NORTH,
                new FakeWorld()).successful());
    }

    @Test
    void brassAndIronMountIdentityEnterTheSameAuthoritativePlan() {
        BannerDefinitionId id = BannerDefinitionId.parse("britannia_mod:joined_wards");
        for (MountId mount : List.of(BRASS, IRON)) {
            ItemStack stack = natural(id, mount);
            var planned = plan(stack, production, BannerOrientation.WALL_PERPENDICULAR,
                    BlockPos.ZERO, Direction.SOUTH, new FakeWorld()).plan().orElseThrow();
            assertEquals(mount, planned.bannerState().mountId());
            assertEquals(mount, item.stateAccess().read(stack).orElseThrow().mountId());
        }
    }

    private static BannerDefinition copy(BannerDefinition source, List<BannerOrientation> orientations) {
        return new BannerDefinition(source.schemaVersion(), source.id(), source.displayNameKey(),
                source.contentStatus(), source.sourceReference(), source.catalogueGroup(), source.dimensions(),
                orientations, source.supportedMounts(), source.defaultMount(), source.defaultMaterial(),
                source.assets(), source.placementProfile());
    }

    private static BannerPlacementPlanningResult plan(
            ItemStack stack, RegistrySnapshot snapshot, BannerOrientation orientation,
            BlockPos clicked, Direction face, FakeWorld world) {
        return BannerPlacementPlanner.plan(item, stack, snapshot, true, orientation,
                clicked, face, anchor, part, world);
    }

    private static ItemStack natural(BannerDefinitionId id, MountId mount) {
        return factory.craftedMaterialBanner(id,
                com.seggellion.britannia_mod.dye.api.FabricMaterialId.parse("britannia_mod:cotton"),
                Optional.of(mount), production, true).stack().orElseThrow();
    }

    private static final class FakeWorld implements BannerPlacementWorld {
        final Set<BlockPos> invalidSupports = new HashSet<>();
        final Set<BlockPos> outOfBounds = new HashSet<>();
        int reads;
        int blockStateReads;
        int mutations;
        @Override public BlockState blockState(BlockPos pos) {
            reads++; blockStateReads++; return Blocks.SHORT_GRASS.defaultBlockState();
        }
        @Override public boolean targetReplaceable(BlockPos pos) { reads++; return true; }
        @Override public boolean inWorldBounds(BlockPos pos) { reads++; return !outOfBounds.contains(pos); }
        @Override public boolean chunkLoaded(BlockPos pos) { reads++; return true; }
        @Override public boolean unrelatedBannerCell(BlockPos pos) { reads++; return false; }
        @Override public boolean validWallSupport(BlockPos pos, Direction facing) {
            reads++; return !invalidSupports.contains(pos);
        }
        @Override public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack) {
            reads++; return true;
        }
        @Override public boolean canCreateBannerBlockEntity(BlockState state) { reads++; return true; }
        @Override public boolean canEncodePart(BlockState state) { reads++; return true; }
        @Override public boolean canAcceptState(BannerInstanceState state) { reads++; return true; }
    }
}

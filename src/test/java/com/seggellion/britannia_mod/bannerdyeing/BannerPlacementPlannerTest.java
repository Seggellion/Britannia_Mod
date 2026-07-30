package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementFailure;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlanner;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementPlanningResult;
import com.seggellion.britannia_mod.banner.placement.BannerPlacementWorld;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacementPlannerTest {
    private static final BannerDefinitionId SMALL = BannerDefinitionId.parse("britannia_mod:silver_and_gold_pennon");
    private static final BannerDefinitionId MULTI = BannerDefinitionId.parse("britannia_mod:joined_wards");
    private static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static final MountId IRON = MountId.parse("britannia_mod:iron");
    private static RegistrySnapshot production;
    private static BannerItem item;
    private static BannerItemFactory factory;
    private static BannerBlock block;
    private static BannerPartBlock partBlock;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        item = Milestone7RegisteredTestContent.banner();
        factory = new BannerItemFactory(item, item.stateAccess());
        block = new BannerBlock(BlockBehaviour.Properties.of());
        partBlock = new BannerPartBlock(BlockBehaviour.Properties.of());
    }

    @Test
    void validConfiguredOneByOneBannerBuildsExactOneCellPlan() {
        ItemStack stack = natural(SMALL);
        FakeWorld world = new FakeWorld();
        BannerPlacementPlanningResult result = plan(stack, production, true, Direction.EAST, world);
        assertTrue(result.successful());
        var plan = result.plan().orElseThrow();
        assertEquals(new BlockPos(1, 0, 0), plan.anchorPos());
        assertEquals(Direction.EAST, plan.facing());
        assertEquals(Direction.EAST, plan.anchorBlockState().getValue(BannerBlock.FACING));
        assertEquals(1, plan.cells().size());
        assertEquals(List.of(BlockPos.ZERO), plan.requiredSupportPositions());
        assertEquals(item.stateAccess().read(stack).orElseThrow(), plan.bannerState());
        assertEquals(1, stack.getCount());
        assertEquals(0, world.mutations);
    }

    @Test
    void unconfiguredWrongItemAndUnavailableRegistryAreTypedAndNonMutating() {
        FakeWorld world = new FakeWorld();
        assertEquals(BannerPlacementFailure.UNCONFIGURED_BANNER,
                plan(new ItemStack(item), production, true, Direction.NORTH, world).failure());
        assertEquals(BannerPlacementFailure.INVALID_ITEM,
                plan(new ItemStack(Items.STICK), production, true, Direction.NORTH, world).failure());
        assertEquals(BannerPlacementFailure.REGISTRY_UNAVAILABLE,
                plan(natural(SMALL), RegistrySnapshot.empty(), false, Direction.NORTH, world).failure());
        assertEquals(0, world.mutations);
    }

    @Test
    void missingAndDisabledDefinitionAreDistinct() {
        assertEquals(BannerPlacementFailure.DEFINITION_MISSING,
                plan(natural(SMALL), RegistrySnapshotTestFactory.withoutBanner(production, SMALL), true,
                        Direction.NORTH, new FakeWorld()).failure());
        assertEquals(BannerPlacementFailure.DEFINITION_DISABLED,
                plan(natural(SMALL), RegistrySnapshotTestFactory.withDisabledBanner(production, SMALL), true,
                        Direction.NORTH, new FakeWorld()).failure());
    }

    @Test
    void missingAndDisabledMaterialAreDistinct() {
        assertEquals(BannerPlacementFailure.MATERIAL_MISSING,
                plan(natural(SMALL), RegistrySnapshotTestFactory.withoutMaterial(production, COTTON), true,
                        Direction.NORTH, new FakeWorld()).failure());
        assertEquals(BannerPlacementFailure.MATERIAL_DISABLED,
                plan(natural(SMALL), RegistrySnapshotTestFactory.withDisabledMaterial(production, COTTON), true,
                        Direction.NORTH, new FakeWorld()).failure());
    }

    @Test
    void missingPaletteAndColourAreDistinctAndNeverRepairTheStack() {
        ItemStack stack = natural(SMALL);
        BannerInstanceState original = item.stateAccess().read(stack).orElseThrow();
        RegistrySnapshot noPalette = RegistrySnapshotTestFactory.withoutPalette(
                production, production.fabricMaterials().require(COTTON).paletteId());
        assertEquals(BannerPlacementFailure.PALETTE_MISSING,
                plan(stack, noPalette, true, Direction.NORTH, new FakeWorld()).failure());
        assertEquals(original, item.stateAccess().read(stack).orElseThrow());

        BannerInstanceState missingColour = new BannerInstanceState(
                original.schemaVersion(), original.bannerDefinitionId(), original.materialId(),
                ResolvedColourId.parse("britannia_mod:removed_colour"), original.sourcePigmentId(), original.mountId());
        stack.set(Milestone7RegisteredTestContent.component(), missingColour);
        assertEquals(BannerPlacementFailure.COLOUR_MISSING,
                plan(stack, production, true, Direction.NORTH, new FakeWorld()).failure());
        assertEquals(missingColour, item.stateAccess().read(stack).orElseThrow());
    }

    @Test
    void missingDisabledAndUnsupportedMountAreDistinct() {
        ItemStack iron = natural(SMALL, IRON);
        assertEquals(BannerPlacementFailure.MOUNT_MISSING,
                plan(iron, RegistrySnapshotTestFactory.withoutMount(production, IRON), true,
                        Direction.NORTH, new FakeWorld()).failure());
        assertEquals(BannerPlacementFailure.MOUNT_DISABLED,
                plan(iron, RegistrySnapshotTestFactory.withDisabledMount(production, IRON), true,
                        Direction.NORTH, new FakeWorld()).failure());
        BannerDefinition source = production.banners().require(SMALL);
        BannerDefinition brassOnly = copy(source, source.dimensions(), source.supportedOrientations(), List.of(BRASS));
        assertEquals(BannerPlacementFailure.UNSUPPORTED_MOUNT,
                plan(iron, RegistrySnapshotTestFactory.replaceBanner(production, brassOnly), true,
                        Direction.NORTH, new FakeWorld()).failure());
    }

    @Test
    void unsupportedOrientationIsRejectedBeforeWorldChecksAndMultiCellUsesTheFootprintPath() {
        BannerDefinition source = production.banners().require(SMALL);
        BannerDefinition perpendicular = copy(source, source.dimensions(),
                List.of(BannerOrientation.WALL_PERPENDICULAR), source.supportedMounts());
        FakeWorld world = new FakeWorld();
        world.replaceable = false;
        assertEquals(BannerPlacementFailure.UNSUPPORTED_ORIENTATION,
                plan(natural(SMALL), RegistrySnapshotTestFactory.replaceBanner(production, perpendicular), true,
                        Direction.NORTH, world).failure());
        assertEquals(BannerPlacementFailure.TARGET_OCCUPIED,
                plan(natural(MULTI), production, true, Direction.NORTH, world).failure());
        assertTrue(world.worldChecks > 0);
    }

    @Test
    void verticalFaceOccupiedBoundsSupportAndProtectionFailuresAreDistinct() {
        assertEquals(BannerPlacementFailure.INVALID_CLICKED_FACE,
                plan(natural(SMALL), production, true, Direction.UP, new FakeWorld()).failure());
        FakeWorld occupied = new FakeWorld(); occupied.replaceable = false;
        assertEquals(BannerPlacementFailure.TARGET_OCCUPIED,
                plan(natural(SMALL), production, true, Direction.NORTH, occupied).failure());
        FakeWorld bounds = new FakeWorld(); bounds.inBounds = false;
        assertEquals(BannerPlacementFailure.WORLD_BOUND_FAILURE,
                plan(natural(SMALL), production, true, Direction.NORTH, bounds).failure());
        FakeWorld support = new FakeWorld(); support.support = false;
        assertEquals(BannerPlacementFailure.INVALID_WALL_SUPPORT,
                plan(natural(SMALL), production, true, Direction.NORTH, support).failure());
        FakeWorld denied = new FakeWorld(); denied.allowed = false;
        assertEquals(BannerPlacementFailure.PROTECTED_PLACEMENT,
                plan(natural(SMALL), production, true, Direction.NORTH, denied).failure());
    }

    @Test
    void blockEntityCreationAndStateTransferPreflightFailuresAreDistinct() {
        FakeWorld noEntity = new FakeWorld(); noEntity.canCreate = false;
        assertEquals(BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE,
                plan(natural(SMALL), production, true, Direction.NORTH, noEntity).failure());
        FakeWorld noTransfer = new FakeWorld(); noTransfer.canAccept = false;
        assertEquals(BannerPlacementFailure.STATE_TRANSFER_FAILURE,
                plan(natural(SMALL), production, true, Direction.NORTH, noTransfer).failure());
    }

    private static BannerPlacementPlanningResult plan(
            ItemStack stack, RegistrySnapshot snapshot, boolean available, Direction face, FakeWorld world) {
        return BannerPlacementPlanner.plan(item, stack, snapshot, available, BlockPos.ZERO, face, block, partBlock, world);
    }

    private static ItemStack natural(BannerDefinitionId id) {
        return factory.naturalCottonAdminBanner(id, production, true).stack().orElseThrow();
    }

    private static ItemStack natural(BannerDefinitionId id, MountId mount) {
        return factory.craftedMaterialBanner(id, COTTON, Optional.of(mount), production, true)
                .stack().orElseThrow();
    }

    private static BannerDefinition copy(
            BannerDefinition source, BannerDimensions dimensions,
            List<BannerOrientation> orientations, List<MountId> mounts) {
        return new BannerDefinition(source.schemaVersion(), source.id(), source.displayNameKey(),
                source.contentStatus(), source.sourceReference(), source.catalogueGroup(), dimensions, orientations,
                mounts, mounts.getFirst(), source.defaultMaterial(), source.assets(), source.placementProfile());
    }

    private static final class FakeWorld implements BannerPlacementWorld {
        boolean replaceable = true;
        boolean inBounds = true;
        boolean support = true;
        boolean allowed = true;
        boolean canCreate = true;
        boolean canAccept = true;
        boolean loaded = true;
        int worldChecks;
        int mutations;

        @Override public BlockState blockState(BlockPos pos) { worldChecks++; return Blocks.SHORT_GRASS.defaultBlockState(); }
        @Override public boolean targetReplaceable(BlockPos pos) { worldChecks++; return replaceable; }
        @Override public boolean inWorldBounds(BlockPos pos) { worldChecks++; return inBounds; }
        @Override public boolean chunkLoaded(BlockPos pos) { worldChecks++; return loaded; }
        @Override public boolean unrelatedBannerCell(BlockPos pos) { worldChecks++; return false; }
        @Override public boolean validWallSupport(BlockPos pos, Direction facing) { worldChecks++; return support; }
        @Override public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack) { worldChecks++; return allowed; }
        @Override public boolean canCreateBannerBlockEntity(BlockState state) { worldChecks++; return canCreate; }
        @Override public boolean canEncodePart(BlockState state) { worldChecks++; return true; }
        @Override public boolean canAcceptState(BannerInstanceState state) { worldChecks++; return canAccept; }
    }
}

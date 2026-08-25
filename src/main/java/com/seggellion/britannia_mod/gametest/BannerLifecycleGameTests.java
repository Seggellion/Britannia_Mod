package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.item.BannerItemFactoryResult;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerRemovalCause;
import com.seggellion.britannia_mod.banner.structure.BannerStructureLifecycle;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Placed-banner lifecycle coverage for the orientation, dye, and mount repairs: the complete
 * item -> place -> restyle -> save/reload -> break -> item journey, exercised through the real
 * placement service, the real Interior Decorator wiring, and the real removal lifecycle.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BannerLifecycleGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    /** Own batch: keeps the default batch's composition identical to the pre-banner baseline. */
    private static final String BATCH = "banner_lifecycle";
    /** Perpendicular-only 1x2 medium. */
    private static final BannerDefinitionId ARGENT_SHIELD = BannerDefinitionId.parse("britannia_mod:argent_shield");
    /** Parallel-only 1x2 medium-wall. */
    private static final BannerDefinitionId ANKH_PENNON = BannerDefinitionId.parse("britannia_mod:ankh_pennon");
    /** Perpendicular-only 1x1 x-small (owner ruling 2026-08-24: non-wall families hang perpendicular). */
    private static final BannerDefinitionId ROAD_GUARD = BannerDefinitionId.parse("britannia_mod:road_guard");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static final MountId IRON = MountId.parse("britannia_mod:iron");
    private static final ResolvedColourId COTTON_CHARCOAL = ResolvedColourId.parse("britannia_mod:cotton_charcoal");
    private static final ResolvedColourId COTTON_IVORY = ResolvedColourId.parse("britannia_mod:cotton_ivory");

    private BannerLifecycleGameTests() {
    }

    private static ServerPlayer pinnedPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        return player;
    }

    /** Builds a 3x3 stone wall whose north face carries the banners; returns the wall column pos. */
    private static BlockPos buildWall(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE.defaultBlockState());
            }
        }
        return new BlockPos(3, 3, 5);
    }

    private static ItemStack banner(GameTestHelper helper, BannerDefinitionId definition, MountId mount) {
        BannerItem item = BannerItemRegistry.BANNER.get();
        BannerItemFactory factory = new BannerItemFactory(item, item.stateAccess());
        var snapshot = BannerDataRegistries.current();
        BannerInstanceState natural = new BannerInstanceState(
                1, definition,
                com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants.DEFAULT_COTTON_MATERIAL_ID,
                snapshot.fabricMaterials().require(
                        com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants.DEFAULT_COTTON_MATERIAL_ID)
                        .naturalColourId(),
                Optional.empty(), mount);
        BannerItemFactoryResult result = factory.fullySpecifiedBanner(
                natural.bannerDefinitionId(), natural.materialId(), natural.resolvedColourId(),
                natural.sourcePigmentId(), natural.mountId(), snapshot, BannerDataRegistries.isAvailable());
        if (!result.successful()) {
            helper.fail("banner item factory refused " + definition + ": " + result.failure());
        }
        return result.stack().orElseThrow();
    }

    private static void dyeItem(GameTestHelper helper, ItemStack stack, ResolvedColourId colour) {
        BannerItem item = BannerItemRegistry.BANNER.get();
        var plan = item.stateAccess().planColourUpdate(stack, colour, Optional.<PigmentId>empty(),
                BannerDataRegistries.current(), BannerDataRegistries.isAvailable());
        if (!item.stateAccess().applyColourUpdate(stack, plan)) {
            helper.fail("colour update refused for " + colour);
        }
    }

    /** Places by right-clicking the wall block's north face with the held banner. */
    private static BlockPos place(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos wall) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absoluteWall = helper.absolutePos(wall);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absoluteWall).add(0, 0, -0.5), Direction.NORTH, absoluteWall, false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            helper.fail("banner placement refused: " + result);
        }
        return wall.relative(Direction.NORTH);
    }

    private static BannerBlockEntity anchorEntity(GameTestHelper helper, BlockPos anchor) {
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(anchor));
        if (!(entity instanceof BannerBlockEntity banner)) {
            helper.fail("no banner block entity at " + anchor);
            throw new IllegalStateException("unreachable");
        }
        return banner;
    }

    private static BannerInstanceState state(GameTestHelper helper, BannerBlockEntity entity) {
        return entity.bannerState().orElseGet(() -> {
            helper.fail("banner block entity holds no instance state");
            throw new IllegalStateException("unreachable");
        });
    }

    private static void cycleMountWithDecorator(GameTestHelper helper, ServerPlayer player, BlockPos cell) {
        ItemStack tool = new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BlockPos absolute = helper.absolutePos(cell);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
        var result = tool.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            helper.fail("decorator mount cycle refused: " + result);
        }
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void aperpendicularbannerplacesrendersandsurvivesreloadperpendicular(GameTestHelper helper) {
        ServerPlayer player = pinnedPlayer(helper);
        BlockPos wall = buildWall(helper);
        BlockPos anchor = place(helper, player, banner(helper, ARGENT_SHIELD, BRASS), wall);

        helper.assertBlockProperty(anchor, BannerBlock.ORIENTATION, BannerOrientation.WALL_PERPENDICULAR);
        BlockPos part = anchor.below();
        helper.assertBlockProperty(part, BannerPartBlock.ORIENTATION, BannerOrientation.WALL_PERPENDICULAR);
        BannerBlockEntity entity = anchorEntity(helper, anchor);
        var structure = entity.placedStructure().orElseThrow();
        if (structure.orientation() != BannerOrientation.WALL_PERPENDICULAR) {
            helper.fail("placed structure lost its perpendicular orientation");
        }

        // Chunk save/reload and the vanilla client sync packet share saveWithoutMetadata: a
        // freshly loaded entity must reproduce the orientation, structure, and instance state.
        ServerLevel level = helper.getLevel();
        var tag = entity.saveWithoutMetadata(level.registryAccess());
        BannerBlockEntity reloaded = new BannerBlockEntity(
                helper.absolutePos(anchor), level.getBlockState(helper.absolutePos(anchor)));
        reloaded.loadWithComponents(tag, level.registryAccess());
        if (reloaded.placedStructure().orElseThrow().orientation() != BannerOrientation.WALL_PERPENDICULAR) {
            helper.fail("perpendicular orientation did not survive the save/load round trip");
        }
        if (!state(helper, reloaded).equals(state(helper, entity))) {
            helper.fail("instance state did not survive the save/load round trip");
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void parallelandperpendicularbannersdisagreeinstateandshape(GameTestHelper helper) {
        ServerPlayer player = pinnedPlayer(helper);
        buildWall(helper);
        BlockPos parallelAnchor = place(helper, player, banner(helper, ANKH_PENNON, BRASS), new BlockPos(2, 3, 5));
        BlockPos perpendicularAnchor = place(helper, player, banner(helper, ARGENT_SHIELD, BRASS), new BlockPos(4, 3, 5));

        helper.assertBlockProperty(parallelAnchor, BannerBlock.ORIENTATION, BannerOrientation.WALL_PARALLEL);
        helper.assertBlockProperty(perpendicularAnchor, BannerBlock.ORIENTATION, BannerOrientation.WALL_PERPENDICULAR);
        ServerLevel level = helper.getLevel();
        var parallelShape = level.getBlockState(helper.absolutePos(parallelAnchor))
                .getShape(level, helper.absolutePos(parallelAnchor));
        var perpendicularShape = level.getBlockState(helper.absolutePos(perpendicularAnchor))
                .getShape(level, helper.absolutePos(perpendicularAnchor));
        if (parallelShape.bounds().equals(perpendicularShape.bounds())) {
            helper.fail("parallel and perpendicular anchors share one collision envelope");
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void nonwallbannersplaceperpendicularwithoutanycycling(GameTestHelper helper) {
        // The regression the owner observed in-game: small and x-small banners carried a
        // dual-orientation scaffold default, so they placed parallel every session. They are
        // non-wall designs and must place perpendicular straight from the item, no sneak-cycle.
        ServerPlayer player = pinnedPlayer(helper);
        BlockPos wall = buildWall(helper);
        ItemStack stack = banner(helper, ROAD_GUARD, BRASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        BlockPos absoluteWall = helper.absolutePos(wall);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absoluteWall).add(0, 0, -0.5), Direction.NORTH, absoluteWall, false);
        // Sneak-use still answers (only-supported-mode feedback) and must not place or crash.
        player.setShiftKeyDown(true);
        var cycled = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setShiftKeyDown(false);
        if (!cycled.consumesAction()) {
            helper.fail("sneak-use orientation feedback refused: " + cycled);
        }
        if (helper.getLevel().getBlockState(helper.absolutePos(wall.relative(Direction.NORTH)))
                .getBlock() instanceof BannerBlock) {
            helper.fail("sneak-use placed the banner instead of reporting orientation");
        }
        BlockPos anchor = place(helper, player, stack, wall);
        helper.assertBlockProperty(anchor, BannerBlock.ORIENTATION, BannerOrientation.WALL_PERPENDICULAR);
        helper.succeed();
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void mountcyclepreservesdyeorientationandfacingandresolveschildcells(GameTestHelper helper) {
        ServerPlayer player = pinnedPlayer(helper);
        BlockPos wall = buildWall(helper);
        ItemStack stack = banner(helper, ARGENT_SHIELD, BRASS);
        dyeItem(helper, stack, COTTON_CHARCOAL);
        BlockPos anchor = place(helper, player, stack, wall);
        BannerBlockEntity entity = anchorEntity(helper, anchor);
        BannerInstanceState before = state(helper, entity);
        if (!before.mountId().equals(BRASS) || !before.resolvedColourId().equals(COTTON_CHARCOAL)) {
            helper.fail("placement did not carry the dyed brass state into the world");
        }
        BlockState anchorStateBefore = helper.getLevel().getBlockState(helper.absolutePos(anchor));

        // Cycle by clicking the CHILD cell: the interaction must resolve the anchor.
        cycleMountWithDecorator(helper, player, anchor.below());
        BannerInstanceState afterCycle = state(helper, anchorEntity(helper, anchor));
        if (!afterCycle.mountId().equals(IRON)) {
            helper.fail("decorator did not cycle brass to iron, got " + afterCycle.mountId());
        }
        if (!afterCycle.resolvedColourId().equals(COTTON_CHARCOAL)
                || !afterCycle.materialId().equals(before.materialId())
                || !afterCycle.bannerDefinitionId().equals(before.bannerDefinitionId())) {
            helper.fail("mount cycle disturbed dye, material, or definition state");
        }
        BlockState anchorStateAfter = helper.getLevel().getBlockState(helper.absolutePos(anchor));
        if (!anchorStateAfter.equals(anchorStateBefore)) {
            helper.fail("mount cycle mutated the anchor block state (facing/orientation must not move)");
        }
        var structure = anchorEntity(helper, anchor).placedStructure().orElseThrow();
        if (structure.orientation() != BannerOrientation.WALL_PERPENDICULAR) {
            helper.fail("mount cycle disturbed the placed structure orientation");
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void brokenbannersrecovertheirfullconfigurationandreplaceidentically(GameTestHelper helper) {
        ServerPlayer player = pinnedPlayer(helper);
        BlockPos wall = buildWall(helper);
        ItemStack stack = banner(helper, ARGENT_SHIELD, BRASS);
        dyeItem(helper, stack, COTTON_IVORY);
        BlockPos anchor = place(helper, player, stack, wall);
        cycleMountWithDecorator(helper, player, anchor);
        BannerInstanceState configured = state(helper, anchorEntity(helper, anchor));

        ServerLevel level = helper.getLevel();
        BlockPos absoluteAnchor = helper.absolutePos(anchor);
        var removal = BannerStructureLifecycle.removeFrom(
                level, absoluteAnchor, level.getBlockState(absoluteAnchor),
                BannerRemovalCause.SURVIVAL_PLAYER, player);
        if (!removal.claimed() || removal.drops() != 1) {
            helper.fail("removal did not drop exactly one configured banner: " + removal);
        }
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class, new AABB(absoluteAnchor).inflate(3.0),
                drop -> drop.getItem().getItem() instanceof BannerItem);
        if (drops.size() != 1) {
            helper.fail("expected exactly one dropped banner item, found " + drops.size());
        }
        ItemStack recovered = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        BannerInstanceState recoveredState = BannerItemRegistry.BANNER.get().stateAccess()
                .read(recovered).orElseThrow();
        if (!recoveredState.equals(configured)) {
            helper.fail("recovered item lost configuration: " + recoveredState + " vs " + configured);
        }

        BlockPos replacedAnchor = place(helper, player, recovered, wall);
        BannerInstanceState replaced = state(helper, anchorEntity(helper, replacedAnchor));
        if (!replaced.equals(configured)) {
            helper.fail("replaced banner lost configuration: " + replaced + " vs " + configured);
        }
        helper.assertBlockProperty(replacedAnchor, BannerBlock.ORIENTATION, BannerOrientation.WALL_PERPENDICULAR);
        helper.succeed();
    }

    @GameTest(batch = BATCH, template = TEMPLATE)
    public static void thedecoratorneverrotatesabannercelloutofitsstructure(GameTestHelper helper) {
        ServerPlayer player = pinnedPlayer(helper);
        BlockPos wall = buildWall(helper);
        BlockPos anchor = place(helper, player, banner(helper, ANKH_PENNON, BRASS), wall);
        BlockState before = helper.getLevel().getBlockState(helper.absolutePos(anchor));

        // Before the banner branch existed, this click fell into the generic FACING rotation
        // and spun one cell out of its own multi-block structure.
        cycleMountWithDecorator(helper, player, anchor);
        BlockState after = helper.getLevel().getBlockState(helper.absolutePos(anchor));
        if (!after.equals(before)) {
            helper.fail("decorator interaction changed the anchor block state");
        }
        if (!(helper.getLevel().getBlockState(helper.absolutePos(anchor.below()))
                .getBlock() instanceof BannerPartBlock)) {
            helper.fail("banner part vanished after decorator interaction");
        }
        helper.succeed();
    }
}

package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewEvaluator;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Live-world proof that M4's inspection seam is wholly non-mutating. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ResourceDepositPreviewGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private ResourceDepositPreviewGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void previewInspectionDoesNotMutateBlocksLedgersOrRestoration(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        ResourceDefinition silver = ResourceCatalog.instance().byPath("silver").orElseThrow();
        PlannedDeposit deposit = PlacementPlanner.plan(
                silver, level.dimension().location().toString(), origin,
                2, ShapeRotation.ZW, 1234L);
        for (BlockPos pos : deposit.positions()) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }

        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        deposit.positions().forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
        int ledgerBefore = DepositLedger.get(level).size();
        int restorationBefore = BrokenBlockDataStorage.get(level).getBrokenBlocks().size();

        MaterializationService.Inspection inspection = MaterializationService.inspect(level, deposit);

        check(inspection.planned() == deposit.count(), "inspection must cover the complete plan");
        check(inspection.validHosts() == deposit.count(),
                "stone candidates must all be eligible hosts");
        check(inspection.rejectedHosts() == 0, "stone candidates must not be rejected");
        check(DepositLedger.get(level).size() == ledgerBefore,
                "preview inspection must not register or update a deposit");
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().size() == restorationBefore,
                "preview inspection must not create restoration debt");
        before.forEach((pos, state) -> check(level.getBlockState(pos).equals(state),
                "preview inspection changed " + pos.toShortString()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void previewEvaluatorResolvesYDeterministicallyWithoutPersistentMutation(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos requested = helper.absolutePos(new BlockPos(2, 2, 2));
        UUID serverKey = UUID.fromString("44444444-4444-4444-8444-444444444444");
        ResourceDepositPreviewProtocol.Request request = new ResourceDepositPreviewProtocol.Request(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                7, "silver",
                new ResourceDepositPreviewProtocol.Target(
                        UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        serverKey, "Britannia", level.dimension().location().toString()),
                requested.getX(), requested.getZ());
        int ledgerBefore = DepositLedger.get(level).size();
        int restorationBefore = BrokenBlockDataStorage.get(level).getBrokenBlocks().size();

        ResourceDepositPreviewProtocol.Evaluation first = evaluation(
                ResourceDepositPreviewEvaluator.evaluate(level.getServer(), request, serverKey));
        check(first.resolvedY() >= level.getMinBuildHeight()
                        && first.resolvedY() < level.getMaxBuildHeight(),
                "preview must resolve Y inside the live dimension");
        check(first.plannedBlockCount() > 0, "preview must return the planner's footprint count");
        check(first.validHostCount() + first.rejectedHostCount() == first.plannedBlockCount(),
                "preview host counts must cover the complete deterministic plan");

        Map<BlockPos, BlockState> beforeRepeat = new LinkedHashMap<>();
        ResourceDepositPreviewProtocol.Bounds bounds = first.footprint();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    beforeRepeat.put(pos, level.getBlockState(pos));
                }
            }
        }
        ResourceDepositPreviewProtocol.Evaluation repeated = evaluation(
                ResourceDepositPreviewEvaluator.evaluate(level.getServer(), request, serverKey));

        check(first.equals(repeated), "unchanged request and world must produce the same preview");
        check(DepositLedger.get(level).size() == ledgerBefore,
                "preview evaluation must not register or update a deposit");
        check(BrokenBlockDataStorage.get(level).getBrokenBlocks().size() == restorationBefore,
                "preview evaluation must not create restoration debt");
        beforeRepeat.forEach((pos, state) -> check(level.getBlockState(pos).equals(state),
                "repeat preview evaluation changed " + pos.toShortString()));
        helper.succeed();
    }

    private static ResourceDepositPreviewProtocol.Evaluation evaluation(
            ResourceDepositPreviewProtocol.Outcome outcome) {
        if (outcome instanceof ResourceDepositPreviewProtocol.Evaluation result) return result;
        ResourceDepositPreviewProtocol.Failure failure =
                (ResourceDepositPreviewProtocol.Failure) outcome;
        throw new GameTestAssertException("preview evaluation failed: " + failure.code());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

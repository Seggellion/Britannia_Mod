package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.materialization.ResourceDepositMaterializationProtocol;
import com.seggellion.britannia_mod.resource.materialization.ResourceDepositMaterializer;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewEvaluator;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
import com.seggellion.britannia_mod.resource.removal.ResourceDepositRemovalInspector;
import com.seggellion.britannia_mod.resource.removal.ResourceDepositRemovalProtocol;
import com.seggellion.britannia_mod.resource.removal.ResourceDepositRemover;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ResourceDepositRemovalGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID SHARD = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SERVER = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private ResourceDepositRemovalGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void previewIsNonMutatingAndRemovalUsesExactProvenanceThenReplays(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID depositUuid = UUID.randomUUID();
        UUID materializationUuid = UUID.randomUUID();
        ResourceDepositPreviewProtocol.Request request = new ResourceDepositPreviewProtocol.Request(
                UUID.randomUUID(), depositUuid, 7, "gold",
                new ResourceDepositPreviewProtocol.Target(
                        SHARD, SERVER, "Britannia", "minecraft:overworld"),
                helper.absolutePos(new BlockPos(2, 2, 2)).getX(),
                helper.absolutePos(new BlockPos(2, 2, 2)).getZ());

        ResourceDepositPreviewProtocol.Evaluation initial = evaluation(
                ResourceDepositPreviewEvaluator.evaluate(level.getServer(), request, SERVER));
        ResourceDefinition resource = ResourceCatalog.instance().byPath("gold").orElseThrow();
        PlannedDeposit planned = PlacementPlanner.plan(resource,
                level.dimension().location().toString(),
                new BlockPos(request.x(), initial.resolvedY(), request.z()),
                initial.radius(), initial.rotation(),
                ResourceDepositPreviewEvaluator.previewSeed(request));
        BlockPos playerCell = planned.positions().get(0);
        BlockPos removableCell = planned.positions().get(1);
        BlockPos depletedCell = planned.positions().get(2);
        level.setBlock(playerCell, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(removableCell, Blocks.STONE.defaultBlockState(), 2);

        ResourceDepositPreviewProtocol.Evaluation approved = evaluation(
                ResourceDepositPreviewEvaluator.evaluate(level.getServer(), request, SERVER));
        ResourceDepositMaterializationProtocol.Operation materialization =
                new ResourceDepositMaterializationProtocol.Operation(
                        materializationUuid, depositUuid, 7, request.previewUuid(), "a".repeat(64),
                        "gold", new ResourceDepositMaterializationProtocol.Target(
                        SHARD, SERVER, "Britannia", "minecraft:overworld"),
                        request.x(), request.z(), approved);
        ResourceDepositMaterializationProtocol.Outcome generatedOutcome =
                ResourceDepositMaterializer.process(level.getServer(), materialization, SERVER);
        check(generatedOutcome instanceof ResourceDepositMaterializationProtocol.Generated,
                "test setup must materialize an exact managed DepositInstance");
        ResourceDepositMaterializationProtocol.Generated generated =
                (ResourceDepositMaterializationProtocol.Generated) generatedOutcome;
        DepositLedger ledger = DepositLedger.get(level);
        DepositInstance instance = ledger.byId(generated.depositInstanceId()).orElseThrow();
        Block resourceBlock = Resources.block(resource.generation().orElseThrow().blockId());
        check(level.getBlockState(playerCell).is(resourceBlock)
                && level.getBlockState(removableCell).is(resourceBlock),
                "both approved hosts must be managed resource blocks");

        MiningProvenance.markPlayerPlaced(level, playerCell);
        level.setBlock(depletedCell, Blocks.AIR.defaultBlockState(), 2);
        BrokenBlockDataStorage debts = BrokenBlockDataStorage.get(level);
        debts.add(new BrokenBlockData(depletedCell, resourceBlock.defaultBlockState(),
                System.currentTimeMillis(), UUID.randomUUID(), Long.MAX_VALUE,
                instance.instanceId(), instance.resourceId(), 0L, 0));
        BlockPos nearbySameBlock = instance.boundsMax().offset(5, 0, 5);
        level.setBlock(nearbySameBlock, resourceBlock.defaultBlockState(), 2);

        ResourceDepositRemovalProtocol.PreviewRequest removalPreview =
                new ResourceDepositRemovalProtocol.PreviewRequest(
                        UUID.randomUUID(), depositUuid, 7, 7, materializationUuid,
                        Long.toUnsignedString(instance.instanceId(), 16), "gold",
                        new ResourceDepositRemovalProtocol.Target(
                                SHARD, SERVER, "Britannia", "minecraft:overworld"));
        var beforePlayer = level.getBlockState(playerCell);
        var beforeRemovable = level.getBlockState(removableCell);
        var beforeNearby = level.getBlockState(nearbySameBlock);
        ResourceDepositRemovalProtocol.PreviewOutcome previewOutcome =
                ResourceDepositRemovalInspector.evaluate(level.getServer(), removalPreview, SERVER);
        check(previewOutcome instanceof ResourceDepositRemovalProtocol.Inspection,
                "valid exact instance identity must produce a destructive preview");
        ResourceDepositRemovalProtocol.Inspection inspection =
                (ResourceDepositRemovalProtocol.Inspection) previewOutcome;
        check(level.getBlockState(playerCell).equals(beforePlayer)
                        && level.getBlockState(removableCell).equals(beforeRemovable)
                        && level.getBlockState(nearbySameBlock).equals(beforeNearby),
                "destructive preview must be completely non-mutating");
        check(inspection.playerModifiedCellCount() == 1,
                "player-authored standing cell must be classified for preservation");
        check(inspection.depletedCellCount() == 1,
                "exact owned restoration debt must be classified as depleted");
        check(inspection.affectedBlockCount() >= 1,
                "at least one exact standing non-player managed cell must be removable");
        long overlapId = instance.instanceId() == Long.MIN_VALUE
                ? 1L : instance.instanceId() ^ Long.MIN_VALUE;
        DepositInstance overlap = new DepositInstance(overlapId, instance.resourceId(),
                instance.definitionRevision(), com.seggellion.britannia_mod.resource.deposit.DepositSource.RETROFIT,
                "m6-overlap", instance.origin(), instance.seed(), instance.radius(),
                instance.rotation(), instance.boundsMin(), instance.boundsMax(),
                instance.plannedCells(), instance.materializedCells(), instance.blockedCells(),
                instance.materializationVersion());
        check(ledger.register(overlap).mayMaterialize(),
                "test setup must register a second managed deposit with overlapping exact cells");
        ResourceDepositRemovalProtocol.PreviewOutcome overlapOutcome =
                ResourceDepositRemovalInspector.evaluate(level.getServer(), removalPreview, SERVER);
        check(overlapOutcome instanceof ResourceDepositRemovalProtocol.Inspection
                        && !((ResourceDepositRemovalProtocol.Inspection) overlapOutcome).safeToRemove()
                        && !((ResourceDepositRemovalProtocol.Inspection) overlapOutcome)
                        .overlapConflicts().isEmpty(),
                "managed-deposit overlap must make destructive removal unsafe");
        UUID overlapCleanup = UUID.randomUUID();
        check(ledger.beginRemoval(overlapCleanup, overlap, 0).accepted(),
                "test overlap must have a distinct removable ledger identity");
        ledger.completeRemoval(overlapCleanup, System.currentTimeMillis());
        previewOutcome = ResourceDepositRemovalInspector.evaluate(
                level.getServer(), removalPreview, SERVER);
        check(previewOutcome instanceof ResourceDepositRemovalProtocol.Inspection
                        && ((ResourceDepositRemovalProtocol.Inspection) previewOutcome).safeToRemove(),
                "removing the conflicting active identity must restore preview safety");
        inspection = (ResourceDepositRemovalProtocol.Inspection) previewOutcome;

        UUID staleRemovalUuid = UUID.randomUUID();
        ResourceDepositRemovalProtocol.RemovalOperation staleRemoval =
                new ResourceDepositRemovalProtocol.RemovalOperation(
                        staleRemovalUuid, depositUuid, 7, 7, materializationUuid,
                        Long.toUnsignedString(instance.instanceId(), 16),
                        removalPreview.previewUuid(), "c".repeat(64), "gold",
                        removalPreview.target(), inspection);
        level.setBlock(removableCell, Blocks.STONE.defaultBlockState(), 2);
        ResourceDepositRemovalProtocol.RemovalOutcome staleOutcome =
                ResourceDepositRemover.process(level.getServer(), staleRemoval, SERVER);
        check(staleOutcome instanceof ResourceDepositRemovalProtocol.Failure
                        && ((ResourceDepositRemovalProtocol.Failure) staleOutcome).code()
                        .equals("removal_preview_stale"),
                "world change after destructive preview must be refused before mutation");
        check(level.getBlockState(removableCell).is(Blocks.STONE)
                        && ledger.removalByOperation(staleRemovalUuid).isEmpty(),
                "stale refusal must preserve changed content and create no removal identity");
        level.setBlock(removableCell, resourceBlock.defaultBlockState(), 2);
        previewOutcome = ResourceDepositRemovalInspector.evaluate(
                level.getServer(), removalPreview, SERVER);
        check(previewOutcome instanceof ResourceDepositRemovalProtocol.Inspection,
                "a fresh destructive preview must be available after state restoration");
        inspection = (ResourceDepositRemovalProtocol.Inspection) previewOutcome;

        UUID removalUuid = UUID.randomUUID();
        ResourceDepositRemovalProtocol.RemovalOperation removal =
                new ResourceDepositRemovalProtocol.RemovalOperation(
                        removalUuid, depositUuid, 7, 7, materializationUuid,
                        Long.toUnsignedString(instance.instanceId(), 16),
                        removalPreview.previewUuid(), "b".repeat(64), "gold",
                        removalPreview.target(), inspection);
        ResourceDepositRemovalProtocol.RemovalOutcome first =
                ResourceDepositRemover.process(level.getServer(), removal, SERVER);
        check(first instanceof ResourceDepositRemovalProtocol.Removed,
                "approved provenance-aware removal must complete");
        ResourceDepositRemovalProtocol.Removed removed =
                (ResourceDepositRemovalProtocol.Removed) first;
        check(removed.removedBlockCount() == inspection.affectedBlockCount()
                        && removed.depletedDebtCount() == 1,
                "result must report every approved matching block and one cancelled exact debt");
        check(level.getBlockState(removableCell).isAir(),
                "exact managed standing cell must be removed");
        check(level.getBlockState(playerCell).is(resourceBlock),
                "player-modified cell must be preserved");
        check(level.getBlockState(nearbySameBlock).is(resourceBlock),
                "same block type outside the deterministic plan must be preserved");
        check(debts.debtAt(depletedCell) == null,
                "only the exact instance-owned restoration debt must be cancelled");
        check(DepositLedger.get(level).byId(instance.instanceId()).isEmpty(),
                "completed removal must replace active ownership with a tombstone");

        ResourceDepositRemovalProtocol.RemovalOutcome replay =
                ResourceDepositRemover.process(level.getServer(), removal, SERVER);
        check(replay instanceof ResourceDepositRemovalProtocol.Removed
                        && ((ResourceDepositRemovalProtocol.Removed) replay).replayed(),
                "same operation UUID must replay the durable tombstone without another mutation");
        check(level.getBlockState(playerCell).is(resourceBlock)
                        && level.getBlockState(nearbySameBlock).is(resourceBlock),
                "tombstone replay must preserve all previously preserved cells");
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

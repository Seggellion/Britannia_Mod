package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.materialization.ResourceDepositMaterializationProtocol;
import com.seggellion.britannia_mod.resource.materialization.ResourceDepositMaterializer;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewEvaluator;
import com.seggellion.britannia_mod.resource.preview.ResourceDepositPreviewProtocol;
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

/**
 * In-world M5 proof: approval is revalidated before the first write, one operation owns one
 * DepositInstance, and replay acknowledges the persisted result without duplicating terrain.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ResourceDepositMaterializationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID SHARD =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SERVER =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    private ResourceDepositMaterializationGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void approvedOperationMaterializesOnceAndReplayIsIdempotent(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceDepositPreviewProtocol.Request request = request(
                helper.absolutePos(new BlockPos(2, 2, 2)),
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"), 7);
        Approved approved = approveOneHost(level, request);
        ResourceDepositMaterializationProtocol.Operation operation = operation(
                request, approved.evaluation(),
                UUID.randomUUID());
        DepositLedger ledger = DepositLedger.get(level);
        int ledgerBefore = ledger.size();

        ResourceDepositMaterializationProtocol.Outcome first =
                ResourceDepositMaterializer.process(level.getServer(), operation, SERVER);
        check(first instanceof ResourceDepositMaterializationProtocol.Generated,
                "approved operation must produce a generated acknowledgment");
        ResourceDepositMaterializationProtocol.Generated generated =
                (ResourceDepositMaterializationProtocol.Generated) first;
        check(!generated.replayed(), "first completion must not be marked replayed");
        check(generated.materializedBlockCount() > 0,
                "success must report at least one actual managed block");
        check(generated.materializedBlockCount() + generated.blockedBlockCount()
                        == generated.plannedBlockCount(),
                "actual counts must partition the deterministic plan");
        check(ledger.size() == ledgerBefore + 1,
                "one approved operation must register exactly one DepositInstance");

        Block resourceBlock = Resources.block(
                ResourceCatalog.instance().byPath("silver").orElseThrow()
                        .generation().orElseThrow().blockId());
        check(level.getBlockState(approved.host()).is(resourceBlock),
                "the only eligible host must be written through guarded placement");

        ResourceDepositMaterializationProtocol.Outcome repeated =
                ResourceDepositMaterializer.process(level.getServer(), operation, SERVER);
        check(repeated instanceof ResourceDepositMaterializationProtocol.Generated,
                "exact replay must rediscover the acknowledged result");
        ResourceDepositMaterializationProtocol.Generated replayed =
                (ResourceDepositMaterializationProtocol.Generated) repeated;
        check(replayed.replayed(), "second completion must be identified as replay");
        check(replayed.depositInstanceId() == generated.depositInstanceId(),
                "replay must correlate to the same DepositInstance");
        check(ledger.size() == ledgerBefore + 1,
                "replay must not create another ledger entry");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void changedWorldFailsBeforeRegistrationOrResourceWrites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceDepositPreviewProtocol.Request request = request(
                helper.absolutePos(new BlockPos(2, 2, 2)),
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                UUID.fromString("77777777-7777-4777-8777-777777777777"), 3);
        Approved approved = approveOneHost(level, request);
        ResourceDepositMaterializationProtocol.Operation operation = operation(
                request, approved.evaluation(),
                UUID.fromString("88888888-8888-4888-8888-888888888888"));
        int ledgerBefore = DepositLedger.get(level).size();

        level.setBlock(approved.host(), Blocks.BEDROCK.defaultBlockState(), 2);
        ResourceDepositMaterializationProtocol.Outcome outcome =
                ResourceDepositMaterializer.process(level.getServer(), operation, SERVER);
        check(outcome instanceof ResourceDepositMaterializationProtocol.Failure,
                "world drift must produce a structured failure");
        ResourceDepositMaterializationProtocol.Failure failure =
                (ResourceDepositMaterializationProtocol.Failure) outcome;
        check(failure.code().equals("preview_world_changed"),
                "world drift must be distinguished from transport failure");
        check(DepositLedger.get(level).size() == ledgerBefore,
                "failed revalidation must occur before ledger registration");
        check(level.getBlockState(approved.host()).is(Blocks.BEDROCK),
                "failed revalidation must not mutate the changed cell");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void existingManagedDepositConflictRefusesDuplicatePlacement(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceDepositPreviewProtocol.Request request = request(
                helper.absolutePos(new BlockPos(2, 2, 2)),
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), 4);
        Approved approved = approveOneHost(level, request);
        ResourceDefinition resource = ResourceCatalog.instance().byPath("silver").orElseThrow();
        PlannedDeposit plan = PlacementPlanner.plan(resource,
                level.dimension().location().toString(),
                new BlockPos(request.x(), approved.evaluation().resolvedY(), request.z()),
                approved.evaluation().radius(), approved.evaluation().rotation(),
                ResourceDepositPreviewEvaluator.previewSeed(request));
        DepositLedger ledger = DepositLedger.get(level);
        long blockerId = DepositIdentity.hash64("m5-conflict|" + UUID.randomUUID());
        DepositLedger.Registration blocker = ledger.register(DepositRegistrar.describe(
                plan, blockerId, DepositSource.ADMIN, "m5-gametest-conflict"));
        check(blocker.mayMaterialize(), "test setup must register one conflicting managed deposit");
        int ledgerWithBlocker = ledger.size();

        UUID operationUuid = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        ResourceDepositMaterializationProtocol.Operation operation =
                operation(request, approved.evaluation(), operationUuid);
        long requestedInstance = DepositIdentity.worldAdmin(
                operationUuid, request.resourceDepositUuid(), request.resourceDepositRevision(),
                level.dimension().location().toString(), resource.id());
        ResourceDepositMaterializationProtocol.Outcome outcome =
                ResourceDepositMaterializer.process(level.getServer(), operation, SERVER);

        check(outcome instanceof ResourceDepositMaterializationProtocol.Failure,
                "a newly conflicting managed deposit must refuse materialization");
        check(((ResourceDepositMaterializationProtocol.Failure) outcome).code()
                        .equals("preview_world_changed"),
                "a conflict introduced after approval must be reported as changed preview state");
        check(ledger.size() == ledgerWithBlocker,
                "conflict refusal must not register the requested DepositInstance");
        check(ledger.byId(requestedInstance).isEmpty(),
                "conflict refusal must leave no ownership record for the requested operation");
        check(level.getBlockState(approved.host()).is(Blocks.STONE),
                "conflict refusal must not place resource blocks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void invalidTargetAndResourceFailWithoutRegistrationOrWrites(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceDepositPreviewProtocol.Request request = request(
                helper.absolutePos(new BlockPos(2, 2, 2)),
                UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"), 5);
        Approved approved = approveOneHost(level, request);
        ResourceDepositMaterializationProtocol.Operation valid = operation(
                request, approved.evaluation(),
                UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"));
        int ledgerBefore = DepositLedger.get(level).size();

        ResourceDepositMaterializationProtocol.Outcome wrongTarget =
                ResourceDepositMaterializer.process(level.getServer(), valid,
                        UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"));
        check(wrongTarget instanceof ResourceDepositMaterializationProtocol.Failure
                        && ((ResourceDepositMaterializationProtocol.Failure) wrongTarget)
                        .code().equals("wrong_target"),
                "server identity mismatch must fail with a stable protocol code");

        ResourceDepositMaterializationProtocol.Operation unknownResource =
                new ResourceDepositMaterializationProtocol.Operation(
                        UUID.fromString("12121212-1212-4212-8212-121212121212"),
                        request.resourceDepositUuid(), request.resourceDepositRevision(),
                        request.previewUuid(),
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "not_in_catalogue",
                        new ResourceDepositMaterializationProtocol.Target(
                                SHARD, SERVER, "Britannia", "minecraft:overworld"),
                        request.x(), request.z(), approved.evaluation());
        ResourceDepositMaterializationProtocol.Outcome missing =
                ResourceDepositMaterializer.process(level.getServer(), unknownResource, SERVER);
        check(missing instanceof ResourceDepositMaterializationProtocol.Failure
                        && ((ResourceDepositMaterializationProtocol.Failure) missing)
                        .code().equals("unknown_resource"),
                "unknown resource must fail with a stable protocol code");
        check(DepositLedger.get(level).size() == ledgerBefore,
                "invalid target/resource must not register a DepositInstance");
        check(level.getBlockState(approved.host()).is(Blocks.STONE),
                "invalid target/resource must not place resource blocks");
        helper.succeed();
    }

    private static Approved approveOneHost(ServerLevel level,
            ResourceDepositPreviewProtocol.Request request) {
        ResourceDepositPreviewProtocol.Evaluation initial = evaluation(
                ResourceDepositPreviewEvaluator.evaluate(level.getServer(), request, SERVER));
        ResourceDefinition resource = ResourceCatalog.instance().byPath("silver").orElseThrow();
        PlannedDeposit plan = PlacementPlanner.plan(resource,
                level.dimension().location().toString(),
                new BlockPos(request.x(), initial.resolvedY(), request.z()),
                initial.radius(), initial.rotation(),
                ResourceDepositPreviewEvaluator.previewSeed(request));
        BlockPos host = plan.positions().getFirst();
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 2);
        ResourceDepositPreviewProtocol.Evaluation approved = evaluation(
                ResourceDepositPreviewEvaluator.evaluate(level.getServer(), request, SERVER));
        check(approved.valid(), "test setup must produce a valid real preview");
        check(approved.validHostCount() > 0, "test setup must expose an eligible host");
        return new Approved(approved, host);
    }

    private static ResourceDepositPreviewProtocol.Request request(
            BlockPos requested, UUID previewUuid, UUID depositUuid, long revision) {
        return new ResourceDepositPreviewProtocol.Request(previewUuid, depositUuid, revision,
                "silver", new ResourceDepositPreviewProtocol.Target(
                        SHARD, SERVER, "Britannia", "minecraft:overworld"),
                requested.getX(), requested.getZ());
    }

    private static ResourceDepositMaterializationProtocol.Operation operation(
            ResourceDepositPreviewProtocol.Request request,
            ResourceDepositPreviewProtocol.Evaluation approved, UUID operationUuid) {
        return new ResourceDepositMaterializationProtocol.Operation(operationUuid,
                request.resourceDepositUuid(), request.resourceDepositRevision(),
                request.previewUuid(),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                request.resourceDefinitionKey(),
                new ResourceDepositMaterializationProtocol.Target(
                        SHARD, SERVER, "Britannia", "minecraft:overworld"),
                request.x(), request.z(), approved);
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

    private record Approved(ResourceDepositPreviewProtocol.Evaluation evaluation, BlockPos host) {}
}

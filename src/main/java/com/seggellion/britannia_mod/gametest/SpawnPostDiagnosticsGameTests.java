package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.GenericVendorEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import com.seggellion.britannia_mod.service.spawn.SpawnPostDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Vendor/Trader Milestone 18: the operator diagnostics snapshot reports real
 * authoritative state — post identity, configuration, assignment, pending
 * outbox depth, and duplicate stamped-projection detection — read-only.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class SpawnPostDiagnosticsGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private SpawnPostDiagnosticsGameTests() {
    }

    /**
     * Credentials naming a shard that is deliberately not the compiled default. A post refuses to
     * record durable work when the shard is unknown, and running against a non-default shard is
     * what makes a compiled-constant regression visible instead of silently agreeing.
     */
    private static void installNonDefaultShardCredentials(GameTestHelper helper) {
        com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.installForGameTesting(
                helper.getLevel().getServer(),
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), java.util.UUID.randomUUID(),
                        "gametest_diagnostics_shard"));
    }

    @GameTest(template = TEMPLATE)
    public static void snapshotReportsConfigurationAssignmentAndDuplicates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        installNonDefaultShardCredentials(helper);
        ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);

        UUID cityId = UUID.randomUUID();
        check(post.applyConfiguration(level, cityId, EconomicNpcTypeKeys.prefixed("baker"),
                        true, post.getConfigurationRevision()) == ServiceNpcSpawnValidationError.NONE,
                "configuration failed");

        UUID worldNpcId = UUID.randomUUID();
        post.applyAssignmentReconciliation(worldNpcId, "Marta", 3L);

        // Two live stamped projections for ONE assignment: a duplication bug
        // the diagnostics must surface.
        for (int i = 0; i < 2; i++) {
            GenericVendorEntity vendor = EntityRegistry.VENDOR.get().create(level);
            check(vendor != null, "could not create vendor projection");
            vendor.setWorldNpcPublicId(worldNpcId);
            vendor.moveTo(absolute.getX() + i, absolute.getY(), absolute.getZ(), 0, 0);
            check(level.addFreshEntity(vendor), "could not add vendor projection");
        }

        SpawnPostDiagnostics.Snapshot snapshot = SpawnPostDiagnostics.collect(level, post);
        check(post.getSpawnPointId().equals(snapshot.postId()), "post id mismatch");
        check(cityId.equals(snapshot.cityPublicId()), "city mismatch");
        check(snapshot.economic() && "baker".equals(snapshot.economicTypeKey()),
                "economic type not decoded");
        check(snapshot.enabled(), "enabled flag lost");
        check(snapshot.configurationRevision() >= 1L, "configuration revision missing");
        check(worldNpcId.equals(snapshot.assignedNpcPublicId())
                        && "Marta".equals(snapshot.assignedNpcDisplayName())
                        && snapshot.assignmentRevision() == 3L,
                "assignment state mismatch");
        check(snapshot.pendingOperationCount() >= 1,
                "the staged registration must appear in the pending outbox count");
        check(snapshot.stampedEntityCount() == 2 && snapshot.duplicateEntitiesDetected(),
                "duplicate stamped projections must be detected, got " + snapshot.stampedEntityCount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void healthyPostReportsSingleProjectionAndNoDuplicates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(3, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        installNonDefaultShardCredentials(helper);
        ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);

        UUID cityId = UUID.randomUUID();
        check(post.applyConfiguration(level, cityId, EconomicNpcTypeKeys.prefixed("wood_trader"),
                        true, post.getConfigurationRevision()) == ServiceNpcSpawnValidationError.NONE,
                "configuration failed");
        UUID worldNpcId = UUID.randomUUID();
        post.applyAssignmentReconciliation(worldNpcId, "Rollo", 1L);

        GenericVendorEntity vendor = EntityRegistry.VENDOR.get().create(level);
        check(vendor != null, "could not create vendor projection");
        vendor.setWorldNpcPublicId(worldNpcId);
        vendor.moveTo(absolute.getX(), absolute.getY(), absolute.getZ(), 0, 0);
        check(level.addFreshEntity(vendor), "could not add vendor projection");

        SpawnPostDiagnostics.Snapshot snapshot = SpawnPostDiagnostics.collect(level, post);
        check(snapshot.stampedEntityCount() == 1 && !snapshot.duplicateEntitiesDetected(),
                "a single healthy projection must not be flagged");
        check(snapshot.lastErrorCode() == null, "healthy post must carry no error code");
        helper.succeed();
    }

    private static ServiceNpcSpawnBlockEntity requirePost(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity) return blockEntity;
        throw new IllegalStateException("no authoritative post entity at " + pos);
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

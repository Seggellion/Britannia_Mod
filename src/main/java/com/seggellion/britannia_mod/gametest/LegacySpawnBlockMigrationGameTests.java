package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.MerchantSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrationLedger;
import com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrator;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingOperation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 16 parity proof: a world containing old
 * Merchant/Trader spawn posts converges onto the authoritative
 * registration/assignment model without losing its NPC configuration, its
 * world data, or its TownPersons -- and stays on legacy behavior whenever the
 * conversion cannot yet complete. These tests are the playbook-required
 * evidence that bypassing the old sourceId/heartbeat path is safe.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class LegacySpawnBlockMigrationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private LegacySpawnBlockMigrationGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void merchantBlockMigratesPreservingConfigurationAndTownPersons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        UUID cityId = UUID.randomUUID();
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(cityId, "Britain")
        )));
        try {
            helper.setBlock(relative, BlockRegistry.MERCHANT_SPAWN_BLOCK.get());
            MerchantSpawnBlockEntity legacy = requireMerchant(level, absolute);
            legacy.setCityName("Britain");
            legacy.setMerchantType("tavernkeeper");
            legacy.setTownPersonAmount(3);

            // A legacy-managed merchant and a legacy townsperson stand at the post.
            Entity merchant = EntityRegistry.TAVERNKEEPER.get().create(level);
            check(merchant != null, "could not create legacy merchant");
            merchant.moveTo(absolute.getX(), absolute.getY(), absolute.getZ(), 0, 0);
            merchant.addTag("britannia_merchant_spawn");
            merchant.addTag("merchant_source_" + legacy.getSourceId().toString().replace("-", ""));
            check(level.addFreshEntity(merchant), "could not add legacy merchant");

            TownPersonEntity townPerson = EntityRegistry.TOWNSPERSON.get().create(level);
            check(townPerson != null, "could not create townsperson");
            townPerson.moveTo(absolute.getX() + 2, absolute.getY(), absolute.getZ(), 0, 0);
            townPerson.addTag("britannia_townsperson_spawn");
            check(level.addFreshEntity(townPerson), "could not add townsperson");

            check(LegacySpawnBlockMigrator.migrateMerchantBlock(level, legacy),
                    "a resolvable legacy merchant block must migrate");

            // The block is now a real authoritative post carrying the mapped config.
            ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
            check(cityId.equals(post.getCityPublicId()), "migrated post lost the city");
            check(EconomicNpcTypeKeys.prefixed("tavernkeeper").equals(post.getServiceNpcTypeKey()),
                    "migrated post lost the merchant type");
            check(post.isEnabled(), "migrated post must be enabled");
            UUID postId = post.getSpawnPointId();
            check(postId != null, "migrated post has no claimed identity");

            // The durable pending UPSERT that registers the post with Rails exists.
            ServiceNpcSpawnPendingRecord pending = ServiceNpcSpawnPendingData.get(level).findPending(postId);
            check(pending != null && pending.operation() == ServiceNpcSpawnPendingOperation.UPSERT,
                    "migration staged no pending Rails registration");
            check(EconomicNpcTypeKeys.prefixed("tavernkeeper").equals(pending.serviceNpcTypeKey()),
                    "pending registration lost the economic key");

            // The rollback receipt preserves the legacy block byte-for-byte.
            LegacySpawnBlockMigrationLedger.Receipt receipt = LegacySpawnBlockMigrationLedger.get(level)
                    .find(level.dimension().location().toString(), absolute);
            check(receipt != null, "migration recorded no rollback receipt");
            check("britannia_mod:merchant_spawn_block".equals(receipt.legacyBlockId()),
                    "receipt lost the legacy block id, got " + receipt.legacyBlockId());
            check("tavernkeeper".equals(receipt.legacyBlockEntityNbt().getString("MerchantType")),
                    "receipt lost the legacy NBT configuration");
            check(receipt.townPersonAmount() == 3,
                    "receipt must preserve townPersonAmount for the future population system");
            check(postId.equals(receipt.migratedPostId()), "receipt must name the replacing post");
            check(LegacySpawnBlockMigrator.OUTCOME_CONFIGURED.equals(receipt.outcome()),
                    "unexpected outcome " + receipt.outcome());

            // Duplicate prevention and owner decision #12, in one scene: the
            // legacy-managed merchant is gone, the townsperson survives.
            check(!merchant.isAlive(), "the legacy-managed merchant must be despawned");
            check(townPerson.isAlive(), "TownPersons must be preserved through migration");
        } finally {
            BootstrapCityRegistryCache.clear();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void traderBlockMigratesWithItsOwnTypeKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(3, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        UUID cityId = UUID.randomUUID();
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(cityId, "Trinsic")
        )));
        try {
            helper.setBlock(relative, BlockRegistry.TRADER_SPAWN_BLOCK.get());
            TraderSpawnBlockEntity legacy = requireTrader(level, absolute);
            legacy.setCityName("Trinsic");
            legacy.setTraderType("wood_trader");

            check(LegacySpawnBlockMigrator.migrateTraderBlock(level, legacy),
                    "a resolvable legacy trader block must migrate");

            ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
            check(EconomicNpcTypeKeys.prefixed("wood_trader").equals(post.getServiceNpcTypeKey()),
                    "trader migration lost the trader type");
            check(cityId.equals(post.getCityPublicId()), "trader migration lost the city");
        } finally {
            BootstrapCityRegistryCache.clear();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unresolvableBlocksStayOnLegacyBehaviorUntouched(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos unconfigured = helper.absolutePos(new BlockPos(1, 1, 3));
        BlockPos unknownCity = helper.absolutePos(new BlockPos(3, 1, 3));
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(UUID.randomUUID(), "Britain")
        )));
        try {
            // Blank city: nothing to migrate; the block stays.
            helper.setBlock(new BlockPos(1, 1, 3), BlockRegistry.MERCHANT_SPAWN_BLOCK.get());
            MerchantSpawnBlockEntity blank = requireMerchant(level, unconfigured);
            check(!LegacySpawnBlockMigrator.migrateMerchantBlock(level, blank),
                    "an unconfigured legacy block must not migrate");
            check(level.getBlockEntity(unconfigured) instanceof MerchantSpawnBlockEntity,
                    "an unconfigured legacy block must remain in place");

            // A city the bootstrap registry does not know: wait, keep legacy behavior.
            helper.setBlock(new BlockPos(3, 1, 3), BlockRegistry.MERCHANT_SPAWN_BLOCK.get());
            MerchantSpawnBlockEntity stranger = requireMerchant(level, unknownCity);
            stranger.setCityName("Atlantis");
            stranger.setMerchantType("baker");
            check(!LegacySpawnBlockMigrator.migrateMerchantBlock(level, stranger),
                    "an unresolvable city must not migrate");
            check(level.getBlockEntity(unknownCity) instanceof MerchantSpawnBlockEntity,
                    "an unresolvable legacy block must remain in place");

            // No receipts were written for either.
            LegacySpawnBlockMigrationLedger ledger = LegacySpawnBlockMigrationLedger.get(level);
            String dimension = level.dimension().location().toString();
            check(ledger.find(dimension, unconfigured) == null && ledger.find(dimension, unknownCity) == null,
                    "no-op migrations must write no receipts");
        } finally {
            BootstrapCityRegistryCache.clear();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void registryUnavailableMeansNoMigrationAtAll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 5));
        BootstrapCityRegistryCache.clear();
        helper.setBlock(new BlockPos(1, 1, 5), BlockRegistry.MERCHANT_SPAWN_BLOCK.get());
        MerchantSpawnBlockEntity legacy = requireMerchant(level, absolute);
        legacy.setCityName("Britain");
        legacy.setMerchantType("baker");
        check(!LegacySpawnBlockMigrator.migrateMerchantBlock(level, legacy),
                "an unavailable city registry must leave legacy behavior running");
        check(level.getBlockEntity(absolute) instanceof MerchantSpawnBlockEntity,
                "the legacy block must remain until the registry is available");
        helper.succeed();
    }

    private static MerchantSpawnBlockEntity requireMerchant(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MerchantSpawnBlockEntity blockEntity) return blockEntity;
        throw new IllegalStateException("no merchant spawn block entity at " + pos);
    }

    private static TraderSpawnBlockEntity requireTrader(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TraderSpawnBlockEntity blockEntity) return blockEntity;
        throw new IllegalStateException("no trader spawn block entity at " + pos);
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

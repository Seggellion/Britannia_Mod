package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ArchitectSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryCache;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Milestone 8: Rails decides whether a city has earned an Architect, and the mod does not.
 *
 * <p>The Architect block used to carry its own economy -- 400 food and 200 wood, polled every
 * 1200 ticks -- and would spawn or despawn on that alone. It was a second opinion that could
 * disagree with Rails in either direction: staffing a city Rails had refused, or emptying one
 * Rails had staffed because the harvest was poor.
 *
 * <p>It now migrates itself onto the authoritative post architecture and stops having opinions.
 * These tests are mostly about the authority boundary rather than the migration mechanics --
 * the migration itself is the same code the merchant and trader blocks already use, and is
 * covered by {@code LegacySpawnBlockMigrationGameTests}. What is new here is that the economic
 * gate is gone, and that its absence is the point rather than an oversight.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArchitectSpawnGatingGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The Rails economic type, verbatim. The only string this milestone turns on. */
    private static final String ARCHITECT_VENDOR = "architect_vendor";

    private ArchitectSpawnGatingGameTests() {
    }

    /* ------------------------------------------------------------------ */
    /*  Registration                                                       */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE)
    public static void thearchitectblockregistersapostasarchitectvendor(GameTestHelper helper) {
        com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.installForGameTesting(
                helper.getLevel().getServer(),
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), java.util.UUID.randomUUID(),
                        "gametest_shard_identity"));
        try {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        UUID cityId = UUID.randomUUID();
        withCity(cityId, () -> {
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Britain");

            check(LegacySpawnBlockMigrator.migrateArchitectBlock(level, legacy),
                    "a resolvable Architect block must migrate onto an authoritative post");

            ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
            check(cityId.equals(post.getCityPublicId()), "the migrated post lost its city");
            check(EconomicNpcTypeKeys.prefixed(ARCHITECT_VENDOR).equals(post.getServiceNpcTypeKey()),
                    "the post must carry economic:architect_vendor, got " + post.getServiceNpcTypeKey());
            check(post.isEnabled(), "the migrated post must be enabled");
            check(post.getSpawnPointId() != null, "the post claimed no identity");

            // Rails cannot invent world coordinates, so the mod is what tells it this post
            // exists. That happens through the ordinary durable outbox, not a housing endpoint.
            ServiceNpcSpawnPendingRecord pending =
                    ServiceNpcSpawnPendingData.get(level).findPending(post.getSpawnPointId());
            check(pending != null && pending.operation() == ServiceNpcSpawnPendingOperation.UPSERT,
                    "migration staged no Rails registration for the Architect post");
            check(EconomicNpcTypeKeys.prefixed(ARCHITECT_VENDOR).equals(pending.serviceNpcTypeKey()),
                    "the registration lost the economic key");
        });
        helper.succeed();
        } finally {
            // Cleared so the rest of the run does not inherit credentials: their presence is
            // what makes every mock-player join attempt a real Rails fetch.
            com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.clear(
                    helper.getLevel().getServer());
        }
    }

    /** Migrating twice does not produce two posts: the block is gone after the first. */
    @GameTest(template = TEMPLATE)
    public static void migratingisidempotentbecausetheblockisreplaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        withCity(UUID.randomUUID(), () -> {
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Britain");
            check(LegacySpawnBlockMigrator.migrateArchitectBlock(level, legacy), "first migration failed");

            UUID firstPostId = requirePost(level, absolute).getSpawnPointId();

            // The legacy entity is detached now. Ticking it again must not mint a second post.
            legacy.tick();
            ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
            check(firstPostId != null && firstPostId.equals(post.getSpawnPointId()),
                    "a second migration pass produced a different post");
        });
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The old gate, and its absence                                      */
    /* ------------------------------------------------------------------ */

    /**
     * The authority boundary, from the side that used to be blocked.
     *
     * <p>A city with no food and no wood at all would have failed the old 400/200 gate outright.
     * The block no longer asks, so it migrates regardless and Rails decides from there. Nothing
     * in the mod reads a city's food or wood on the Architect's behalf any more -- the block does
     * not even hold a reference to the cache that would answer.
     */
    @GameTest(template = TEMPLATE)
    public static void zerofoodandwoodnolongerpreventsanarchitectpost(GameTestHelper helper) {
        com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.installForGameTesting(
                helper.getLevel().getServer(),
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), java.util.UUID.randomUUID(),
                        "gametest_shard_identity"));
        try {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        withCity(UUID.randomUUID(), () -> {
            // No CityFoodSupplyCache entry is seeded for this city, so every food/wood reading
            // available to the mod is absent -- which under the old rule meant "skip the cycle"
            // and under the previous rule before that meant "despawn everything".
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Britain");

            check(LegacySpawnBlockMigrator.migrateArchitectBlock(level, legacy),
                    "a starving city must still get a post; whether it gets an Architect is Rails' "
                    + "decision, not this block's");
            ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
            check(EconomicNpcTypeKeys.prefixed(ARCHITECT_VENDOR).equals(post.getServiceNpcTypeKey()),
                    "the post was created without the economic key");
        });
        helper.succeed();
        } finally {
            // Cleared so the rest of the run does not inherit credentials: their presence is
            // what makes every mock-player join attempt a real Rails fetch.
            com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.clear(
                    helper.getLevel().getServer());
        }
    }

    /**
     * And from the other side: a well-fed city gets no Architect from the mod alone.
     *
     * <p>The block spawns nothing, ever. It has no entity-creation path left at all -- the only
     * thing that can put an Architect in the world is the assignment reconciler acting on a Rails
     * assignment, and no assignment exists here.
     */
    @GameTest(template = TEMPLATE)
    public static void awellfedcitygetsnoarchitectwithoutarailsassignment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        withCity(UUID.randomUUID(), () -> {
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Britain");

            // Tick it repeatedly. Under the old rule a city over the thresholds would have had an
            // Architect standing after the first pass.
            for (int pass = 0; pass < 3; pass++) {
                if (level.getBlockEntity(helper.absolutePos(relative)) instanceof ArchitectSpawnBlockEntity still) {
                    still.tick();
                }
            }

            long architects = level.getEntitiesOfClass(ArchitectEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(relative)).inflate(24.0D)).size();
            check(architects == 0,
                    "the block spawned " + architects + " Architect(s) on its own authority");
        });
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  What migration does and does not disturb                           */
    /* ------------------------------------------------------------------ */

    /**
     * The legacy Architect is removed and the townspeople are not.
     *
     * <p>Both halves matter. Leaving the legacy Architect standing would duplicate the one the
     * assignment pipeline is about to staff; removing the townspeople would take a decision that
     * belongs to {@code TownPersonPopulationManager}, which is the regional system Rails drives.
     * This is the same outcome a migrating merchant block produces.
     */
    @GameTest(template = TEMPLATE)
    public static void migrationremovesthelegacyarchitectbutsparesthetownspeople(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        withCity(UUID.randomUUID(), () -> {
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Britain");

            ArchitectEntity architect = EntityRegistry.ARCHITECT_ENTITY.get().create(level);
            check(architect != null, "could not create a legacy Architect");
            architect.moveTo(absolute.getX(), absolute.getY(), absolute.getZ(), 0, 0);
            check(level.addFreshEntity(architect), "could not add the legacy Architect");

            TownPersonEntity townPerson = EntityRegistry.TOWNSPERSON.get().create(level);
            check(townPerson != null, "could not create a townsperson");
            townPerson.moveTo(absolute.getX() + 2, absolute.getY(), absolute.getZ(), 0, 0);
            check(level.addFreshEntity(townPerson), "could not add the townsperson");

            // Populated the way a world saved before this change would populate it: through
            // the block entity's own NBT, not a test-only setter.
            trackManagedNpcs(helper, legacy, architect.getUUID(), townPerson.getUUID());

            check(LegacySpawnBlockMigrator.migrateArchitectBlock(level, legacy), "migration failed");

            check(!architect.isAlive(),
                    "the legacy Architect survived migration and would duplicate the assigned one");
            check(townPerson.isAlive(),
                    "migration despawned a townsperson; that belongs to the population manager");
        });
        helper.succeed();
    }

    /** The rollback receipt names the Architect block, so the migration is reversible. */
    @GameTest(template = TEMPLATE)
    public static void migrationrecordsarollbackreceipt(GameTestHelper helper) {
        com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.installForGameTesting(
                helper.getLevel().getServer(),
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), java.util.UUID.randomUUID(),
                        "gametest_shard_identity"));
        try {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        withCity(UUID.randomUUID(), () -> {
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Britain");
            check(LegacySpawnBlockMigrator.migrateArchitectBlock(level, legacy), "migration failed");

            LegacySpawnBlockMigrationLedger.Receipt receipt = LegacySpawnBlockMigrationLedger.get(level)
                    .find(level.dimension().location().toString(), absolute);
            check(receipt != null, "migration recorded no rollback receipt");
            check("britannia_mod:architect_spawn_block".equals(receipt.legacyBlockId()),
                    "receipt names the wrong legacy block: " + receipt.legacyBlockId());
            check(ARCHITECT_VENDOR.equals(receipt.economicTypeKey()),
                    "receipt lost the economic key: " + receipt.economicTypeKey());
            check(LegacySpawnBlockMigrator.OUTCOME_CONFIGURED.equals(receipt.outcome()),
                    "unexpected migration outcome " + receipt.outcome());
        });
        helper.succeed();
        } finally {
            // Cleared so the rest of the run does not inherit credentials: their presence is
            // what makes every mock-player join attempt a real Rails fetch.
            com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.clear(
                    helper.getLevel().getServer());
        }
    }

    /** An unknown city keeps the legacy block in place rather than stranding a broken post. */
    @GameTest(template = TEMPLATE)
    public static void anunknowncityleavesthelegacyblockalone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        withCity(UUID.randomUUID(), () -> {
            ArchitectSpawnBlockEntity legacy = placeArchitectBlock(helper, relative, "Nowhere");

            check(!LegacySpawnBlockMigrator.migrateArchitectBlock(level, legacy),
                    "a block naming an unknown city must not migrate");
            check(level.getBlockEntity(absolute) instanceof ArchitectSpawnBlockEntity,
                    "the legacy block was replaced despite the failed migration");
        });
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    private static ArchitectSpawnBlockEntity placeArchitectBlock(
            GameTestHelper helper, BlockPos relative, String cityName) {
        helper.setBlock(relative, BlockRegistry.ARCHITECT_SPAWN_BLOCK.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        if (!(blockEntity instanceof ArchitectSpawnBlockEntity legacy)) {
            throw new GameTestAssertException("the Architect spawn block grew no block entity");
        }
        legacy.setCityName(cityName);
        return legacy;
    }

    /** Writes the tracked-NPC list through the real load path a saved world would use. */
    private static void trackManagedNpcs(GameTestHelper helper, ArchitectSpawnBlockEntity legacy,
                                         UUID... ids) {
        net.minecraft.core.HolderLookup.Provider provider = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag tag = legacy.saveWithFullMetadata(provider);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (UUID id : ids) {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putUUID("NPC", id);
            list.add(entry);
        }
        tag.put("AssociatedNPCs", list);
        legacy.loadWithComponents(tag, provider);
    }

    private static ServiceNpcSpawnBlockEntity requirePost(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity post)) {
            throw new GameTestAssertException("expected an authoritative post at " + pos
                    + " but found " + level.getBlockState(pos));
        }
        return post;
    }

    /**
     * A resolvable city AND the Rails economic row for the Architect: migration will not destroy a
     * legacy block unless the authoritative side can rebuild the NPC afterwards, so a scene that
     * expects a conversion has to state both halves. Production receives the row through the world
     * bootstrap, published by the Architect's own vendor seed.
     */
    private static void withCity(UUID cityId, Runnable body) {
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(cityId, "Britain")
        )));
        LegacySpawnBlockMigrationGameTests.installEconomicRegistry(
                ARCHITECT_VENDOR, "vendor", "britannia_mod:architect");
        try {
            body.run();
        } finally {
            BootstrapCityRegistryCache.clear();
            EconomicNpcRegistryCache.clear();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

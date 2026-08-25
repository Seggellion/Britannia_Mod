package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.MerchantSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.entity.BakerEntity;
import com.seggellion.britannia_mod.entity.FarmerEntity;
import com.seggellion.britannia_mod.merchant.MerchantFoodSupplyGate;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrationLedger;
import com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Farming-economy restore: the legacy MerchantSpawnBlock can be configured as a Farmer, and the
 * Farmer alone is gated on the city food-supply reading -- below 20 no farmer stands, at 20 and
 * above one does, and a reading Rails has never produced neither spawns nor despawns. Bakers
 * keep spawning ungated, exactly as before the farmer existed.
 *
 * <p>The Rails reading is driven through {@link MerchantFoodSupplyGate#installReader}, the
 * codebase's fake-port idiom, keyed per test-city so concurrently batched scenes cannot read
 * each other's values. Entity assertions filter by city name for the same reason.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class FarmerMerchantSpawnGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "farmer_merchant";
    private static final BlockPos SPAWNER = new BlockPos(1, 1, 1);

    /** Per-city fake Rails readings; an absent city is "Rails has never answered". */
    private static final Map<String, Double> READINGS = new ConcurrentHashMap<>();

    private FarmerMerchantSpawnGameTests() {
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void belowTheFoodFloorNoFarmerSpawns(GameTestHelper helper) {
        String city = "FarmGate19";
        READINGS.put(city, 19.0D);
        MerchantSpawnBlockEntity spawner = placeSpawner(helper);

        spawner.applyAndResync("farmer", city, 0);

        check(farmersOf(helper, city).isEmpty(), "a city at food supply 19 must have no farmer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void atTheFoodFloorTheFarmerSpawns(GameTestHelper helper) {
        String city = "FarmGate20";
        READINGS.put(city, 20.0D);
        MerchantSpawnBlockEntity spawner = placeSpawner(helper);

        spawner.applyAndResync("farmer", city, 0);

        List<FarmerEntity> farmers = farmersOf(helper, city);
        check(farmers.size() == 1, "exactly one farmer must staff the block, found " + farmers.size());
        check(farmers.get(0).getTags().contains("merchant_type_farmer"),
                "the spawned merchant must carry the farmer type tag");
        check("farmer".equals(spawner.getMerchantType()),
                "the block must persist the canonical farmer key, got " + spawner.getMerchantType());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aboveTheFoodFloorTheFarmerSpawns(GameTestHelper helper) {
        String city = "FarmGate21";
        READINGS.put(city, 21.0D);
        MerchantSpawnBlockEntity spawner = placeSpawner(helper);

        spawner.applyAndResync("farmer", city, 0);

        check(farmersOf(helper, city).size() == 1, "food supply 21 must staff the farmer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aStarvingCityLosesItsFarmer(GameTestHelper helper) {
        String city = "FarmGateStarves";
        READINGS.put(city, 20.0D);
        MerchantSpawnBlockEntity spawner = placeSpawner(helper);
        spawner.applyAndResync("farmer", city, 0);
        check(farmersOf(helper, city).size() == 1, "the well-fed city must first gain its farmer");

        READINGS.put(city, 19.0D);
        spawner.applyAndResync("farmer", city, 0);

        check(farmersOf(helper, city).isEmpty(), "dropping below the floor must despawn the farmer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aMissingReadingNeitherSpawnsNorDespawns(GameTestHelper helper) {
        String city = "FarmGateUnread";
        READINGS.remove(city);
        MerchantSpawnBlockEntity spawner = placeSpawner(helper);

        spawner.applyAndResync("farmer", city, 0);
        check(farmersOf(helper, city).isEmpty(),
                "no farmer may spawn before Rails has ever answered for the city");

        READINGS.put(city, 20.0D);
        spawner.applyAndResync("farmer", city, 0);
        check(farmersOf(helper, city).size() == 1, "the first sufficient reading must staff the farmer");

        READINGS.remove(city);
        spawner.applyAndResync("farmer", city, 0);
        check(farmersOf(helper, city).size() == 1,
                "losing the reading is not a reading of zero; the farmer must stay");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void bakersKeepSpawningWithoutAnyReading(GameTestHelper helper) {
        String city = "FarmGateBakery";
        READINGS.remove(city);
        MerchantSpawnBlockEntity spawner = placeSpawner(helper);

        spawner.applyAndResync("baker", city, 0);

        List<BakerEntity> bakers = helper.getLevel().getEntitiesOfClass(
                        BakerEntity.class, scanBounds(helper)).stream()
                .filter(baker -> city.equals(baker.getCityName()))
                .toList();
        check(bakers.size() == 1, "the ungated baker must spawn with no food reading at all");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aFarmerBlockMigratesOntoAnEconomicFarmerPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String city = "FarmGateMigrates";
        READINGS.put(city, 20.0D);
        UUID cityId = UUID.randomUUID();
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(List.of(
                new BootstrapCityDefinition(cityId, city))));
        try {
            MerchantSpawnBlockEntity spawner = placeSpawner(helper);
            spawner.applyAndResync("farmer", city, 0);
            check(farmersOf(helper, city).size() == 1, "the legacy farmer must stand before migration");

            check(LegacySpawnBlockMigrator.migrateMerchantBlock(level, spawner),
                    "a resolvable farmer block must migrate onto the authoritative post architecture");

            BlockPos absolute = helper.absolutePos(SPAWNER);
            if (!(level.getBlockEntity(absolute) instanceof ServiceNpcSpawnBlockEntity post)) {
                throw new IllegalStateException("no authoritative post entity at " + absolute);
            }
            check(EconomicNpcTypeKeys.prefixed("farmer").equals(post.getServiceNpcTypeKey()),
                    "migrated post must carry economic:farmer, got " + post.getServiceNpcTypeKey());
            check(cityId.equals(post.getCityPublicId()), "migrated post lost the city");

            LegacySpawnBlockMigrationLedger.Receipt receipt = LegacySpawnBlockMigrationLedger.get(level)
                    .find(level.dimension().location().toString(), absolute);
            check(receipt != null, "migration recorded no rollback receipt");
            check("farmer".equals(receipt.legacyBlockEntityNbt().getString("MerchantType")),
                    "the receipt must preserve the farmer key through NBT serialization");
            check(farmersOf(helper, city).isEmpty(),
                    "the legacy-managed farmer must despawn so the authoritative pipeline can staff the post");
        } finally {
            BootstrapCityRegistryCache.clear();
        }
        helper.succeed();
    }

    private static MerchantSpawnBlockEntity placeSpawner(GameTestHelper helper) {
        installMapReader();
        // A sturdy floor under the searchable spawn area, so position searches never
        // depend on what the shared empty template happens to contain.
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(SPAWNER, BlockRegistry.MERCHANT_SPAWN_BLOCK.get());
        BlockPos absolute = helper.absolutePos(SPAWNER);
        if (helper.getLevel().getBlockEntity(absolute) instanceof MerchantSpawnBlockEntity spawner) {
            return spawner;
        }
        throw new IllegalStateException("no merchant spawn block entity at " + absolute);
    }

    private static void installMapReader() {
        MerchantFoodSupplyGate.installReader((level, city) -> {
            Double reading = READINGS.get(city);
            return reading == null ? OptionalDouble.empty() : OptionalDouble.of(reading);
        });
    }

    private static List<FarmerEntity> farmersOf(GameTestHelper helper, String city) {
        return helper.getLevel().getEntitiesOfClass(FarmerEntity.class, scanBounds(helper)).stream()
                .filter(Mob::isAlive)
                .filter(farmer -> city.equals(farmer.getCityName()))
                .toList();
    }

    private static AABB scanBounds(GameTestHelper helper) {
        return new AABB(helper.absolutePos(SPAWNER)).inflate(16.0D);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.ArchitectSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.MerchantSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.block.entity.TraderSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.merchant.MerchantTypes;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;

/**
 * Vendor/Trader Milestone 16: converts a legacy MerchantSpawnBlock or
 * TraderSpawnBlock, in place, onto the authoritative Service NPC post
 * architecture. Invoked from the legacy block entities' own tick at their
 * existing maintenance cadence; until a conversion SUCCEEDS the legacy
 * behavior keeps running unchanged, so a world whose Rails backend or city
 * bootstrap is unavailable keeps its merchants exactly as before -- the old
 * path is bypassed only at the moment the new one takes over (playbook
 * Milestone 16 deprecation rule; parity proven by the migration GameTests).
 *
 * <p>The conversion, one tick, in order: despawn the legacy-MANAGED
 * merchant/trader NPC (the authoritative assignment pipeline will staff the
 * post with a persistent World NPC -- leaving the legacy mob standing would
 * duplicate it; TownPersons are deliberately NOT touched, per owner decision
 * #12 they remain until the regional population system reaches parity); write
 * the full-fidelity rollback receipt to {@link
 * LegacySpawnBlockMigrationLedger}; replace the block with a real {@link
 * com.seggellion.britannia_mod.block.ServiceNpcSpawnBlock} whose fresh
 * entity mints its stable UUID through the claim store exactly like a
 * player-placed post; and apply the migrated configuration, which stages the
 * durable pending UPSERT the delivery pipeline registers with Rails. From
 * there Milestones 5/6/15 do the rest: registration, staffing sweep,
 * assignment projection, entity materialization.
 *
 * <p>Legacy block/item/block-entity REGISTRY ids are untouched -- old chunks
 * keep deserializing; only in-world instances convert (the deliberate,
 * proven-safe migration the playbook allows). Rollback: see
 * docs/vendor-trader-economy/LEGACY_SPAWN_BLOCK_MIGRATION.md.
 */
public final class LegacySpawnBlockMigrator {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Rails economic type keys legacy trader blocks may carry (Milestone 11 seed). */
    static final Set<String> TRADER_KEYS = Set.of(
            "wood_trader", "fish_trader", "salvage_trader", "alcohol_trader", "ore_trader",
            "stone_trader", "meat_trader", "grain_trader", "produce_trader", "fur_leather_trader",
            "reagent_trader", "provision_trader", "textile_trader", "glass_trader", "scribe_trader"
    );
    /** Rails economic type keys legacy merchant blocks map onto (Milestones 7 + 16 seeds). */
    static final Set<String> MERCHANT_KEYS = Set.of("baker", "tavernkeeper", "costermonger");

    /**
     * Housing Deed Milestone 8: the Architect post.
     *
     * <p>A legacy Architect block is not a typed block the way a merchant block is -- there is
     * one kind of Architect -- so the key is the constant rather than something read off the
     * block. It is the Rails economic type verbatim, and Rails is where the decision now lives.
     */
    static final Set<String> ARCHITECT_KEYS = Set.of("architect_vendor");

    public static final String OUTCOME_CONFIGURED = "configured";
    public static final String OUTCOME_CONFIGURATION_FAILED = "configuration_failed";
    public static final String OUTCOME_REPLACE_FAILED = "replace_failed";

    private LegacySpawnBlockMigrator() {
    }

    /** True when the legacy block was replaced; the caller's tick must stop immediately. */
    public static boolean migrateMerchantBlock(ServerLevel level, MerchantSpawnBlockEntity legacy) {
        String key = MerchantTypes.normalize(legacy.getMerchantType());
        return migrate(level, legacy, "merchant", key, MERCHANT_KEYS,
                legacy.getCityName(), legacy.getTownPersonAmount(),
                () -> legacy.despawnManagedNpcForMigration(level));
    }

    /**
     * True when the legacy Architect block was replaced; the caller must stop ticking.
     *
     * <p>The Architect had its own economic gate -- 400 food and 200 wood -- which is what this
     * migration exists to retire. Rails now evaluates the city and opens or closes the
     * assignment; the post this produces is staffed by the same pipeline that staffs every other
     * economic vendor, which is also what finally stamps the Architect with its economic type so
     * the deed catalogue works on a living NPC rather than only in principle.
     */
    public static boolean migrateArchitectBlock(ServerLevel level, ArchitectSpawnBlockEntity legacy) {
        return migrate(level, legacy, "architect", "architect_vendor", ARCHITECT_KEYS,
                legacy.getCityName(), legacy.getTrackedTownPersonCount(),
                () -> legacy.despawnManagedNpcForMigration(level));
    }

    /** True when the legacy block was replaced; the caller's tick must stop immediately. */
    public static boolean migrateTraderBlock(ServerLevel level, TraderSpawnBlockEntity legacy) {
        String key = legacy.getTraderType() == null
                ? "" : legacy.getTraderType().trim().toLowerCase(Locale.ROOT);
        return migrate(level, legacy, "trader", key, TRADER_KEYS,
                legacy.getCityName(), legacy.getTownPersonAmount(),
                () -> legacy.despawnManagedNpcForMigration(level));
    }

    private static boolean migrate(ServerLevel level, BlockEntity legacy, String kind,
                                   String economicKey, Set<String> knownKeys,
                                   String cityName, int townPersonAmount,
                                   Runnable despawnManagedNpc) {
        if (cityName == null || cityName.isBlank()) return false;
        BlockPos pos = legacy.getBlockPos();

        if (!knownKeys.contains(economicKey)) {
            // Fail safe: an unrecognized key keeps the legacy block working
            // untouched rather than stranding an erroring authoritative post.
            LOGGER.warn("Legacy {} spawn block at {} has unmapped type '{}'; leaving legacy behavior in place",
                    kind, pos, economicKey);
            return false;
        }

        BootstrapCityRegistrySnapshot cities = BootstrapCityRegistryCache.snapshot();
        if (!cities.available()) return false;
        BootstrapCityDefinition city = findCityByName(cities, cityName);
        if (city == null) {
            LOGGER.warn("Legacy {} spawn block at {} names unknown city '{}'; waiting (legacy behavior continues)",
                    kind, pos, cityName);
            return false;
        }

        // Order matters: the legacy-managed NPC must be gone before the
        // authoritative pipeline can staff this post, or it would duplicate.
        despawnManagedNpc.run();

        String dimensionKey = level.dimension().location().toString();
        String legacyBlockId = BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock()).toString();
        CompoundTag legacyNbt = legacy.saveWithFullMetadata(level.registryAccess());
        LegacySpawnBlockMigrationLedger ledger = LegacySpawnBlockMigrationLedger.get(level);

        level.setBlockAndUpdate(pos, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity post)) {
            ledger.record(new LegacySpawnBlockMigrationLedger.Receipt(
                    dimensionKey, pos, legacyBlockId, legacyNbt, null,
                    economicKey, city.publicId(), cityName, townPersonAmount,
                    System.currentTimeMillis(), OUTCOME_REPLACE_FAILED));
            LOGGER.error("Legacy {} spawn block migration at {} produced no authoritative post entity", kind, pos);
            return true;
        }

        ServiceNpcSpawnValidationError error = post.applyConfiguration(
                level, city.publicId(), EconomicNpcTypeKeys.prefixed(economicKey),
                true, post.getConfigurationRevision());
        String outcome = error == ServiceNpcSpawnValidationError.NONE
                ? OUTCOME_CONFIGURED
                : OUTCOME_CONFIGURATION_FAILED + ":" + error.name().toLowerCase(Locale.ROOT);
        ledger.record(new LegacySpawnBlockMigrationLedger.Receipt(
                dimensionKey, pos, legacyBlockId, legacyNbt, post.getSpawnPointId(),
                economicKey, city.publicId(), cityName, townPersonAmount,
                System.currentTimeMillis(), outcome));

        if (error == ServiceNpcSpawnValidationError.NONE) {
            LOGGER.info("Legacy {} spawn block at {} migrated to authoritative post {} (economic:{}, city {})",
                    kind, pos, post.getSpawnPointId(), economicKey, cityName);
        } else {
            // The post exists but is unconfigured; it is visible in the normal
            // admin menu and the ledger records why. Never silently retried --
            // the legacy block is gone, so the operator decides.
            LOGGER.error("Legacy {} spawn block at {} was replaced but configuration failed: {} (see migration ledger)",
                    kind, pos, error);
        }
        return true;
    }

    @Nullable
    private static BootstrapCityDefinition findCityByName(BootstrapCityRegistrySnapshot cities, String cityName) {
        String wanted = cityName.trim();
        for (BootstrapCityDefinition city : cities.cities().values()) {
            if (city.displayName().trim().equalsIgnoreCase(wanted)) {
                return city;
            }
        }
        return null;
    }
}

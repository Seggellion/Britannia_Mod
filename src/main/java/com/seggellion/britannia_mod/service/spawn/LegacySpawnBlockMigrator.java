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
import com.seggellion.britannia_mod.service.EconomicNpcRegistryCache;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    /** Rails economic type keys legacy merchant blocks map onto (Milestones 7 + 16 seeds; farmer joined with the farming-economy restore). */
    static final Set<String> MERCHANT_KEYS = Set.of("baker", "tavernkeeper", "costermonger", "farmer");

    /**
     * Housing Deed Milestone 8: the Architect post.
     *
     * <p>A legacy Architect block is not a typed block the way a merchant block is -- there is
     * one kind of Architect -- so the key is the constant rather than something read off the
     * block. It is the Rails economic type verbatim, and Rails is where the decision now lives.
     */
    static final Set<String> ARCHITECT_KEYS = Set.of("architect_vendor");

    /**
     * Patch 18 live hotfix: legacy posts parked because their economic type is published but not
     * presently registrable, and the flag state each was last reported at.
     *
     * <p>The legacy blocks call this migrator from their own 200-tick maintenance cadence, so an
     * unconditional warn below would repeat every ten seconds, for every parked legacy block in
     * the world, for as long as the Rails flags stay down. Re-reporting only when the flag state
     * CHANGES puts the diagnostic where an operator will actually find it -- once when the block
     * parks, once more when the flags move -- instead of burying it in its own repetitions.
     *
     * <p>Bounded by the number of legacy blocks that have parked in this server session, one
     * short string each, and an entry is dropped the moment its block converts.
     */
    private static final Map<String, ParkedGate> PARKED_TYPE_GATES = new ConcurrentHashMap<>();

    /**
     * The flag state a parked post was last reported at, and how many diagnostics it has drawn.
     *
     * <p>The count is carried per position rather than in one process-wide counter so a GameTest
     * can assert the throttle against its OWN arena: GameTests in a batch run concurrently, and a
     * shared counter would make each of them depend on what the others happened to do.
     */
    private record ParkedGate(String state, long reports) {
    }

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

        EconomicNpcTypeDefinition published = EconomicNpcRegistryCache.snapshot()
                .economicNpcTypes().get(economicKey);

        if (!railsCanRematerialize(published)) {
            // The conversion is one-way and destroys the block: the legacy NPC is despawned and
            // the family-specific block is replaced by a post that only Rails can staff. Knowing
            // the key locally is not enough to justify that -- Rails must also be able to build
            // the entity again, which is precisely what
            // ServiceNpcAssignmentReconciler.reconcileEconomic requires. Without this check a key
            // present in knownKeys but absent from the Rails registry converts anyway and strands
            // an unstaffable post that reads as a broken Service NPC spawner, taking the merchant
            // with it. Staying legacy is the safe half: the merchant keeps working, and the
            // conversion happens by itself on a later tick once Rails publishes the type.
            LOGGER.warn("Legacy {} spawn block at {} keeps legacy behavior: the Rails economic registry "
                            + "cannot rematerialize '{}' yet (type absent or carrying no entity mapping)",
                    kind, pos, economicKey);
            return false;
        }

        if (!railsWillRegister(published)) {
            // Patch 18 live hotfix. Being able to REBUILD the NPC is not the same as being
            // allowed to REGISTER the post, and registration happens first: Rails answers an
            // inactive or unspawnable economic type with 422 ECONOMIC_NPC_TYPE_INACTIVE /
            // ECONOMIC_NPC_TYPE_NOT_SPAWNABLE, which ServiceNpcSpawnDeliveryDecision classifies
            // PERMANENT_FAILURE -- parked forever, never retried, no assignment ever made. The
            // old code converted anyway, so the destructive half (legacy NPC despawned, block
            // replaced) completed and the constructive half could not, leaving a post reading
            // "Invalid Service NPC type" where a working trader used to stand. That is exactly
            // how production's Serpent's Hold Wood Trader was lost. Keep the legacy block and
            // its NPC until the authoritative side will actually take the registration.
            reportParkedTypeGate(level, pos, kind, economicKey, published);
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

        // Converting now, so this position is no longer parked: drop its throttle entry and
        // leave the map holding only blocks that are still waiting on Rails.
        PARKED_TYPE_GATES.remove(parkedGateKey(level, pos));

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

    /**
     * Whether the authoritative pipeline could put this NPC back in the world: the same two
     * conditions {@code reconcileEconomic} materializes on -- the type is published by Rails, and
     * it names an entity to build.
     *
     * <p>An empty registry (Rails unreachable, or the bootstrap section never delivered) answers
     * false for every key, which is the intended fail-safe: no backend, no conversion.
     *
     * <p>This is necessary but NOT sufficient on its own; see {@link
     * #railsWillRegister(EconomicNpcTypeDefinition)}.
     */
    private static boolean railsCanRematerialize(@Nullable EconomicNpcTypeDefinition definition) {
        return definition != null
                && definition.minecraftEntityTypeKey() != null
                && !definition.minecraftEntityTypeKey().isBlank();
    }

    /**
     * Whether Rails will presently ACCEPT a post registration for this type.
     *
     * <p>This gate used to read the other way round: {@code active} and {@code spawnable} were
     * deliberately not required, on the reasoning that they are runtime staffing decisions Rails
     * may flip either way and a post is the right home for such an NPC regardless. That
     * reasoning only holds for a post that already exists. It does not hold for the one-way,
     * destructive conversion below, because the conversion's two halves are not gated by the same
     * thing: {@code reconcileEconomic} needs only a published type with an entity mapping, but
     * nothing reaches reconciliation until the post is REGISTERED, and registration additionally
     * requires both flags. Rails rejects the rest with 422, the mod records
     * {@code economic_npc_type_inactive} / {@code economic_npc_type_not_spawnable} as a
     * PERMANENT_FAILURE, and a permanent failure is never retried by design.
     *
     * <p>So the destructive half completed and the constructive half could not: a working legacy
     * NPC was despawned and its block replaced by a post that could never be staffed and would
     * never try again. The Patch 18 invariant is therefore that a working legacy NPC stays in the
     * world until the authoritative type is presently acceptable for post registration -- not
     * merely known to exist. Once an operator sets both flags and the world bootstrap refreshes,
     * the very same block converts on a later tick with no further intervention.
     *
     * <p>Narrow on purpose: this is a live Patch 18 hotfix, so it gates the conversion and
     * nothing else. Posts that already migrated are untouched, and the deferred-despawn state
     * machine that would let a conversion begin and pause remains future work.
     */
    private static boolean railsWillRegister(EconomicNpcTypeDefinition definition) {
        return definition.active() && definition.spawnable();
    }

    /**
     * One warning per parked post per distinct flag state, naming the repair.
     *
     * <p>Deliberately not silent: an operator whose trader stopped converting needs to find out
     * from the log why, and "keeps legacy behavior" is the good outcome being reported, not a
     * fault. Deliberately not repeated either -- see {@link #PARKED_TYPE_GATES}.
     */
    private static void reportParkedTypeGate(ServerLevel level, BlockPos pos, String kind,
                                             String economicKey, EconomicNpcTypeDefinition published) {
        String state = "active=" + published.active() + ",spawnable=" + published.spawnable();
        ParkedGate previous = PARKED_TYPE_GATES.get(parkedGateKey(level, pos));
        if (previous != null && previous.state().equals(state)) return;
        PARKED_TYPE_GATES.put(parkedGateKey(level, pos),
                new ParkedGate(state, previous == null ? 1L : previous.reports() + 1L));
        LOGGER.warn("Legacy {} spawn block at {} keeps legacy behavior: Rails publishes economic type "
                        + "'{}' but will not presently accept a post registration for it ({}). The "
                        + "legacy NPC is left standing and nothing was replaced. Set BOTH active and "
                        + "spawnable on this economic NPC type in the Rails admin; the conversion then "
                        + "resumes by itself once the world bootstrap refreshes.",
                kind, pos, economicKey, state);
    }

    private static String parkedGateKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    /**
     * How many parked-type diagnostics the post at this position has drawn; 0 once it converts.
     *
     * <p>Public only so {@code LegacySpawnBlockMigrationGameTests} can prove the throttle holds
     * across repeated ticks rather than asserting it in prose; nothing in production reads it.
     */
    public static long parkedTypeGateReportsForGameTesting(ServerLevel level, BlockPos pos) {
        ParkedGate gate = PARKED_TYPE_GATES.get(parkedGateKey(level, pos));
        return gate == null ? 0L : gate.reports();
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

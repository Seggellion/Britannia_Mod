package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.entity.AbstractTraderEntity;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.spawner.PopulationMaintenance;
import com.seggellion.britannia_mod.trader.TraderAppearance;
import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import com.seggellion.britannia_mod.util.NameLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TraderSpawnBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int HEARTBEAT_INTERVAL_TICKS = 600;
    private static final int LOAD_GRACE_TICKS = 40;
    private static final int SPAWN_SEARCH_ATTEMPTS = 50;
    private static final String TAG_TOWN_NPCS = "TownNPCs";

    /**
     * Hard ceiling on townsperson spawn attempts in one maintenance cycle.
     *
     * <p>Sized against the two things that bound it in practice: the sibling food-gated spawners
     * treat {@code TOWNSPERSON_COUNT = 4} as a normal town complement, and this maintenance runs
     * every {@link #CHECK_INTERVAL_TICKS} ticks. Eight therefore fills a typical spawner's entire
     * cold start in one cycle with headroom to spare, so ordinary recovery is unaffected, while a
     * spawner that can never place anyone costs eight bounded position searches every ten seconds
     * instead of looping until the server dies. A larger configured population is not refused, it
     * just fills across consecutive cycles.
     */
    private static final int MAX_TOWNSPERSON_SPAWN_ATTEMPTS_PER_CYCLE = 8;

    private UUID sourceId = UUID.randomUUID();
    private UUID traderNpcId;
    private CompoundTag savedTraderData;
    private final List<UUID> townNpcIds = new ArrayList<>();

    private String cityName = "";
    private String traderType = "wood_trader";
    private int townPersonAmount = 0;
    private int spawnRadius = 5;
    private int initTicks = 0;
    private int checkCooldown = 0;
    private long lastHeartbeatTick = 0;

    private double foodSupply;
    private double woodSupply;
    private double metalSupply;
    private double stoneSupply;
    private double textileSupply;
    private double alcoholSupply;
    private double technologySupply;

    public TraderSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.TRADER_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public String getCityName() { return cityName; }
    public void setCityName(String name) { this.cityName = name == null ? "" : name; setChanged(); }
    public String getTraderType() { return traderType; }
    public void setTraderType(String type) { this.traderType = normalizeTraderType(type); setChanged(); }
    public int getTownPersonAmount() { return townPersonAmount; }
    public void setTownPersonAmount(int amount) { this.townPersonAmount = Math.max(0, amount); setChanged(); }
    public UUID getSourceId() { return sourceId; }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;

        if (initTicks < LOAD_GRACE_TICKS) {
            initTicks++;
            return;
        }

        if (cityName == null || cityName.isBlank()) return;

        if (checkCooldown-- > 0) {
            heartbeatIfDue(sl);
            return;
        }
        checkCooldown = CHECK_INTERVAL_TICKS;

        // Vendor/Trader Milestone 16: converge onto the authoritative post
        // architecture. Until a conversion SUCCEEDS (city resolved from the
        // bootstrap registry, type mapped), everything below keeps running
        // unchanged -- a world without its backend keeps its traders.
        if (com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrator
                .migrateTraderBlock(sl, this)) {
            return;
        }

        maintainTrader(sl);
        maintainTownspeople(sl);
        heartbeatIfDue(sl);
    }

    /**
     * Milestone 16 migration despawn: removes ONLY the legacy-managed trader
     * NPC (the authoritative assignment pipeline staffs the migrated post, so
     * leaving it would duplicate). TownPersons and their tracked ids are
     * deliberately untouched -- owner decision #12 preserves them until the
     * regional population system reaches parity.
     */
    public void despawnManagedNpcForMigration(ServerLevel sl) {
        TraderDefinition definition = definitionFor(traderType);
        Entity traderEntity = findOwnedTraderAnyType(sl);
        UUID npcId = traderEntity != null ? traderEntity.getUUID() : traderNpcId;

        if (npcId != null) {
            CityDataSync.markLiveNpcInactiveAsync(
                    sl, npcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "despawned", "migrated_to_authoritative_post"
            );
            LOGGER.info("Legacy trader despawned for migration source={} npc={} type={} city={} railsSync=scheduled",
                    sourceId, npcId, definition.npcType(), cityName);
            if (traderEntity != null) {
                traderEntity.remove(RemovalReason.DISCARDED);
            }
        }
        traderNpcId = null;
        savedTraderData = null;
    }

    private void maintainTrader(ServerLevel sl) {
        TraderDefinition definition = definitionFor(traderType);
        Entity live = findTrackedTrader(sl, definition);

        if (live != null && live.isAlive()) {
            configureEntity(sl, live, definition);
            updateSnapshot(live);
            enforceBoundary(sl, live);
            return;
        }

        if (traderNpcId != null) {
            CityDataSync.markLiveNpcInactiveAsync(
                    sl, traderNpcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "dead", "missing_or_dead"
            );
            LOGGER.info("Rails NPC despawn/delete scheduled source={} npc={} type={} city={} status=dead reason=missing_or_dead railsSync=scheduled",
                    sourceId, traderNpcId, definition.npcType(), cityName);
        }

        spawnTrader(sl, definition);
    }

    private void spawnTrader(ServerLevel sl, TraderDefinition definition) {
        LOGGER.info("Trader spawn attempt source={} type={} city={} spawner={} radius={} yRange={}..{}",
                sourceId, definition.configKey(), cityName, worldPosition, spawnRadius,
                worldPosition.getY() - 1, worldPosition.getY() + 1);
        BlockPos spawnPos = findAnchoredSpawnPos(sl, "trader " + definition.configKey());
        if (spawnPos == null) {
            LOGGER.warn("Trader spawn skipped source={} city={} type={} spawner={} reason=no_valid_position",
                    sourceId, cityName, definition.configKey(), worldPosition);
            return;
        }
        LOGGER.info("Trader spawn position selected source={} type={} city={} spawner={} selected={} spawnerY={} selectedY={}",
                sourceId, definition.configKey(), cityName, worldPosition, spawnPos,
                worldPosition.getY(), spawnPos.getY());

        if (traderNpcId == null) traderNpcId = UUID.randomUUID();
        Entity trader = createOrRestoreTrader(sl, definition);
        if (!(trader instanceof Mob mob)) {
            LOGGER.warn("Trader spawn failed: could not create entity for type={}", definition.configKey());
            return;
        }

        trader.setUUID(traderNpcId);
        trader.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                sl.random.nextFloat() * 360.0F, 0.0F);
        mob.setPersistenceRequired();
        mob.restrictTo(worldPosition, spawnRadius);
        configureEntity(sl, trader, definition);
        addSourceTags(trader, definition);

        if (sl.addFreshEntity(trader)) {
            traderNpcId = trader.getUUID();
            updateSnapshot(trader);
            associateWithCity(sl, trader);
            CityDataSync.upsertLiveNpcAsync(
                    sl, trader, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "active"
            );
            LOGGER.info("Trader spawn success source={} npc={} type={} city={} pos={}",
                    sourceId, trader.getUUID(), definition.npcType(), cityName, trader.blockPosition());
            setChanged();
        } else {
            LOGGER.warn("Trader spawn failed: addFreshEntity rejected npc={} source={}", trader.getUUID(), sourceId);
        }
    }

    private Entity createOrRestoreTrader(ServerLevel sl, TraderDefinition definition) {
        if (savedTraderData != null) {
            CompoundTag tag = savedTraderData.copy();
            tag.putUUID("UUID", traderNpcId);
            Entity restored = EntityType.loadEntityRecursive(tag, sl, e -> e);
            if (restored != null && restored.getType() == definition.entityType()) {
                return restored;
            }
        }
        return definition.entityType().create(sl);
    }

    private Entity findTrackedTrader(ServerLevel sl, TraderDefinition definition) {
        if (traderNpcId != null) {
            Entity byUuid = sl.getEntity(traderNpcId);
            if (byUuid != null && byUuid.getType() == definition.entityType()) {
                LOGGER.debug("Trader existing reused by UUID source={} npc={} type={} city={}",
                        sourceId, traderNpcId, definition.configKey(), cityName);
                return byUuid;
            }
        }

        AABB area = new AABB(worldPosition).inflate(spawnRadius + 8);
        List<? extends Mob> nearby = sl.getEntitiesOfClass(Mob.class, area,
                e -> e.getType() == definition.entityType() && e.getTags().contains(sourceTag()));
        if (!nearby.isEmpty()) {
            Entity found = nearby.get(0);
            traderNpcId = found.getUUID();
            LOGGER.info("Trader existing reused by source tag source={} npc={} type={} city={} pos={}",
                    sourceId, traderNpcId, definition.configKey(), cityName, found.blockPosition());
            return found;
        }
        return null;
    }

    private void configureEntity(ServerLevel sl, Entity entity, TraderDefinition definition) {
        if (entity instanceof CitizenEntity citizen) {
            if (citizen.getCityName() == null || citizen.getCityName().isBlank()) citizen.setCityName(cityName);
            if (citizen.getPersonalName() == null || citizen.getPersonalName().equals("Unnamed")) {
                String gender = selectTraderGender(definition);
                citizen.setGender(gender);
                citizen.setPersonalName("male".equals(gender) ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName());
            }
            applyTraderAppearance(citizen, definition);
        } else {
            callStringSetter(entity, "setCityName", cityName);
        }

        if (entity instanceof AbstractTraderEntity trader) {
            trader.setSpawnBlockPos(worldPosition);
        }

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.restrictTo(worldPosition, spawnRadius);
        }
        addSourceTags(entity, definition);
    }

    private String selectTraderGender(TraderDefinition definition) {
        TraderAppearance appearance = definition.appearance();
        List<String> genders = appearance == null
                ? List.of("male", "female")
                : appearance.safeAllowedGenders();
        int index = Math.floorMod((sourceId.toString() + ":" + definition.configKey()).hashCode(), genders.size());
        return genders.get(index);
    }

    private void applyTraderAppearance(CitizenEntity citizen, TraderDefinition definition) {
        TraderAppearance appearance = definition.appearance();
        if (appearance == null) return;

        citizen.setOutfitKey(appearance.safeOutfitKey());
        citizen.setClothingIndex("hair", appearance.hairIndex());
        citizen.setClothingIndex("facial_hair", appearance.facialHairIndex());
        citizen.setClothingIndex("shirt", appearance.shirtIndex());
        citizen.setClothingIndex("chest", appearance.chestIndex());
        citizen.setClothingIndex("pants", appearance.pantsIndex());
        citizen.setClothingIndex("shoes", appearance.shoesIndex());
        citizen.setClothingIndex("cape", appearance.capeIndex());
    }

    private void addSourceTags(Entity entity, TraderDefinition definition) {
        entity.addTag("britannia_trader_spawn");
        entity.addTag(sourceTag());
        entity.addTag("trader_type_" + definition.configKey());
    }

    private String sourceTag() {
        return "trader_source_" + sourceId.toString().replace("-", "");
    }

    private void callStringSetter(Entity entity, String methodName, String value) {
        try {
            entity.getClass().getMethod(methodName, String.class).invoke(entity, value);
        } catch (Exception ignored) {
        }
    }

    private void updateSnapshot(Entity entity) {
        savedTraderData = new CompoundTag();
        entity.saveWithoutId(savedTraderData);
        savedTraderData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        savedTraderData.putUUID("UUID", entity.getUUID());
        setChanged();
    }

    private void enforceBoundary(ServerLevel sl, Entity entity) {
        if (!(entity instanceof Mob mob)) return;

        double centerX = worldPosition.getX() + 0.5D;
        double centerY = worldPosition.getY();
        double centerZ = worldPosition.getZ() + 0.5D;
        double dx = entity.getX() - centerX;
        double dz = entity.getZ() - centerZ;
        double distSq = dx * dx + dz * dz;
        double softSq = spawnRadius * spawnRadius;
        double hard = spawnRadius + 4.0D;

        if (distSq <= softSq) return;
        if (mob.getTarget() != null) mob.setTarget(null);

        if (distSq > hard * hard) {
            BlockPos ground = findAnchoredSpawnPos(sl, "boundary reset " + entity.getType());
            if (ground == null) {
                LOGGER.warn("Trader boundary reset skipped source={} npc={} type={} spawner={} reason=no_valid_position",
                        sourceId, entity.getUUID(), entity.getType(), worldPosition);
                mob.getNavigation().stop();
                return;
            }
            LOGGER.info("Trader boundary reset teleport source={} npc={} type={} from={} to={} spawner={} reason=outside_hard_radius",
                    sourceId, entity.getUUID(), entity.getType(), entity.blockPosition(), ground, worldPosition);
            mob.teleportTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
            mob.getNavigation().stop();
        } else {
            mob.getNavigation().moveTo(centerX, centerY, centerZ, 1.2D);
        }
    }

    private void maintainTownspeople(ServerLevel sl) {
        townNpcIds.removeIf(id -> {
            Entity e = sl.getEntity(id);
            return e == null || !e.isAlive();
        });

        // Bounded, because spawnTownsperson can legitimately fail forever: an unbounded
        // "loop until the shortfall is gone" freezes the server thread outright when no valid
        // position exists. Whatever is left over is picked up by the next maintenance cycle.
        int startingPopulation = townNpcIds.size();
        PopulationMaintenance.Outcome outcome = PopulationMaintenance.fill(
                startingPopulation, townPersonAmount, MAX_TOWNSPERSON_SPAWN_ATTEMPTS_PER_CYCLE,
                () -> spawnTownsperson(sl));

        if (outcome.incomplete(startingPopulation, townPersonAmount)) {
            // One line per cycle rather than one per failed candidate: the old per-attempt warning
            // fired from inside a loop that never ended, which is how a freeze became a log flood.
            LOGGER.debug("Townsperson maintenance incomplete source={} city={} spawner={} current={} target={} attempts={} successful={}",
                    sourceId, cityName, worldPosition, townNpcIds.size(), townPersonAmount,
                    outcome.attempts(), outcome.successes());
        }
        setChanged();
    }

    /** @return whether a townsperson was actually added, which is what bounds the caller's loop. */
    private boolean spawnTownsperson(ServerLevel sl) {
        // Failure is summarised once per maintenance cycle by the caller, so this search does not
        // log its own: it runs up to the attempt budget per cycle, and used to run without end.
        BlockPos spawnPos = findAnchoredSpawnPos(sl, "townsperson", false);
        if (spawnPos == null) {
            return false;
        }
        LOGGER.info("Townsperson spawn position selected source={} city={} spawner={} selected={} spawnerY={} selectedY={}",
                sourceId, cityName, worldPosition, spawnPos, worldPosition.getY(), spawnPos.getY());

        TownPersonEntity townPerson = EntityRegistry.TOWNSPERSON.get().create(sl);
        if (townPerson == null) return false;

        boolean male = sl.random.nextBoolean();
        townPerson.setGender(male ? "male" : "female");
        townPerson.setPersonalName(male ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName());
        townPerson.setCityName(cityName);
        townPerson.setSpawnPosition(worldPosition);
        townPerson.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                sl.random.nextFloat() * 360.0F, 0.0F);
        townPerson.setPersistenceRequired();
        townPerson.restrictTo(worldPosition, spawnRadius);
        townPerson.addTag("britannia_townsperson_spawn");
        townPerson.addTag(sourceTag());

        if (sl.addFreshEntity(townPerson)) {
            townNpcIds.add(townPerson.getUUID());
            associateWithCity(sl, townPerson);
            CityDataSync.upsertLiveNpcAsync(
                    sl, townPerson, "townsperson", cityName, sourceId.toString(),
                    worldPosition.toShortString(), "active"
            );
            LOGGER.info("Townsperson spawn success source={} npc={} city={}",
                    sourceId, townPerson.getUUID(), cityName);
            return true;
        }
        return false;
    }

    private BlockPos findAnchoredSpawnPos(ServerLevel sl, String purpose) {
        return findAnchoredSpawnPos(sl, purpose, true);
    }

    /**
     * @param warnOnFailure whether an exhausted search logs. Townsperson maintenance passes false:
     *                      it can attempt several searches per cycle and reports them as one line.
     */
    private BlockPos findAnchoredSpawnPos(ServerLevel sl, String purpose, boolean warnOnFailure) {
        SpawnSearchStats stats = new SpawnSearchStats();
        int radius = Math.max(0, spawnRadius);
        int[] yOffsets = {0, -1, 1};

        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            int offX = radius == 0 ? 0 : sl.random.nextInt(radius * 2 + 1) - radius;
            int offZ = radius == 0 ? 0 : sl.random.nextInt(radius * 2 + 1) - radius;

            for (int dy : yOffsets) {
                BlockPos pos = new BlockPos(
                        worldPosition.getX() + offX,
                        worldPosition.getY() + dy,
                        worldPosition.getZ() + offZ
                );
                String rejection = spawnRejectionReason(sl, pos);
                if (rejection == null) {
                    return pos;
                }
                stats.record(rejection, pos);
            }
        }

        for (int offX = -radius; offX <= radius; offX++) {
            for (int offZ = -radius; offZ <= radius; offZ++) {
                for (int dy : yOffsets) {
                    BlockPos pos = new BlockPos(
                            worldPosition.getX() + offX,
                            worldPosition.getY() + dy,
                            worldPosition.getZ() + offZ
                    );
                    String rejection = spawnRejectionReason(sl, pos);
                    if (rejection == null) {
                        return pos;
                    }
                    stats.record(rejection, pos);
                }
            }
        }

        if (warnOnFailure) {
            LOGGER.warn("Trader spawn position search failed source={} purpose={} city={} type={} spawner={} radius={} yRange={}..{} randomAttempts={} exhaustivePositions={} {}",
                    sourceId, purpose, cityName, traderType, worldPosition, radius,
                    worldPosition.getY() - 1, worldPosition.getY() + 1,
                    SPAWN_SEARCH_ATTEMPTS, (radius * 2 + 1) * (radius * 2 + 1), stats.summary());
        }
        return null;
    }

    private String spawnRejectionReason(ServerLevel sl, BlockPos pos) {
        if (!sl.hasChunkAt(pos)) {
            return "chunk_not_loaded";
        }

        BlockPos groundPos = pos.below();
        BlockState groundState = sl.getBlockState(groundPos);
        if (!groundState.isFaceSturdy(sl, groundPos, Direction.UP)) {
            return "no_solid_ground";
        }

        if (!isSpawnSpacePassable(sl, pos)) {
            return "feet_blocked";
        }

        if (!isSpawnSpacePassable(sl, pos.above())) {
            return "head_blocked";
        }

        AABB occupiedSpace = new AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 2.0D, pos.getZ() + 1.0D
        ).inflate(0.2D);
        if (!sl.getEntitiesOfClass(Entity.class, occupiedSpace, Entity::isAlive).isEmpty()) {
            return "occupied";
        }

        return null;
    }

    private boolean isSpawnSpacePassable(ServerLevel sl, BlockPos pos) {
        BlockState state = sl.getBlockState(pos);
        return state.getCollisionShape(sl, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private void heartbeatIfDue(ServerLevel sl) {
        if (sl.getGameTime() - lastHeartbeatTick < HEARTBEAT_INTERVAL_TICKS) return;
        lastHeartbeatTick = sl.getGameTime();

        TraderDefinition definition = definitionFor(traderType);
        Entity live = traderNpcId == null ? null : sl.getEntity(traderNpcId);
        if (live != null && live.isAlive()) {
            CityDataSync.heartbeatLiveNpcAsync(
                    sl, live, definition.npcType(), cityName, sourceId.toString(), worldPosition.toShortString()
            );
        }
    }

    private void associateWithCity(ServerLevel sl, Entity entity) {
        CityManager manager = CityManager.get(sl);
        City city = manager.getCity(cityName);
        if (city == null) {
            manager.addCity(cityName);
            city = manager.getCity(cityName);
        }
        if (city != null) {
            city.associateNpcWithBlock(worldPosition, entity);
            manager.setDirty();
        }
    }

    private Entity findOwnedTraderAnyType(ServerLevel sl) {
        if (traderNpcId != null) {
            Entity byUuid = sl.getEntity(traderNpcId);
            if (byUuid != null) {
                return byUuid;
            }
        }

        AABB area = new AABB(worldPosition).inflate(Math.max(spawnRadius + 16, 32));
        List<? extends Mob> nearby = sl.getEntitiesOfClass(Mob.class, area,
                e -> e.getTags().contains("britannia_trader_spawn") && e.getTags().contains(sourceTag()));
        if (!nearby.isEmpty()) {
            Entity found = nearby.get(0);
            traderNpcId = found.getUUID();
            LOGGER.info("Trader existing adopted for removal by source tag source={} npc={} type={} pos={}",
                    sourceId, traderNpcId, found.getType(), found.blockPosition());
            return found;
        }
        return null;
    }

    private void despawnTrackedNpcs(ServerLevel sl, String status, String reason) {
        TraderDefinition definition = definitionFor(traderType);
        Entity traderEntity = findOwnedTraderAnyType(sl);
        UUID npcId = traderEntity != null ? traderEntity.getUUID() : traderNpcId;

        if (npcId != null) {
            LOGGER.info("Trader removal requested source={} npc={} type={} city={} status={} reason={} liveEntityFound={}",
                    sourceId, npcId, definition.npcType(), cityName, status, reason, traderEntity != null);
            CityDataSync.markLiveNpcInactiveAsync(
                    sl, npcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), status, reason
            );

            if (traderEntity != null) {
                traderEntity.remove(RemovalReason.DISCARDED);
                LOGGER.info("Trader entity removed source={} npc={} entityType={} pos={} reason={}",
                        sourceId, npcId, traderEntity.getType(), traderEntity.blockPosition(), reason);
            }
        } else {
            LOGGER.info("Trader removal skipped source={} type={} city={} status={} reason={} because no tracked trader was found",
                    sourceId, definition.npcType(), cityName, status, reason);
        }

        for (UUID id : new ArrayList<>(townNpcIds)) {
            Entity e = sl.getEntity(id);
            LOGGER.info("Townsperson removal requested source={} npc={} city={} status={} reason={} liveEntityFound={}",
                    sourceId, id, cityName, status, reason, e != null);
            CityDataSync.markLiveNpcInactiveAsync(
                    sl, id, "townsperson", cityName, sourceId.toString(),
                    worldPosition.toShortString(), status, reason
            );
            if (e != null) {
                e.remove(RemovalReason.DISCARDED);
                LOGGER.info("Townsperson entity removed source={} npc={} pos={} reason={}",
                        sourceId, id, e.blockPosition(), reason);
            }
        }

        townNpcIds.clear();
        traderNpcId = null;
        savedTraderData = null;
        setChanged();
    }

    public void forceResync() {
        if (!(level instanceof ServerLevel sl)) return;
        despawnTrackedNpcs(sl, "despawned", "manual_resync");
        checkCooldown = 0;
        maintainTrader(sl);
    }

    public void applyAndResync(String traderType, String cityName, int townPersonAmount) {
        if (!(level instanceof ServerLevel sl)) return;
        String oldType = this.traderType;
        String newType = normalizeTraderType(traderType);
        String oldCity = this.cityName;
        int oldTownPersonAmount = this.townPersonAmount;
        String newCity = cityName == null ? "" : cityName.trim();

        LOGGER.info("Trader spawner config change requested source={} oldType={} newType={} oldCity={} newCity={} oldTownspeople={} newTownspeople={}",
                sourceId, oldType, newType, oldCity, newCity, oldTownPersonAmount, Math.max(0, townPersonAmount));
        int newTownPersonAmount = Math.max(0, townPersonAmount);
        boolean unchanged = oldType.equals(newType)
                && (oldCity == null ? "" : oldCity.trim()).equals(newCity)
                && oldTownPersonAmount == newTownPersonAmount;

        if (unchanged) {
            LOGGER.info("Trader spawner config unchanged source={} type={} city={} townspeople={} action=keep_existing_npc",
                    sourceId, oldType, oldCity, oldTownPersonAmount);
            this.traderType = newType;
            this.cityName = newCity;
            this.townPersonAmount = newTownPersonAmount;
            this.checkCooldown = 0;
            this.initTicks = LOAD_GRACE_TICKS;
            setChanged();
            maintainTrader(sl);
            maintainTownspeople(sl);
            return;
        }

        despawnTrackedNpcs(sl, "despawned", "config_changed");

        this.traderType = newType;
        this.cityName = newCity;
        this.townPersonAmount = newTownPersonAmount;
        this.checkCooldown = 0;
        this.initTicks = LOAD_GRACE_TICKS;

        LOGGER.info("Trader spawner config applied source={} oldType={} newType={} city={} townspeople={}",
                sourceId, oldType, this.traderType, this.cityName, this.townPersonAmount);
        setChanged();
        maintainTrader(sl);
        maintainTownspeople(sl);
    }

    public void onDestroyed(ServerLevel sl) {
        despawnTrackedNpcs(sl, "despawned", "spawn_block_removed");
    }

    public void applyCityUpdate(int food, int treasury) {
        if (!(level instanceof ServerLevel sl)) return;
        LOGGER.info("Trader spawner city update source={} city={} food={} treasuryCopper={}",
                sourceId, cityName, food, treasury);
        maintainTrader(sl);
    }

    public void setSupplyLevels(double food, double wood, double metal, double stone,
                                double textile, double alcohol, double technology) {
        this.foodSupply = food;
        this.woodSupply = wood;
        this.metalSupply = metal;
        this.stoneSupply = stone;
        this.textileSupply = textile;
        this.alcoholSupply = alcohol;
        this.technologySupply = technology;
        setChanged();
    }

    private static String normalizeTraderType(String type) {
        return TraderTypes.normalize(type);
    }

    private TraderDefinition definitionFor(String configuredType) {
        return TraderTypes.byId(configuredType);
    }

    private static final class SpawnSearchStats {
        private int chunkNotLoaded;
        private int noSolidGround;
        private int feetBlocked;
        private int headBlocked;
        private int occupied;
        private int other;
        private String firstRejection = "";

        private void record(String reason, BlockPos pos) {
            if (firstRejection.isBlank()) {
                firstRejection = reason + " at " + pos.toShortString();
            }

            switch (reason) {
                case "chunk_not_loaded" -> chunkNotLoaded++;
                case "no_solid_ground" -> noSolidGround++;
                case "feet_blocked" -> feetBlocked++;
                case "head_blocked" -> headBlocked++;
                case "occupied" -> occupied++;
                default -> other++;
            }
        }

        private String summary() {
            return "rejections{chunk_not_loaded=" + chunkNotLoaded
                    + ", no_solid_ground=" + noSolidGround
                    + ", feet_blocked=" + feetBlocked
                    + ", head_blocked=" + headBlocked
                    + ", occupied=" + occupied
                    + ", other=" + other
                    + ", first=" + firstRejection
                    + "}";
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("SourceId", sourceId);
        tag.putString("CityName", cityName);
        tag.putString("TraderType", traderType);
        tag.putInt("TownPersonAmount", townPersonAmount);
        tag.putInt("SpawnRadius", spawnRadius);
        if (traderNpcId != null) tag.putUUID("TraderNpcId", traderNpcId);
        if (savedTraderData != null) tag.put("SavedTraderData", savedTraderData);

        tag.putDouble("FoodSupply", foodSupply);
        tag.putDouble("WoodSupply", woodSupply);
        tag.putDouble("MetalSupply", metalSupply);
        tag.putDouble("StoneSupply", stoneSupply);
        tag.putDouble("TextileSupply", textileSupply);
        tag.putDouble("AlcoholSupply", alcoholSupply);
        tag.putDouble("TechnologySupply", technologySupply);

        ListTag towns = new ListTag();
        for (UUID id : townNpcIds) {
            CompoundTag town = new CompoundTag();
            town.putUUID("NPC", id);
            towns.add(town);
        }
        tag.put(TAG_TOWN_NPCS, towns);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("SourceId")) sourceId = tag.getUUID("SourceId");
        cityName = tag.getString("CityName");
        traderType = normalizeTraderType(tag.getString("TraderType"));
        townPersonAmount = tag.getInt("TownPersonAmount");
        if (tag.contains("SpawnRadius")) spawnRadius = Math.max(1, tag.getInt("SpawnRadius"));
        if (tag.hasUUID("TraderNpcId")) traderNpcId = tag.getUUID("TraderNpcId");
        if (tag.contains("SavedTraderData")) savedTraderData = tag.getCompound("SavedTraderData");

        if (tag.contains("FoodSupply")) foodSupply = tag.getDouble("FoodSupply");
        if (tag.contains("WoodSupply")) woodSupply = tag.getDouble("WoodSupply");
        if (tag.contains("MetalSupply")) metalSupply = tag.getDouble("MetalSupply");
        if (tag.contains("StoneSupply")) stoneSupply = tag.getDouble("StoneSupply");
        if (tag.contains("TextileSupply")) textileSupply = tag.getDouble("TextileSupply");
        if (tag.contains("AlcoholSupply")) alcoholSupply = tag.getDouble("AlcoholSupply");
        if (tag.contains("TechnologySupply")) technologySupply = tag.getDouble("TechnologySupply");

        townNpcIds.clear();
        if (tag.contains(TAG_TOWN_NPCS)) {
            ListTag towns = tag.getList(TAG_TOWN_NPCS, Tag.TAG_COMPOUND);
            for (int i = 0; i < towns.size(); i++) {
                CompoundTag town = towns.getCompound(i);
                if (town.hasUUID("NPC")) townNpcIds.add(town.getUUID("NPC"));
            }
        }
    }
}

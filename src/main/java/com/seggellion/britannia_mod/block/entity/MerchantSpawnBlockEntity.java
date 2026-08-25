package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.entity.AbstractEconomyMerchantEntity;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.merchant.MerchantDefinition;
import com.seggellion.britannia_mod.merchant.MerchantFoodSupplyGate;
import com.seggellion.britannia_mod.merchant.MerchantTypes;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.spawner.PopulationMaintenance;
import com.seggellion.britannia_mod.trader.TraderAppearance;
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

public class MerchantSpawnBlockEntity extends BlockEntity {
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
    private UUID merchantNpcId;
    private CompoundTag savedMerchantData;
    private final List<UUID> townNpcIds = new ArrayList<>();

    private String cityName = "";
    private String merchantType = MerchantTypes.BAKER;
    private int townPersonAmount = 0;
    private int spawnRadius = 5;
    private int initTicks = 0;
    private int checkCooldown = 0;
    private long lastHeartbeatTick = 0;

    public MerchantSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.MERCHANT_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String name) {
        this.cityName = name == null ? "" : name;
        setChanged();
    }

    public String getMerchantType() {
        return merchantType;
    }

    public void setMerchantType(String type) {
        this.merchantType = normalizeMerchantType(type);
        setChanged();
    }

    public int getTownPersonAmount() {
        return townPersonAmount;
    }

    public void setTownPersonAmount(int amount) {
        this.townPersonAmount = Math.max(0, amount);
        setChanged();
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (initTicks < LOAD_GRACE_TICKS) {
            initTicks++;
            return;
        }

        if (cityName == null || cityName.isBlank()) return;

        if (checkCooldown-- > 0) {
            heartbeatIfDue(serverLevel);
            return;
        }
        checkCooldown = CHECK_INTERVAL_TICKS;

        // Vendor/Trader Milestone 16: converge onto the authoritative post
        // architecture. Until a conversion SUCCEEDS (city resolved from the
        // bootstrap registry, type mapped), everything below keeps running
        // unchanged -- a world without its backend keeps its merchants.
        if (com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrator
                .migrateMerchantBlock(serverLevel, this)) {
            return;
        }

        maintainUnderFoodGate(serverLevel, true);
        heartbeatIfDue(serverLevel);
    }

    /**
     * Runs maintenance under the definition's food-supply gate. Types without a minimum (baker,
     * tavernkeeper, costermonger) never consult the reading and behave exactly as before; a gated
     * type (farmer) follows the sibling food-gated spawners' contract -- maintain at or above the
     * minimum, despawn the staff below it, and do neither while Rails has never answered.
     */
    private void maintainUnderFoodGate(ServerLevel serverLevel, boolean includeTownspeople) {
        MerchantDefinition definition = definitionFor(merchantType);
        switch (MerchantFoodSupplyGate.decide(serverLevel, definition, cityName)) {
            case SKIP -> { }
            case DESPAWN -> despawnForInsufficientFood(serverLevel, definition);
            case MAINTAIN -> {
                maintainMerchant(serverLevel);
                if (includeTownspeople) maintainTownspeople(serverLevel);
            }
        }
    }

    private void despawnForInsufficientFood(ServerLevel serverLevel, MerchantDefinition definition) {
        if (merchantNpcId == null && savedMerchantData == null && townNpcIds.isEmpty()) return;
        LOGGER.info("Merchant despawn: food supply below minimum source={} type={} city={} minimum={}",
                sourceId, definition.configKey(), cityName, definition.minimumFoodSupply());
        despawnTrackedNpcs(serverLevel, "despawned", "insufficient_food_supply");
    }

    /**
     * Milestone 16 migration despawn: removes ONLY the legacy-managed merchant
     * NPC (the authoritative assignment pipeline staffs the migrated post, so
     * leaving it would duplicate). TownPersons and their tracked ids are
     * deliberately untouched -- owner decision #12 preserves them until the
     * regional population system reaches parity.
     */
    public void despawnManagedNpcForMigration(ServerLevel serverLevel) {
        MerchantDefinition definition = definitionFor(merchantType);
        Entity merchantEntity = findOwnedMerchantAnyType(serverLevel);
        UUID npcId = merchantEntity != null ? merchantEntity.getUUID() : merchantNpcId;

        if (npcId != null) {
            CityDataSync.markLiveNpcInactiveAsync(
                    serverLevel, npcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "despawned", "migrated_to_authoritative_post"
            );
            LOGGER.info("Legacy merchant despawned for migration source={} npc={} type={} city={} railsSync=scheduled",
                    sourceId, npcId, definition.npcType(), cityName);
            if (merchantEntity != null) {
                merchantEntity.remove(RemovalReason.DISCARDED);
            }
        }
        merchantNpcId = null;
        savedMerchantData = null;
    }

    private void maintainMerchant(ServerLevel serverLevel) {
        MerchantDefinition definition = definitionFor(merchantType);
        Entity live = findTrackedMerchant(serverLevel, definition);

        if (live != null && live.isAlive()) {
            configureEntity(live, definition);
            updateSnapshot(live);
            enforceBoundary(serverLevel, live);
            return;
        }

        if (merchantNpcId != null) {
            CityDataSync.markLiveNpcInactiveAsync(
                    serverLevel, merchantNpcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "dead", "missing_or_dead"
            );
            LOGGER.info("Rails merchant despawn/delete scheduled source={} npc={} type={} city={} status=dead reason=missing_or_dead railsSync=scheduled",
                    sourceId, merchantNpcId, definition.npcType(), cityName);
        }

        spawnMerchant(serverLevel, definition);
    }

    private void spawnMerchant(ServerLevel serverLevel, MerchantDefinition definition) {
        LOGGER.info("Merchant spawn attempt source={} type={} city={} spawner={} radius={}",
                sourceId, definition.configKey(), cityName, worldPosition, spawnRadius);
        BlockPos spawnPos = findAnchoredSpawnPos(serverLevel, "merchant " + definition.configKey());
        if (spawnPos == null) {
            LOGGER.warn("Merchant spawn skipped source={} city={} type={} spawner={} reason=no_valid_position",
                    sourceId, cityName, definition.configKey(), worldPosition);
            return;
        }

        if (merchantNpcId == null) merchantNpcId = UUID.randomUUID();
        Entity merchant = createOrRestoreMerchant(serverLevel, definition);
        if (!(merchant instanceof Mob mob)) {
            LOGGER.warn("Merchant spawn failed: could not create entity for type={}", definition.configKey());
            return;
        }

        merchant.setUUID(merchantNpcId);
        merchant.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        mob.setPersistenceRequired();
        mob.restrictTo(worldPosition, spawnRadius);
        configureEntity(merchant, definition);
        addSourceTags(merchant, definition);

        if (serverLevel.addFreshEntity(merchant)) {
            merchantNpcId = merchant.getUUID();
            updateSnapshot(merchant);
            associateWithCity(serverLevel, merchant);
            CityDataSync.upsertLiveNpcAsync(
                    serverLevel, merchant, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "active"
            );
            LOGGER.info("Merchant spawn success source={} npc={} type={} city={} pos={}",
                    sourceId, merchant.getUUID(), definition.npcType(), cityName, merchant.blockPosition());
            setChanged();
        } else {
            LOGGER.warn("Merchant spawn failed: addFreshEntity rejected npc={} source={}", merchant.getUUID(), sourceId);
        }
    }

    private Entity createOrRestoreMerchant(ServerLevel serverLevel, MerchantDefinition definition) {
        if (savedMerchantData != null) {
            CompoundTag tag = savedMerchantData.copy();
            tag.putUUID("UUID", merchantNpcId);
            Entity restored = EntityType.loadEntityRecursive(tag, serverLevel, entity -> entity);
            if (restored != null && restored.getType() == definition.entityType()) {
                return restored;
            }
        }
        return definition.entityType().create(serverLevel);
    }

    private Entity findTrackedMerchant(ServerLevel serverLevel, MerchantDefinition definition) {
        if (merchantNpcId != null) {
            Entity byUuid = serverLevel.getEntity(merchantNpcId);
            if (byUuid != null && byUuid.getType() == definition.entityType()) {
                return byUuid;
            }
        }

        AABB area = new AABB(worldPosition).inflate(spawnRadius + 8);
        List<? extends Mob> nearby = serverLevel.getEntitiesOfClass(Mob.class, area,
                entity -> entity.getType() == definition.entityType() && entity.getTags().contains(sourceTag()));
        if (!nearby.isEmpty()) {
            Entity found = nearby.get(0);
            merchantNpcId = found.getUUID();
            LOGGER.info("Merchant existing reused by source tag source={} npc={} type={} city={} pos={}",
                    sourceId, merchantNpcId, definition.configKey(), cityName, found.blockPosition());
            return found;
        }
        return null;
    }

    private void configureEntity(Entity entity, MerchantDefinition definition) {
        if (entity instanceof CitizenEntity citizen) {
            if (citizen.getCityName() == null || citizen.getCityName().isBlank()) citizen.setCityName(cityName);
            if (citizen.getPersonalName() == null || citizen.getPersonalName().equals("Unnamed")) {
                String gender = selectMerchantGender(definition);
                citizen.setGender(gender);
                citizen.setPersonalName("male".equals(gender) ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName());
            }
            applyMerchantAppearance(citizen, definition);
        } else {
            callStringSetter(entity, "setCityName", cityName);
        }

        if (entity instanceof AbstractEconomyMerchantEntity merchant) {
            merchant.setSpawnBlockPos(worldPosition);
        }

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.restrictTo(worldPosition, spawnRadius);
        }
        addSourceTags(entity, definition);
    }

    private String selectMerchantGender(MerchantDefinition definition) {
        TraderAppearance appearance = definition.appearance();
        List<String> genders = appearance == null
                ? List.of("male", "female")
                : appearance.safeAllowedGenders();
        int index = Math.floorMod((sourceId + ":" + definition.configKey()).hashCode(), genders.size());
        return genders.get(index);
    }

    private void applyMerchantAppearance(CitizenEntity citizen, MerchantDefinition definition) {
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

    private void addSourceTags(Entity entity, MerchantDefinition definition) {
        entity.addTag("britannia_merchant_spawn");
        entity.addTag(sourceTag());
        entity.addTag("merchant_type_" + definition.configKey());
    }

    private String sourceTag() {
        return "merchant_source_" + sourceId.toString().replace("-", "");
    }

    private void callStringSetter(Entity entity, String methodName, String value) {
        try {
            entity.getClass().getMethod(methodName, String.class).invoke(entity, value);
        } catch (Exception ignored) {
        }
    }

    private void updateSnapshot(Entity entity) {
        savedMerchantData = new CompoundTag();
        entity.saveWithoutId(savedMerchantData);
        savedMerchantData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        savedMerchantData.putUUID("UUID", entity.getUUID());
        setChanged();
    }

    private void enforceBoundary(ServerLevel serverLevel, Entity entity) {
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
            BlockPos ground = findAnchoredSpawnPos(serverLevel, "boundary reset " + entity.getType());
            if (ground == null) {
                LOGGER.warn("Merchant boundary reset skipped source={} npc={} type={} spawner={} reason=no_valid_position",
                        sourceId, entity.getUUID(), entity.getType(), worldPosition);
                mob.getNavigation().stop();
                return;
            }
            LOGGER.info("Merchant boundary reset teleport source={} npc={} type={} from={} to={} spawner={} reason=outside_hard_radius",
                    sourceId, entity.getUUID(), entity.getType(), entity.blockPosition(), ground, worldPosition);
            mob.teleportTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
            mob.getNavigation().stop();
        } else {
            mob.getNavigation().moveTo(centerX, centerY, centerZ, 1.2D);
        }
    }

    private void maintainTownspeople(ServerLevel serverLevel) {
        townNpcIds.removeIf(id -> {
            Entity entity = serverLevel.getEntity(id);
            return entity == null || !entity.isAlive();
        });

        // Bounded, because spawnTownsperson can legitimately fail forever: an unbounded
        // "loop until the shortfall is gone" freezes the server thread outright when no valid
        // position exists. Whatever is left over is picked up by the next maintenance cycle.
        int startingPopulation = townNpcIds.size();
        PopulationMaintenance.Outcome outcome = PopulationMaintenance.fill(
                startingPopulation, townPersonAmount, MAX_TOWNSPERSON_SPAWN_ATTEMPTS_PER_CYCLE,
                () -> spawnTownsperson(serverLevel));

        if (outcome.incomplete(startingPopulation, townPersonAmount)) {
            // One line per cycle rather than one per failed candidate: the old per-attempt warning
            // fired from inside a loop that never ended, which is how a freeze became a log flood.
            LOGGER.debug("Merchant townsperson maintenance incomplete source={} city={} spawner={} current={} target={} attempts={} successful={}",
                    sourceId, cityName, worldPosition, townNpcIds.size(), townPersonAmount,
                    outcome.attempts(), outcome.successes());
        }
        setChanged();
    }

    /** @return whether a townsperson was actually added, which is what bounds the caller's loop. */
    private boolean spawnTownsperson(ServerLevel serverLevel) {
        // Failure is summarised once per maintenance cycle by the caller, so this search does not
        // log its own: it runs up to the attempt budget per cycle, and used to run without end.
        BlockPos spawnPos = findAnchoredSpawnPos(serverLevel, "townsperson", false);
        if (spawnPos == null) {
            return false;
        }

        TownPersonEntity townPerson = EntityRegistry.TOWNSPERSON.get().create(serverLevel);
        if (townPerson == null) return false;

        boolean male = serverLevel.random.nextBoolean();
        townPerson.setGender(male ? "male" : "female");
        townPerson.setPersonalName(male ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName());
        townPerson.setCityName(cityName);
        townPerson.setSpawnPosition(worldPosition);
        townPerson.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        townPerson.setPersistenceRequired();
        townPerson.restrictTo(worldPosition, spawnRadius);
        townPerson.addTag("britannia_townsperson_spawn");
        townPerson.addTag(sourceTag());

        if (serverLevel.addFreshEntity(townPerson)) {
            townNpcIds.add(townPerson.getUUID());
            associateWithCity(serverLevel, townPerson);
            CityDataSync.upsertLiveNpcAsync(
                    serverLevel, townPerson, "townsperson", cityName, sourceId.toString(),
                    worldPosition.toShortString(), "active"
            );
            LOGGER.info("Merchant townsperson spawn success source={} npc={} city={}",
                    sourceId, townPerson.getUUID(), cityName);
            return true;
        }
        return false;
    }

    private BlockPos findAnchoredSpawnPos(ServerLevel serverLevel, String purpose) {
        return findAnchoredSpawnPos(serverLevel, purpose, true);
    }

    /**
     * @param warnOnFailure whether an exhausted search logs. Townsperson maintenance passes false:
     *                      it can attempt several searches per cycle and reports them as one line.
     */
    private BlockPos findAnchoredSpawnPos(ServerLevel serverLevel, String purpose, boolean warnOnFailure) {
        SpawnSearchStats stats = new SpawnSearchStats();
        int radius = Math.max(0, spawnRadius);
        int[] yOffsets = {0, -1, 1};

        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            int offX = radius == 0 ? 0 : serverLevel.random.nextInt(radius * 2 + 1) - radius;
            int offZ = radius == 0 ? 0 : serverLevel.random.nextInt(radius * 2 + 1) - radius;

            for (int dy : yOffsets) {
                BlockPos pos = new BlockPos(worldPosition.getX() + offX, worldPosition.getY() + dy, worldPosition.getZ() + offZ);
                String rejection = spawnRejectionReason(serverLevel, pos);
                if (rejection == null) return pos;
                stats.record(rejection, pos);
            }
        }

        for (int offX = -radius; offX <= radius; offX++) {
            for (int offZ = -radius; offZ <= radius; offZ++) {
                for (int dy : yOffsets) {
                    BlockPos pos = new BlockPos(worldPosition.getX() + offX, worldPosition.getY() + dy, worldPosition.getZ() + offZ);
                    String rejection = spawnRejectionReason(serverLevel, pos);
                    if (rejection == null) return pos;
                    stats.record(rejection, pos);
                }
            }
        }

        if (warnOnFailure) {
            LOGGER.warn("Merchant spawn position search failed source={} purpose={} city={} type={} spawner={} radius={} {}",
                    sourceId, purpose, cityName, merchantType, worldPosition, radius, stats.summary());
        }
        return null;
    }

    private String spawnRejectionReason(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.hasChunkAt(pos)) {
            return "chunk_not_loaded";
        }

        BlockPos groundPos = pos.below();
        BlockState groundState = serverLevel.getBlockState(groundPos);
        if (!groundState.isFaceSturdy(serverLevel, groundPos, Direction.UP)) {
            return "no_solid_ground";
        }

        if (!isSpawnSpacePassable(serverLevel, pos)) {
            return "feet_blocked";
        }

        if (!isSpawnSpacePassable(serverLevel, pos.above())) {
            return "head_blocked";
        }

        AABB occupiedSpace = new AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 2.0D, pos.getZ() + 1.0D
        ).inflate(0.2D);
        if (!serverLevel.getEntitiesOfClass(Entity.class, occupiedSpace, Entity::isAlive).isEmpty()) {
            return "occupied";
        }

        return null;
    }

    private boolean isSpawnSpacePassable(ServerLevel serverLevel, BlockPos pos) {
        BlockState state = serverLevel.getBlockState(pos);
        return state.getCollisionShape(serverLevel, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private void heartbeatIfDue(ServerLevel serverLevel) {
        if (serverLevel.getGameTime() - lastHeartbeatTick < HEARTBEAT_INTERVAL_TICKS) return;
        lastHeartbeatTick = serverLevel.getGameTime();

        MerchantDefinition definition = definitionFor(merchantType);
        Entity live = merchantNpcId == null ? null : serverLevel.getEntity(merchantNpcId);
        if (live != null && live.isAlive()) {
            CityDataSync.heartbeatLiveNpcAsync(
                    serverLevel, live, definition.npcType(), cityName, sourceId.toString(), worldPosition.toShortString()
            );
        }
    }

    private void associateWithCity(ServerLevel serverLevel, Entity entity) {
        CityManager manager = CityManager.get(serverLevel);
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

    private Entity findOwnedMerchantAnyType(ServerLevel serverLevel) {
        if (merchantNpcId != null) {
            Entity byUuid = serverLevel.getEntity(merchantNpcId);
            if (byUuid != null) return byUuid;
        }

        AABB area = new AABB(worldPosition).inflate(Math.max(spawnRadius + 16, 32));
        List<? extends Mob> nearby = serverLevel.getEntitiesOfClass(Mob.class, area,
                entity -> entity.getTags().contains("britannia_merchant_spawn") && entity.getTags().contains(sourceTag()));
        if (!nearby.isEmpty()) {
            Entity found = nearby.get(0);
            merchantNpcId = found.getUUID();
            LOGGER.info("Merchant existing adopted for removal by source tag source={} npc={} type={} pos={}",
                    sourceId, merchantNpcId, found.getType(), found.blockPosition());
            return found;
        }
        return null;
    }

    private void despawnTrackedNpcs(ServerLevel serverLevel, String status, String reason) {
        MerchantDefinition definition = definitionFor(merchantType);
        Entity merchantEntity = findOwnedMerchantAnyType(serverLevel);
        UUID npcId = merchantEntity != null ? merchantEntity.getUUID() : merchantNpcId;

        if (npcId != null) {
            LOGGER.info("Merchant removal requested source={} npc={} type={} city={} status={} reason={} liveEntityFound={}",
                    sourceId, npcId, definition.npcType(), cityName, status, reason, merchantEntity != null);
            CityDataSync.markLiveNpcInactiveAsync(
                    serverLevel, npcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), status, reason
            );
            LOGGER.info("Rails merchant despawn/delete scheduled source={} npc={} type={} city={} status={} reason={} railsSync=scheduled",
                    sourceId, npcId, definition.npcType(), cityName, status, reason);

            if (merchantEntity != null) {
                merchantEntity.remove(RemovalReason.DISCARDED);
            }
        }

        for (UUID id : new ArrayList<>(townNpcIds)) {
            Entity entity = serverLevel.getEntity(id);
            CityDataSync.markLiveNpcInactiveAsync(
                    serverLevel, id, "townsperson", cityName, sourceId.toString(),
                    worldPosition.toShortString(), status, reason
            );
            if (entity != null) {
                entity.remove(RemovalReason.DISCARDED);
            }
        }

        townNpcIds.clear();
        merchantNpcId = null;
        savedMerchantData = null;
        setChanged();
    }

    public void forceResync() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        despawnTrackedNpcs(serverLevel, "despawned", "manual_resync");
        checkCooldown = 0;
        maintainUnderFoodGate(serverLevel, false);
    }

    public void applyAndResync(String merchantType, String cityName, int townPersonAmount) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        String oldType = this.merchantType;
        String newType = normalizeMerchantType(merchantType);
        String oldCity = this.cityName;
        int oldTownPersonAmount = this.townPersonAmount;
        String newCity = cityName == null ? "" : cityName.trim();
        int newTownPersonAmount = Math.max(0, townPersonAmount);

        boolean unchanged = oldType.equals(newType)
                && (oldCity == null ? "" : oldCity.trim()).equals(newCity)
                && oldTownPersonAmount == newTownPersonAmount;

        if (unchanged) {
            this.merchantType = newType;
            this.cityName = newCity;
            this.townPersonAmount = newTownPersonAmount;
            this.checkCooldown = 0;
            this.initTicks = LOAD_GRACE_TICKS;
            setChanged();
            maintainUnderFoodGate(serverLevel, true);
            return;
        }

        despawnTrackedNpcs(serverLevel, "despawned", "config_changed");

        this.merchantType = newType;
        this.cityName = newCity;
        this.townPersonAmount = newTownPersonAmount;
        this.checkCooldown = 0;
        this.initTicks = LOAD_GRACE_TICKS;
        setChanged();
        maintainUnderFoodGate(serverLevel, true);
    }

    public void onDestroyed(ServerLevel serverLevel) {
        despawnTrackedNpcs(serverLevel, "despawned", "spawn_block_removed");
    }

    private static String normalizeMerchantType(String type) {
        return MerchantTypes.normalize(type);
    }

    private MerchantDefinition definitionFor(String configuredType) {
        return MerchantTypes.byId(configuredType);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("SourceId", sourceId);
        tag.putString("CityName", cityName);
        tag.putString("MerchantType", merchantType);
        tag.putInt("TownPersonAmount", townPersonAmount);
        tag.putInt("SpawnRadius", spawnRadius);
        if (merchantNpcId != null) tag.putUUID("MerchantNpcId", merchantNpcId);
        if (savedMerchantData != null) tag.put("SavedMerchantData", savedMerchantData);

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
        merchantType = normalizeMerchantType(tag.getString("MerchantType"));
        townPersonAmount = tag.getInt("TownPersonAmount");
        if (tag.contains("SpawnRadius")) spawnRadius = Math.max(1, tag.getInt("SpawnRadius"));
        if (tag.hasUUID("MerchantNpcId")) merchantNpcId = tag.getUUID("MerchantNpcId");
        if (tag.contains("SavedMerchantData")) savedMerchantData = tag.getCompound("SavedMerchantData");

        townNpcIds.clear();
        if (tag.contains(TAG_TOWN_NPCS)) {
            ListTag towns = tag.getList(TAG_TOWN_NPCS, Tag.TAG_COMPOUND);
            for (int i = 0; i < towns.size(); i++) {
                CompoundTag town = towns.getCompound(i);
                if (town.hasUUID("NPC")) townNpcIds.add(town.getUUID("NPC"));
            }
        }
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
}

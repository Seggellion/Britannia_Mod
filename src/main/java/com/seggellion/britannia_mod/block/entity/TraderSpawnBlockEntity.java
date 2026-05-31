package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.entity.EntityWoodMerchant;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.util.Util;
import net.minecraft.core.BlockPos;
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
import java.util.Locale;
import java.util.UUID;

public class TraderSpawnBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int HEARTBEAT_INTERVAL_TICKS = 600;
    private static final int LOAD_GRACE_TICKS = 40;
    private static final String TAG_TOWN_NPCS = "TownNPCs";

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

        maintainTrader(sl);
        maintainTownspeople(sl);
        heartbeatIfDue(sl);
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
            CityDataSync.markLiveNpcInactive(
                    sl, traderNpcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), "dead", "missing_or_dead"
            );
        }

        spawnTrader(sl, definition);
    }

    private void spawnTrader(ServerLevel sl, TraderDefinition definition) {
        BlockPos spawnPos = Util.findGround(sl, worldPosition, spawnRadius);
        if (spawnPos == null) {
            LOGGER.warn("Trader spawn skipped: no safe ground near {} for city={} type={}",
                    worldPosition, cityName, definition.configKey());
            return;
        }

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
            CityDataSync.upsertLiveNpc(
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
            if (byUuid != null && byUuid.getType() == definition.entityType()) return byUuid;
        }

        AABB area = new AABB(worldPosition).inflate(spawnRadius + 8);
        List<? extends Mob> nearby = sl.getEntitiesOfClass(Mob.class, area,
                e -> e.getType() == definition.entityType() && e.getTags().contains(sourceTag()));
        if (!nearby.isEmpty()) {
            Entity found = nearby.get(0);
            traderNpcId = found.getUUID();
            return found;
        }
        return null;
    }

    private void configureEntity(ServerLevel sl, Entity entity, TraderDefinition definition) {
        if (entity instanceof CitizenEntity citizen) {
            if (citizen.getCityName() == null || citizen.getCityName().isBlank()) citizen.setCityName(cityName);
            if (citizen.getPersonalName() == null || citizen.getPersonalName().equals("Unnamed")) {
                boolean male = sl.random.nextBoolean();
                citizen.setGender(male ? "male" : "female");
                citizen.setPersonalName(male ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName());
            }
        } else {
            callStringSetter(entity, "setCityName", cityName);
        }

        if (entity instanceof EntityWoodMerchant woodMerchant) {
            woodMerchant.setSpawnBlockPos(worldPosition);
        }

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.restrictTo(worldPosition, spawnRadius);
        }
        addSourceTags(entity, definition);
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
            BlockPos ground = Util.findGround(sl, worldPosition, spawnRadius);
            if (ground == null) ground = worldPosition;
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

        while (townNpcIds.size() < townPersonAmount) {
            spawnTownsperson(sl);
        }
        setChanged();
    }

    private void spawnTownsperson(ServerLevel sl) {
        BlockPos spawnPos = Util.findGround(sl, worldPosition, spawnRadius);
        if (spawnPos == null) return;

        TownPersonEntity townPerson = EntityRegistry.TOWNSPERSON.get().create(sl);
        if (townPerson == null) return;

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
            CityDataSync.upsertLiveNpc(
                    sl, townPerson, "townsperson", cityName, sourceId.toString(),
                    worldPosition.toShortString(), "active"
            );
            LOGGER.info("Townsperson spawn success source={} npc={} city={}",
                    sourceId, townPerson.getUUID(), cityName);
        }
    }

    private void heartbeatIfDue(ServerLevel sl) {
        if (sl.getGameTime() - lastHeartbeatTick < HEARTBEAT_INTERVAL_TICKS) return;
        lastHeartbeatTick = sl.getGameTime();

        TraderDefinition definition = definitionFor(traderType);
        Entity live = traderNpcId == null ? null : sl.getEntity(traderNpcId);
        if (live != null && live.isAlive()) {
            CityDataSync.heartbeatLiveNpc(
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

    private void despawnTrackedNpcs(ServerLevel sl, String status, String reason) {
        TraderDefinition definition = definitionFor(traderType);
        if (traderNpcId != null) {
            Entity e = sl.getEntity(traderNpcId);
            if (e != null) {
                e.remove(RemovalReason.DISCARDED);
            }
            CityDataSync.markLiveNpcInactive(
                    sl, traderNpcId, definition.npcType(), cityName, sourceId.toString(),
                    worldPosition.toShortString(), status, reason
            );
            LOGGER.info("Trader despawn source={} npc={} status={} reason={}",
                    sourceId, traderNpcId, status, reason);
        }

        for (UUID id : new ArrayList<>(townNpcIds)) {
            Entity e = sl.getEntity(id);
            if (e != null) e.remove(RemovalReason.DISCARDED);
            CityDataSync.markLiveNpcInactive(
                    sl, id, "townsperson", cityName, sourceId.toString(),
                    worldPosition.toShortString(), status, reason
            );
        }

        townNpcIds.clear();
        traderNpcId = null;
        savedTraderData = null;
        setChanged();
    }

    public void forceResync() {
        if (!(level instanceof ServerLevel sl)) return;
        despawnTrackedNpcs(sl, "replaced", "manual_resync");
        checkCooldown = 0;
        maintainTrader(sl);
    }

    public void applyAndResync(String traderType, String cityName, int townPersonAmount) {
        if (!(level instanceof ServerLevel sl)) return;
        despawnTrackedNpcs(sl, "replaced", "config_changed");

        this.traderType = normalizeTraderType(traderType);
        this.cityName = cityName == null ? "" : cityName.trim();
        this.townPersonAmount = Math.max(0, townPersonAmount);
        this.checkCooldown = 0;
        this.initTicks = LOAD_GRACE_TICKS;

        LOGGER.info("Trader spawner config applied source={} type={} city={} townspeople={}",
                sourceId, this.traderType, this.cityName, this.townPersonAmount);
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
        if (type == null || type.isBlank()) return "wood_trader";
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private TraderDefinition definitionFor(String configuredType) {
        String normalized = normalizeTraderType(configuredType);
        return switch (normalized) {
            case "wood_trader", "wood_merchant" -> new TraderDefinition(
                    "wood_trader", "wood_trader", "Wood Trader", EntityRegistry.WOOD_MERCHANT_ENTITY.get());
            case "fish_trader" -> new TraderDefinition(
                    "fish_trader", "fish_trader", "Fish Trader", EntityRegistry.FISH_TRADER.get());
            case "salvage_trader" -> new TraderDefinition(
                    "salvage_trader", "salvage_trader", "Salvage Trader", EntityRegistry.SALVAGE_TRADER.get());
            case "alcohol_trader" -> new TraderDefinition(
                    "alcohol_trader", "alcohol_trader", "Alcohol Trader", EntityRegistry.ALCOHOL_TRADER.get());
            case "meat_trader" -> new TraderDefinition(
                    "meat_trader", "meat_trader", "Meat Trader", EntityRegistry.MEAT_TRADER.get());
            case "metal_trader", "metal_merchant" -> new TraderDefinition(
                    "metal_trader", "metal_trader", "Metal Trader", EntityRegistry.METAL_MERCHANT_ENTITY.get());
            case "stone_trader", "stone_merchant" -> new TraderDefinition(
                    "stone_trader", "stone_trader", "Stone Trader", EntityRegistry.STONE_MERCHANT_ENTITY.get());
            default -> new TraderDefinition(
                    "wood_trader", "wood_trader", "Wood Trader", EntityRegistry.WOOD_MERCHANT_ENTITY.get());
        };
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

    private record TraderDefinition(String configKey, String npcType, String roleTitle,
                                    EntityType<? extends Mob> entityType) {}
}

package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.util.Util;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.city.City;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EntityType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.entity.Mob;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TraderSpawnBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int TRADER_RADIUS = 1; // adjust as needed
    private static final int BOUNDARY_MARGIN = 1;
    private final Map<UUID, Integer> outsideTicks = new HashMap<>();
    private static final int OUTSIDE_DESPAWN_TICKS = 20 * 60; // 1 minute safety
 
    private int treasuryCopper = 0;
    private String cityName = "";
    private String traderType = "fish_trader"; // default
    private int townPersonAmount = 0;           // configurable
    private int spawnCooldown = 0;
    private double foodSupply;
    private double woodSupply;
    private double metalSupply;
    private double stoneSupply;
    private double textileSupply;
    private double alcoholSupply;
    private double technologySupply;
    private int coldStartTicks = 40; // ~2 seconds
    private final List<UUID> associatedNpcs = new ArrayList<>();
    private final Map<UUID, SavedNpc> saved = new HashMap<>();

    private static final int MAX_COOLDOWN = 1200;
    private static final int MAX_TRADERS = 1;
    private static final String TAG_SAVED_NPCS = "SavedNPCs";

private static final class SavedNpc {
    final UUID uuid;
    final net.minecraft.resources.ResourceLocation typeId;
    final CompoundTag nbt;

    SavedNpc(UUID uuid, net.minecraft.resources.ResourceLocation typeId, CompoundTag nbt) {
        this.uuid = uuid;
        this.typeId = typeId;
        this.nbt = nbt;
    }
}

private int getRequiredCopper() {
     LOGGER.info("traderType Copper! {}",traderType);
    return switch (traderType) {
        case "fish_trader" -> 200;
        case "meat_trader" -> 500;
        case "alcohol_trader" -> 500;
        case "salvage_trader" -> 0;
        default -> 100;
    };
}



    public TraderSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.TRADER_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    // === Getters / Setters ===
    public String getCityName() { return cityName; }
    public void setCityName(String name) { this.cityName = name; setChanged(); }

    public String getTraderType() { return traderType; }
    public void setTraderType(String type) { this.traderType = type; setChanged(); }

    public int getTownPersonAmount() { return townPersonAmount; }
    public void setTownPersonAmount(int amount) { this.townPersonAmount = amount; setChanged(); }

    // === Tick ===
public void serverTick() {
    if (level == null || level.isClientSide) return;
    ServerLevel sl = (ServerLevel) level;

    // Give restore a small head start on cold load
    if (coldStartTicks > 0) {
        // Try a restore once during the delay, then pause the rest of the tick work
        if (--coldStartTicks == 20) {
            restoreSavedNpcs(sl);
        }
        return;
    }


    // Always restore/reassociate first, every tick
    maintainNpcs(sl);

    if (cityName == null || cityName.isEmpty()) return;

    if (spawnCooldown-- > 0) return;
    spawnCooldown = MAX_COOLDOWN;

    long traderCount = associatedNpcs.stream()
        .map(sl::getEntity)
        .filter(e -> e != null && (
            e.getType() == EntityRegistry.FISH_TRADER.get() ||
            e.getType() == EntityRegistry.SALVAGE_TRADER.get() ||
            e.getType() == EntityRegistry.ALCOHOL_TRADER.get() ||
            e.getType() == EntityRegistry.MEAT_TRADER.get()))
        .count();

    // If we already have a saved trader snapshot, don’t spawn a new one
    boolean hasSavedTrader = !saved.isEmpty();
    if (traderCount < MAX_TRADERS && !hasSavedTrader) {
        spawnTrader(sl);
    }

    long currentTownspeople = associatedNpcs.stream()
        .map(sl::getEntity)
        .filter(e -> e != null && e.getType() == EntityRegistry.TOWNSPERSON.get())
        .count();

    if (currentTownspeople < townPersonAmount) {
        int toSpawn = townPersonAmount - (int) currentTownspeople;
        for (int i = 0; i < toSpawn; i++) spawnTownsperson(sl);
    }

    if (level.getGameTime() % (20 * 300) == 0) resyncAllNpcs(sl);
}

private boolean isTraderType(net.minecraft.resources.ResourceLocation typeId) {
    return typeId.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegistry.FISH_TRADER.get())) ||
        typeId.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegistry.ALCOHOL_TRADER.get())) ||
           typeId.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegistry.SALVAGE_TRADER.get())) ||
           typeId.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegistry.MEAT_TRADER.get()));
}

private void snapshotAndTrack(ServerLevel sl, Mob mob) {
    mob.setPersistenceRequired();

    CompoundTag t = new CompoundTag();
    mob.saveWithoutId(t);
    t.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(mob.getType()).toString());
    t.putUUID("UUID", mob.getUUID());

    SavedNpc sn = new SavedNpc(mob.getUUID(),
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
            t);

    saved.put(mob.getUUID(), sn);
    if (!associatedNpcs.contains(mob.getUUID())) {
        associatedNpcs.add(mob.getUUID());
    }

    // 🔥 ensure the BE is marked dirty so saveAdditional() runs on unload
    setChanged();
}


private void maintainNpcs(ServerLevel sl) {
    boolean touched = false;

    // 1) Refresh snapshots for any live NPCs we already know
    for (UUID id : new ArrayList<>(associatedNpcs)) {
        Entity e = sl.getEntity(id);
        if (e instanceof Mob mob && mob.isAlive()) {
            CompoundTag t = new CompoundTag();
            mob.saveWithoutId(t);
            t.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
            t.putUUID("UUID", mob.getUUID());
            saved.put(id, new SavedNpc(id,
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), t));
            touched = true; // we modified the snapshot set
        }
    }

    // 2) Resurrect missing NPCs from their saved NBT
    for (SavedNpc sn : new ArrayList<>(saved.values())) {
        Entity live = sl.getEntity(sn.uuid);
        if (live == null) {

CompoundTag tag = sn.nbt.copy();
if (tag.hasUUID("UUID")) tag.remove("UUID"); // ✅ Force new UUID generation

Entity recreated = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, sl, e -> e);

if (recreated != null) {
    sl.addFreshEntity(recreated);

    // Update saved maps with the new UUID to prevent repeated restores
    UUID newId = recreated.getUUID();
    saved.remove(sn.uuid);
    associatedNpcs.remove(sn.uuid);

    CompoundTag newTag = new CompoundTag();
    recreated.saveWithoutId(newTag);
    newTag.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(recreated.getType()).toString());
    newTag.putUUID("UUID", newId);

    saved.put(newId, new SavedNpc(newId,
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(recreated.getType()),
            newTag));
    associatedNpcs.add(newId);

    if (recreated instanceof Mob m) m.setPersistenceRequired();
    touched = true;
} else {
    saved.remove(sn.uuid);
    associatedNpcs.remove(sn.uuid);
    touched = true;
}
        }
    }

    if (touched) setChanged(); // 🔥 ensure changes persist
}



private void spawnTrader(ServerLevel sl) {
    BlockPos spawnPos = Util.findGround(sl, worldPosition, 10);
    if (spawnPos == null) return;

    boolean alreadyExists = !sl.getEntities(EntityRegistry.SALVAGE_TRADER.get(),
        e -> e.blockPosition().closerThan(worldPosition, 10)).isEmpty();
    if (alreadyExists) {
        return;
    }

    // === NEW: Sync treasuryCopper from the live CityInventory ===
    CityManager manager = CityManager.get(sl);
    City city = manager.getCity(cityName);
    if (city != null) {
        CityInventory inv = city.getInventory();
        int gold = inv.getCurrencyAmount("gold");
        int silver = inv.getCurrencyAmount("silver");
        int copper = inv.getCurrencyAmount("copper");
              LOGGER.warn("CityInventory! {}",inv);
               LOGGER.warn("city! {}",city);
        this.treasuryCopper = copper;
    } else {
        this.treasuryCopper = 0;
        LOGGER.warn("City {} not found for trader spawn sync; assuming 0 treasury.", cityName);
    }

    // === Threshold check using latest synced treasury ===
    int requiredCopper = getRequiredCopper();
         LOGGER.warn("COPPER CHECK! {}",treasuryCopper);
          LOGGER.warn("COPPER CHECK! Required: {}",requiredCopper);
    if (treasuryCopper < requiredCopper) {
        return;
    }

    // === Spawn logic ===
    Mob trader = switch (traderType) {
        case "fish_trader" -> EntityRegistry.FISH_TRADER.get().create(sl);
        case "salvage_trader" -> EntityRegistry.SALVAGE_TRADER.get().create(sl);
        case "alcohol_trader" -> EntityRegistry.ALCOHOL_TRADER.get().create(sl);
        case "meat_trader" -> EntityRegistry.MEAT_TRADER.get().create(sl);
        default -> null;
    };

    if (trader == null) {
        LOGGER.warn("Unknown trader type: {}", traderType);
        return;
    }

    trader.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                  sl.random.nextFloat() * 360F, 0);
    trader.setPersistenceRequired();

    try {
        var gender = sl.random.nextBoolean() ? "male" : "female";
        trader.getClass().getMethod("setGender", String.class).invoke(trader, gender);
        String name = gender.equals("male") ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName();
        trader.getClass().getMethod("setPersonalName", String.class).invoke(trader, name);
        trader.getClass().getMethod("setCityName", String.class).invoke(trader, cityName);
    } catch (Exception ex) {
        LOGGER.warn("Trader entity does not support gender/name assignment: {}", traderType);
    }

    sl.addFreshEntity(trader);
    if (!associatedNpcs.contains(trader.getUUID())) associatedNpcs.add(trader.getUUID());
    snapshotAndTrack(sl, trader);
    // === Register NPC to Rails API ===
    String gender = "unknown";
    try {
        Object genderObj = trader.getClass().getMethod("getGender").invoke(trader);
        if (genderObj instanceof String g) gender = g;
    } catch (Exception ignored) {}

    String personalName;
    try {
        Object personalNameObj = trader.getClass().getMethod("getPersonalName").invoke(trader);
        personalName = (personalNameObj instanceof String) ? (String) personalNameObj : trader.getName().getString();
    } catch (Exception ex) {
        personalName = trader.getName().getString();
    }

    CityDataSync.registerNpc(
        sl,
        trader.getUUID(),
        traderType,
        cityName,
        personalName,
        "Trader NPC active in " + cityName,
        1,
        (int) trader.getHealth(),
        0,
        true,
        worldPosition.toShortString(),
        gender
    );


}


    private void spawnTownsperson(ServerLevel sl) {
        BlockPos spawnPos = Util.findGround(sl, worldPosition, 10);
        if (spawnPos == null) return;

        Mob citizen = EntityRegistry.TOWNSPERSON.get().create(sl);
        if (citizen == null) {
            LOGGER.warn("Failed to create Townsperson entity");
            return;
        }

        citizen.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    sl.random.nextFloat() * 360F, 0);
        citizen.setPersistenceRequired();

        try {
            var gender = sl.random.nextBoolean() ? "male" : "female";
            citizen.getClass().getMethod("setGender", String.class).invoke(citizen, gender);
            String name = gender.equals("male") ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName();
            citizen.getClass().getMethod("setPersonalName", String.class).invoke(citizen, name);
            citizen.getClass().getMethod("setCityName", String.class).invoke(citizen, cityName);
        } catch (Exception ex) {
            LOGGER.warn("Townsperson entity does not support gender/name assignment");
        }

        sl.addFreshEntity(citizen);
        associatedNpcs.add(citizen.getUUID());
         snapshotAndTrack(sl, citizen);
        CityDataSync.registerNpc(
            sl,
            citizen.getUUID(),
            "townsperson",
            cityName,
            citizen.getName().getString(),
            "Resident of " + cityName,
            1,
            (int) citizen.getHealth(),
            0,
            true,
            worldPosition.toShortString(),
            "unknown"
        );
    }

    // === NEW: Centralized NPC Resync Helper ===
    private void resyncAllNpcs(ServerLevel sl) {
        for (UUID id : associatedNpcs) {
            Entity e = sl.getEntity(id);
            if (e == null || !e.isAlive()) continue;

            String npcType = "unknown";
            if (e.getType() == EntityRegistry.TOWNSPERSON.get()) npcType = "townsperson";
            else if (e.getType() == EntityRegistry.FISH_TRADER.get()) npcType = "fish_trader";
            else if (e.getType() == EntityRegistry.SALVAGE_TRADER.get()) npcType = "salvage_trader";
            else if (e.getType() == EntityRegistry.ALCOHOL_TRADER.get()) npcType = "alcohol_trader";
            else if (e.getType() == EntityRegistry.MEAT_TRADER.get()) npcType = "meat_trader";

            String gender = "unknown";
            try {
                Object genderObj = e.getClass().getMethod("getGender").invoke(e);
                if (genderObj instanceof String g) gender = g;
            } catch (Exception ignored) {}

            String personalName;
            try {
                Object personalNameObj = e.getClass().getMethod("getPersonalName").invoke(e);
                personalName = (personalNameObj instanceof String) ? (String) personalNameObj : e.getName().getString();
            } catch (Exception ex) {
                personalName = e.getName().getString();
            }

            CityDataSync.registerNpc(
                sl,
                e.getUUID(),
                npcType,
                cityName,
                personalName,
                "Periodic resync for " + cityName,
                1,
                (int) ((Mob)e).getHealth(),
                0,
                true,
                worldPosition.toShortString(),
                gender
            );
        }
    }

    private void despawnAssociatedNpcs(ServerLevel sl) {
        for (UUID id : associatedNpcs) {
            Entity e = sl.getEntity(id);
            if (e != null) e.remove(RemovalReason.DISCARDED);
            CityDataSync.removeNpc(sl, id);
        }
        associatedNpcs.clear();
    }

    public void forceResync() {
        if (level instanceof ServerLevel sl) {
            despawnAssociatedNpcs(sl);
            spawnTrader(sl);
        }
    }

    public void applyAndResync(
        String traderType,
        String cityName,
        int townPersonAmount
    ) {
        if (!(level instanceof ServerLevel sl)) return;

        // 1. Apply config
        this.traderType = traderType;
        this.cityName = cityName;
        this.townPersonAmount = townPersonAmount;

        // 2. Clear ALL transient state
        spawnCooldown = 0;
        coldStartTicks = 0;
        treasuryCopper = 0;

        saved.clear();
        despawnAssociatedNpcs(sl);

        // 3. Force a fresh evaluation
        spawnTrader(sl);

        setChanged();
    }


    @Override
    public void setRemoved() {
        super.setRemoved();
       // if (level instanceof ServerLevel sl) despawnAssociatedNpcs(sl);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) {
            // Restore NPCs immediately when the chunk loads
        restoreSavedNpcs(sl); // try an immediate restore when the chunk loads

        }
    }

    public void onDestroyed(ServerLevel sl) {
        despawnAssociatedNpcs(sl); // intentional, permanent
    }

private void restoreSavedNpcs(ServerLevel sl) {
    if (saved.isEmpty()) return;

    boolean anyExists = saved.keySet().stream().anyMatch(id -> sl.getEntity(id) != null);
    if (anyExists) return; // skip restore — they're already live

    for (SavedNpc sn : new ArrayList<>(saved.values())) {
        if (sl.getEntity(sn.uuid) != null) continue;

        CompoundTag tag = sn.nbt.copy();
        if (tag.hasUUID("UUID")) tag.remove("UUID"); // regenerate UUID

        Entity recreated = EntityType.loadEntityRecursive(tag, sl, e -> e);
        if (recreated != null) {
            sl.addFreshEntity(recreated);

            UUID newId = recreated.getUUID();
            if (recreated instanceof Mob mob) mob.setPersistenceRequired();

            // Remove the old saved record and replace it with the new UUID snapshot
            saved.remove(sn.uuid);
            associatedNpcs.remove(sn.uuid);

            CompoundTag newTag = new CompoundTag();
            recreated.saveWithoutId(newTag);
            newTag.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(recreated.getType()).toString());
            newTag.putUUID("UUID", newId);

            saved.put(newId, new SavedNpc(newId,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(recreated.getType()),
                newTag));
            associatedNpcs.add(newId);

            setChanged();
        } else {
            LOGGER.info("Failed to restore NPC {} ({})", sn.uuid, sn.typeId);
        }

    }
}



    private void enforceBoundary(ServerLevel sl) {
        if (associatedNpcs.isEmpty()) return;

        final double centerX = worldPosition.getX() + 0.5;
        final double centerY = worldPosition.getY() + 1;
        final double centerZ = worldPosition.getZ() + 0.5;
        final double radius = TRADER_RADIUS;
        final double hardR = radius + 1.5; // teleport trigger
        final double softR = Math.max(1, radius - BOUNDARY_MARGIN);

        for (UUID id : new ArrayList<>(associatedNpcs)) {
            Entity e = sl.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                associatedNpcs.remove(id);
                outsideTicks.remove(id);
                continue;
            }

            double dx = mob.getX() - centerX;
            double dz = mob.getZ() - centerZ;
            double distSq = dx * dx + dz * dz;
            double dist = Math.sqrt(distSq);

            if (dist > softR && dist <= radius) {
                if (mob.getTarget() != null) mob.setTarget(null);
                mob.getNavigation().moveTo(centerX, centerY, centerZ, 1.2);
                mob.setYRot((float)(Math.atan2(-dz, -dx) * (180F / Math.PI)));
                continue;
            }

            if (dist > radius) {
                outsideTicks.put(id, outsideTicks.getOrDefault(id, 0) + 1);

                if (mob.getTarget() != null) mob.setTarget(null);
                mob.getNavigation().stop();

                if (dist > hardR) {
                    BlockPos safeGround = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPosition);
                    mob.teleportTo(safeGround.getX() + 0.5, safeGround.getY() + 1, safeGround.getZ() + 0.5);
                    mob.getNavigation().stop();
                } else {
                    mob.teleportTo(centerX, centerY, centerZ);
                    mob.getNavigation().stop();
                }

                mob.setDeltaMovement(0, 0, 0);
                outsideTicks.remove(id);
            }
        }
    }

public void applyCityUpdate(int food, int treasury) {
    if (!(level instanceof ServerLevel sl)) return;

    // Store latest treasury so spawn logic can use it in future ticks
    this.treasuryCopper = treasury;
    setChanged();

int requiredCopper = getRequiredCopper();

    boolean traderShouldExist = treasuryCopper >= requiredCopper;
    boolean townspeopleShouldExist = food >= 50;

    long traderCount = associatedNpcs.stream()
            .map(sl::getEntity)
            .filter(e -> e != null && (
                    e.getType() == EntityRegistry.FISH_TRADER.get() ||
                    e.getType() == EntityRegistry.SALVAGE_TRADER.get() ||
                    e.getType() == EntityRegistry.ALCOHOL_TRADER.get() ||
                    e.getType() == EntityRegistry.MEAT_TRADER.get()))
            .count();

    long townCount = associatedNpcs.stream()
            .map(sl::getEntity)
            .filter(e -> e != null && e.getType() == EntityRegistry.TOWNSPERSON.get())
            .count();

    // Trader logic
    if (!traderShouldExist && traderCount > 0) {
        despawnAssociatedNpcs(sl);
        LOGGER.info("Despawning trader in {} — treasury dropped below 200 copper (now at {})", cityName, treasuryCopper);
        return;
    }

    // Townspeople logic (optional threshold on food)
    if (!townspeopleShouldExist && townCount > 0) {
        despawnAssociatedNpcs(sl);
        LOGGER.info("Despawning townspeople in {} — insufficient food (now at {})", cityName, food);
        return;
    }

    // Spawn logic
    if (traderShouldExist && traderCount == 0) {
        LOGGER.info("Spawning trader in {} — treasury sufficient ({} copper)", cityName, treasuryCopper);
        spawnTrader(sl);
    }

    if (townspeopleShouldExist && townCount < townPersonAmount) {
        LOGGER.info("Spawning townspeople in {} — food sufficient ({})", cityName, food);
        for (int i = 0; i < townPersonAmount - townCount; i++) {
            spawnTownsperson(sl);
        }
    }
}


    public void setSupplyLevels(
            double food,
            double wood,
            double metal,
            double stone,
            double textile,
            double alcohol,
            double technology
    ) {
        this.foodSupply = food;
        this.woodSupply = wood;
        this.metalSupply = metal;
        this.stoneSupply = stone;
        this.textileSupply = textile;
        this.alcoholSupply = alcohol;
        this.technologySupply = technology;

        // Optional: persist or trigger recalculation logic
        setChanged(); // marks block entity dirty for saving
    }


    // === NBT ===

@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.saveAdditional(tag, provider);

    // === City & Trader Meta ===
    tag.putString("CityName", cityName);
    tag.putString("TraderType", traderType);
    tag.putInt("TownPersonAmount", townPersonAmount);
    tag.putInt("TreasuryCopper", treasuryCopper);

    // === Resource Levels ===
    tag.putDouble("FoodSupply", foodSupply);
    tag.putDouble("WoodSupply", woodSupply);
    tag.putDouble("MetalSupply", metalSupply);
    tag.putDouble("StoneSupply", stoneSupply);
    tag.putDouble("TextileSupply", textileSupply);
    tag.putDouble("AlcoholSupply", alcoholSupply);
    tag.putDouble("TechnologySupply", technologySupply);

    // === Save all NPC snapshots ===
    ListTag savedList = new ListTag();
    for (SavedNpc sn : saved.values()) {
        savedList.add(sn.nbt.copy()); // includes id + UUID + all custom data
    }
    tag.put(TAG_SAVED_NPCS, savedList);

}

@Override
protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.loadAdditional(tag, provider);

    // === City & Trader Meta ===
    cityName = tag.getString("CityName");
    traderType = tag.getString("TraderType");
    townPersonAmount = tag.getInt("TownPersonAmount");
    if (tag.contains("TreasuryCopper")) treasuryCopper = tag.getInt("TreasuryCopper");

    // === Resource Levels ===
    if (tag.contains("FoodSupply")) foodSupply = tag.getDouble("FoodSupply");
    if (tag.contains("WoodSupply")) woodSupply = tag.getDouble("WoodSupply");
    if (tag.contains("MetalSupply")) metalSupply = tag.getDouble("MetalSupply");
    if (tag.contains("StoneSupply")) stoneSupply = tag.getDouble("StoneSupply");
    if (tag.contains("TextileSupply")) textileSupply = tag.getDouble("TextileSupply");
    if (tag.contains("AlcoholSupply")) alcoholSupply = tag.getDouble("AlcoholSupply");
    if (tag.contains("TechnologySupply")) technologySupply = tag.getDouble("TechnologySupply");

    // === Restore saved NPC data ===
    saved.clear();
    associatedNpcs.clear();

    if (tag.contains(TAG_SAVED_NPCS)) {
        ListTag savedList = tag.getList(TAG_SAVED_NPCS, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < savedList.size(); i++) {
            CompoundTag e = savedList.getCompound(i);
            if (!e.contains("id") || !e.hasUUID("UUID")) continue;
            UUID uuid = e.getUUID("UUID");
            var typeId = net.minecraft.resources.ResourceLocation.parse(e.getString("id"));
            saved.put(uuid, new SavedNpc(uuid, typeId, e.copy()));
            associatedNpcs.add(uuid);
        }
    }
}


}

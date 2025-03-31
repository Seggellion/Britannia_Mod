package com.seggellion.britannia_mod.block.entity;

//import com.seggellion.britannia_mod.entity.EntityJourneymanBlacksmith;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.villager.BlacksmithProfessions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.GlobalPos;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlacksmithSpawnBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final double SPAWN_RADIUS = 10.0;
    private static final int MAX_COOLDOWN = 1000;
    private static final int REQUIRED_FOOD = 400;
    private static final int REQUIRED_WOOD = 200;
    private static final int MAX_MERCHANTS = 1;
    private static final int TOWNSPERSON_COUNT = 2;

    private int spawnCooldown = 0;
    private String cityName = "";
    private final List<UUID> associatedNpcs = new ArrayList<>();

    public BlacksmithSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.BLACKSMITH_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void setCityName(String name) {
        this.cityName = name;
        setChanged();
    }

    public String getCityName() {
        return cityName;
    }

public void tick() {
    if (level == null || level.isClientSide || spawnCooldown-- > 0) return;
    if (cityName == null || cityName.isEmpty()) return; // Ensure cityName is valid
    spawnCooldown = MAX_COOLDOWN;
    if (!(level instanceof ServerLevel serverLevel)) return;

    double[] supplies = CityDataSync.fetchFoodAndWoodSupply(serverLevel, cityName);
    double currentFood = supplies[0]; // Food supply
    double currentWood = supplies[1]; // Wood supply

    long blacksmithMerchantCount = associatedNpcs.stream()
            .map(serverLevel::getEntity)
            .filter(e -> e instanceof Villager)
            .count();

if (currentFood >= REQUIRED_FOOD && currentWood >= REQUIRED_WOOD && blacksmithMerchantCount < MAX_MERCHANTS) {
        spawnBlacksmithMerchant(serverLevel);
        long currentTownspeople = associatedNpcs.stream()
                .map(serverLevel::getEntity)
                .filter(e -> e instanceof TownPersonEntity)
                .count();
        int spawnCount = Math.max(0, TOWNSPERSON_COUNT - (int) currentTownspeople);
        spawnTownspersons(serverLevel, spawnCount);
    } else if (currentFood < REQUIRED_FOOD || currentWood < REQUIRED_WOOD) {
        despawnAssociatedNpcs(serverLevel);
    }
}

private void spawnBlacksmithMerchant(ServerLevel serverLevel) {
    // 1) Check if we already have a blacksmith villager
    boolean hasBlacksmith = associatedNpcs.stream()
        .map(serverLevel::getEntity)
        .filter(e -> e instanceof Villager)
        .map(e -> (Villager) e)
        .anyMatch(v -> v.getVillagerData().getProfession() == BlacksmithProfessions.JOURNEYMAN_BLACKSMITH.get());

    if (hasBlacksmith) return;

// debugging

List<PoiRecord> poiList = serverLevel.getPoiManager().getInSquare(
        poi -> true,  // Match all POIs
        worldPosition, // Replace with actual spawn block position
        50, // Search radius
        PoiManager.Occupancy.ANY
).toList();

if (poiList.isEmpty()) {
    LOGGER.warn(" No POIs found in the area! Check if POIs are properly registered.");
} else {
    for (PoiRecord poiEntry : poiList) {
        LOGGER.info("POI found at {} -> Type: {}", poiEntry.getPos(), poiEntry.getPoiType());
    }
}
// end debugging

    // 2) Create a normal villager
    Villager villager = EntityType.VILLAGER.create(serverLevel);
    if (villager == null) return;

    // 3) Find a spawn position
    BlockPos spawnPos = findNonWaterSpawnLocation(serverLevel);
    if (spawnPos == null) return;
    villager.getPersistentData().putString("CityName", this.cityName); // <-- KEY ADDITION

    // 4) Assign the blacksmith profession **with level 1**
    villager.setVillagerData(
        new VillagerData(VillagerType.PLAINS, BlacksmithProfessions.JOURNEYMAN_BLACKSMITH.get(), 1)
    );

    // 5) Lock the Villager to the POI
    BlockPos poiPos = findNearbyBlacksmithPOI(serverLevel, spawnPos);
    if (poiPos != null) {
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(serverLevel.dimension(), poiPos));
    }

    // 6) Move to spawn location
    villager.moveTo(
        spawnPos.getX() + 0.5,
        spawnPos.getY(),
        spawnPos.getZ() + 0.5,
        serverLevel.random.nextFloat() * 360F,
        0
    );

    // 7) Prevent wandering until fully initialized
    villager.setPersistenceRequired(); // Prevents despawning
    villager.refreshBrain(serverLevel); // Forces job recognition


// 🆕 8) Restrict movement to a 5-block radius
    villager.restrictTo(spawnPos, 5); // Set the restriction center and radius

    // 🆕 9) Setup restricted AI goals
    villager.goalSelector.getAvailableGoals().clear();
    villager.goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(villager, 1.0D));
    villager.goalSelector.addGoal(2, new RandomStrollGoal(villager, 0.6D));
    villager.goalSelector.addGoal(3, new LookAtPlayerGoal(villager, Player.class, 8.0F));
    villager.goalSelector.addGoal(4, new RandomLookAroundGoal(villager));

    // 8) Add to the world
    serverLevel.addFreshEntity(villager);
    associatedNpcs.add(villager.getUUID());

    // 7) Assign a random name (and optionally store city data if you want)
    String randomName = NameLoader.getRandomFemaleName();
    villager.setCustomName(
        Component.literal("Journeyman Blacksmith " + randomName)
    );

    // 8) Optionally track them in your CityDataSync
    //    We'll pass "journeyman_blacksmith" or "blacksmith" as npc_type
    String npcType = "journeyman_blacksmith";
    String description = "A friendly blacksmith named " + randomName;
    int level = 1;
    int health = 100;
    int mana = 50;
    boolean isActive = true;
    String spawnLocation = String.format(
        "[x=%.1f, y=%.1f, z=%.1f]",
        (double) spawnPos.getX(),
        (double) spawnPos.getY(),
        (double) spawnPos.getZ()
    );

    CityDataSync.registerNpc(
        serverLevel,
        villager.getUUID(),
        npcType,
        cityName,          // The cityName from your spawn block
        randomName,
        description,
        level,
        health,
        mana,
        isActive,
        spawnLocation
    );
}


    private void spawnTownspersons(ServerLevel serverLevel, int count) {
    for (int i = 0; i < count; i++) {
        TownPersonEntity person = EntityRegistry.TOWN_PERSON_ENTITY.get().create(serverLevel);
        BlockPos spawnPos = findNonWaterSpawnLocation(serverLevel);
        if (spawnPos == null) {
            continue;
        }
        if (person != null) {
            person.setCityName(cityName);
            person.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, serverLevel.random.nextFloat() * 360F, 0);
            serverLevel.addFreshEntity(person);
            associatedNpcs.add(person.getUUID());

            // Use a random name
            String randomName = NameLoader.getRandomMaleName();
            String description = "A friendly townsman named " + randomName;
            int level = 1;
            int health = 100;
            int mana = 50;
            boolean isActive = true;
            String spawnLocation = String.format("[x=%.1f, y=%.1f, z=%.1f]", (float) spawnPos.getX(), (float) spawnPos.getY(), (float) spawnPos.getZ());

            CityDataSync.registerNpc(serverLevel, person.getUUID(), "town_person", cityName, randomName, description, level, health, mana, isActive, spawnLocation);
        }
    }
}

    @Override
public void setRemoved() {
    super.setRemoved();
    if (level instanceof ServerLevel serverLevel) {
        despawnAssociatedNpcs(serverLevel);
    }
}

    private void despawnAssociatedNpcs(ServerLevel serverLevel) {
        for (UUID uuid : associatedNpcs) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity != null) {
                entity.remove(RemovalReason.DISCARDED);
                CityDataSync.removeNpc(serverLevel, uuid);
            }
        }
        associatedNpcs.clear();
    }

private BlockPos findNonWaterSpawnLocation(ServerLevel serverLevel) {
    for (int attempts = 0; attempts < 50; attempts++) {
        int offsetX = (int) ((serverLevel.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2);
        int offsetZ = (int) ((serverLevel.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2);
        int baseY = worldPosition.getY();

        // Search for valid Y-level near the base Y
        for (int deltaY = -5; deltaY <= 5; deltaY++) {
            int checkY = baseY + deltaY;
            BlockPos pos = new BlockPos(worldPosition.getX() + offsetX, checkY, worldPosition.getZ() + offsetZ);
            
            // Ensure solid ground below and air above
            if (serverLevel.getBlockState(pos.below()).isSolid() &&
                serverLevel.getBlockState(pos).isAir() &&
                serverLevel.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0.2)).isEmpty()) {
                return pos; // Valid spawn position found
            }
        }
    }
    return null; // No suitable location found
}

private BlockPos findNearbyBlacksmithPOI(ServerLevel serverLevel, BlockPos startPos) {
    int radius = 20; // Search within 20 blocks
    for (BlockPos pos : BlockPos.betweenClosed(
            startPos.offset(-radius, -1, -radius),
            startPos.offset(radius, 1, radius))) {
        if (serverLevel.getPoiManager().getType(pos)
                .map(poi -> poi.is(BlacksmithProfessions.JOURNEYMAN_BLACKSMITH_POI))
                .orElse(false)) {
            return pos.immutable();
        }
    }
    return null;
}


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("CityName", cityName);
        ListTag npcList = new ListTag();
        for (UUID uuid : associatedNpcs) {
            CompoundTag npcTag = new CompoundTag();
            npcTag.putUUID("NPC", uuid);
            npcList.add(npcTag);
        }
        tag.put("AssociatedNPCs", npcList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        cityName = tag.getString("CityName");
        associatedNpcs.clear();
        ListTag npcList = tag.getList("AssociatedNPCs", 10);
        for (int i = 0; i < npcList.size(); i++) {
            CompoundTag npcTag = npcList.getCompound(i);
            associatedNpcs.add(npcTag.getUUID("NPC"));
        }
    }
}

package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.entity.EntityWoodMerchant;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.resources.model.Material;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

public class WoodSpawnBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final double SPAWN_CHECK_RADIUS = 10.0;  // Radius to check for existing entities
    private static final double ENTITY_SPACING = 2.0;      // Minimum blocks between spawned entities
    
    private static final int MAX_COOLDOWN = 100;      // Ticks between checks (5 seconds at 20 tps)
    private static final double SPAWN_RADIUS = 10.0;  // Radius around block to spawn merchant / townspeople
    private static final int MAX_MERCHANTS = 1;       // We only want 1 wood merchant at a time
    private static final int REQUIRED_FOOD = 100;     // 200 stones of food required
    private static final int TOWNSPERSON_COUNT = 4;   // Additional NPCs to spawn

    private int spawnCooldown = 0;
    private String cityName = ""; // If your block can store a city name
    private boolean isRemoved = false;

    private final List<UUID> associatedNpcs = new ArrayList<>();

    public WoodSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.WOOD_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void setCityName(String name) {
        this.cityName = name;
        setChanged(); // Mark data changed, so it can save
        LOGGER.info("WoodSpawnBlockEntity cityName: {}",cityName);
        LOGGER.info("WoodSpawnBlockEntity at {} assigned to city: {}", this.worldPosition, name);
    }

    public String getCityName() {
        return cityName;
    }

public void tick() {
    if (this.level == null || this.level.isClientSide || isRemoved) return;

    // Check if the block still exists
    if (!this.level.getBlockState(this.worldPosition).is(BlockRegistry.WOOD_SPAWN_BLOCK.get())) {
        setRemoved();
        return;
    }

    spawnCooldown--;
    if (spawnCooldown > 0) return;
    spawnCooldown = MAX_COOLDOWN;

    if (!(this.level instanceof ServerLevel serverLevel)) return;

    City city = getCityOrNull(serverLevel);
    if (city == null) {
        LOGGER.warn("No city found for name: {}", cityName);
        return;
    }

    CityInventory cityInventory = city.getInventory();

    // Sync NPCs to city population
    syncCityPopulation(serverLevel, cityInventory);

    double currentFood = cityInventory.getCategoryTotalWeight("food");
    LOGGER.info("Current food supply for {} is {} stones", cityName, currentFood);

    // Count NPCs associated with this block
    long woodMerchantCount = cityInventory.getAssociatedNpcs().stream()
        .map(serverLevel::getEntity)
        .filter(entity -> entity instanceof EntityWoodMerchant)
        .count();


    if (currentFood >= REQUIRED_FOOD && woodMerchantCount < MAX_MERCHANTS) {
        LOGGER.info("Food supply >= {} stones. Spawning WoodMerchant near {}", REQUIRED_FOOD, this.worldPosition);
        spawnWoodMerchant(serverLevel);

        // Track townspeople separately to avoid overlap
        long trackedTownspersonCount = associatedNpcs.stream()
                .map(serverLevel::getEntity)
                .filter(entity -> entity instanceof TownPersonEntity)
                .count();
        if (trackedTownspersonCount < TOWNSPERSON_COUNT) {
            spawnTownspersons(serverLevel, (int) (TOWNSPERSON_COUNT - trackedTownspersonCount));
        }
    } else if (currentFood < REQUIRED_FOOD) {
        LOGGER.info("Food supply below {} stones. Despawning NPCs near {}", REQUIRED_FOOD, this.worldPosition);
        despawnAssociatedNpcs(serverLevel);
    }
}

private void syncCityPopulation(ServerLevel serverLevel, CityInventory cityInventory) {
    // Remove non-existent NPCs from both associatedNpcs and city inventory
    associatedNpcs.removeIf(uuid -> {
        Entity entity = serverLevel.getEntity(uuid);
        if (entity == null) {
            LOGGER.warn("Removing non-existent NPC {} from associations and city inventory.", uuid);
            cityInventory.removeNpc(null); // Adjust city population
            return true; // Remove from associatedNpcs
        }
        return false;
    });

    // Re-associate existing NPCs with the city inventory
    associatedNpcs.forEach(uuid -> {
        Entity entity = serverLevel.getEntity(uuid);
        if (entity != null && !cityInventory.getAssociatedNpcs().contains(entity.getUUID())) {
            cityInventory.associateNpc(entity);
            LOGGER.info("Re-associated NPC {} with city inventory.", entity.getUUID());
        }
    });
}



private City getCityOrNull(ServerLevel serverLevel) {
    CityManager cityManager = CityManager.get(serverLevel);
    City city = cityManager.getCity(cityName);
    return city;
}

@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.saveAdditional(tag, provider);
        LOGGER.info("Saving additional {}", cityName);

    tag.putString("CityName", this.cityName != null ? this.cityName : "");

    // Save associated NPC UUIDs
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
    this.cityName = tag.getString("CityName");

    // Load associated NPC UUIDs
    associatedNpcs.clear();
    ListTag npcList = tag.getList("AssociatedNPCs", 10); // 10 is the ID for CompoundTag
    for (int i = 0; i < npcList.size(); i++) {
        CompoundTag npcTag = npcList.getCompound(i);
        associatedNpcs.add(npcTag.getUUID("NPC"));
    }
    LOGGER.info("Loaded WoodSpawnBlockEntity at {} with city: {} and {} associated NPCs", this.worldPosition, this.cityName, associatedNpcs.size());
}

private void spawnWoodMerchant(ServerLevel serverLevel) {
    if (associatedNpcs.stream()
            .map(serverLevel::getEntity)
            .filter(entity -> entity instanceof EntityWoodMerchant)
            .count() >= MAX_MERCHANTS) {
        LOGGER.warn("Max WoodMerchants already associated with block at {}", this.worldPosition);
        return;
    }
     
     

LOGGER.info("spawnWoodMerchant: this={}, cityName={}", System.identityHashCode(this), cityName);


    EntityWoodMerchant merchant = EntityRegistry.WOOD_MERCHANT_ENTITY.get().create(serverLevel);
    if (merchant != null) {
        BlockPos spawnPos = findNonWaterSpawnLocation(serverLevel);
        if (spawnPos != null) {
            merchant.setCityName(this.cityName);
            LOGGER.info("WoodMerchant CityName: {}", cityName);
            merchant.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, serverLevel.random.nextFloat() * 360F, 0);
            serverLevel.addFreshEntity(merchant);
            associatedNpcs.add(merchant.getUUID());
            LOGGER.info("Spawned WoodMerchant at {} and associated with block at {}", spawnPos, this.worldPosition);
        } else {
            LOGGER.warn("Failed to find a valid spawn position for WoodMerchant near {}", this.worldPosition);
        }
    } else {
        LOGGER.warn("Failed to create WoodMerchant entity.");
    }
}

private void spawnTownspersons(ServerLevel serverLevel, int count) {
    long existingTownspersonCount = associatedNpcs.stream()
            .map(serverLevel::getEntity)
            .filter(entity -> entity instanceof TownPersonEntity)
            .count();

    for (int i = 0; i < count && existingTownspersonCount + i < TOWNSPERSON_COUNT; i++) {
        TownPersonEntity person = EntityRegistry.TOWN_PERSON_ENTITY.get().create(serverLevel);
        if (person != null) {
            BlockPos spawnPos = findNonWaterSpawnLocation(serverLevel);
            if (spawnPos != null) {
                person.setCityName(this.cityName);
                LOGGER.info("TownPerson CityName: {}", this.cityName);
                person.setSpawnPosition(spawnPos);
                person.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, serverLevel.random.nextFloat() * 360F, 0);
                serverLevel.addFreshEntity(person);

                associatedNpcs.add(person.getUUID());
                LOGGER.info("Spawned TownPerson at {} and associated with block at {} for the city of {}", spawnPos, this.worldPosition, this.cityName);
            } else {
                LOGGER.warn("Failed to find a valid spawn position for TownPerson near {}", this.worldPosition);
            }
        } else {
            LOGGER.warn("Failed to create TownPerson entity.");
        }
    }
}


@Override
public void setRemoved() {
    if (!isRemoved) {
        super.setRemoved();
        isRemoved = true;

        if (this.level instanceof ServerLevel serverLevel) {
            despawnAssociatedNpcs(serverLevel);
            LOGGER.info("WoodSpawnBlockEntity removed at {}. Associated NPCs and entries cleared.", this.worldPosition);
        }
    }
}

private void despawnAssociatedNpcs(ServerLevel serverLevel) {
    for (UUID uuid : associatedNpcs) {
        Entity npc = serverLevel.getEntity(uuid);
        if (npc != null) {
            LOGGER.info("Despawning NPC {} at {}", uuid, npc.blockPosition());
            npc.remove(RemovalReason.DISCARDED);
        }
    }
    associatedNpcs.clear(); // Clear the list after removal
}

private boolean isPositionOccupied(ServerLevel serverLevel, BlockPos spawnPos) {
    return !serverLevel.getEntitiesOfClass(Entity.class, new AABB(spawnPos).inflate(0.5)).isEmpty();
}

private BlockPos findNonWaterSpawnLocation(ServerLevel serverLevel) {
    for (int attempts = 0; attempts < 20; attempts++) {
        double spawnX = this.worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2;
        double spawnZ = this.worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2;
        BlockPos spawnPos = new BlockPos((int) spawnX, this.worldPosition.getY(), (int) spawnZ);

        // Ensure the block below is solid and not water
        BlockPos groundPos = spawnPos.below();
        if (serverLevel.getBlockState(groundPos).isSolid() &&
            !serverLevel.getBlockState(spawnPos).is(Blocks.WATER) &&
            !isPositionOccupied(serverLevel, spawnPos)) {
            return spawnPos;
        }
    }
    LOGGER.warn("Failed to find a valid spawn location after multiple attempts.");
    return null; // No suitable location found
}


}

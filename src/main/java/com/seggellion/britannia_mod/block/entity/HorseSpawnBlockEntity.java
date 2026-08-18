package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.entity.EntityHorseMerchant;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.network.CityFoodSupplyCache;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HorseSpawnBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final double SPAWN_RADIUS = 10.0;
    private static final int MAX_COOLDOWN = 1000;
    private static final int REQUIRED_FOOD = 10;
    private static final int MAX_MERCHANTS = 1;
    private static final int TOWNSPERSON_COUNT = 2;

    private int spawnCooldown = 0;
    private String cityName = "";
    private final List<UUID> associatedNpcs = new ArrayList<>();

    public HorseSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.HORSE_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
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

    // Was a blocking Rails GET on the thread that runs the world. The reading still gates
        // spawning exactly as before; it is now served from the last answer Rails gave while the
        // next one is fetched behind it. Empty means Rails has not answered for this city yet, and
        // the cycle is skipped rather than acted on -- a missing reading is not a reading of zero.
        java.util.OptionalDouble foodSupply = CityFoodSupplyCache.poll(serverLevel, cityName);
        if (foodSupply.isEmpty()) return;
        double currentFood = foodSupply.getAsDouble();
    long horseMerchantCount = associatedNpcs.stream()
            .map(serverLevel::getEntity)
            .filter(e -> e instanceof EntityHorseMerchant)
            .count();

    if (currentFood >= REQUIRED_FOOD && horseMerchantCount < MAX_MERCHANTS) {
        spawnHorseMerchant(serverLevel);
        long currentTownspeople = associatedNpcs.stream()
                .map(serverLevel::getEntity)
                .filter(e -> e instanceof TownPersonEntity)
                .count();
        int spawnCount = Math.max(0, TOWNSPERSON_COUNT - (int) currentTownspeople);
        spawnTownspersons(serverLevel, spawnCount);
    } else if (currentFood < REQUIRED_FOOD) {
        despawnAssociatedNpcs(serverLevel);
    }
}

private void spawnHorseMerchant(ServerLevel serverLevel) {
    if (associatedNpcs.stream().map(serverLevel::getEntity).anyMatch(e -> e instanceof EntityHorseMerchant)) return;
    EntityHorseMerchant merchant = EntityRegistry.HORSE_MERCHANT_ENTITY.get().create(serverLevel);
    BlockPos spawnPos = findNonWaterSpawnLocation(serverLevel);

    if (merchant != null && spawnPos != null) {
        merchant.setCityName(cityName);
        merchant.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, serverLevel.random.nextFloat() * 360F, 0);
        serverLevel.addFreshEntity(merchant);
        associatedNpcs.add(merchant.getUUID());
        // Use the random name loader
        String randomName = NameLoader.getRandomMaleName();
        String description = "A friendly horse merchant named " + randomName;
        int level = 1;
        int health = 100;
        int mana = 50;
        boolean isActive = true;
        String spawnLocation = String.format("[x=%.1f, y=%.1f, z=%.1f]", (double) spawnPos.getX(), (double) spawnPos.getY(), (double) spawnPos.getZ());
/*
        CityDataSync.registerNpc(serverLevel, merchant.getUUID(), "horse_merchant", cityName, randomName, description, level, health, mana, isActive, spawnLocation);
 */
    }
}


    private void spawnTownspersons(ServerLevel serverLevel, int count) {
    for (int i = 0; i < count; i++) {
        TownPersonEntity person = EntityRegistry.TOWNSPERSON.get().create(serverLevel);
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
/*
            CityDataSync.registerNpc(serverLevel, person.getUUID(), "town_person", cityName, randomName, description, level, health, mana, isActive, spawnLocation);
    */
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

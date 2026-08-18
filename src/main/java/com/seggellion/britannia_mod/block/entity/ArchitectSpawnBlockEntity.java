package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.client.renderer.CityNameBlockRenderer;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.network.CityFoodSupplyCache;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.util.NameLoader;
import com.seggellion.britannia_mod.util.Util;
import com.seggellion.britannia_mod.util.HasCityName;

import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;


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

import org.jetbrains.annotations.Nullable;

public class ArchitectSpawnBlockEntity extends BlockEntity implements HasCityName {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final double SPAWN_RADIUS = 10.0;
    private static final int MAX_COOLDOWN = 1200;
    private static final int REQUIRED_FOOD = 400;
    private static final int REQUIRED_WOOD = 200;
    private static final int MAX_ARCHITECTS = 1;
    private static final int TOWNSPERSON_COUNT = 2;

    private int spawnCooldown = 0;
    private String cityName = "";
    private final List<UUID> associatedNpcs = new ArrayList<>();

    public ArchitectSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ARCHITECT_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    @Override
    public String getCityName() {
        return cityName;
    }


    public void setCityName(String name) {
        this.cityName = name;
        setChanged();
    }

@Override
public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
    CompoundTag tag = super.getUpdateTag(provider);
    tag.putString("CityName", cityName);
    return tag;
}

@Override
public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
    super.handleUpdateTag(tag, provider);
    this.cityName = tag.getString("CityName");
}

@Override
public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}

public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
    HolderLookup.Provider provider = (this.level != null) ? this.level.registryAccess() : null;
    this.handleUpdateTag(pkt.getTag(), provider);
}


    public void tick() {
        if (level == null || level.isClientSide || spawnCooldown-- > 0) return;
        if (cityName == null || cityName.isEmpty()) return;

        spawnCooldown = MAX_COOLDOWN;
        ServerLevel serverLevel = (ServerLevel) level;

        // Was a blocking Rails GET on the thread that runs the world. The reading still gates
        // spawning exactly as before; it is now served from the last answer Rails gave while the
        // next one is fetched behind it. Empty means Rails has not answered for this city yet, and
        // the cycle is skipped rather than acted on -- a missing reading is not a reading of zero.
        java.util.Optional<double[]> supplyReading = CityFoodSupplyCache.pollFoodAndWood(serverLevel, cityName);
        if (supplyReading.isEmpty()) return;
        double[] supplies = supplyReading.get();
        double currentFood = supplies[0];
        double currentWood = supplies[1];

        long architectCount = associatedNpcs.stream()
                .map(serverLevel::getEntity)
                .filter(e -> e instanceof ArchitectEntity)
                .count();

        if (currentFood >= REQUIRED_FOOD &&
            currentWood >= REQUIRED_WOOD &&
            architectCount < MAX_ARCHITECTS) {
            spawnArchitect(serverLevel);

            long townspeople = associatedNpcs.stream()
                    .map(serverLevel::getEntity)
                    .filter(e -> e instanceof TownPersonEntity)
                    .count();
            int toSpawn = Math.max(0, TOWNSPERSON_COUNT - (int) townspeople);
            spawnTownspersons(serverLevel, toSpawn);

        } else if (currentFood < REQUIRED_FOOD || currentWood < REQUIRED_WOOD) {
            despawnAssociatedNpcs(serverLevel);
        }
    }

private void spawnArchitect(ServerLevel sl) {
    boolean hasOne = associatedNpcs.stream()
            .map(sl::getEntity)
            .anyMatch(e -> e instanceof ArchitectEntity);
    if (hasOne) return;

    BlockPos spawnPos = Util.findGround(sl, worldPosition, 10);
    if (spawnPos == null) {
        LOGGER.warn("Could not find ground to place Architect at {}", worldPosition);
        return;
    }

    ArchitectEntity arch = EntityRegistry.ARCHITECT_ENTITY.get().create(sl);
    if (arch == null) {
        LOGGER.error("Failed to create ArchitectEntity!");
        return;
    }

    arch.setCityName(cityName);
    arch.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, sl.random.nextFloat() * 360F, 0);
    arch.setPersistenceRequired();

    // Assign random gender
    String gender = sl.random.nextBoolean() ? "male" : "female";
    arch.setGender(gender); // Ensure ArchitectEntity has setGender(String) and getGender()

    // Pick name and description based on gender
    String randomName = gender.equals("male") ? NameLoader.getRandomMaleName() : NameLoader.getRandomFemaleName();
    arch.setPersonalName(randomName);

    String description = "A master " + (gender.equals("male") ? "builder" : "architect") + " named " + randomName;

    sl.addFreshEntity(arch);
    associatedNpcs.add(arch.getUUID());

    String spawnLoc = String.format("[x=%d, y=%d, z=%d]", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

   // CityDataSync.registerNpc(sl, arch.getUUID(), "architect", cityName, randomName, description, 1, 100, 0, true, spawnLoc);
}


    private void spawnTownspersons(ServerLevel sl, int count) {
        for (int i = 0; i < count; i++) {
            TownPersonEntity person = EntityRegistry.TOWNSPERSON.get().create(sl);
            if (person == null) continue;

            BlockPos spawnPos = Util.findGround(sl, worldPosition, 10);
            if (spawnPos == null) continue;

            person.setCityName(cityName);
            person.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, sl.random.nextFloat() * 360F, 0);
            sl.addFreshEntity(person);
            associatedNpcs.add(person.getUUID());

            String randName = NameLoader.getRandomMaleName();
            String loc = String.format("[x=%d, y=%d, z=%d]", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

         //   CityDataSync.registerNpc(sl, person.getUUID(), "town_person", cityName, randName, "A friendly townsman named " + randName, 1, 100, 0, true, loc);
        }
    }

    private void despawnAssociatedNpcs(ServerLevel sl) {
        for (UUID id : associatedNpcs) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                e.remove(RemovalReason.DISCARDED);
                CityDataSync.removeNpcAsync(sl, id);
            }
        }
        associatedNpcs.clear();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) despawnAssociatedNpcs(sl);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("CityName", cityName);

        ListTag list = new ListTag();
        for (UUID id : associatedNpcs) {
            CompoundTag t = new CompoundTag();
            t.putUUID("NPC", id);
            list.add(t);
        }
        tag.put("AssociatedNPCs", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        cityName = tag.getString("CityName");

        associatedNpcs.clear();
        ListTag list = tag.getList("AssociatedNPCs", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            associatedNpcs.add(t.getUUID("NPC"));
        }
    }
}

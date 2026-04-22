package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class QuestGiverSpawnBlockEntity extends BlockEntity {

    public enum SpawnerMode { STORY, ESCORT }

    private SpawnerMode mode = SpawnerMode.STORY;
    private String cityName = "";
    private String npcName = ""; 
    private String customApiId = "";
    private String gender = "female";
    private int spawnRadius = 5; // NEW: Controls strict AI wandering

    // For Escorts
    private String escortDestination = "";
    private static final List<String> ESCORT_DESTINATIONS = List.of("Jhelom", "Vesper", "Ocllo", "Buccaneer's Den", "Cove", "Britain", "Minoc", "Moonglow", "Trinsic", "Yew", "Skara Brae", "New Magincia", "Serpent's Hold", "Nujel'm");
    private static final Random RANDOM = new Random();

    private UUID spawnedNpcId = null;
    private CompoundTag savedNpcData = null;
    private int spawnCooldown = 0;
    private int initTicks = 0; // NEW: Grace period for world loading

    public QuestGiverSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.QUEST_GIVER_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public String getCityName() { return cityName; }
    public String getNpcName() { return npcName; }
    public String getGender() { return gender; }
    public String getCustomApiId() { return customApiId; }
    public SpawnerMode getMode() { return mode; }
    public int getSpawnRadius() { return spawnRadius; }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        ServerLevel sl = (ServerLevel) level;

        // Give the world 2 seconds (40 ticks) to fully load entities before checking for duplicates
        if (initTicks < 40) {
            initTicks++;
            return;
        }

        if (npcName == null || npcName.isEmpty()) return;

        if (spawnCooldown-- > 0) return;
        spawnCooldown = 200; // Check every 10 seconds

        Entity currentNpc = spawnedNpcId != null ? sl.getEntity(spawnedNpcId) : null;

        // Fallback: If UUID lookup fails (chunk edge cases), physically check the area for our NPC
        if (currentNpc == null && spawnedNpcId != null) {
            AABB searchArea = new AABB(worldPosition).inflate(spawnRadius + 5);
            List<QuestGiverEntity> nearbyStrays = sl.getEntitiesOfClass(QuestGiverEntity.class, searchArea, 
                e -> e.getUUID().equals(spawnedNpcId));
            if (!nearbyStrays.isEmpty()) {
                currentNpc = nearbyStrays.get(0);
            }
        }

        if (currentNpc == null || !currentNpc.isAlive()) {
            this.savedNpcData = null;
            spawnOrRestoreNpc(sl);
        } else {
            updateSnapshot((QuestGiverEntity) currentNpc);
        }
    }

    private void spawnOrRestoreNpc(ServerLevel sl) {
        // Use our configurable radius to find ground!
        BlockPos spawnPos = Util.findGround(sl, worldPosition, this.spawnRadius);
        if (spawnPos == null) return;

        QuestGiverEntity npc;
        boolean isFreshSpawn = false; 
        
        if (savedNpcData != null) {
            CompoundTag tag = savedNpcData.copy();
            tag.remove("UUID"); 
            Entity restored = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, sl, e -> e);
            if (restored instanceof QuestGiverEntity qg) {
                npc = qg;
            } else {
                npc = EntityRegistry.QUEST_GIVER.get().create(sl);
                isFreshSpawn = true;
            }
        } else {
            npc = EntityRegistry.QUEST_GIVER.get().create(sl);
            isFreshSpawn = true;
        }

        if (npc == null) return;

        npc.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, sl.random.nextFloat() * 360F, 0);
        npc.setPersistenceRequired();

        // THIS IS THE MAGIC FOR STRICT RADIUS: Fences in the AI pathfinding
        if (npc instanceof Mob mob) {
            mob.restrictTo(this.worldPosition, this.spawnRadius);
        }
        
        if (isFreshSpawn) {
            if ("Generic Escort".equals(npcName)) {
                boolean isMale = sl.random.nextBoolean();
                String assignedGender = isMale ? "male" : "female";
                String randomName = isMale ? 
                    com.seggellion.britannia_mod.util.NameLoader.getRandomMaleName() : 
                    com.seggellion.britannia_mod.util.NameLoader.getRandomFemaleName();
                
                npc.setGender(assignedGender);

                String safeOrigin = cityName != null ? cityName.toLowerCase().replace("'", "").replace(" ", "_") : "unknown";

                java.util.List<String> validDestinations = ESCORT_DESTINATIONS.stream()
                    .filter(d -> !d.equalsIgnoreCase(cityName))
                    .toList();
                    
                String randomDest = "unknown";
                if (!validDestinations.isEmpty()) {
                    randomDest = validDestinations.get(sl.random.nextInt(validDestinations.size()));
                }
                String safeDest = randomDest.toLowerCase().replace("'", "").replace(" ", "_");
                
                npc.setPersonalName(randomName + ":escort_" + safeOrigin + "_to_" + safeDest);
                npc.addTag("generic_escort");
                npc.addTag("origin_" + safeOrigin);
                npc.addTag("destination_" + safeDest);

            } else if ("Generic Combat".equals(npcName)) {
                String safeApiId = customApiId != null && !customApiId.isEmpty() ? customApiId.trim() : "unknown_combat_npc";
                String visualName = "Fighter";
                if (safeApiId.contains("_")) {
                    String rawPrefix = safeApiId.split("_")[0];
                    if (!rawPrefix.isEmpty()) {
                        visualName = rawPrefix.substring(0, 1).toUpperCase() + rawPrefix.substring(1).toLowerCase();
                    }
                } else {
                    visualName = safeApiId.substring(0, 1).toUpperCase() + safeApiId.substring(1).toLowerCase();
                }

                npc.setGender("male");
                npc.setCityName(cityName);
                npc.setPersonalName(visualName + ":" + safeApiId);
                npc.addTag("generic_combat");

            } else {
                npc.setPersonalName(npcName);
                npc.setCityName(cityName);
                npc.setGender(this.gender != null && !this.gender.isEmpty() ? this.gender : "female");
            }
        }

        sl.addFreshEntity(npc);
        spawnedNpcId = npc.getUUID();
        updateSnapshot(npc);
    }

    private void updateSnapshot(QuestGiverEntity npc) {
        savedNpcData = new CompoundTag();
        npc.saveWithoutId(savedNpcData);
        savedNpcData.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString());
        setChanged();
    }

    // Now accepts spawnRadius
    public void applyConfig(String npcName, String cityName, String customApiId, String gender, int spawnRadius) {
        if (!(level instanceof ServerLevel sl)) return;

        this.npcName = npcName;
        this.cityName = cityName;
        this.customApiId = customApiId;
        this.gender = gender; 
        this.spawnRadius = spawnRadius;
        this.savedNpcData = null; 

        onDestroyed(sl); 
        spawnCooldown = 0; 
        setChanged();
    }

    public void onDestroyed(ServerLevel sl) {
        if (spawnedNpcId != null) {
            Entity e = sl.getEntity(spawnedNpcId);
            if (e != null) e.remove(RemovalReason.DISCARDED);
            spawnedNpcId = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("Mode", mode.name());
        tag.putString("CityName", cityName);
        tag.putString("NpcName", npcName);
        tag.putString("EscortDestination", escortDestination);
        tag.putString("Gender", gender);
        tag.putInt("SpawnRadius", spawnRadius); // Save radius
        if (spawnedNpcId != null) tag.putUUID("SpawnedNpcId", spawnedNpcId);
        if (savedNpcData != null) tag.put("SavedNpcData", savedNpcData);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("Mode")) mode = SpawnerMode.valueOf(tag.getString("Mode"));
        cityName = tag.getString("CityName");
        npcName = tag.getString("NpcName");
        escortDestination = tag.getString("EscortDestination");
        if (tag.contains("Gender")) gender = tag.getString("Gender"); 
        if (tag.contains("SpawnRadius")) spawnRadius = tag.getInt("SpawnRadius"); // Load radius
        if (tag.hasUUID("SpawnedNpcId")) spawnedNpcId = tag.getUUID("SpawnedNpcId");
        if (tag.contains("SavedNpcData")) savedNpcData = tag.getCompound("SavedNpcData");
    }
}
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class QuestGiverSpawnBlockEntity extends BlockEntity {

    public enum SpawnerMode { STORY, ESCORT }

    private SpawnerMode mode = SpawnerMode.STORY;
    private String cityName = "";
    private String npcName = ""; 
    private String customApiId = "";

    public String getCustomApiId() { return customApiId; }
    
    // For Escorts
    private String escortDestination = "";

    private static final List<String> ESCORT_DESTINATIONS = List.of("Jhelom");
//    private static final List<String> ESCORT_DESTINATIONS = List.of("Jhelom", "Britain", "Minoc", "Moonglow", "Trinsic", "Yew", "Skara Brae", "Magincia", "Serpent's Hold", "Nujel'm");
    private static final Random RANDOM = new Random();

    private UUID spawnedNpcId = null;
    private CompoundTag savedNpcData = null;
    private int spawnCooldown = 0;

    public QuestGiverSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.QUEST_GIVER_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public String getCityName() { return cityName; }
    public String getNpcName() { return npcName; }
    public SpawnerMode getMode() { return mode; }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        ServerLevel sl = (ServerLevel) level;

        if (npcName == null || npcName.isEmpty()) return;

        if (spawnCooldown-- > 0) return;
        spawnCooldown = 200; // Check every 10 seconds

        Entity currentNpc = spawnedNpcId != null ? sl.getEntity(spawnedNpcId) : null;

        if (currentNpc == null || !currentNpc.isAlive()) {
            spawnOrRestoreNpc(sl);
            // If the NPC was just killed or taken as an escort, put spawner on a longer cooldown!
            // E.g., spawnCooldown = 6000; // 5 minutes 
        } else {
            updateSnapshot((QuestGiverEntity) currentNpc);
        }
    }

    private void spawnOrRestoreNpc(ServerLevel sl) {
        BlockPos spawnPos = Util.findGround(sl, worldPosition, 5);
        if (spawnPos == null) return;

        QuestGiverEntity npc;
        
        // Restore from NBT if we have a saved snapshot
        if (savedNpcData != null) {
            CompoundTag tag = savedNpcData.copy();
            tag.remove("UUID"); // Force new UUID
            Entity restored = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, sl, e -> e);
            if (restored instanceof QuestGiverEntity qg) {
                npc = qg;
            } else {
                npc = EntityRegistry.QUEST_GIVER.get().create(sl);
            }
        } else {
            // Fresh spawn
            npc = EntityRegistry.QUEST_GIVER.get().create(sl);
        }

        if (npc == null) return;

        npc.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, sl.random.nextFloat() * 360F, 0);
        npc.setPersistenceRequired();
        
// NEW: Identity Logic with Random Destinations!
        if ("Generic Escort".equals(npcName)) {
            // 1. Generate a random name
            boolean isMale = sl.random.nextBoolean();
            String assignedGender = isMale ? "male" : "female";
            
            // 2. Generate a random name based on the coin flip
            String randomName = isMale ? 
                com.seggellion.britannia_mod.util.NameLoader.getRandomMaleName() : 
                com.seggellion.britannia_mod.util.NameLoader.getRandomFemaleName();
            
            // 3. SET THE GENDER so it syncs to the client!
            npc.setGender(assignedGender);

            // 2. Format the Origin City (Where the block is placed)
            String safeOrigin = cityName != null ? cityName.toLowerCase().replace(" ", "_") : "unknown";
            
            // 3. Pick a Random Destination (that is NOT the current city)
            java.util.List<String> validDestinations = ESCORT_DESTINATIONS.stream()
                .filter(d -> !d.equalsIgnoreCase(cityName))
                .toList();
                
            String randomDest = "unknown";
            if (!validDestinations.isEmpty()) {
                randomDest = validDestinations.get(sl.random.nextInt(validDestinations.size()));
            }
            String safeDest = randomDest.toLowerCase().replace(" ", "_");
            
            // 4. ENCODE the FULL route into the name! (e.g., "Harrison:escort_britain_to_jhelom")
            npc.setPersonalName(randomName + ":escort_" + safeOrigin + "_to_" + safeDest);

            // Add server-side tags for the death event listener
            npc.addTag("generic_escort");
            npc.addTag("origin_" + safeOrigin);
            npc.addTag("destination_" + safeDest);
        } else if ("Generic Combat".equals(npcName)) {
        // 1. Get the exact database ID you typed into the UI
        String safeApiId = customApiId != null && !customApiId.isEmpty() ? customApiId.trim() : "unknown_combat_npc";

        // 2. Derive the visual name from the API ID! 
        // e.g., "kane_combat_1" -> splits at "_" -> takes "kane" -> capitalizes to "Kane"
        String visualName = "Fighter";
        if (safeApiId.contains("_")) {
            String rawPrefix = safeApiId.split("_")[0];
            if (!rawPrefix.isEmpty()) {
                visualName = rawPrefix.substring(0, 1).toUpperCase() + rawPrefix.substring(1).toLowerCase();
            }
        } else {
            // Fallback if they just typed "kane" with no underscores
            visualName = safeApiId.substring(0, 1).toUpperCase() + safeApiId.substring(1).toLowerCase();
        }

        // 3. Set a default gender for combat voice lines (or randomize if you prefer)
        npc.setGender("male");
        npc.setCityName(cityName);

        // 4. ENCODE them together! (VisualName:DatabaseID)
        // This will result in exactly: "Kane:kane_combat_1"
        npc.setPersonalName(visualName + ":" + safeApiId);
        npc.addTag("generic_combat");

        } else {
            // It's a story NPC, set the explicit name
            npc.setPersonalName(npcName);
            npc.setCityName(cityName);
        }

        npc.setCityName(cityName);

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

    // Updated to accept the mode
    public void applyConfig(String npcName, String cityName, String customApiId) {
        if (!(level instanceof ServerLevel sl)) return;

        this.npcName = npcName;
        this.cityName = cityName;
        this.customApiId = customApiId;
        this.savedNpcData = null; // Clear old save since we are changing identity

        onDestroyed(sl); // Remove old NPC
        spawnCooldown = 0; // Force immediate spawn next tick
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
        if (tag.hasUUID("SpawnedNpcId")) spawnedNpcId = tag.getUUID("SpawnedNpcId");
        if (tag.contains("SavedNpcData")) savedNpcData = tag.getCompound("SavedNpcData");
    }
}
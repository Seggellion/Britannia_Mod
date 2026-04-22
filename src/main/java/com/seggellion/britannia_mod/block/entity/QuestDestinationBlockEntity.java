package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;

import java.util.List;

public class QuestDestinationBlockEntity extends BlockEntity {

    private String cityName = "Jhelom"; 
    private int tickCounter = 0;

    public QuestDestinationBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.QUEST_DESTINATION_BLOCK_ENTITY.get(), pos, state);
    }

    public String getCityName() { return cityName; }

    public void applyConfig(String newCity) {
        this.cityName = newCity;
        setChanged();
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;

        if (tickCounter++ % 20 != 0) return;

        AABB searchBox = new AABB(worldPosition).inflate(7.5D);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, searchBox);

        // Format this block's city name exactly like the tag (e.g., "britain" or "new_magincia")
        String safeCityName = this.cityName.toLowerCase().replace("'", "").replace(" ", "_");
        String requiredDestinationTag = "destination_" + safeCityName;

        for (ServerPlayer player : nearbyPlayers) {
            String expectedTag = "quest_escort_" + player.getUUID().toString();

            List<Mob> nearbyEscorts = level.getEntitiesOfClass(
                Mob.class, searchBox, entity -> entity.getTags().contains(expectedTag)
            );

            if (!nearbyEscorts.isEmpty()) {
                Mob escort = nearbyEscorts.get(0);
                
                // NEW: Does this escort actually want to go to THIS city?
                // If not, ignore them and let them keep following the player!
                if (!escort.getTags().contains(requiredDestinationTag)) {
                    continue; 
                }

                long activeQuestId = 0;
                for (String tag : escort.getTags()) {
                    if (tag.startsWith("quest_id_")) {
                        try {
                            activeQuestId = Long.parseLong(tag.replace("quest_id_", ""));
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }

                if (activeQuestId > 0) {
                    // 1. Extract the NPC's identity BEFORE we delete them
                    String npcName = "Traveler";
                    String npcGender = "unknown";
                    java.util.UUID npcUuid = escort.getUUID();

                    if (escort instanceof com.seggellion.britannia_mod.entity.QuestGiverEntity qg) {
                        npcName = qg.getName().getString(); // Safely gets "Mitexi" without the encoded route
                        npcGender = qg.getGender();
                    }

                    // 2. Discard the escort ONLY because we know they are at the right place
                    escort.discard(); 

                    // 3. Fire the Payload to the Client!
                    String dynamicTriggerKey = "arrived_" + safeCityName; 
                    
                    com.seggellion.britannia_mod.network.payload.EscortArrivedS2CPayload.send(
                        player, activeQuestId, dynamicTriggerKey, npcName, npcGender, npcUuid
                    );
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("CityName", cityName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        cityName = tag.getString("CityName");
    }
}
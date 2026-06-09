package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.network.payload.SpawnEscortC2SPayload;
import com.seggellion.britannia_mod.network.payload.OpenQuestScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.ItemBurnedS2CPayload;
import com.seggellion.britannia_mod.quest.QuestManager;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestCleanupService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.events.QuestEventHandlers;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import com.seggellion.britannia_mod.quest.network.QuestClient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class QuestPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);

public static void handleItemBurned(final ItemBurnedS2CPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ClientProxy.evaluateLavaQuest(payload.item(), payload.pos());
            }
        });
    }


public static class ClientProxy {
        public static void openQuestUI(long questId, String triggerKey) {
            // Because this is inside a separate class block, the server's
            // ClassLoader won't crash when it sees these client-side references!
            QuestClient.sendTrigger(questId, triggerKey, response -> {
                if (response.success) {
                    ClientNetworkHandler.openQuestDecisionScreen(response, "The Guardian", null);
                }
            });
        }


public static void evaluateLavaQuest(ItemStack stack, BlockPos pos) {
            QuestModels.QuestResponse state = QuestManager.getInstance().getCurrentQuestState();
            if (state == null || state.currentNode == null || state.currentNode.metadata == null) return;
            if (state.currentNode.metadata.has("destroy_trigger")) {
                com.google.gson.JsonObject destroyData = state.currentNode.metadata.getAsJsonObject("destroy_trigger");
                String targetTag = destroyData.has("item_tag") ? destroyData.get("item_tag").getAsString() : "";
                String triggerKey = destroyData.has("trigger_key") ? destroyData.get("trigger_key").getAsString() : "";
                if (!targetTag.isEmpty() && !triggerKey.isEmpty()) {
                    BlockPos min = new BlockPos(QuestEventHandlers.getSafeInt(destroyData, "min_x"), QuestEventHandlers.getSafeInt(destroyData, "min_y"), QuestEventHandlers.getSafeInt(destroyData, "min_z"));
                    BlockPos max = new BlockPos(QuestEventHandlers.getSafeInt(destroyData, "max_x"), QuestEventHandlers.getSafeInt(destroyData, "max_y"), QuestEventHandlers.getSafeInt(destroyData, "max_z"));
                    
                    if (QuestEventHandlers.isInsideZone(pos, min, max)) {
                        if (QuestEventHandlers.isQuestItemMatch(stack, targetTag)) {
                            QuestManager.getInstance().clearState();
                            QuestClient.sendTrigger(state.quest_id, triggerKey, response -> {
                                if (response.success) {
                                    ClientNetworkHandler.openQuestDecisionScreen(response, "The Guardian", null);
                                }
                            });
                        }
                    }
                }
            }
        }


    }

    public static void handleSpawnEscort(final SpawnEscortC2SPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            String questStateId = payload.questStateId() == null ? "" : payload.questStateId().trim();
            if (questStateId.isBlank()) {
                LOGGER.warn("Rejected escort activation without quest_state_id player={} quest_id={} npc_uuid={}",
                        player.getStringUUID(), payload.questId(), payload.npcUuid());
                player.sendSystemMessage(uoMessage("The escort could not be assigned yet."));
                return;
            }

            ClientQuestEntry acceptedQuest = ServerQuestTable.get(player, questStateId);
            if (acceptedQuest == null) {
                LOGGER.warn("Rejected escort activation for inactive quest player={} quest_state_id={} quest_id={} npc_uuid={}",
                        player.getStringUUID(), questStateId, payload.questId(), payload.npcUuid());
                player.sendSystemMessage(uoMessage("The escort quest is not active."));
                return;
            }

            // Default to the payload data in case the original entity has already unloaded
            String finalName = payload.npcName();
            String finalGender = payload.npcGender();
            com.seggellion.britannia_mod.entity.QuestGiverEntity oldQg = null;

            // 1. Find and DELETE the original NPC
            Entity oldEntity = level.getEntity(payload.npcUuid());

            if (oldEntity instanceof com.seggellion.britannia_mod.entity.QuestGiverEntity foundQg) {
                oldQg = foundQg;
                // Grab the raw database name so we don't lose the encoded API routing data
                finalName = oldQg.getPersonalName();
                finalGender = oldQg.getGender();
                String npcApiId = internalApiId(finalName);
                if (!isCompatibleQuestNpc(acceptedQuest, npcApiId)) {
                    LOGGER.warn("Rejected escort activation for mismatched npc player={} quest_state_id={} quest_key={} npc_api_id={} npc_uuid={}",
                            player.getStringUUID(), questStateId, acceptedQuest.questKey(), npcApiId, oldQg.getStringUUID());
                    player.sendSystemMessage(uoMessage("That escort does not belong to this quest."));
                    return;
                }
                QuestCleanupService.clearSpawnerForRemovedQuestGiver(level, oldQg.getUUID(), internalApiId(finalName), 600);
                oldEntity.discard(); // Deleting this frees up the Spawner Block to generate a new escort
            } else {
                LOGGER.warn("Rejected escort activation because NPC was not found player={} quest_state_id={} quest_id={} npc_uuid={}",
                        player.getStringUUID(), questStateId, payload.questId(), payload.npcUuid());
                player.sendSystemMessage(uoMessage("The escort could not be found."));
                return;
            }

            // 2. Spawn a NEW QuestGiverEntity
            com.seggellion.britannia_mod.entity.QuestGiverEntity escort = com.seggellion.britannia_mod.registry.EntityRegistry.QUEST_GIVER.get().create(level);
            
            if (escort != null) {
                // If we successfully found the old NPC, clone its exact appearance (clothing, name, gender) via NBT!
                if (oldQg != null) {
                    CompoundTag tag = new CompoundTag();
                    oldQg.saveWithoutId(tag);
                    tag.remove("UUID"); // Strip the old UUID so it generates a fresh one
                    escort.load(tag);
                } else {
                    // Fallback: apply the data explicitly from the payload
                    escort.setPersonalName(finalName);
                    escort.setGender(finalGender);
                }

                // Move it to the player's exact location
                escort.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                
                // Apply our tracking tags
                escort.addTag("escort_active");
                escort.addTag("quest_escort_" + player.getUUID().toString());
                escort.addTag("quest_id_" + payload.questId());
                escort.addTag("quest_state_id_" + questStateId);
                String questKey = internalApiId(finalName);
                if (!questKey.isBlank()) {
                    escort.addTag("quest_key_" + questKey);
                }
                escort.setPersistenceRequired();

                // 3. INJECT THE FOLLOW AI GOAL!
                escort.goalSelector.addGoal(2, new FollowPlayerGoal(escort, player, 1.2D, 5.0F, 2.0F));

                level.addFreshEntity(escort);

                // Format the chat message so it doesn't print raw database IDs
                String displayString = finalName != null && finalName.contains(":") ? finalName.split(":", 2)[0] : finalName;
                player.sendSystemMessage(uoMessage(displayString + " joins your side. Lead the way."));
                LOGGER.info("Activated escort after Rails accept player={} quest_state_id={} quest_id={} npc_api_id={} npc_uuid={}",
                        player.getStringUUID(), questStateId, payload.questId(), questKey, escort.getStringUUID());
            }
        });
    }

    private static Component uoMessage(String text) {
        return Component.literal(text).withStyle(UO_STYLE);
    }

    private static String internalApiId(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        if (!rawName.contains(":")) return rawName.trim();
        return rawName.split(":", 2)[1].trim();
    }

    private static boolean isCompatibleQuestNpc(ClientQuestEntry quest, String npcApiId) {
        if (quest == null) return false;
        String questKey = quest.questKey() == null ? "" : quest.questKey().trim();
        String npcKey = npcApiId == null ? "" : npcApiId.trim();
        if (questKey.isBlank() || npcKey.isBlank()) return true;
        if (!questKey.startsWith("escort_")) return true;
        return questKey.equals(npcKey);
    }

    // --- CUSTOM AI: Makes the NPC follow the player ---
    public static class FollowPlayerGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final net.minecraft.world.entity.Mob mob;
        private final net.minecraft.world.entity.player.Player player;
        private final double speedModifier;
        private final float startDist;
        private final float stopDist;

        public FollowPlayerGoal(net.minecraft.world.entity.Mob mob, net.minecraft.world.entity.player.Player player, double speed, float startDist, float stopDist) {
            this.mob = mob;
            this.player = player;
            this.speedModifier = speed;
            this.startDist = startDist;
            this.stopDist = stopDist;
            this.setFlags(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.player.isAlive()) return false;
            if (!hasActiveQuestAssignment()) return false;
            return this.mob.distanceToSqr(this.player) > (this.startDist * this.startDist);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && this.mob.distanceToSqr(this.player) > (this.stopDist * this.stopDist);
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.mob.getLookControl().setLookAt(this.player, 10.0F, (float)this.mob.getMaxHeadXRot());
            this.mob.getNavigation().moveTo(this.player, this.speedModifier);
        }

        private boolean hasActiveQuestAssignment() {
            String questStateId = tagValue("quest_state_id_");
            if (!this.mob.getTags().contains("escort_active") || questStateId.isBlank()) {
                clearInvalidEscortAssignment("missing quest_state_id");
                return false;
            }
            if (this.mob.level().isClientSide()) {
                return true;
            }
            boolean active = ServerQuestTable.hasActiveQuestState(this.player.getUUID(), questStateId);
            if (!active) {
                clearInvalidEscortAssignment("inactive quest_state_id");
            }
            return active;
        }

        private String tagValue(String prefix) {
            for (String tag : this.mob.getTags()) {
                if (tag.startsWith(prefix)) {
                    return tag.substring(prefix.length());
                }
            }
            return "";
        }

        private void clearInvalidEscortAssignment(String reason) {
            if (this.mob.level().isClientSide()) return;

            boolean changed = false;
            for (String tag : java.util.List.copyOf(this.mob.getTags())) {
                if (tag.equals("escort_active")
                        || tag.startsWith("quest_escort_")
                        || tag.startsWith("quest_state_id_")
                        || tag.startsWith("quest_id_")
                        || tag.startsWith("quest_key_")) {
                    changed |= this.mob.removeTag(tag);
                }
            }
            if (changed) {
                this.mob.getNavigation().stop();
                LOGGER.warn("Cleared invalid injected escort assignment reason={} entity={}", reason, this.mob.getStringUUID());
            }
        }
    }
}

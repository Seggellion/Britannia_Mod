package com.seggellion.britannia_mod.network;

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

/**
     * Milestone 6: this used to hand an item-destruction back to the CLIENT to decide whether it
     * met a quest objective. The server evaluates its own journal now
     * ({@code QuestObjectiveWatcher.onQuestItemDestroyed}), so nothing here asserts anything --
     * the payload is retained only so an older client cannot desync on an unknown type.
     */
    public static void handleItemBurned(final ItemBurnedS2CPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> LOGGER.debug("Ignoring legacy item-burned payload; objectives are server-side"));
    }

    public static void activateEscort(ServerPlayer player, long questId, String rawQuestStateId,
                                      java.util.UUID npcUuid) {
        if (player == null || npcUuid == null || questId <= 0) return;
        ServerLevel level = player.serverLevel();
        String questStateId = rawQuestStateId == null ? "" : rawQuestStateId.trim();
        ClientQuestEntry acceptedQuest = ServerQuestTable.get(player, questStateId);
        if (questStateId.isBlank() || acceptedQuest == null || !questIdMatches(acceptedQuest, questId)) {
            LOGGER.warn("Rejected authoritative escort activation for inactive quest player={} quest_id={}",
                    player.getStringUUID(), questId);
            return;
        }

        Entity oldEntity = level.getEntity(npcUuid);
        if (!(oldEntity instanceof com.seggellion.britannia_mod.entity.QuestGiverEntity oldQuestGiver)
                || !oldEntity.isAlive() || player.distanceToSqr(oldEntity) > 64.0D) {
            LOGGER.warn("Rejected authoritative escort activation because the nearby quest NPC was unavailable player={} quest_id={}",
                    player.getStringUUID(), questId);
            return;
        }

        String finalName = oldQuestGiver.getPersonalName();
        String npcApiId = internalApiId(finalName);
        if (!isCompatibleQuestNpc(acceptedQuest, npcApiId)) {
            LOGGER.warn("Rejected authoritative escort activation for mismatched quest NPC player={} quest_id={}",
                    player.getStringUUID(), questId);
            return;
        }

        CompoundTag sourceTag = new CompoundTag();
        oldQuestGiver.saveWithoutId(sourceTag);
        sourceTag.remove("UUID");
        QuestCleanupService.clearSpawnerForRemovedQuestGiver(level, oldQuestGiver.getUUID(), npcApiId, 600);
        oldEntity.discard();

        com.seggellion.britannia_mod.entity.QuestGiverEntity escort =
                com.seggellion.britannia_mod.registry.EntityRegistry.QUEST_GIVER.get().create(level);
        if (escort == null) return;
        escort.load(sourceTag);
        escort.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        escort.addTag("escort_active");
        escort.addTag("quest_escort_" + player.getUUID());
        escort.addTag("quest_id_" + questId);
        escort.addTag("quest_state_id_" + questStateId);
        if (!npcApiId.isBlank()) escort.addTag("quest_key_" + npcApiId);
        escort.setPersistenceRequired();
        escort.goalSelector.addGoal(2, new FollowPlayerGoal(escort, player, 1.2D, 5.0F, 2.0F));
        level.addFreshEntity(escort);

        String displayString = finalName != null && finalName.contains(":") ? finalName.split(":", 2)[0] : finalName;
        player.sendSystemMessage(uoMessage(displayString + " joins your side. Lead the way."));
        LOGGER.info("Activated escort from authoritative Rails result player={} quest_state_id={} quest_id={} npc_uuid={}",
                player.getStringUUID(), questStateId, questId, escort.getStringUUID());
    }

    private static boolean questIdMatches(ClientQuestEntry quest, long questId) {
        try { return Long.parseLong(quest.questId()) == questId; }
        catch (NumberFormatException ignored) { return false; }
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
        private boolean reportedUnknownJournal;

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

            ServerQuestTable.JournalState status =
                    ServerQuestTable.questStateStatus(this.player.getUUID(), questStateId);
            if (status == ServerQuestTable.JournalState.UNKNOWN) {
                // Same rule as EscortPlayerGoal: an unloaded journal is not evidence that the
                // quest ended, so idle without destroying the persisted assignment.
                if (!this.reportedUnknownJournal) {
                    this.reportedUnknownJournal = true;
                    LOGGER.debug("Injected escort idling because the quest journal has not loaded entity={} quest_state_id={}",
                            this.mob.getStringUUID(), questStateId);
                }
                return false;
            }
            if (status == ServerQuestTable.JournalState.INACTIVE) {
                clearInvalidEscortAssignment("inactive quest_state_id");
                return false;
            }
            return true;
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

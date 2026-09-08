package com.seggellion.britannia_mod.quest;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Takes back what a quest lent the player once the quest is over: its escort, and its temporary
 * items.
 *
 * <p>Rowan farming questline M1 (discovery D1, protocol section 1.5): only a stack carrying a
 * TEMPORARY stamp -- one with a {@code quest_trigger_key}, see {@link QuestItemStamp} -- is ever
 * deleted, and only when its quest is no longer active for this player. A stack with the pre-M1
 * blanket stamp (no trigger key) was a permanent reward all along: instead of deleting it, the pass
 * that would have deleted it strips the stamp and keeps the item. A stack without any stamp is never
 * touched.
 */
public final class QuestCleanupService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ESCORT_SEARCH_RADIUS = 160;
    private static final int SPAWNER_SEARCH_RADIUS = 48;
    private static final int ABANDONED_ESCORT_RESPAWN_COOLDOWN_TICKS = 600;

    private QuestCleanupService() {}

    public static void cleanupAfterQuestQuit(ServerPlayer player, ClientQuestEntry quest) {
        if (player == null || quest == null) return;

        ServerLevel level = player.serverLevel();
        int removedEscorts = cleanupEscortNpc(player, level, quest);
        Sweep items = sweepForQuitQuest(carriedSlots(player), player.getStringUUID(), quest);
        if (items.changed()) player.inventoryMenu.broadcastChanges();

        if (removedEscorts > 0 || items.changed()) {
            LOGGER.info("Cleaned abandoned quest side effects player={} quest_state_id={} quest_id={} escorts={} items={} stripped_legacy_stamps={}",
                    player.getStringUUID(), quest.questStateId(), quest.questId(), removedEscorts,
                    items.removed(), items.stripped());
        }
    }

    public static void cleanupStaleLocalQuestState(ServerPlayer player, Collection<ClientQuestEntry> activeQuests) {
        if (player == null) return;

        ActiveQuestIds active = ActiveQuestIds.from(activeQuests);
        int removedEscorts = cleanupStaleEscorts(player, active);
        Sweep items = sweepForStaleQuests(carriedSlots(player), player.getStringUUID(), active);
        if (items.changed()) player.inventoryMenu.broadcastChanges();

        if (removedEscorts > 0 || items.changed()) {
            LOGGER.info("Cleaned stale local quest side effects player={} escorts={} items={} stripped_legacy_stamps={}",
                    player.getStringUUID(), removedEscorts, items.removed(), items.stripped());
        }
    }

    /**
     * The quit pass over one player's carried stacks: a TEMPORARY stamp of the quit quest owned by
     * this player is deleted; a LEGACY stamp of the quit quest is stripped and kept. Package-private
     * so the decision table can be unit-tested without a server player.
     */
    static Sweep sweepForQuitQuest(Iterable<NonNullList<ItemStack>> slotLists, String ownerUuid, ClientQuestEntry quest) {
        return sweepItems(slotLists,
            tag -> ownedBy(tag, ownerUuid) && matchesQuestItem(tag, quest),
            tag -> matchesQuestItem(tag, quest));
    }

    /**
     * The login/bootstrap pass: a TEMPORARY stamp owned by this player whose quest is no longer in
     * the active journal is deleted (only when the stamp names a quest at all); a LEGACY stamp whose
     * quest is no longer active, or that names no quest, is stripped and kept. Stamps of quests still
     * in the journal are left exactly as they are.
     */
    static Sweep sweepForStaleQuests(Iterable<NonNullList<ItemStack>> slotLists, String ownerUuid,
                                     Collection<ClientQuestEntry> activeQuests) {
        return sweepForStaleQuests(slotLists, ownerUuid, ActiveQuestIds.from(activeQuests));
    }

    private static Sweep sweepForStaleQuests(Iterable<NonNullList<ItemStack>> slotLists, String ownerUuid,
                                             ActiveQuestIds active) {
        return sweepItems(slotLists,
            tag -> ownedBy(tag, ownerUuid) && hasReliableQuestItemMetadata(tag) && !active.matches(tag),
            tag -> !hasReliableQuestItemMetadata(tag) || !active.matches(tag));
    }

    /** Every slot a player carries: main inventory (hotbar included), armour, off hand. */
    private static List<NonNullList<ItemStack>> carriedSlots(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        return List.of(inventory.items, inventory.armor, inventory.offhand);
    }

    public static void clearSpawnerForRemovedQuestGiver(ServerLevel level, UUID npcId, String npcApiId, int cooldownTicks) {
        if (level == null || npcId == null) return;

        Entity removedEntity = level.getEntity(npcId);
        BlockPos center = removedEntity != null ? removedEntity.blockPosition() : null;
        if (center == null) return;

        clearNearbySpawners(level, center, npcId, npcApiId, cooldownTicks);
    }

    private static int cleanupEscortNpc(ServerPlayer player, ServerLevel level, ClientQuestEntry quest) {
        if (level == null || !isEscortQuest(quest)) return 0;

        String playerTag = escortPlayerTag(player);
        AABB searchArea = player.getBoundingBox().inflate(ESCORT_SEARCH_RADIUS);
        List<QuestGiverEntity> candidates = level.getEntitiesOfClass(
                QuestGiverEntity.class,
                searchArea,
                entity -> entity.getTags().contains(playerTag)
        );

        List<QuestGiverEntity> strictMatches = candidates.stream()
                .filter(entity -> matchesQuest(entity, quest))
                .toList();
        List<QuestGiverEntity> matches = strictMatches;

        if (matches.isEmpty() && candidates.size() == 1) {
            QuestGiverEntity onlyEscort = candidates.get(0);
            if (onlyEscort.getTags().contains("generic_escort") || internalApiId(onlyEscort).startsWith("escort_")) {
                LOGGER.warn("Cleaning player escort by fallback match player={} quest_state_id={} quest_id={} npc={}",
                        player.getStringUUID(), quest.questStateId(), quest.questId(), onlyEscort.getStringUUID());
                matches = candidates;
            }
        }

        int removed = 0;
        for (QuestGiverEntity escort : matches) {
            String npcApiId = internalApiId(escort);
            UUID npcId = escort.getUUID();
            escort.getNavigation().stop();
            clearEscortAssignmentTags(escort, "quit cleanup");
            escort.discard();
            clearNearbySpawners(level, escort.blockPosition(), npcId, npcApiId, ABANDONED_ESCORT_RESPAWN_COOLDOWN_TICKS);
            removed++;
        }

        if (removed == 0) {
            LOGGER.warn("No matching escort NPC found for abandoned escort quest player={} quest_state_id={} quest_id={} quest_key={}",
                    player.getStringUUID(), quest.questStateId(), quest.questId(), quest.questKey());
        }
        return removed;
    }

    private static int cleanupStaleEscorts(ServerPlayer player, ActiveQuestIds active) {
        ServerLevel level = player.serverLevel();
        String playerTag = escortPlayerTag(player);
        AABB searchArea = player.getBoundingBox().inflate(ESCORT_SEARCH_RADIUS);
        List<QuestGiverEntity> staleEscorts = level.getEntitiesOfClass(
                QuestGiverEntity.class,
                searchArea,
                entity -> entity.getTags().contains(playerTag)
                        && (!hasQuestStateId(entity) || (hasReliableQuestMetadata(entity) && !active.matches(entity)))
        );

        int removed = 0;
        for (QuestGiverEntity escort : staleEscorts) {
            String npcApiId = internalApiId(escort);
            UUID npcId = escort.getUUID();
            escort.getNavigation().stop();
            clearEscortAssignmentTags(escort, "stale bootstrap cleanup");
            escort.discard();
            clearNearbySpawners(level, escort.blockPosition(), npcId, npcApiId, ABANDONED_ESCORT_RESPAWN_COOLDOWN_TICKS);
            removed++;
        }
        return removed;
    }

    /** Items deleted and legacy stamps stripped by one pass over the inventory, armour and off hand. */
    record Sweep(int removed, int stripped) {
        boolean changed() {
            return removed > 0 || stripped > 0;
        }
    }

    /**
     * One pass over the given slots. A TEMPORARY stamp inside {@code deleteScope} is deleted; a
     * LEGACY stamp inside {@code stripScope} is stripped and the item kept; anything else -- an
     * unstamped stack, or a stamp whose quest is still running -- is left exactly as it was.
     * Nothing is ever deleted without a trigger key, and nothing legacy is ever deleted.
     */
    private static Sweep sweepItems(Iterable<NonNullList<ItemStack>> slotLists, TagPredicate deleteScope,
                                    TagPredicate stripScope) {
        int removed = 0;
        int stripped = 0;
        for (NonNullList<ItemStack> slots : slotLists) {
            for (int i = 0; i < slots.size(); i++) {
                ItemStack stack = slots.get(i);
                if (stack.isEmpty()) continue;

                CompoundTag tag = QuestItemStamp.read(stack);
                if (!QuestItemStamp.isStamped(tag)) continue;

                if (QuestItemStamp.isTemporary(tag)) {
                    if (deleteScope.test(tag)) {
                        removed += stack.getCount();
                        slots.set(i, ItemStack.EMPTY);
                    }
                } else if (stripScope.test(tag) && QuestItemStamp.strip(stack)) {
                    stripped += stack.getCount();
                }
            }
        }
        return new Sweep(removed, stripped);
    }

    private static void clearNearbySpawners(ServerLevel level, BlockPos center, UUID npcId, String npcApiId, int cooldownTicks) {
        int radius = SPAWNER_SEARCH_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -16, -radius), center.offset(radius, 16, radius))) {
            if (!(level.getBlockEntity(pos) instanceof QuestGiverSpawnBlockEntity spawner)) continue;

            boolean clearedById = spawner.clearTrackedNpc(npcId, true, cooldownTicks);
            boolean clearedByApiId = !clean(npcApiId).isBlank() && spawner.clearTrackedEscort(npcApiId, cooldownTicks);
            if (clearedById || clearedByApiId) {
                LOGGER.info("Cleared escort assignment snapshot npc_id={} npc_api_id={} cooldown_ticks={}",
                        npcId, npcApiId, cooldownTicks);
                return;
            }
        }
    }

    private static void clearEscortAssignmentTags(QuestGiverEntity escort, String reason) {
        boolean changed = false;
        for (String tag : List.copyOf(escort.getTags())) {
            if (tag.equals("escort_active")
                    || tag.startsWith("quest_escort_")
                    || tag.startsWith("quest_state_id_")
                    || tag.startsWith("quest_id_")
                    || tag.startsWith("quest_key_")) {
                changed |= escort.removeTag(tag);
            }
        }
        if (changed) {
            LOGGER.info("Cleared live escort assignment reason={} npc={}", reason, escort.getStringUUID());
        }
    }

    private static boolean matchesQuest(QuestGiverEntity entity, ClientQuestEntry quest) {
        Set<String> tags = entity.getTags();
        if (!quest.questStateId().isBlank() && tags.contains("quest_state_id_" + quest.questStateId())) {
            return true;
        }
        if (!quest.questId().isBlank() && tags.contains("quest_id_" + quest.questId())) {
            return true;
        }
        if (!quest.questKey().isBlank()) {
            return tags.contains("quest_key_" + quest.questKey()) || quest.questKey().equals(internalApiId(entity));
        }
        return false;
    }

    private static boolean matchesQuestItem(CompoundTag tag, ClientQuestEntry quest) {
        if (!quest.questStateId().isBlank() && quest.questStateId().equals(tag.getString(QuestItemStamp.QUEST_STATE_ID))) {
            return true;
        }
        if (!quest.questId().isBlank() && quest.questId().equals(Long.toString(tag.getLong(QuestItemStamp.QUEST_ID)))) {
            return true;
        }
        if (!quest.questKey().isBlank() && quest.questKey().equals(tag.getString(QuestItemStamp.QUEST_KEY))) {
            return true;
        }
        return false;
    }

    private static boolean ownedBy(CompoundTag tag, String ownerUuid) {
        String stampedOwner = tag.getString(QuestItemStamp.OWNER_UUID);
        return !stampedOwner.isBlank() && stampedOwner.equals(ownerUuid);
    }

    private static boolean hasReliableQuestItemMetadata(CompoundTag tag) {
        return tag.contains(QuestItemStamp.QUEST_STATE_ID) || tag.contains(QuestItemStamp.QUEST_ID)
                || tag.contains(QuestItemStamp.QUEST_KEY);
    }

    private static boolean hasReliableQuestMetadata(QuestGiverEntity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith("quest_state_id_") || tag.startsWith("quest_id_") || tag.startsWith("quest_key_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQuestStateId(QuestGiverEntity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith("quest_state_id_") && tag.length() > "quest_state_id_".length()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEscortQuest(ClientQuestEntry quest) {
        return startsWithEscort(quest.questKey())
                || startsWithEscort(quest.name())
                || startsWithEscort(quest.briefDescription());
    }

    private static boolean startsWithEscort(String value) {
        return clean(value).toLowerCase(java.util.Locale.ROOT).contains("escort");
    }

    private static String escortPlayerTag(ServerPlayer player) {
        return "quest_escort_" + player.getUUID();
    }

    private static String internalApiId(QuestGiverEntity entity) {
        return internalApiId(entity != null ? entity.getPersonalName() : "");
    }

    private static String internalApiId(String rawName) {
        String cleaned = clean(rawName);
        if (cleaned.isBlank()) return "";
        if (!cleaned.contains(":")) return cleaned;
        return cleaned.split(":", 2)[1].trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private interface TagPredicate {
        boolean test(CompoundTag tag);
    }

    private record ActiveQuestIds(Set<String> questStateIds, Set<String> questIds, Set<String> questKeys) {
        static ActiveQuestIds from(Collection<ClientQuestEntry> quests) {
            Set<String> stateIds = new HashSet<>();
            Set<String> ids = new HashSet<>();
            Set<String> keys = new HashSet<>();

            if (quests != null) {
                for (ClientQuestEntry quest : quests) {
                    if (quest == null) continue;
                    add(stateIds, quest.questStateId());
                    add(ids, quest.questId());
                    add(keys, quest.questKey());
                }
            }

            return new ActiveQuestIds(stateIds, ids, keys);
        }

        boolean matches(QuestGiverEntity entity) {
            for (String tag : entity.getTags()) {
                if (tag.startsWith("quest_state_id_") && questStateIds.contains(tag.substring("quest_state_id_".length()))) {
                    return true;
                }
                if (tag.startsWith("quest_id_") && questIds.contains(tag.substring("quest_id_".length()))) {
                    return true;
                }
                if (tag.startsWith("quest_key_") && questKeys.contains(tag.substring("quest_key_".length()))) {
                    return true;
                }
            }
            return false;
        }

        boolean matches(CompoundTag tag) {
            String stateId = tag.getString(QuestItemStamp.QUEST_STATE_ID);
            if (!stateId.isBlank()) return questStateIds.contains(stateId);

            if (tag.contains(QuestItemStamp.QUEST_ID)) {
                return questIds.contains(Long.toString(tag.getLong(QuestItemStamp.QUEST_ID)));
            }

            String questKey = tag.getString(QuestItemStamp.QUEST_KEY);
            return !questKey.isBlank() && questKeys.contains(questKey);
        }

        private static void add(Set<String> values, String value) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) {
                values.add(cleaned);
            }
        }
    }
}

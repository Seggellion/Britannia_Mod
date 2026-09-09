package com.seggellion.britannia_mod.quest;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.quest.handin.QuestHandinInventory;
import com.seggellion.britannia_mod.wildresource.WildResourceQuestScheduling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-quest side effects of accepting a stage of "From Soil to Supper" (M9 item 5).
 *
 * <p>There is exactly one today: quest 1 sends the player to find a dung pile, so accepting it
 * brings the world's own dung placement schedule forward near the Rowan the player is standing at.
 * See {@link WildResourceQuestScheduling} for why this schedules rather than places.
 *
 * <p>Kept here, on the mod side, rather than as a new Rails client action, because the questline's
 * authored content and its {@code quest_contract/v1} fixtures were frozen at M0 and pinned by a
 * SHA-256 manifest: adding an accept effect to quest 1 would have rewritten a frozen fixture to
 * express something only the game can act on anyway. The quest key is the contract between the two
 * halves, and it is stable by construction -- Rails finds a quest row by {@code quest_key} and
 * never mints a new one.
 */
public final class RowanQuestlineHooks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Mirrors {@code QuestContent::RowanFarmingQuestline::QUESTLINE_KEY} and its {@code quest_key(1)}. */
    public static final String QUESTLINE_KEY_PREFIX = "rowan_farming_";
    public static final String DUNG_QUEST_KEY = QUESTLINE_KEY_PREFIX + "1";

    /**
     * Stage four, Mix Fertilized Dirt, and stage five, Plant and Harvest.
     *
     * <p>Both send the player round the gathering loop again, and neither says so. Stage three
     * consumed the bowl of dirt, the bowl of water and the dung that stage four now wants back; and
     * stage five wants fertilized dirt, which means the whole loop a second time before a seed can
     * go in the ground. The quest text is authored for the first time through, so without this the
     * two stages read as "make the thing" to a player who has nothing left to make it with.
     */
    public static final String MIX_QUEST_KEY = QUESTLINE_KEY_PREFIX + "4";
    public static final String HARVEST_QUEST_KEY = QUESTLINE_KEY_PREFIX + "5";

    /** How often one stage will repeat its material guidance, however many times it is opened. */
    static final long GUIDANCE_INTERVAL_MILLIS = 5L * 60L * 1000L;

    private static final Map<UUID, Map<String, Long>> LAST_GUIDANCE = new ConcurrentHashMap<>();

    private RowanQuestlineHooks() {
    }

    /** Whether this quest key is the stage that asks the player to find dung. */
    public static boolean isDungGatheringQuest(@Nullable String questKey) {
        return DUNG_QUEST_KEY.equals(questKey);
    }

    /**
     * Whether accepting this stage should bring the world dung schedule forward.
     *
     * <p>Stage one obviously. Stages four and five for the same reason: they need a fresh pile, and
     * a player who has already cleared the area around Rowan would otherwise be waiting out a 6-12
     * minute placement window with no way to know that is what they are doing.
     */
    public static boolean expeditesDung(@Nullable String questKey) {
        return isDungGatheringQuest(questKey) || RowanFarmingMaterials.needsDung(questKey);
    }

    /**
     * Runs the accept-time side effects for one newly accepted quest. Safe to call for every
     * accepted entry; anything that is not a stage with a side effect does nothing.
     */
    public static void onQuestAccepted(ServerPlayer player, ClientQuestEntry accepted,
                                       @Nullable UUID questGiverUuid) {
        if (player == null || accepted == null) {
            return;
        }
        String questKey = accepted.questKey();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (expeditesDung(questKey)) {
            BlockPos center = questGiverPosition(level, questGiverUuid, player);
            int expedited = WildResourceQuestScheduling.expediteDungNear(level, center);
            LOGGER.info("event=rowan_dung_schedule_expedited player_uuid={} quest_key={} chunks={}",
                    player.getStringUUID(), questKey, expedited);
        }
        sendMaterialGuidance(player, questKey);
    }

    /**
     * Tells the player what this stage needs that they are not already carrying.
     *
     * <p>Says nothing at all when they have everything, and nothing at all for a stage with no
     * materials -- which is every quest in the game but two. Repeats at most every five minutes per
     * stage, because a stage can be re-accepted and a journal can be refreshed, and a line the
     * player has read twice in a minute is one they stop reading.
     */
    public static void sendMaterialGuidance(ServerPlayer player, @Nullable String questKey) {
        if (player == null || questKey == null) return;
        List<RowanFarmingMaterials.Requirement> outstanding =
                RowanFarmingMaterials.outstanding(questKey, carried(player));
        if (outstanding.isEmpty()) return;
        if (!due(player.getUUID(), questKey, System.currentTimeMillis())) return;

        player.sendSystemMessage(Component.translatable(QuestScreenText.ROWAN_GATHER_AGAIN));
        for (RowanFarmingMaterials.Requirement requirement : outstanding) {
            player.sendSystemMessage(Component.translatable(QuestScreenText.ROWAN_GATHER_LINE,
                    itemName(requirement.itemId()), requirement.count()));
        }
        LOGGER.info("event=rowan_material_guidance player_uuid={} quest_key={} outstanding={}",
                player.getStringUUID(), questKey, outstanding.size());
    }

    /** Package-visible so the throttle can be exercised without a clock. */
    static boolean due(UUID playerId, String questKey, long nowMillis) {
        Map<String, Long> perStage = LAST_GUIDANCE.computeIfAbsent(playerId, key -> new ConcurrentHashMap<>());
        Long last = perStage.get(questKey);
        if (last != null && nowMillis - last < GUIDANCE_INTERVAL_MILLIS) return false;
        perStage.put(questKey, nowMillis);
        return true;
    }

    /** Drops the throttle for a player who has gone; their next login starts fresh. */
    public static void forgetPlayer(UUID playerId) {
        if (playerId != null) LAST_GUIDANCE.remove(playerId);
    }

    /** Everything the player is carrying, by namespaced id -- carried inventory only. */
    private static Map<String, Integer> carried(ServerPlayer player) {
        Map<String, Integer> held = new HashMap<>();
        for (int slot = 0; slot < QuestHandinInventory.MAIN_SLOT_COUNT; slot++) {
            count(held, player.getInventory().getItem(slot));
        }
        count(held, player.getInventory().getItem(QuestHandinInventory.OFFHAND_SLOT));
        return held;
    }

    private static void count(Map<String, Integer> held, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) held.merge(id.toString(), stack.getCount(), Integer::sum);
    }

    private static Component itemName(String itemId) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId);
        if (parsed == null) return Component.literal(itemId);
        return BuiltInRegistries.ITEM.getOptional(parsed)
                .<Component>map(item -> item.getDescription())
                .orElseGet(() -> Component.literal(itemId));
    }

    /**
     * Where to centre the search: the quest giver if we can still see them, otherwise the player,
     * who is by definition standing in front of the quest giver at this moment.
     */
    private static BlockPos questGiverPosition(ServerLevel level, @Nullable UUID questGiverUuid,
                                               ServerPlayer player) {
        if (questGiverUuid != null) {
            Entity giver = level.getEntity(questGiverUuid);
            if (giver != null) {
                return giver.blockPosition();
            }
        }
        return player.blockPosition();
    }
}

package com.seggellion.britannia_mod.quest;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.wildresource.WildResourceQuestScheduling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

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

    private RowanQuestlineHooks() {
    }

    /** Whether this quest key is the stage that asks the player to find dung. */
    public static boolean isDungGatheringQuest(@Nullable String questKey) {
        return DUNG_QUEST_KEY.equals(questKey);
    }

    /**
     * Runs the accept-time side effects for one newly accepted quest. Safe to call for every
     * accepted entry; anything that is not a stage with a side effect does nothing.
     */
    public static void onQuestAccepted(ServerPlayer player, ClientQuestEntry accepted,
                                       @Nullable UUID questGiverUuid) {
        if (player == null || accepted == null || !isDungGatheringQuest(accepted.questKey())) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = questGiverPosition(level, questGiverUuid, player);
        int expedited = WildResourceQuestScheduling.expediteDungNear(level, center);
        LOGGER.info("event=rowan_dung_schedule_expedited player_uuid={} quest_key={} chunks={}",
                player.getStringUUID(), accepted.questKey(), expedited);
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

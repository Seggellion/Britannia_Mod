package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Carries this mod's own player compound across a respawn.
 *
 * <h2>Why this is necessary rather than tidy</h2>
 * A respawn -- from death, or from returning through the end portal -- does not reuse the
 * {@code ServerPlayer}. {@code ServerPlayer#restoreFrom} builds a new one and copies a fixed list of
 * fields, and of {@code getPersistentData()} it copies exactly one sub-tag:
 *
 * <pre>
 *   CompoundTag old = p_9016_.getPersistentData();
 *   if (old.contains(PERSISTED_NBT_TAG))
 *        getPersistentData().put(PERSISTED_NBT_TAG, old.get(PERSISTED_NBT_TAG));
 * </pre>
 *
 * Everything else in that compound is dropped. This mod's data lives at the top level under
 * {@code britannia_player}, so without this handler it does not survive dying.
 *
 * <h2>What that cost</h2>
 * Two durable markers live in that compound, and both are load-bearing.
 *
 * <p>The <b>hand-in removal marker</b> is the player-file half of the crash-safety argument for a
 * strict item hand-in: it records what left the pack, written in the same step as the shrink so the
 * two persist together or not at all. Losing it while a confirmation is in flight makes the ledger
 * and the player file disagree in the direction this server refuses to act on, and the transaction
 * is stranded -- items gone, quest unfinished, no refund. Dying at the wrong second is not an
 * acceptable way to lose an item.
 *
 * <p>The <b>reward delivery marker</b> is the same idea for an insertion, and loses it the other
 * way: a marker missing beside a ledger row that says {@code applied} is read as "the insertion
 * never reached disk", and the delivery is applied again. This handler was written for the hand-in
 * and repairs that one too, because both live in the one compound and fixing only half of it would
 * be an arbitrary place to stop.
 *
 * <p>The rest of the compound -- gender, fame, karma, contributions -- is re-synced from Rails at
 * the next login, so losing it was survivable rather than silent. It is carried across here as
 * well, which simply means a player is not briefly wrong about themselves between dying and
 * reconnecting.
 *
 * <h2>Scope</h2>
 * Copies the compound whole and only when the new player has none, so it can never overwrite data
 * a later handler has already installed. It does not distinguish death from the end-portal return:
 * both build a fresh player, and nothing in this compound is meant to be lost either way.
 */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerDataCloneHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerDataCloneHandler() {}

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player clone = event.getEntity();
        if (original == null || clone == null) return;
        copy(original, clone, event.isWasDeath());
    }

    /** Public so a GameTest can drive it with a synthetic clone, as the repository does. */
    public static void copy(Player original, Player clone, boolean wasDeath) {
        CompoundTag from = original.getPersistentData();
        if (!from.contains(PlayerDataStore.PERSISTENT_KEY, CompoundTag.TAG_COMPOUND)) return;
        CompoundTag to = clone.getPersistentData();
        if (to.contains(PlayerDataStore.PERSISTENT_KEY, CompoundTag.TAG_COMPOUND)) return;
        to.put(PlayerDataStore.PERSISTENT_KEY, from.getCompound(PlayerDataStore.PERSISTENT_KEY).copy());
        LOGGER.debug("event=britannia_player_data_carried_across_respawn player_uuid={} was_death={}",
                clone.getStringUUID(), wasDeath);
    }
}

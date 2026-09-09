// com/seggellion/britannia_mod/player/PlayerDataStore.java
package com.seggellion.britannia_mod.player;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.quest.handin.QuestHandinRemoval;
import com.seggellion.britannia_mod.quest.handin.QuestHandinRemovalNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerDataStore() {}
    private static final String KEY = "britannia_player";

    /**
     * The compound this mod keeps inside {@code player.getPersistentData()}.
     *
     * <p>Public because a respawn does not carry it: {@code ServerPlayer#restoreFrom} copies only
     * the {@code PlayerPersisted} sub-tag, so {@code PlayerDataCloneHandler} has to move this one
     * across by hand. Both durable markers below live in here, and losing either to a death is how
     * a player loses an item.
     */
    public static final String PERSISTENT_KEY = KEY;

    /**
     * Rowan farming questline M3 (protocol section 1.8): the bounded list of reward deliveries
     * whose items this player received, kept in the same persistent compound as the rest of the
     * player's mod data. It is appended in the same server-thread step as the item insertion, so
     * the vanilla player-file write (temp file, {@code SYNC}, atomic replace) persists the items
     * and the marker together or not at all -- which is what lets a restart tell "inserted and
     * saved" from "inserted and lost" without a second grant.
     */
    public static final String APPLIED_DELIVERY_UUIDS = "applied_delivery_uuids";
    public static final int MAX_APPLIED_DELIVERY_MARKERS = 256;

    public static PlayerData get(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag dataTag = root.getCompound(KEY);
        if (dataTag.isEmpty() || !dataTag.contains("UUID", Tag.TAG_STRING)) {
            PlayerData fresh = new PlayerData(player.getUUID());
            save(player, fresh);
            return fresh;
        }
        return PlayerData.load(dataTag);
    }

    /**
     * Rowan farming questline, strict item hand-ins (protocol section 1.5): the bounded list of
     * hand-in removals whose items have left this player's pack, each carrying the concrete proof
     * of what was taken.
     *
     * <p>It is appended in the same server-thread step as the shrink, so the vanilla player-file
     * write (temp file, atomic replace) persists the removal and its proof together or not at all.
     * That is what lets a restart tell "removed and saved" from "removed and lost" -- and, because
     * each marker carries the whole proof rather than an id, a marker found without its ledger row
     * is still enough to confirm the transaction and get the player either their quest or their
     * items back.
     */
    public static final String HANDIN_REMOVALS = "handin_removals";

    /**
     * Bounded like the delivery markers, but never dropped to make room: a marker is removed only
     * when its transaction has reached an ending, so hitting this bound means the player genuinely
     * holds that many unfinished hand-ins and the ledger refuses to mint another.
     */
    public static final int MAX_HANDIN_REMOVAL_MARKERS = 64;

    private static final String MARKER_HANDIN_UUID = "HandinUuid";
    private static final String MARKER_REQUEST_UUID = "RequestUuid";
    private static final String MARKER_REMOVED_AT = "RemovedAt";
    private static final String MARKER_PROOF = "Proof";

    public static void save(ServerPlayer player, PlayerData data) {
        CompoundTag root = player.getPersistentData();
        CompoundTag out = new CompoundTag();
        data.save(out);
        // Neither marker list is part of PlayerData; carry both across so a profile save (the
        // bootstrap writes one at every login) can never drop them.
        CompoundTag existing = root.getCompound(KEY);
        ListTag markers = markerList(existing);
        if (!markers.isEmpty()) out.put(APPLIED_DELIVERY_UUIDS, markers.copy());
        ListTag handins = handinList(existing);
        if (!handins.isEmpty()) out.put(HANDIN_REMOVALS, handins.copy());
        root.put(KEY, out);
    }

    // --- hand-in removal marker -----------------------------------------------------------------

    /** What one marker records: the transaction, the correlation id, and exactly what was taken. */
    public record HandinRemovalMarker(UUID handinUuid, UUID requestUuid, long removedAtMillis,
                                      List<QuestHandinRemoval> proof) {
        public HandinRemovalMarker {
            java.util.Objects.requireNonNull(handinUuid, "handinUuid");
            java.util.Objects.requireNonNull(requestUuid, "requestUuid");
            proof = List.copyOf(proof);
            if (proof.isEmpty()) {
                throw new IllegalArgumentException("a removal marker without its proof proves nothing");
            }
        }
    }

    /**
     * Records that the hand-in's items have left the pack. Idempotent. Only mutates the in-memory
     * persistent data: the caller forces the player-file write, in the same step as the shrink.
     *
     * @return false when the player already holds the maximum unfinished markers, in which case
     *         nothing is recorded and the caller must not remove anything
     */
    public static boolean markHandinRemoved(ServerPlayer player, HandinRemovalMarker marker) {
        if (player == null || marker == null) return false;
        get(player);
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = handinList(dataTag);
        String value = marker.handinUuid().toString();
        for (Tag tag : markers) {
            if (tag instanceof CompoundTag entry
                    && value.equals(entry.getString(MARKER_HANDIN_UUID))) {
                return true;
            }
        }
        // Never evicts to make room. A marker is the only durable evidence that this player gave
        // something up, so the bound refuses a new removal instead of forgetting an old one.
        if (markers.size() >= MAX_HANDIN_REMOVAL_MARKERS) return false;

        CompoundTag entry = new CompoundTag();
        entry.putString(MARKER_HANDIN_UUID, value);
        entry.putString(MARKER_REQUEST_UUID, marker.requestUuid().toString());
        entry.putLong(MARKER_REMOVED_AT, marker.removedAtMillis());
        entry.put(MARKER_PROOF, QuestHandinRemovalNbt.toList(marker.proof()));
        markers.add(entry);
        dataTag.put(HANDIN_REMOVALS, markers);
        return true;
    }

    /**
     * Whether the player's own file records a removal for this transaction <b>at all</b>, readable
     * or not.
     *
     * <p>The distinction matters more than it looks. A marker and the shrink that produced it are
     * written in one in-memory step, so an entry existing is proof the items left the pack, whatever
     * state its bytes are in. Absence means the mutation never reached disk and the player still has
     * everything; a corrupt entry means the opposite. Collapsing the two would either strand a
     * player who could simply hand in again, or offer a second removal to one who already paid.
     */
    public static boolean handinRemovalRecorded(ServerPlayer player, UUID handinUuid) {
        if (player == null || handinUuid == null) return false;
        String value = handinUuid.toString();
        for (Tag tag : handinList(player.getPersistentData().getCompound(KEY))) {
            if (tag instanceof CompoundTag entry && value.equals(entry.getString(MARKER_HANDIN_UUID))) {
                return true;
            }
        }
        return false;
    }

    /** The marker for one transaction, or empty. */
    public static Optional<HandinRemovalMarker> handinRemoval(ServerPlayer player, UUID handinUuid) {
        if (player == null || handinUuid == null) return Optional.empty();
        String value = handinUuid.toString();
        for (HandinRemovalMarker marker : handinRemovals(player)) {
            if (marker.handinUuid().toString().equals(value)) return Optional.of(marker);
        }
        return Optional.empty();
    }

    /** Every readable marker, oldest first. A marker this build cannot read names no transaction. */
    public static List<HandinRemovalMarker> handinRemovals(ServerPlayer player) {
        List<HandinRemovalMarker> found = new ArrayList<>();
        if (player == null) return found;
        for (Tag tag : handinList(player.getPersistentData().getCompound(KEY))) {
            if (!(tag instanceof CompoundTag entry)) continue;
            try {
                found.add(new HandinRemovalMarker(
                        UUID.fromString(entry.getString(MARKER_HANDIN_UUID)),
                        UUID.fromString(entry.getString(MARKER_REQUEST_UUID)),
                        entry.getLong(MARKER_REMOVED_AT),
                        QuestHandinRemovalNbt.fromList(entry.getList(MARKER_PROOF, Tag.TAG_COMPOUND))));
            } catch (RuntimeException unreadable) {
                // Skipped rather than thrown: one bad marker must not hide the others. It is NOT
                // treated as absent, though -- see handinRemovalRecorded. An entry existing at all
                // proves the shrink happened, because the two were written in one step, so reading
                // "unreadable" as "never removed" would offer the player a second removal.
                LOGGER.warn("event=quest_handin_marker_unreadable player_uuid={} detail={}",
                        player.getStringUUID(), unreadable.toString());
            }
        }
        return found;
    }

    /**
     * Forgets one marker, once its transaction has reached an ending.
     *
     * <p>Unlike the delivery markers, production does remove these -- an unfinished hand-in is a
     * bounded, short-lived thing and a marker that outlived its row would look like an orphan worth
     * confirming. It is removed only <b>after</b> the ledger row is durably settled, so a crash in
     * between leaves a stale marker whose row already says the transaction is over, which
     * reconciliation ignores.
     */
    public static boolean forgetHandinRemoval(ServerPlayer player, UUID handinUuid) {
        if (player == null || handinUuid == null) return false;
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = handinList(dataTag);
        String value = handinUuid.toString();
        boolean removed = markers.removeIf(tag -> tag instanceof CompoundTag entry
                && value.equals(entry.getString(MARKER_HANDIN_UUID)));
        if (removed) dataTag.put(HANDIN_REMOVALS, markers);
        return removed;
    }

    private static ListTag handinList(CompoundTag dataTag) {
        if (dataTag == null || !dataTag.contains(HANDIN_REMOVALS, Tag.TAG_LIST)) return new ListTag();
        return dataTag.getList(HANDIN_REMOVALS, Tag.TAG_COMPOUND);
    }

    // --- reward delivery marker (M3) ------------------------------------------------------------

    /** Whether this player's persistent data records the delivery's items as inserted. */
    public static boolean hasAppliedDelivery(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return false;
        String value = deliveryUuid.toString();
        for (Tag tag : markerList(player.getPersistentData().getCompound(KEY))) {
            if (value.equals(tag.getAsString())) return true;
        }
        return false;
    }

    /** The marker list, oldest first. */
    public static List<UUID> appliedDeliveries(ServerPlayer player) {
        List<UUID> found = new ArrayList<>();
        if (player == null) return found;
        for (Tag tag : markerList(player.getPersistentData().getCompound(KEY))) {
            try {
                found.add(UUID.fromString(tag.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // A marker this build cannot read names no delivery it could match.
            }
        }
        return found;
    }

    /**
     * Records that the delivery's items are in the inventory. Idempotent; bounded to the
     * {@value #MAX_APPLIED_DELIVERY_MARKERS} newest. Only mutates the in-memory persistent data:
     * the caller forces the player-file write, in the same step as the insertion.
     */
    public static void markDeliveryApplied(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return;
        get(player);
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = markerList(dataTag);
        String value = deliveryUuid.toString();
        for (Tag tag : markers) {
            if (value.equals(tag.getAsString())) return;
        }
        markers.add(StringTag.valueOf(value));
        while (markers.size() > MAX_APPLIED_DELIVERY_MARKERS) markers.remove(0);
        dataTag.put(APPLIED_DELIVERY_UUIDS, markers);
    }

    /**
     * Forgets one marker, as if the player file had been written before it was appended. A
     * restart-simulation seam for tests; production code never removes a marker.
     */
    public static boolean removeAppliedDeliveryMarker(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return false;
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = markerList(dataTag);
        String value = deliveryUuid.toString();
        boolean removed = markers.removeIf(tag -> value.equals(tag.getAsString()));
        if (removed) dataTag.put(APPLIED_DELIVERY_UUIDS, markers);
        return removed;
    }

    private static ListTag markerList(CompoundTag dataTag) {
        if (dataTag == null || !dataTag.contains(APPLIED_DELIVERY_UUIDS, Tag.TAG_LIST)) return new ListTag();
        return dataTag.getList(APPLIED_DELIVERY_UUIDS, Tag.TAG_STRING);
    }
}

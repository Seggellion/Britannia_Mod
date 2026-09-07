// com/seggellion/britannia_mod/player/PlayerDataStore.java
package com.seggellion.britannia_mod.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerDataStore {
    private PlayerDataStore() {}
    private static final String KEY = "britannia_player";

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

    public static void save(ServerPlayer player, PlayerData data) {
        CompoundTag root = player.getPersistentData();
        CompoundTag out = new CompoundTag();
        data.save(out);
        // The delivery marker is not part of PlayerData; carry it across so a profile save (the
        // bootstrap writes one at every login) can never drop it.
        ListTag markers = markerList(root.getCompound(KEY));
        if (!markers.isEmpty()) out.put(APPLIED_DELIVERY_UUIDS, markers.copy());
        root.put(KEY, out);
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

package com.seggellion.britannia_mod.structure.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Every house this shard has placed and not yet proved Rails knows about.
 *
 * <h2>Why the send could not stay a fire-and-forget call</h2>
 *
 * <p>Placement used to POST the house to Rails inline, on the server thread, and log a warning if
 * the answer was not 200. That warning was the entire failure handling. A Rails restart, a network
 * blip, a rate-limit, a 500 — any of them and the house existed in the world and nowhere else, for
 * good. Nothing ever tried again, and nothing anywhere recorded that a house was owed. That is the
 * whole of "newly placed houses are not appearing on the website": not a wrong endpoint or a wrong
 * payload, but a delivery with no second attempt and no record of having failed.
 *
 * <p>It also matters more than it looks. Rails is what
 * {@code StructureRegionRehydrator} rebuilds every house region from at boot, so a house that never
 * reached Rails loses its region at the next restart: its doors resolve no lock and its owner loses
 * the right to build inside it.
 *
 * <h2>What this is</h2>
 *
 * <p>A durable queue on the overworld's {@code SavedData}, keyed by house UUID, holding the exact
 * request body that was built at placement time. The body is stored verbatim rather than rebuilt on
 * retry, because a retry must send the same house — the same coordinates, the same rotation, the
 * same origin — and not whatever the world happens to look like now.
 *
 * <p>Retries are idempotent by house UUID at both ends: this store holds at most one entry per
 * house, and the Rails endpoint upserts on {@code (uuid, shard)}. Retrying is therefore safe by
 * construction rather than by timing.
 *
 * <p>Same shape as {@code TraderSaleReservationStore}: unreadable entries are kept verbatim and
 * skipped rather than dropped, because losing a record of a house is the failure this class exists
 * to prevent and a decode bug is not a reason to commit it.
 */
public final class HousePersistenceOutbox extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "britannia_house_persistence_outbox";
    private static final int SCHEMA_VERSION = 1;

    private final LinkedHashMap<UUID, PendingHouse> pending = new LinkedHashMap<>();
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static HousePersistenceOutbox get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HousePersistenceOutbox::new, HousePersistenceOutbox::load),
                DATA_NAME);
    }

    public static HousePersistenceOutbox load(CompoundTag tag, HolderLookup.Provider provider) {
        HousePersistenceOutbox store = new HousePersistenceOutbox();
        ListTag entries = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            PendingHouse house = PendingHouse.fromTag(entry);
            if (house == null) {
                store.unreadable.add(entry.copy());
                continue;
            }
            store.pending.put(house.houseUuid(), house);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (PendingHouse house : pending.values()) {
            entries.add(house.toTag());
        }
        for (CompoundTag orphan : unreadable) {
            entries.add(orphan.copy());
        }
        tag.put("Pending", entries);
        return tag;
    }

    /**
     * Records a house as owed to Rails. Overwrites any earlier attempt for the same house.
     *
     * <p>Called before the first send, not after a failure. If the process dies between the world
     * write and the HTTP call, the house is still owed and still recorded — which is the only
     * ordering that survives a crash.
     */
    public synchronized void enqueue(UUID houseUuid, String requestBody) {
        pending.put(houseUuid, new PendingHouse(houseUuid, requestBody, 0, 0L));
        setDirty();
    }

    /** Marks a house as durably recorded by Rails. */
    public synchronized void settle(UUID houseUuid) {
        if (pending.remove(houseUuid) != null) {
            setDirty();
        }
    }

    /** Records a failed attempt and when the next one may be made. */
    public synchronized void deferAfterFailure(UUID houseUuid, long nextAttemptEpochMillis) {
        PendingHouse existing = pending.get(houseUuid);
        if (existing == null) return;
        pending.put(houseUuid, new PendingHouse(
                existing.houseUuid(), existing.requestBody(),
                existing.attempts() + 1, nextAttemptEpochMillis));
        setDirty();
    }

    /** Everything owed whose backoff has elapsed, oldest first. */
    public synchronized List<PendingHouse> due(long nowEpochMillis) {
        List<PendingHouse> due = new ArrayList<>();
        for (PendingHouse house : pending.values()) {
            if (house.nextAttemptEpochMillis() <= nowEpochMillis) {
                due.add(house);
            }
        }
        return due;
    }

    public synchronized int outstanding() {
        return pending.size();
    }

    /**
     * One house owed to Rails.
     *
     * @param requestBody the exact JSON built at placement. Kept verbatim so a retry sends the
     *                    house that was placed rather than a house re-derived from a world that has
     *                    since changed.
     */
    public record PendingHouse(UUID houseUuid, String requestBody, int attempts, long nextAttemptEpochMillis) {

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("HouseUuid", houseUuid);
            tag.putString("Body", requestBody);
            tag.putInt("Attempts", attempts);
            tag.putLong("NextAttempt", nextAttemptEpochMillis);
            return tag;
        }

        public static PendingHouse fromTag(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("HouseUuid") || !tag.contains("Body")) {
                return null;
            }
            String body = tag.getString("Body");
            try {
                JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
                if (!parsed.has("uuid")) {
                    LOGGER.error("[housing] A queued house body has no uuid; keeping it unreadable");
                    return null;
                }
            } catch (RuntimeException malformed) {
                LOGGER.error("[housing] A queued house body will not parse; keeping it unreadable", malformed);
                return null;
            }
            return new PendingHouse(
                    tag.getUUID("HouseUuid"), body,
                    Math.max(0, tag.getInt("Attempts")),
                    Math.max(0L, tag.getLong("NextAttempt")));
        }
    }
}

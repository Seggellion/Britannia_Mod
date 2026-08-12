package com.seggellion.britannia_mod.economy;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 19.5: durable storage for trader-sale reservations,
 * so a crash between "items left the player's inventory" and "Rails answered"
 * can no longer destroy those items.
 *
 * <p>Stored on the overworld like {@code ServiceNpcSpawnClaimData} and the
 * migration ledger, so one store covers every dimension. Unreadable entries are
 * retained verbatim and skipped rather than dropped — the same
 * never-silently-lose-data convention the banking receipt store uses.
 */
public final class TraderSaleReservationStore extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "britannia_trader_sale_reservations";
    private static final int SCHEMA_VERSION = 1;

    private final LinkedHashMap<String, TraderSaleReservationReceipt> receipts = new LinkedHashMap<>();
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static TraderSaleReservationStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TraderSaleReservationStore::new, TraderSaleReservationStore::load),
                DATA_NAME
        );
    }

    public static TraderSaleReservationStore load(CompoundTag tag, HolderLookup.Provider provider) {
        TraderSaleReservationStore store = new TraderSaleReservationStore();
        ListTag entries = tag.getList("Receipts", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            TraderSaleReservationReceipt receipt = TraderSaleReservationReceipt.fromTag(entry);
            if (receipt == null) {
                store.unreadable.add(entry.copy());
                continue;
            }
            store.receipts.put(receipt.idempotencyKey(), receipt);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (TraderSaleReservationReceipt receipt : receipts.values()) {
            entries.add(receipt.toTag());
        }
        for (CompoundTag orphan : unreadable) {
            entries.add(orphan.copy());
        }
        tag.put("Receipts", entries);
        return tag;
    }

    /**
     * Records a new reservation, or returns false if this key is already
     * tracked (idempotent by construction — the sale's own idempotency key is
     * the receipt id, so a retry can never create a second reservation).
     */
    public boolean record(TraderSaleReservationReceipt receipt) {
        if (receipts.containsKey(receipt.idempotencyKey())) return false;
        receipts.put(receipt.idempotencyKey(), receipt);
        setDirty();
        return true;
    }

    public void advance(String idempotencyKey, TraderSaleReservationReceipt.Status status) {
        TraderSaleReservationReceipt existing = receipts.get(idempotencyKey);
        if (existing == null) return;
        receipts.put(idempotencyKey, existing.withStatus(status));
        setDirty();
    }

    /** Removes a settled reservation; the item risk is over. */
    public void resolve(String idempotencyKey) {
        if (receipts.remove(idempotencyKey) != null) setDirty();
    }

    @Nullable
    public TraderSaleReservationReceipt find(String idempotencyKey) {
        return receipts.get(idempotencyKey);
    }

    public List<TraderSaleReservationReceipt> forPlayer(UUID playerUuid) {
        List<TraderSaleReservationReceipt> matches = new ArrayList<>();
        for (TraderSaleReservationReceipt receipt : receipts.values()) {
            if (receipt.playerUuid().equals(playerUuid)) matches.add(receipt);
        }
        return matches;
    }

    public List<TraderSaleReservationReceipt> snapshot() {
        return new ArrayList<>(receipts.values());
    }

    public int unreadableCount() {
        return unreadable.size();
    }

    /**
     * Forces this store to disk immediately. The reservation flow needs its
     * state on disk at specific instants (before items leave the inventory,
     * and again once their removal is durable), not merely at the next
     * autosave — that is the whole point of the crash-safety guarantee.
     */
    public void flush(ServerLevel level) {
        try {
            level.getServer().overworld().getDataStorage().save();
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not force-save trader sale reservations: {}", failure.toString());
        }
    }
}

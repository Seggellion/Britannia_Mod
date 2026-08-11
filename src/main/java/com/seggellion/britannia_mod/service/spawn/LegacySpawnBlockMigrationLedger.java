package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 16: the durable rollback record for every legacy
 * MerchantSpawnBlock/TraderSpawnBlock converted onto the authoritative post
 * architecture. One receipt per migrated position, carrying the COMPLETE
 * legacy block-entity NBT (block id included) so an operator can restore the
 * pre-migration block byte-for-byte, plus the migration outcome and the
 * authoritative post UUID that replaced it. Receipts are never deleted by
 * code; they are the world's own migration history.
 *
 * <p>Stored on the overworld like {@link ServiceNpcSpawnClaimData}, so one
 * ledger covers every dimension. The pre-migration {@code townPersonAmount}
 * is preserved here for the future Rails-driven regional population system
 * (owner decision #12) to consult when it takes over city population.
 */
public final class LegacySpawnBlockMigrationLedger extends SavedData {
    private static final String DATA_NAME = "britannia_legacy_spawn_block_migrations";
    private static final int SCHEMA_VERSION = 1;

    public record Receipt(
            String dimensionKey,
            BlockPos pos,
            String legacyBlockId,
            CompoundTag legacyBlockEntityNbt,
            @Nullable UUID migratedPostId,
            String economicTypeKey,
            @Nullable UUID cityPublicId,
            String cityName,
            int townPersonAmount,
            long migratedAtEpochMillis,
            String outcome
    ) {
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("DimensionKey", dimensionKey);
            tag.putLong("Pos", pos.asLong());
            tag.putString("LegacyBlockId", legacyBlockId);
            tag.put("LegacyBlockEntityNbt", legacyBlockEntityNbt);
            if (migratedPostId != null) tag.putUUID("MigratedPostId", migratedPostId);
            tag.putString("EconomicTypeKey", economicTypeKey);
            if (cityPublicId != null) tag.putUUID("CityPublicId", cityPublicId);
            tag.putString("CityName", cityName);
            tag.putInt("TownPersonAmount", townPersonAmount);
            tag.putLong("MigratedAtEpochMillis", migratedAtEpochMillis);
            tag.putString("Outcome", outcome);
            return tag;
        }

        private static Receipt fromTag(CompoundTag tag) {
            return new Receipt(
                    tag.getString("DimensionKey"),
                    BlockPos.of(tag.getLong("Pos")),
                    tag.getString("LegacyBlockId"),
                    tag.getCompound("LegacyBlockEntityNbt"),
                    tag.hasUUID("MigratedPostId") ? tag.getUUID("MigratedPostId") : null,
                    tag.getString("EconomicTypeKey"),
                    tag.hasUUID("CityPublicId") ? tag.getUUID("CityPublicId") : null,
                    tag.getString("CityName"),
                    tag.getInt("TownPersonAmount"),
                    tag.getLong("MigratedAtEpochMillis"),
                    tag.getString("Outcome")
            );
        }
    }

    private final LinkedHashMap<String, Receipt> receipts = new LinkedHashMap<>();

    public static LegacySpawnBlockMigrationLedger get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LegacySpawnBlockMigrationLedger::new, LegacySpawnBlockMigrationLedger::load),
                DATA_NAME
        );
    }

    public static LegacySpawnBlockMigrationLedger load(CompoundTag tag, HolderLookup.Provider provider) {
        LegacySpawnBlockMigrationLedger ledger = new LegacySpawnBlockMigrationLedger();
        ListTag entries = tag.getList("Receipts", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            Receipt receipt = Receipt.fromTag(entries.getCompound(i));
            ledger.receipts.put(key(receipt.dimensionKey(), receipt.pos()), receipt);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (Receipt receipt : receipts.values()) {
            entries.add(receipt.toTag());
        }
        tag.put("Receipts", entries);
        return tag;
    }

    /** Records (or supersedes, for a retried migration at the same position) a receipt. */
    public void record(Receipt receipt) {
        receipts.put(key(receipt.dimensionKey(), receipt.pos()), receipt);
        setDirty();
    }

    @Nullable
    public Receipt find(String dimensionKey, BlockPos pos) {
        return receipts.get(key(dimensionKey, pos));
    }

    public List<Receipt> snapshot() {
        return new ArrayList<>(receipts.values());
    }

    public Map<String, Receipt> byPosition() {
        return Map.copyOf(receipts);
    }

    private static String key(String dimensionKey, BlockPos pos) {
        return dimensionKey + "@" + pos.asLong();
    }
}

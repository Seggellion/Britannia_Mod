package com.seggellion.britannia_mod.mining;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Which mineable blocks a player put there, so mining your own wall is not mining a mountain.
 *
 * <p>Mining milestone 7. Design §13 requires the place-break loop to stop being a Mining exploit,
 * and warns against inventing "a large new global block-provenance database". This is the smallest
 * model that works: <b>absence is the default</b>, so every naturally generated block in every
 * existing save is natural with no migration pass, and only the positions a player actually placed
 * a catalogued mineable at are stored. Entries are removed the moment such a block is broken, so
 * the set tracks standing player construction rather than growing forever.
 *
 * <p>This is provenance, not a scheduler: block restoration keeps its single existing timer in
 * {@code BlockRestoreHandler}. The nearest existing model, {@code GrabbyProvenance}, could not be
 * reused directly because it rides on a block entity and mineables are plain blocks — but its
 * principle is kept, inverted: Grabby defaults to protected-WORLD, Mining defaults to natural,
 * because natural blocks are the overwhelming majority and must cost nothing to represent.
 *
 * <p>Player-placed is judged by who placed it, not by game mode: a Creative-built granite wall is
 * still construction rather than a mineral deposit. World generation, structures, and restoration
 * itself never route through a player placement event, so they stay natural.
 */
public final class MiningProvenance extends SavedData {

    private static final String DATA_NAME = "britannia_mining_placed";
    private static final String POSITIONS_TAG = "placed";

    private final LongSet playerPlaced;

    private MiningProvenance() {
        this.playerPlaced = new LongOpenHashSet();
    }

    private MiningProvenance(CompoundTag tag) {
        this.playerPlaced = new LongOpenHashSet(tag.getLongArray(POSITIONS_TAG));
    }

    public static MiningProvenance get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MiningProvenance::new, (tag, provider) -> new MiningProvenance(tag)),
                DATA_NAME);
    }

    /** Records that a player placed a catalogued mineable here. */
    public static void markPlayerPlaced(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return;
        MiningProvenance provenance = get(level);
        if (provenance.playerPlaced.add(pos.asLong())) {
            provenance.setDirty();
        }
    }

    /** Forgets a position, called when the block there is broken so the set stays bounded. */
    public static void forget(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return;
        MiningProvenance provenance = get(level);
        if (provenance.playerPlaced.remove(pos.asLong())) {
            provenance.setDirty();
        }
    }

    /**
     * Whether Mining should treat this position as player construction rather than a natural
     * deposit. Unknown positions are natural, which is what keeps existing worlds working.
     */
    public static boolean isPlayerPlaced(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        return level != null && pos != null && get(level).playerPlaced.contains(pos.asLong());
    }

    /** Number of tracked positions in this level; diagnostics and tests only. */
    public static int trackedCount(ServerLevel level) {
        return get(level).playerPlaced.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLongArray(POSITIONS_TAG, playerPlaced.toLongArray());
        return tag;
    }
}

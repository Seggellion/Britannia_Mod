package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes Grabby provenance without callers needing to know which BlockEntity type they hold.
 *
 * <p>Everything that is not a {@link GrabbyProvenanceHolder} — a vanilla chest, a shrine anchor, a
 * door, air — reads as {@link GrabbyInstanceState#worldPlaced()}, i.e. protected. Failing closed is
 * the point: the only way for something to become movable is for the Grabby placement transaction to
 * have positively marked it.
 */
public final class GrabbyProvenanceAccess {
    private GrabbyProvenanceAccess() {
    }

    /** Never null. Returns the protected default for anything that cannot carry provenance. */
    public static GrabbyInstanceState read(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof GrabbyProvenanceHolder holder
                ? holder.grabbyState()
                : GrabbyInstanceState.worldPlaced();
    }

    /** Never null. Returns the protected default when the position holds no provenance-capable entity. */
    public static GrabbyInstanceState read(@Nullable BlockGetter level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return GrabbyInstanceState.worldPlaced();
        }
        return read(level.getBlockEntity(pos));
    }

    /**
     * Stamps provenance onto a block entity.
     *
     * @return {@code false} if the target cannot carry provenance, in which case nothing changed
     */
    public static boolean write(@Nullable BlockEntity blockEntity, GrabbyInstanceState state) {
        if (!(blockEntity instanceof GrabbyProvenanceHolder holder)) {
            return false;
        }
        holder.setGrabbyState(state);
        return true;
    }

    /** Convenience for the common "is this exact placed object Grabby-managed" question. */
    public static boolean grabbyManaged(@Nullable BlockGetter level, @Nullable BlockPos pos) {
        return read(level, pos).grabbyManaged();
    }
}

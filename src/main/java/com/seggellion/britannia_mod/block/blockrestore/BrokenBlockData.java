package com.seggellion.britannia_mod.blockrestore;

import com.seggellion.britannia_mod.resource.deposit.DepositInstance;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * One worked-out resource cell, waiting to come back.
 *
 * <h2>What milestone 4 added, and why</h2>
 * The record used to be a position, a state, a timestamp and a player. Everything else was
 * recomputed on every visit, by a loop that visited every debt in the world twenty times a second.
 *
 * <ul>
 *   <li>{@link #dueAt} is computed once, when the cell is worked, from the resource's own
 *       regeneration policy. Storing the answer rather than the inputs is what lets the scheduler
 *       order debts by time instead of asking each one whether it is ready yet.</li>
 *   <li>{@link #instanceId} and {@link #resourceId} say which deposit this cell belonged to.
 *       Both may be absent: a debt recorded before milestone 4, or one from a resource block that
 *       no deposit ever claimed, is genuinely unowned, and inventing an owner for it would be
 *       worse than admitting that.</li>
 *   <li>{@link #retryAt} and {@link #retryCount} are the backoff for a debt whose cell is blocked.
 *       Deliberately separate from {@link #dueAt}: being blocked by a puddle does not make a
 *       deposit economically younger, so the due moment never moves.</li>
 * </ul>
 *
 * <p>{@link #brokenTime} is kept even though {@link #dueAt} supersedes it operationally, because it
 * is the one piece of history the record carries and diagnostics report it.
 */
public class BrokenBlockData {

    /** The historical global delay, still the fallback for anything with no resource policy. */
    public static final long DEFAULT_RESTORE_DELAY = 6L * 60L * 60L * 1000L;

    public final BlockPos pos;
    public final BlockState originalState;
    public final long brokenTime;
    public final UUID playerUUID;

    /** When this cell is economically owed back. Never moved by a blocked retry. */
    public final long dueAt;
    /** The deposit that owned the cell, or {@link DepositInstance#NO_INSTANCE} when unowned. */
    public final long instanceId;
    /** The resource the cell held, or empty when unknown (legacy records). */
    public final String resourceId;
    /** Earliest moment a blocked debt should be tried again; 0 when it has never been blocked. */
    public final long retryAt;
    /** How many times restoration has found this cell blocked. */
    public final int retryCount;

    public BrokenBlockData(BlockPos pos, BlockState originalState, long brokenTime, UUID playerUUID) {
        this(pos, originalState, brokenTime, playerUUID,
                brokenTime + DEFAULT_RESTORE_DELAY, DepositInstance.NO_INSTANCE, "", 0L, 0);
    }

    public BrokenBlockData(
            BlockPos pos,
            BlockState originalState,
            long brokenTime,
            UUID playerUUID,
            long dueAt,
            long instanceId,
            String resourceId,
            long retryAt,
            int retryCount) {
        this.pos = pos;
        this.originalState = originalState;
        this.brokenTime = brokenTime;
        this.playerUUID = playerUUID;
        this.dueAt = dueAt;
        this.instanceId = instanceId;
        this.resourceId = resourceId == null ? "" : resourceId;
        this.retryAt = retryAt;
        this.retryCount = retryCount;
    }

    /** Whether any deposit claims this cell. Legacy and unadopted debts answer false. */
    public boolean owned() {
        return instanceId != DepositInstance.NO_INSTANCE;
    }

    /** The moment the scheduler may next look at this debt: due, or later if it has been blocked. */
    public long effectiveTime() {
        return Math.max(dueAt, retryAt);
    }

    /** A copy backed off after being found blocked. The economic due time is untouched. */
    public BrokenBlockData blockedUntil(long nextRetryAt) {
        return new BrokenBlockData(pos, originalState, brokenTime, playerUUID,
                dueAt, instanceId, resourceId, nextRetryAt, retryCount + 1);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.put("blockState", NbtUtils.writeBlockState(originalState));
        tag.putLong("brokenTime", brokenTime);
        tag.putUUID("playerUUID", playerUUID);
        tag.putLong("dueAt", dueAt);
        if (owned()) {
            tag.putLong("instance", instanceId);
        }
        if (!resourceId.isEmpty()) {
            tag.putString("resource", resourceId);
        }
        if (retryCount > 0) {
            tag.putLong("retryAt", retryAt);
            tag.putInt("retryCount", retryCount);
        }
        return tag;
    }

    /**
     * Read a schema-2 entry.
     *
     * <p>Every milestone-4 field is optional on the way in, so this also reads a legacy entry
     * without a special case — see {@code BrokenBlockDataStorage} for how a legacy file's missing
     * {@code dueAt} is reconstructed from the resource's policy rather than defaulted here.
     */
    public static BrokenBlockData fromNbt(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("blockState"));
        long time = tag.getLong("brokenTime");
        UUID playerUUID = tag.getUUID("playerUUID");
        long dueAt = tag.contains("dueAt") ? tag.getLong("dueAt") : time + DEFAULT_RESTORE_DELAY;
        long instance = tag.contains("instance") ? tag.getLong("instance") : DepositInstance.NO_INSTANCE;
        String resource = tag.contains("resource") ? tag.getString("resource") : "";
        long retryAt = tag.contains("retryAt") ? tag.getLong("retryAt") : 0L;
        int retryCount = tag.contains("retryCount") ? tag.getInt("retryCount") : 0;
        return new BrokenBlockData(pos, state, time, playerUUID, dueAt, instance, resource, retryAt, retryCount);
    }

    /** Rebuild with a due time the caller computed. Used by the legacy migration. */
    public BrokenBlockData withDueAt(long newDueAt) {
        return new BrokenBlockData(pos, originalState, brokenTime, playerUUID,
                newDueAt, instanceId, resourceId, retryAt, retryCount);
    }
}

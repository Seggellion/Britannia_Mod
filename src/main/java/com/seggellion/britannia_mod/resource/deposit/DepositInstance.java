package com.seggellion.britannia_mod.resource.deposit;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One deposit the world knows about: which one it is, what made it, and how far along it is.
 *
 * <h2>What is stored, and what deliberately is not</h2>
 * Not one standing cell. The geometry is reproducible from the resource definition plus the origin,
 * seed, radius and rotation kept here, so persisting coordinates would be storing a derivable thing
 * — and there are up to twenty thousand of them per deposit. What is kept is what cannot be
 * recomputed: which deposit this is, what it was made from, and what has actually happened to it.
 *
 * <h2>Immutable metadata versus progress</h2>
 * {@link #resourceId}, {@link #origin}, {@link #seed}, {@link #radius}, {@link #rotation} and
 * {@link #source} are the deposit's identity in fact as well as in name. Two registrations sharing
 * an id must agree on every one of them or the ledger refuses the second as a collision — see
 * {@link DepositLedger}.
 *
 * <p>{@link #materializedCells} and {@link #blockedCells} are progress, not identity, and they are
 * only meaningful after a <em>complete</em> materialisation pass. Milestone 3 made placement
 * budget-bounded and resumable, so a run that hit its budget has examined only part of the plan;
 * updating counts from it would be a guess. They stay {@link #UNKNOWN_COUNT} until a pass covers
 * the whole plan, and after such a pass they satisfy exactly
 * {@code materialized + blocked == planned} — every cell either holds the resource or could not.
 *
 * <p>Depleted cells are <b>not</b> a field. They are recomputed on demand by counting this
 * deposit's own restoration debts across the chunks its bounds touch, which is bounded by the
 * deposit's footprint rather than by the world's debt count. A cached counter would need
 * decrementing on extraction, restoration, admin removal and migration, and would drift the first
 * time one of those was missed.
 */
public record DepositInstance(
        long instanceId,
        String resourceId,
        int definitionRevision,
        DepositSource source,
        String sourceIdentity,
        BlockPos origin,
        long seed,
        int radius,
        ShapeRotation rotation,
        BlockPos boundsMin,
        BlockPos boundsMax,
        int plannedCells,
        int materializedCells,
        int blockedCells,
        int materializationVersion
) {

    /** No deposit owns this. Used by legacy and unowned restoration debts. */
    public static final long NO_INSTANCE = 0L;

    /** A count that has not been established by a complete materialisation pass. */
    public static final int UNKNOWN_COUNT = -1;

    /** Bumped when the materialisation pipeline changes in a way worth recording per deposit. */
    public static final int CURRENT_MATERIALIZATION_VERSION = 1;

    public DepositInstance {
        Objects.requireNonNull(resourceId, "resource id is required");
        Objects.requireNonNull(source, "deposit source is required");
        Objects.requireNonNull(sourceIdentity, "source identity is required");
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(rotation, "rotation is required");
        Objects.requireNonNull(boundsMin, "bounds are required");
        Objects.requireNonNull(boundsMax, "bounds are required");
        if (instanceId == NO_INSTANCE) {
            throw new IllegalArgumentException("zero is reserved for 'no deposit'");
        }
    }

    /**
     * Whether two registrations describe the same deposit.
     *
     * <p>Deliberately excludes {@link #definitionRevision}: a definition file changing is expected,
     * and a deposit does not stop being itself because of it. That case is reported rather than
     * refused — see {@link DepositLedger}.
     */
    public boolean sameDepositAs(DepositInstance other) {
        return instanceId == other.instanceId
                && resourceId.equals(other.resourceId)
                && source == other.source
                && origin.equals(other.origin)
                && seed == other.seed
                && radius == other.radius
                && rotation == other.rotation;
    }

    /** A one-line description of the immutable half, for collision reports. */
    public String describeIdentity() {
        return resourceId + " " + source.id() + " at " + origin.toShortString()
                + " r=" + radius + " " + rotation.id() + " seed=" + Long.toHexString(seed);
    }

    /** Whether a complete materialisation pass has established the progress counts. */
    public boolean progressKnown() {
        return materializedCells != UNKNOWN_COUNT;
    }

    /** Whether every planned cell that can hold the resource does. */
    public boolean fullyMaterialized() {
        return progressKnown() && materializedCells + blockedCells >= plannedCells;
    }

    public DepositInstance withProgress(int materialized, int blocked) {
        return new DepositInstance(instanceId, resourceId, definitionRevision, source, sourceIdentity,
                origin, seed, radius, rotation, boundsMin, boundsMax, plannedCells,
                materialized, blocked, materializationVersion);
    }

    /** Every chunk this deposit reaches, derived from bounds without reading the world. */
    public List<ChunkPos> touchedChunks() {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = boundsMin.getX() >> 4; x <= (boundsMax.getX() >> 4); x++) {
            for (int z = boundsMin.getZ() >> 4; z <= (boundsMax.getZ() >> 4); z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    public boolean containsPosition(BlockPos pos) {
        return pos.getX() >= boundsMin.getX() && pos.getX() <= boundsMax.getX()
                && pos.getY() >= boundsMin.getY() && pos.getY() <= boundsMax.getY()
                && pos.getZ() >= boundsMin.getZ() && pos.getZ() <= boundsMax.getZ();
    }

    /* ------------------------------------------------------------------ */
    /*  Serialization                                                      */
    /* ------------------------------------------------------------------ */

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", instanceId);
        tag.putString("resource", resourceId);
        tag.putInt("revision", definitionRevision);
        tag.putString("source", source.id());
        tag.putString("identity", sourceIdentity);
        tag.putLong("origin", origin.asLong());
        tag.putLong("seed", seed);
        tag.putInt("radius", radius);
        tag.putString("rotation", rotation.id());
        tag.putLong("boundsMin", boundsMin.asLong());
        tag.putLong("boundsMax", boundsMax.asLong());
        tag.putInt("planned", plannedCells);
        tag.putInt("materialized", materializedCells);
        tag.putInt("blocked", blockedCells);
        tag.putInt("materializationVersion", materializationVersion);
        return tag;
    }

    public static DepositInstance fromNbt(CompoundTag tag) {
        return new DepositInstance(
                tag.getLong("id"),
                tag.getString("resource"),
                tag.getInt("revision"),
                DepositSource.byId(tag.getString("source")).orElseThrow(() ->
                        new IllegalStateException("Unknown deposit source '" + tag.getString("source") + "'")),
                tag.getString("identity"),
                BlockPos.of(tag.getLong("origin")),
                tag.getLong("seed"),
                tag.getInt("radius"),
                ShapeRotation.byId(tag.getString("rotation")).orElseThrow(() ->
                        new IllegalStateException("Unknown rotation '" + tag.getString("rotation") + "'")),
                BlockPos.of(tag.getLong("boundsMin")),
                BlockPos.of(tag.getLong("boundsMax")),
                tag.getInt("planned"),
                tag.getInt("materialized"),
                tag.getInt("blocked"),
                tag.getInt("materializationVersion"));
    }
}

package com.seggellion.britannia_mod.resource.deposit;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * The one derivation from a deposit's source identity to its stable id and its planner seed.
 *
 * <h2>One contract, two values</h2>
 * Milestone 3's {@code DepositSeed} derived a planner seed and said so was temporary. This replaces
 * it outright rather than sitting beside it — there is exactly one place that turns "which deposit
 * is this" into a number, and both the instance id and the planner seed come out of it:
 *
 * <pre>
 *   source identity  ->  canonical encoding (versioned string)  ->  instanceId
 *   instanceId       ->  plannerSeed
 * </pre>
 *
 * <p>{@link #plannerSeed} is deliberately identical to the instance id today. Keeping them as two
 * named concepts rather than one means they can diverge later — if a definition revision ever needs
 * to re-roll geometry without changing identity — without anything having to be redesigned to allow
 * it.
 *
 * <h2>Canonical encoding</h2>
 * The encoding is an explicit versioned string, built here and nowhere else. It is never an
 * object's incidental {@code toString()}: a record's generated formatting is not a serialization
 * contract, and a field reordering would silently change every id in every world. {@link #VERSION}
 * exists so that if the encoding ever must change, the change is deliberate and detectable rather
 * than a surprise. Every component is normalised — lower-cased, null-collapsed to empty — so that
 * two spellings of the same row cannot become two deposits.
 *
 * <h2>What is never in an identity</h2>
 * Command execution time, current server time, a fresh {@code UUID} per import, and any mutable
 * materialisation state. Those are exactly what made {@code /populateores} produce a different vein
 * on every run before milestone 3.
 */
public final class DepositIdentity {

    /** Bump only for a deliberate, breaking change to the canonical encoding. */
    public static final int VERSION = 1;

    private DepositIdentity() {
    }

    /* ------------------------------------------------------------------ */
    /*  Natural                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * A deposit world generation would place, keyed to the world rather than to any command.
     *
     * <p>Natural generation itself is not implemented — that is milestone 5 onward — but the rule
     * is defined and tested now so that when it arrives it inherits an identity contract instead of
     * inventing one. The owner cell is the deterministic grid cell the deposit belongs to, not its
     * exact origin: two runs of the same generator over the same cell must agree even if the origin
     * within the cell is itself derived.
     */
    public static String naturalEncoding(
            long worldSeed, String dimensionId, String resourceId, int cellX, int cellZ, int salt) {
        return encode(DepositSource.NATURAL,
                Long.toString(worldSeed),
                normalise(dimensionId),
                normalise(resourceId),
                cellX + "," + cellZ,
                Integer.toString(salt));
    }

    public static long natural(
            long worldSeed, String dimensionId, String resourceId, int cellX, int cellZ, int salt) {
        return hash64(naturalEncoding(worldSeed, dimensionId, resourceId, cellX, cellZ, salt));
    }

    /* ------------------------------------------------------------------ */
    /*  Rails                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * A curated Rails vein row.
     *
     * <p><b>Fallback contract.</b> Milestone 0 established that the Rails {@code ore_veins} payload
     * carries no stable row id. Until it does, identity is the row's immutable content: the shard
     * and dimension it belongs to, the resource, the origin, the size, the orientation and the
     * region. That is enough for the same row to derive the same id after any restart, which is
     * what idempotent import needs.
     *
     * <p>It has one known consequence, and it is a property of the fallback rather than of the
     * platform: <b>editing a row's position, radius or rotation makes it a different deposit.</b>
     * The old one stays in the ledger and the new one registers beside it. When Rails supplies a
     * stable id, it replaces every component below except the dimension, and moving a curated vein
     * will keep its identity instead.
     */
    public static String railsEncoding(
            String shard,
            String dimensionId,
            String resourceId,
            int x, int y, int z,
            int radius,
            ShapeRotation rotation,
            String region) {
        return encode(DepositSource.RAILS,
                normalise(shard),
                normalise(dimensionId),
                normalise(resourceId),
                x + "," + y + "," + z,
                Integer.toString(radius),
                rotation == null ? "" : rotation.id(),
                normalise(region));
    }

    public static long rails(
            String shard,
            String dimensionId,
            String resourceId,
            int x, int y, int z,
            int radius,
            ShapeRotation rotation,
            String region) {
        return hash64(railsEncoding(shard, dimensionId, resourceId, x, y, z, radius, rotation, region));
    }

    /* ------------------------------------------------------------------ */
    /*  Admin                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * An operator-placed deposit, whose id is generated once and then persisted.
     *
     * <p>There is nothing immutable to derive from — an operator can put the same deposit anywhere,
     * twice, and mean two deposits — so the id is minted at creation and the ledger remembers it.
     * The encoding still records what it was minted from, so a collision can be explained.
     */
    public static String adminEncoding(UUID minted, String dimensionId, String resourceId) {
        return encode(DepositSource.ADMIN,
                minted.toString(),
                normalise(dimensionId),
                normalise(resourceId));
    }

    public static long admin(UUID minted, String dimensionId, String resourceId) {
        return hash64(adminEncoding(minted, dimensionId, resourceId));
    }

    /**
     * A World Admin Map operation approved for one Rails ResourceDeposit revision.
     *
     * <p>The operation UUID is the retry identity. The Rails UUID and revision are retained in
     * the same canonical source identity so a persisted {@code DepositInstance} remains directly
     * explainable and correlatable after restart. This extends the existing ADMIN identity
     * contract; it does not add another ledger or deposit model.
     */
    public static String worldAdminEncoding(
            UUID operationUuid, UUID resourceDepositUuid, long revision,
            String dimensionId, String resourceId) {
        return encode(DepositSource.ADMIN,
                "world_admin_map",
                operationUuid.toString(),
                resourceDepositUuid.toString(),
                Long.toString(revision),
                normalise(dimensionId),
                normalise(resourceId));
    }

    public static long worldAdmin(UUID operationUuid, UUID resourceDepositUuid, long revision,
                                  String dimensionId, String resourceId) {
        return hash64(worldAdminEncoding(operationUuid, resourceDepositUuid, revision,
                dimensionId, resourceId));
    }

    /* ------------------------------------------------------------------ */
    /*  Retrofit                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * A deposit adopted from existing terrain by a migration batch.
     *
     * <p>Defined and tested now as a future contract; the retrofit framework itself is milestone 8.
     * Keyed to the batch so that re-running the same migration adopts the same terrain as the same
     * deposits rather than as new ones.
     */
    public static String retrofitEncoding(
            String batchId, String dimensionId, String resourceId, int x, int y, int z) {
        return encode(DepositSource.RETROFIT,
                normalise(batchId),
                normalise(dimensionId),
                normalise(resourceId),
                x + "," + y + "," + z);
    }

    public static long retrofit(
            String batchId, String dimensionId, String resourceId, int x, int y, int z) {
        return hash64(retrofitEncoding(batchId, dimensionId, resourceId, x, y, z));
    }

    /* ------------------------------------------------------------------ */
    /*  Derivation                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * The seed the shape planner is given for this deposit.
     *
     * <p>Identical to the instance id today. Named separately because they are different ideas:
     * the id says which deposit this is, the seed says what it looks like. A future revision policy
     * that re-rolled geometry would change one and not the other.
     */
    public static long plannerSeed(long instanceId) {
        return instanceId;
    }

    /** The canonical encoding, assembled in one place so its shape is a contract. */
    private static String encode(DepositSource source, String... components) {
        StringBuilder builder = new StringBuilder(64);
        builder.append('v').append(VERSION).append('|').append(source.id());
        for (String component : components) {
            builder.append('|').append(component);
        }
        return builder.toString();
    }

    /** Null collapses to empty and case is flattened, so one row cannot spell two identities. */
    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * FNV-1a over UTF-8, written out rather than delegated.
     *
     * <p>{@code String.hashCode} is 32 bits and clusters; a library hash could change between
     * versions. These ids name persistent world objects, so the function has to be one that cannot
     * drift underneath them.
     */
    public static long hash64(String canonical) {
        final long offsetBasis = 0xCBF29CE484222325L;
        final long prime = 0x100000001B3L;
        long hash = offsetBasis;
        for (byte b : canonical.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= prime;
        }
        // Zero is reserved to mean "no deposit", so a canonical string that happened to hash to it
        // is nudged rather than allowed to look like an absent owner.
        return hash == DepositInstance.NO_INSTANCE ? 1L : hash;
    }
}

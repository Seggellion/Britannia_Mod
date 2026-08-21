package com.seggellion.britannia_mod.resource.deposit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every deposit this dimension knows about.
 *
 * <h2>Registration is not "skip if seen"</h2>
 * Milestone 3 made materialisation budget-bounded and resumable, so a deposit can legitimately be
 * half-written. A ledger that answered "this id exists, do nothing" would strand every one of them
 * permanently. So registration has three outcomes, and the difference between the second and the
 * third is the whole point:
 *
 * <ul>
 *   <li><b>{@code REGISTERED}</b> — a new id. Recorded; materialisation proceeds.</li>
 *   <li><b>{@code ALREADY_REGISTERED}</b> — the id exists and every piece of immutable metadata
 *       agrees. The <em>same deposit</em>. Materialisation proceeds, which is how a partial deposit
 *       resumes and a complete one becomes a no-op. Nothing is rerolled and no second instance is
 *       created.</li>
 *   <li><b>{@code CONFLICT}</b> — the id exists and the metadata disagrees. Two different deposits
 *       have collided on one identity, which should be impossible and means something is wrong with
 *       the derivation or the data. Refused loudly, with both descriptions, and nothing is
 *       mutated.</li>
 *   <li><b>{@code REVISION_MISMATCH}</b> — the same deposit, but recorded under a different
 *       resource-definition revision. Its identity is kept and materialisation is refused, because
 *       continuing would write a deposit half in one shape and half in another. Reconciliation is
 *       milestone 8's; this milestone's job is to notice.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 * Indexed by id for registration and by chunk for "which deposit owns this position". The chunk
 * index is rebuilt in memory from each instance's bounds on load rather than persisted — it is
 * derivable, and persisting it would be a second thing to keep in step.
 */
public class DepositLedger extends SavedData {

    private static final String DATA_NAME = "britannia_deposits";
    /** Bump only for a breaking change to this file's layout. */
    public static final int SCHEMA = 1;

    private final Map<Long, DepositInstance> byId = new LinkedHashMap<>();
    private final Map<ChunkPos, List<Long>> byChunk = new HashMap<>();

    public DepositLedger() {
        super();
    }

    public DepositLedger(CompoundTag tag, HolderLookup.Provider provider) {
        super();
        int schema = tag.contains("schema") ? tag.getInt("schema") : SCHEMA;
        if (schema != SCHEMA) {
            throw new IllegalStateException("Unsupported deposit ledger schema " + schema);
        }
        ListTag list = tag.getList("deposits", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            DepositInstance instance = DepositInstance.fromNbt((CompoundTag) entry);
            byId.put(instance.instanceId(), instance);
            index(instance);
        }
    }

    public static DepositLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DepositLedger::new, DepositLedger::new), DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("schema", SCHEMA);
        ListTag list = new ListTag();
        for (DepositInstance instance : byId.values()) {
            list.add(instance.toNbt());
        }
        tag.put("deposits", list);
        return tag;
    }

    /* ------------------------------------------------------------------ */
    /*  Registration                                                       */
    /* ------------------------------------------------------------------ */

    public enum Outcome {
        REGISTERED,
        ALREADY_REGISTERED,
        CONFLICT,
        REVISION_MISMATCH
    }

    /**
     * The answer, and enough of the reason to act on it.
     *
     * @param instance the instance now in the ledger — the existing one when it was already there
     * @param message  populated for refusals, naming both sides of the disagreement
     */
    public record Registration(Outcome outcome, DepositInstance instance, String message) {

        /** Whether materialisation may proceed for this deposit. */
        public boolean mayMaterialize() {
            return outcome == Outcome.REGISTERED || outcome == Outcome.ALREADY_REGISTERED;
        }
    }

    /** Record this deposit, or explain why it cannot be recorded as described. */
    public Registration register(DepositInstance candidate) {
        DepositInstance existing = byId.get(candidate.instanceId());
        if (existing == null) {
            byId.put(candidate.instanceId(), candidate);
            index(candidate);
            setDirty();
            return new Registration(Outcome.REGISTERED, candidate, "");
        }
        if (!existing.sameDepositAs(candidate)) {
            return new Registration(Outcome.CONFLICT, existing,
                    "deposit id " + Long.toHexString(candidate.instanceId())
                            + " already describes " + existing.describeIdentity()
                            + ", but was offered " + candidate.describeIdentity()
                            + "; refusing rather than overwriting one deposit with another");
        }
        if (existing.definitionRevision() != candidate.definitionRevision()) {
            return new Registration(Outcome.REVISION_MISMATCH, existing,
                    "deposit " + Long.toHexString(existing.instanceId()) + " (" + existing.resourceId()
                            + ") was created under definition revision " + existing.definitionRevision()
                            + " but the catalogue now says revision " + candidate.definitionRevision()
                            + "; its identity is kept and it is not regenerated. Reconciling a "
                            + "revision change is milestone 8's, not something to do silently");
        }
        return new Registration(Outcome.ALREADY_REGISTERED, existing, "");
    }

    /** Record what a complete materialisation pass established about a deposit's progress. */
    public void recordProgress(long instanceId, int materialized, int blocked) {
        DepositInstance existing = byId.get(instanceId);
        if (existing == null) return;
        byId.put(instanceId, existing.withProgress(materialized, blocked));
        setDirty();
    }

    /* ------------------------------------------------------------------ */
    /*  Lookup                                                             */
    /* ------------------------------------------------------------------ */

    public Optional<DepositInstance> byId(long instanceId) {
        return Optional.ofNullable(byId.get(instanceId));
    }

    public Collection<DepositInstance> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /**
     * The deposit whose bounds contain this position and which governs this resource.
     *
     * <p>Bounded by the chunk index, so it costs the number of deposits reaching into one chunk
     * rather than the number of deposits in the dimension. Bounds containment is necessary but not
     * sufficient — two deposits' boxes may overlap — so the resource has to match too. When more
     * than one still qualifies the first is taken and the ambiguity is not hidden from diagnostics;
     * an exact answer would need the standing-cell list this ledger deliberately does not keep.
     */
    public Optional<DepositInstance> owning(BlockPos pos, String resourceId) {
        List<Long> candidates = byChunk.get(new ChunkPos(pos));
        if (candidates == null) return Optional.empty();
        for (long id : candidates) {
            DepositInstance instance = byId.get(id);
            if (instance != null
                    && instance.resourceId().equals(resourceId)
                    && instance.containsPosition(pos)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    private void index(DepositInstance instance) {
        for (ChunkPos chunk : instance.touchedChunks()) {
            byChunk.computeIfAbsent(chunk, key -> new ArrayList<>()).add(instance.instanceId());
        }
    }
}

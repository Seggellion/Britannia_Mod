package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Schema-versioned, per-instance Grabby Hands provenance, stored on the object's own BlockEntity.
 *
 * <p>Modelled on {@code farming.FlowerPersistentState}, which solves the same problem for planted
 * flowers.
 *
 * <h2>The mark is positive, so no migration exists</h2>
 *
 * <p>{@link #read(CompoundTag)} returns {@link #worldPlaced()} whenever the {@value #TAG_KEY} key is
 * absent. Every chair, table, bottle and chest already saved in every existing world therefore
 * resolves to {@link GrabbyProvenance#WORLD} and is protected from day one. Nothing is scanned,
 * rewritten, or versioned up, and a {@code WORLD} value is never written by the placement path — it
 * exists only as the decode-time default and for a possible future admin "pin this object" command.
 *
 * <h2>{@code placerUuid} is for mobility only</h2>
 *
 * <p>It is read by {@link GrabbyPolicy} and by logging. It is never consulted by any
 * {@code useWithoutItem} / {@code useItemOn} path: placing a chair does not make it your chair to
 * sit in. See {@link GrabbyPolicy} for the enforcement side of that rule.
 *
 * <p>{@link #toClientTag()} omits it entirely, so placement provenance is not broadcast to every
 * client that loads the chunk.
 */
public record GrabbyInstanceState(
        int schemaVersion,
        GrabbyProvenance provenance,
        Optional<UUID> placerUuid,
        long placedAtGameTime
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** The single NBT key this state occupies inside a host BlockEntity's tag. */
    public static final String TAG_KEY = "GrabbyState";

    private static final String FIELD_SCHEMA_VERSION = "SchemaVersion";
    private static final String FIELD_PROVENANCE = "Provenance";
    private static final String FIELD_PLACER_UUID = "PlacerUuid";
    private static final String FIELD_PLACED_AT = "PlacedAtGameTime";

    private static final GrabbyInstanceState WORLD_PLACED =
            new GrabbyInstanceState(CURRENT_SCHEMA_VERSION, GrabbyProvenance.WORLD, Optional.empty(), 0L);

    public GrabbyInstanceState {
        Objects.requireNonNull(provenance, "provenance");
        placerUuid = Objects.requireNonNull(placerUuid, "placerUuid");
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Grabby Hands schema version: " + schemaVersion);
        }
        if (placedAtGameTime < 0L) {
            throw new IllegalArgumentException("Placement game time cannot be negative: " + placedAtGameTime);
        }
        // WORLD provenance must never carry a placer. The converse is deliberately NOT an invariant:
        // toClientTag() redacts the UUID from a genuinely PLAYER-placed object, and the client has to
        // be able to decode that redacted form without it blowing up.
        if (provenance == GrabbyProvenance.WORLD && placerUuid.isPresent()) {
            throw new IllegalArgumentException("World-provenance objects cannot record a placer");
        }
    }

    /** The protected default: what any object with no Grabby mark resolves to. */
    public static GrabbyInstanceState worldPlaced() {
        return WORLD_PLACED;
    }

    /** The only state the placement transaction is allowed to stamp. */
    public static GrabbyInstanceState playerPlaced(UUID placerUuid, long placedAtGameTime) {
        return new GrabbyInstanceState(
                CURRENT_SCHEMA_VERSION,
                GrabbyProvenance.PLAYER,
                Optional.of(Objects.requireNonNull(placerUuid, "placerUuid")),
                placedAtGameTime);
    }

    /**
     * Reads Grabby state out of a host BlockEntity tag.
     *
     * <p>Absent, malformed, or future-versioned data all resolve to {@link #worldPlaced()} — the
     * protected default. Corrupt data must never be able to make an object movable.
     */
    public static GrabbyInstanceState read(CompoundTag hostTag) {
        if (hostTag == null || !hostTag.contains(TAG_KEY)) {
            return worldPlaced();
        }
        CompoundTag tag = hostTag.getCompound(TAG_KEY);
        int schemaVersion = tag.getInt(FIELD_SCHEMA_VERSION);
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            return worldPlaced();
        }
        GrabbyProvenance provenance = GrabbyProvenance.byNameOrWorld(tag.getString(FIELD_PROVENANCE));
        if (provenance == GrabbyProvenance.WORLD) {
            return worldPlaced();
        }
        Optional<UUID> placerUuid = tag.hasUUID(FIELD_PLACER_UUID)
                ? Optional.of(tag.getUUID(FIELD_PLACER_UUID))
                : Optional.empty();
        long placedAt = Math.max(0L, tag.getLong(FIELD_PLACED_AT));
        return new GrabbyInstanceState(schemaVersion, provenance, placerUuid, placedAt);
    }

    /**
     * Writes this state into a host BlockEntity tag under {@value #TAG_KEY}.
     *
     * <p>World provenance writes nothing, keeping the mark strictly positive and leaving untouched
     * saves byte-identical.
     */
    public void write(CompoundTag hostTag) {
        Objects.requireNonNull(hostTag, "hostTag");
        if (provenance == GrabbyProvenance.WORLD) {
            hostTag.remove(TAG_KEY);
            return;
        }
        hostTag.put(TAG_KEY, toTag());
    }

    /** Writes the client-safe form: same as {@link #write(CompoundTag)} but without the placer UUID. */
    public void writeClient(CompoundTag hostTag) {
        Objects.requireNonNull(hostTag, "hostTag");
        if (provenance == GrabbyProvenance.WORLD) {
            hostTag.remove(TAG_KEY);
            return;
        }
        hostTag.put(TAG_KEY, toClientTag());
    }

    public CompoundTag toTag() {
        return toTag(true);
    }

    /** Client sync form. Placement provenance is not something every nearby client needs to know. */
    public CompoundTag toClientTag() {
        return toTag(false);
    }

    private CompoundTag toTag(boolean includePlacerUuid) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FIELD_SCHEMA_VERSION, schemaVersion);
        tag.putString(FIELD_PROVENANCE, provenance.name());
        tag.putLong(FIELD_PLACED_AT, placedAtGameTime);
        if (includePlacerUuid) {
            placerUuid.ifPresent(uuid -> tag.putUUID(FIELD_PLACER_UUID, uuid));
        }
        return tag;
    }

    /**
     * Whether this instance is a Grabby-managed player possession at all.
     *
     * <p>Necessary but not sufficient for movement — {@link GrabbyPolicy} still applies region,
     * house and reason checks on top.
     */
    public boolean grabbyManaged() {
        return provenance.playerPlaced();
    }
}

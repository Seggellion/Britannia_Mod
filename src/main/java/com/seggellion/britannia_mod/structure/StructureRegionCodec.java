package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Housing Deed Milestone 2: the one place that knows how a house region is written down.
 *
 * <p>{@link StructureRegionManager} is an in-memory map. Nothing saved it and nothing loaded
 * it, so on every restart every region vanished -- and with it the owner's right to break
 * blocks under their own house, and every door's lock identity, which is what left doors
 * permanently locked. This codec is the format that survives the restart.
 *
 * <p>It sits between {@link StructureRecord} and the {@code structure} jsonb column on the
 * Rails house row. Rails stores the object verbatim and does not read inside it, so the shape
 * is owned here.
 *
 * <p><b>Boxes are stored, not recomputed.</b> Origin, rotation, style and size are all present
 * and the boxes could be derived from them, but the derived value is not what the region was.
 * A player has been living inside the box that was computed at placement time; if
 * {@code StructureUtils.makeStructureBoxes} is ever corrected, recomputing on rehydrate would
 * silently move the walls of every house already standing. Storing the box means a region
 * comes back as the region that was there, and the inputs are kept beside it so a future
 * migration can tell the two apart.
 */
public final class StructureRegionCodec {

    /** Bump when the shape changes incompatibly; decode refuses anything it does not know. */
    public static final int SCHEMA_VERSION = 1;

    private StructureRegionCodec() {}

    /* ------------------------------------------------------------------ */
    /*  Encode                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * The {@code structure} object for a Rails house row.
     *
     * @param origin the un-rotated template origin the structure was placed at; kept for
     *               provenance and for any future re-derivation, never read back by decode
     */
    public static JsonObject encode(StructureRecord record, BlockPos origin) {
        JsonObject structure = new JsonObject();
        structure.addProperty("schema", SCHEMA_VERSION);
        structure.addProperty("rotation_deg", record.getRotationDeg());
        structure.addProperty("style", record.getStyleId());
        structure.addProperty("size_id", record.getSizeId());
        structure.add("origin", encodePos(origin));
        structure.add("structure_box", encodeBox(record.getStructureBox()));
        structure.add("full_box", encodeBox(record.getFullBox()));
        return structure;
    }

    private static JsonObject encodePos(BlockPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    private static JsonObject encodeBox(AABB box) {
        JsonObject json = new JsonObject();
        json.addProperty("min_x", box.minX);
        json.addProperty("min_y", box.minY);
        json.addProperty("min_z", box.minZ);
        json.addProperty("max_x", box.maxX);
        json.addProperty("max_y", box.maxY);
        json.addProperty("max_z", box.maxZ);
        return json;
    }

    /* ------------------------------------------------------------------ */
    /*  Decode                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Rebuild a record from one row of the Rails houses index.
     *
     * <p>Returns empty rather than throwing for every recoverable shortfall -- a house placed
     * before this milestone has no {@code structure} at all, and one bad row must not cost the
     * shard the other three hundred. The caller counts what it skipped and reports it once.
     */
    public static Optional<StructureRecord> decode(JsonObject houseRow) {
        if (houseRow == null) return Optional.empty();

        UUID houseUuid = uuidOrNull(houseRow, "uuid");
        UUID ownerUuid = uuidOrNull(houseRow, "owner_uuid");
        if (houseUuid == null || ownerUuid == null) return Optional.empty();

        JsonObject structure = objectOrNull(houseRow, "structure");
        if (structure == null) return Optional.empty();
        if (intOrDefault(structure, "schema", -1) != SCHEMA_VERSION) return Optional.empty();

        AABB structureBox = decodeBox(objectOrNull(structure, "structure_box"));
        AABB fullBox      = decodeBox(objectOrNull(structure, "full_box"));
        if (structureBox == null || fullBox == null) return Optional.empty();

        return Optional.of(new StructureRecord(
                ownerUuid,
                structureBox,
                fullBox,
                houseUuid,
                stringOrDefault(structure, "size_id", "unknown"),
                stringOrDefault(structure, "style", "unknown"),
                uuidOrNull(houseRow, "deed_id"),
                intOrDefault(structure, "rotation_deg", StructureRecord.ROTATION_UNKNOWN)
        ));
    }

    @Nullable
    private static AABB decodeBox(@Nullable JsonObject json) {
        if (json == null) return null;
        for (String key : new String[] { "min_x", "min_y", "min_z", "max_x", "max_y", "max_z" }) {
            if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        }
        return new AABB(
                json.get("min_x").getAsDouble(),
                json.get("min_y").getAsDouble(),
                json.get("min_z").getAsDouble(),
                json.get("max_x").getAsDouble(),
                json.get("max_y").getAsDouble(),
                json.get("max_z").getAsDouble()
        );
    }

    /* ------------------------------------------------------------------ */
    /*  Json helpers -- tolerant by design, see decode                     */
    /* ------------------------------------------------------------------ */

    @Nullable
    private static JsonObject objectOrNull(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static UUID uuidOrNull(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return null;
        try {
            return UUID.fromString(element.getAsString());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String stringOrDefault(JsonObject parent, String key, String fallback) {
        if (parent == null || !parent.has(key)) return fallback;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int intOrDefault(JsonObject parent, String key, int fallback) {
        if (parent == null || !parent.has(key)) return fallback;
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsInt();
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}

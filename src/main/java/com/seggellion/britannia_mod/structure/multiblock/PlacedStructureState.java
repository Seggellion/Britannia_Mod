package com.seggellion.britannia_mod.structure.multiblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;

/** Schema-versioned, anchor-owned logical structure instance and exact ordered placed footprint. */
public record PlacedStructureState(
        int schemaVersion,
        FamilyId familyId,
        VariantId variantId,
        Direction facing,
        List<LocalOffset> footprint) {
    private static final Codec<String> ID_CODEC = Codec.STRING.validate(value ->
            value == null || value.isBlank()
                    ? DataResult.error(() -> "Stable structure IDs must not be blank")
                    : DataResult.success(value));
    private static final Codec<Direction> HORIZONTAL_FACING_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                Direction direction = Direction.byName(value);
                return direction != null && direction.getAxis().isHorizontal()
                        ? DataResult.success(direction)
                        : DataResult.error(() -> "Facing must be horizontal: " + value);
            }, Direction::getName);
    private static final Codec<LocalOffset> OFFSET_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, StructureGeometry.MAX_LOCAL_X).fieldOf("x").forGetter(LocalOffset::x),
            Codec.intRange(0, StructureGeometry.MAX_LOCAL_Y).fieldOf("y").forGetter(LocalOffset::y),
            Codec.intRange(0, StructureGeometry.MAX_LOCAL_Z).fieldOf("z").forGetter(LocalOffset::z)
    ).apply(instance, LocalOffset::new));
    private static final Codec<Decoded> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(Decoded::schemaVersion),
            ID_CODEC.fieldOf("family_id").forGetter(Decoded::familyId),
            ID_CODEC.fieldOf("variant_id").forGetter(Decoded::variantId),
            HORIZONTAL_FACING_CODEC.fieldOf("facing").forGetter(Decoded::facing),
            OFFSET_CODEC.listOf(4, 18).fieldOf("placed_footprint").forGetter(Decoded::footprint)
    ).apply(instance, Decoded::new));
    public static final Codec<PlacedStructureState> CODEC = RAW_CODEC.flatXmap(
            PlacedStructureState::decode, state -> DataResult.success(Decoded.from(state)));

    public PlacedStructureState(
            FamilyId familyId, VariantId variantId, Direction facing, List<LocalOffset> footprint) {
        this(ShrineItemState.CURRENT_SCHEMA_VERSION, familyId, variantId, facing, footprint);
    }

    public PlacedStructureState {
        if (schemaVersion != ShrineItemState.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema_version " + schemaVersion);
        }
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(facing, "facing");
        footprint = List.copyOf(Objects.requireNonNull(footprint, "footprint"));
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Large structures require a horizontal facing");
        }
        validateFootprint(footprint);
    }

    public boolean contains(LocalOffset offset) {
        return footprint.contains(offset);
    }

    public static boolean isStructurallyValidFootprint(List<LocalOffset> footprint) {
        try {
            validateFootprint(footprint);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void validateFootprint(List<LocalOffset> footprint) {
        if ((footprint.size() != 4 && footprint.size() != 18)
                || !footprint.getFirst().equals(LocalOffset.ANCHOR)
                || footprint.stream().filter(LocalOffset.ANCHOR::equals).count() != 1
                || new HashSet<>(footprint).size() != footprint.size()) {
            throw new IllegalArgumentException(
                    "Placed structure footprint must contain four or eighteen ordered unique cells beginning with anchor");
        }
        for (LocalOffset offset : footprint) {
            Objects.requireNonNull(offset, "footprint offset");
            if (offset.x() < 0 || offset.x() > StructureGeometry.MAX_LOCAL_X
                    || offset.y() < 0 || offset.y() > StructureGeometry.MAX_LOCAL_Y
                    || offset.z() < 0 || offset.z() > StructureGeometry.MAX_LOCAL_Z) {
                throw new IllegalArgumentException("Placed footprint offset is not encodable: " + offset);
            }
        }
    }

    private static DataResult<PlacedStructureState> decode(Decoded decoded) {
        try {
            return DataResult.success(new PlacedStructureState(
                    decoded.schemaVersion(), new FamilyId(decoded.familyId()), new VariantId(decoded.variantId()),
                    decoded.facing(), decoded.footprint()));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Decoded(
            int schemaVersion, String familyId, String variantId,
            Direction facing, List<LocalOffset> footprint) {
        private static Decoded from(PlacedStructureState state) {
            return new Decoded(state.schemaVersion(), state.familyId().value(), state.variantId().value(),
                    state.facing(), state.footprint());
        }
    }
}

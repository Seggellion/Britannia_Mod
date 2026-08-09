package com.seggellion.britannia_mod.dye.palette;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public record MaterialPalette(
        int schemaVersion,
        ResourceLocation id,
        FabricMaterialId materialId,
        ResolvedColourId naturalColourId,
        List<MaterialPaletteEntry> entries,
        Map<PigmentId, ResolvedColourId> pigmentOverrides) {
    private static final Codec<Decoded> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(Decoded::schemaVersion),
            ResourceLocation.CODEC.fieldOf("id").forGetter(Decoded::id),
            FabricMaterialId.CODEC.fieldOf("material_id").forGetter(Decoded::materialId),
            ResolvedColourId.CODEC.fieldOf("natural_colour_id").forGetter(Decoded::naturalColourId),
            MaterialPaletteEntry.CODEC.listOf(1, 512).fieldOf("entries").forGetter(Decoded::entries),
            Codec.unboundedMap(PigmentId.CODEC, ResolvedColourId.CODEC).fieldOf("pigment_overrides")
                    .forGetter(Decoded::pigmentOverrides)
    ).apply(instance, Decoded::new));
    public static final Codec<MaterialPalette> CODEC = RAW_CODEC.flatXmap(MaterialPalette::decode,
            palette -> DataResult.success(Decoded.from(palette)));

    public MaterialPalette {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(naturalColourId, "naturalColourId");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        validateEntries(naturalColourId, entries, pigmentOverrides);
        pigmentOverrides = immutableSortedOverrides(pigmentOverrides);
    }

    private static DataResult<MaterialPalette> decode(Decoded decoded) {
        try {
            return DataResult.success(new MaterialPalette(decoded.schemaVersion, decoded.id, decoded.materialId,
                    decoded.naturalColourId, decoded.entries, decoded.pigmentOverrides));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validateEntries(
            ResolvedColourId naturalColourId,
            List<MaterialPaletteEntry> entries,
            Map<PigmentId, ResolvedColourId> pigmentOverrides) {
        Objects.requireNonNull(pigmentOverrides, "pigmentOverrides");
        Set<ResolvedColourId> ids = entries.stream().map(MaterialPaletteEntry::id).collect(Collectors.toSet());
        if (ids.size() != entries.size()) {
            throw new IllegalArgumentException("Palette entry IDs must be unique");
        }
        if (!ids.contains(naturalColourId)) {
            throw new IllegalArgumentException("naturalColourId must exist in entries");
        }
        List<ResolvedColourId> missingTargets = pigmentOverrides.values().stream()
                .filter(target -> !ids.contains(target))
                .distinct()
                .toList();
        if (!missingTargets.isEmpty()) {
            throw new IllegalArgumentException("Pigment overrides target missing palette entries: " + missingTargets);
        }
    }

    private static Map<PigmentId, ResolvedColourId> immutableSortedOverrides(
            Map<PigmentId, ResolvedColourId> overrides) {
        List<Map.Entry<PigmentId, ResolvedColourId>> sorted = new ArrayList<>(overrides.entrySet());
        sorted.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        Map<PigmentId, ResolvedColourId> ordered = new LinkedHashMap<>();
        sorted.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    private record Decoded(
            int schemaVersion,
            ResourceLocation id,
            FabricMaterialId materialId,
            ResolvedColourId naturalColourId,
            List<MaterialPaletteEntry> entries,
            Map<PigmentId, ResolvedColourId> pigmentOverrides) {
        private static Decoded from(MaterialPalette palette) {
            return new Decoded(palette.schemaVersion, palette.id, palette.materialId, palette.naturalColourId,
                    palette.entries, palette.pigmentOverrides);
        }
    }
}

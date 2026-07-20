package com.seggellion.britannia_mod.dye.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record FabricMaterialDefinition(
        int schemaVersion,
        FabricMaterialId id,
        String displayNameKey,
        ResolvedColourId naturalColourId,
        ResourceLocation paletteId,
        List<String> tags) {
    public static final Codec<FabricMaterialDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version")
                    .forGetter(FabricMaterialDefinition::schemaVersion),
            FabricMaterialId.CODEC.fieldOf("id").forGetter(FabricMaterialDefinition::id),
            DataCodecs.NON_BLANK_STRING.fieldOf("display_name_key")
                    .forGetter(FabricMaterialDefinition::displayNameKey),
            ResolvedColourId.CODEC.fieldOf("natural_colour_id")
                    .forGetter(FabricMaterialDefinition::naturalColourId),
            ResourceLocation.CODEC.fieldOf("palette_id").forGetter(FabricMaterialDefinition::paletteId),
            DataCodecs.TAGS.fieldOf("tags").forGetter(FabricMaterialDefinition::tags)
    ).apply(instance, FabricMaterialDefinition::new));

    public FabricMaterialDefinition {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        displayNameKey = DataCodecs.requireNonBlank(displayNameKey, "displayNameKey");
        Objects.requireNonNull(naturalColourId, "naturalColourId");
        Objects.requireNonNull(paletteId, "paletteId");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}

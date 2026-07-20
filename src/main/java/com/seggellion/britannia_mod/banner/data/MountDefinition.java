package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record MountDefinition(
        int schemaVersion,
        MountId id,
        String displayNameKey,
        ResourceLocation geometry,
        ResourceLocation texture,
        List<String> tags) {
    public static final Codec<MountDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(MountDefinition::schemaVersion),
            MountId.CODEC.fieldOf("id").forGetter(MountDefinition::id),
            DataCodecs.NON_BLANK_STRING.fieldOf("display_name_key").forGetter(MountDefinition::displayNameKey),
            ResourceLocation.CODEC.fieldOf("geometry").forGetter(MountDefinition::geometry),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(MountDefinition::texture),
            DataCodecs.TAGS.fieldOf("tags").forGetter(MountDefinition::tags)
    ).apply(instance, MountDefinition::new));

    public MountDefinition {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        displayNameKey = DataCodecs.requireNonBlank(displayNameKey, "displayNameKey");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(texture, "texture");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}

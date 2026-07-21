package com.seggellion.britannia_mod.dye.palette;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Objects;

public record MaterialPaletteEntry(
        ResolvedColourId id,
        String displayNameKey,
        String displaySrgb,
        List<Double> matchOklab,
        int priority,
        List<String> tags,
        List<String> allowedPigmentTags,
        List<String> excludedPigmentTags) {
    public static final Codec<MaterialPaletteEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResolvedColourId.CODEC.fieldOf("id").forGetter(MaterialPaletteEntry::id),
            DataCodecs.NON_BLANK_STRING.fieldOf("display_name_key").forGetter(MaterialPaletteEntry::displayNameKey),
            DataCodecs.CANONICAL_SRGB.fieldOf("display_srgb").forGetter(MaterialPaletteEntry::displaySrgb),
            DataCodecs.OKLAB_COMPONENTS.fieldOf("match_oklab").forGetter(MaterialPaletteEntry::matchOklab),
            Codec.INT.fieldOf("priority").forGetter(MaterialPaletteEntry::priority),
            DataCodecs.TAGS.fieldOf("tags").forGetter(MaterialPaletteEntry::tags),
            DataCodecs.TAGS.fieldOf("allowed_pigment_tags").forGetter(MaterialPaletteEntry::allowedPigmentTags),
            DataCodecs.TAGS.fieldOf("excluded_pigment_tags").forGetter(MaterialPaletteEntry::excludedPigmentTags)
    ).apply(instance, MaterialPaletteEntry::new));

    public MaterialPaletteEntry {
        Objects.requireNonNull(id, "id");
        displayNameKey = DataCodecs.requireNonBlank(displayNameKey, "displayNameKey");
        displaySrgb = DataCodecs.requireCanonicalSrgb(displaySrgb, "displaySrgb");
        matchOklab = DataCodecs.requireOklab(matchOklab, "matchOklab");
        tags = DataCodecs.requireTags(tags, "tags");
        allowedPigmentTags = DataCodecs.requireTags(allowedPigmentTags, "allowedPigmentTags");
        excludedPigmentTags = DataCodecs.requireTags(excludedPigmentTags, "excludedPigmentTags");
    }
}

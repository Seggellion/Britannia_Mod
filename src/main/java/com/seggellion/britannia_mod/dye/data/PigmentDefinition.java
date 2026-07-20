package com.seggellion.britannia_mod.dye.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import java.util.List;
import java.util.Objects;

/** Authored reference colour data; this record does not perform colour conversion or matching. */
public record PigmentDefinition(
        int schemaVersion,
        PigmentId id,
        String displayNameKey,
        String referenceSrgb,
        List<Double> referenceOklab,
        List<String> tags,
        String rarity) {
    public static final Codec<PigmentDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(PigmentDefinition::schemaVersion),
            PigmentId.CODEC.fieldOf("id").forGetter(PigmentDefinition::id),
            DataCodecs.NON_BLANK_STRING.fieldOf("display_name_key").forGetter(PigmentDefinition::displayNameKey),
            DataCodecs.CANONICAL_SRGB.fieldOf("reference_srgb").forGetter(PigmentDefinition::referenceSrgb),
            DataCodecs.OKLAB_COMPONENTS.fieldOf("reference_oklab").forGetter(PigmentDefinition::referenceOklab),
            DataCodecs.TAGS.fieldOf("tags").forGetter(PigmentDefinition::tags),
            DataCodecs.NON_BLANK_STRING.fieldOf("rarity").forGetter(PigmentDefinition::rarity)
    ).apply(instance, PigmentDefinition::new));

    public PigmentDefinition {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        displayNameKey = DataCodecs.requireNonBlank(displayNameKey, "displayNameKey");
        referenceSrgb = DataCodecs.requireCanonicalSrgb(referenceSrgb, "referenceSrgb");
        referenceOklab = DataCodecs.requireOklab(referenceOklab, "referenceOklab");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        rarity = DataCodecs.requireNonBlank(rarity, "rarity");
    }
}

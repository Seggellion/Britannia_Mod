package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import java.util.Objects;
import java.util.Optional;

/** Source-sheet provenance only; source labels are not approved display names. */
public record BannerSourceReference(int sourcePage, int sourceRow, Optional<String> sourceLabel) {
    public static final Codec<BannerSourceReference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("page").forGetter(BannerSourceReference::sourcePage),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("row").forGetter(BannerSourceReference::sourceRow),
            DataCodecs.NON_BLANK_STRING.optionalFieldOf("source_label").forGetter(BannerSourceReference::sourceLabel)
    ).apply(instance, BannerSourceReference::new));

    public BannerSourceReference {
        if (sourcePage < 1) {
            throw new IllegalArgumentException("sourcePage must be positive");
        }
        if (sourceRow < 1) {
            throw new IllegalArgumentException("sourceRow must be positive");
        }
        sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
        sourceLabel.ifPresent(label -> DataCodecs.requireNonBlank(label, "sourceLabel"));
    }
}

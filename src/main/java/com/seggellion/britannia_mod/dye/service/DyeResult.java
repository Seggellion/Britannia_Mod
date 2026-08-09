package com.seggellion.britannia_mod.dye.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.Objects;

public record DyeResult(ResolvedColourId resolvedColourId, MatchType matchType, double perceptualDistance) {
    public static final Codec<DyeResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResolvedColourId.CODEC.fieldOf("resolved_colour_id").forGetter(DyeResult::resolvedColourId),
            MatchType.CODEC.fieldOf("match_type").forGetter(DyeResult::matchType),
            DataCodecs.NON_NEGATIVE_FINITE_DOUBLE.fieldOf("perceptual_distance")
                    .forGetter(DyeResult::perceptualDistance)
    ).apply(instance, DyeResult::new));

    public DyeResult {
        Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        Objects.requireNonNull(matchType, "matchType");
        if (!Double.isFinite(perceptualDistance) || perceptualDistance < 0.0) {
            throw new IllegalArgumentException("perceptualDistance must be finite and not negative");
        }
    }
}

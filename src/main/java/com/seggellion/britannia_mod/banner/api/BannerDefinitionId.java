package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record BannerDefinitionId(ResourceLocation value) implements StableResourceId {
    private static final Map<ResourceLocation, ResourceLocation> LEGACY_ALIASES = Map.ofEntries(
            alias("x_small_unnamed_01", "small_curtain"),
            alias("medium_wall_01", "verdant_grape_pennon"),
            alias("medium_wall_02", "silver_rosette_pennon"),
            alias("medium_wall_03", "four_seals_pennon"),
            alias("medium_wall_04", "twin_spades_pennon"),
            alias("medium_wall_05", "ankh_pennon"),
            alias("large_01", "tournament_curtain"),
            alias("large_02", "threefold_chain_standard"),
            alias("large_03", "iron_serpent_standard"),
            alias("large_04", "silver_fleur_curtain"),
            alias("large_05", "gilded_trellis_curtain"),
            alias("large_06", "gilded_chevron_curtain"));

    public static final Codec<BannerDefinitionId> CODEC =
            ResourceLocation.CODEC.xmap(BannerDefinitionId::new, BannerDefinitionId::value);
    public static final StreamCodec<ByteBuf, BannerDefinitionId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(BannerDefinitionId::new, BannerDefinitionId::value);

    public BannerDefinitionId {
        Objects.requireNonNull(value, "value");
        value = LEGACY_ALIASES.getOrDefault(value, value);
    }

    public static BannerDefinitionId of(String namespace, String path) {
        return new BannerDefinitionId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static BannerDefinitionId parse(String value) {
        return new BannerDefinitionId(ResourceLocation.parse(value));
    }

    private static Map.Entry<ResourceLocation, ResourceLocation> alias(
            String legacyPath, String canonicalPath) {
        return Map.entry(
                ResourceLocation.fromNamespaceAndPath("britannia_mod", legacyPath),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", canonicalPath));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

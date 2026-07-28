package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record BannerDefinitionId(ResourceLocation value) implements StableResourceId {
    private static final Map<ResourceLocation, ResourceLocation> LEGACY_ALIASES = Map.of(
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "x_small_unnamed_01"),
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "small_curtain"));

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

    @Override
    public String toString() {
        return value.toString();
    }
}

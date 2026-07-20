package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record BannerDefinitionId(ResourceLocation value) implements StableResourceId {
    public static final Codec<BannerDefinitionId> CODEC =
            ResourceLocation.CODEC.xmap(BannerDefinitionId::new, BannerDefinitionId::value);
    public static final StreamCodec<ByteBuf, BannerDefinitionId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(BannerDefinitionId::new, BannerDefinitionId::value);

    public BannerDefinitionId {
        Objects.requireNonNull(value, "value");
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

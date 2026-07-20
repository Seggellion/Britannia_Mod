package com.seggellion.britannia_mod.dye.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ResolvedColourId(ResourceLocation value) implements StableResourceId {
    public static final Codec<ResolvedColourId> CODEC =
            ResourceLocation.CODEC.xmap(ResolvedColourId::new, ResolvedColourId::value);
    public static final StreamCodec<ByteBuf, ResolvedColourId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(ResolvedColourId::new, ResolvedColourId::value);

    public ResolvedColourId {
        Objects.requireNonNull(value, "value");
    }

    public static ResolvedColourId of(String namespace, String path) {
        return new ResolvedColourId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static ResolvedColourId parse(String value) {
        return new ResolvedColourId(ResourceLocation.parse(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

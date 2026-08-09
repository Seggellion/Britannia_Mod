package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record MountId(ResourceLocation value) implements StableResourceId {
    public static final Codec<MountId> CODEC = ResourceLocation.CODEC.xmap(MountId::new, MountId::value);
    public static final StreamCodec<ByteBuf, MountId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(MountId::new, MountId::value);

    public MountId {
        Objects.requireNonNull(value, "value");
    }

    public static MountId of(String namespace, String path) {
        return new MountId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static MountId parse(String value) {
        return new MountId(ResourceLocation.parse(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record PlacementProfileId(ResourceLocation value) implements StableResourceId {
    public static final Codec<PlacementProfileId> CODEC =
            ResourceLocation.CODEC.xmap(PlacementProfileId::new, PlacementProfileId::value);
    public static final StreamCodec<ByteBuf, PlacementProfileId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(PlacementProfileId::new, PlacementProfileId::value);

    public PlacementProfileId {
        Objects.requireNonNull(value, "value");
    }

    public static PlacementProfileId of(String namespace, String path) {
        return new PlacementProfileId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static PlacementProfileId parse(String value) {
        return new PlacementProfileId(ResourceLocation.parse(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

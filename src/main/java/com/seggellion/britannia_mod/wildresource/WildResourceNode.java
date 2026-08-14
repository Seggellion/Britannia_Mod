package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Persisted identity of one placed wild-resource node. */
public record WildResourceNode(ResourceLocation resourceId, BlockPos position, long placedGameTime) {
    public WildResourceNode {
        resourceId = Objects.requireNonNull(resourceId, "resourceId");
        position = Objects.requireNonNull(position, "position").immutable();
        if (placedGameTime < 0) {
            throw new IllegalArgumentException("placedGameTime cannot be negative");
        }
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ResourceId", resourceId.toString());
        tag.putLong("Position", position.asLong());
        tag.putLong("PlacedGameTime", placedGameTime);
        return tag;
    }

    static WildResourceNode fromTag(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("ResourceId"));
        if (id == null || !tag.contains("Position") || !tag.contains("PlacedGameTime")) {
            throw new IllegalArgumentException("Wild resource node is missing required state");
        }
        return new WildResourceNode(id, BlockPos.of(tag.getLong("Position")), tag.getLong("PlacedGameTime"));
    }
}

package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Exact existing community-plot block state to restore after permanent uprooting. */
public record FlowerCommunityRestoration(ResourceLocation blockId, boolean prepared) {
    public static final ResourceLocation COMMUNITY_FARM_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "community_farm_block");

    public FlowerCommunityRestoration {
        Objects.requireNonNull(blockId, "Community restoration block ID is required");
        if (!COMMUNITY_FARM_BLOCK_ID.equals(blockId)) {
            throw new IllegalArgumentException("Community flowers must restore the repository community farm block: " + blockId);
        }
        if (prepared) {
            throw new IllegalArgumentException("A permanently uprooted community flower must restore an unprepared community plot");
        }
    }

    public static FlowerCommunityRestoration currentRepositoryState() {
        return new FlowerCommunityRestoration(COMMUNITY_FARM_BLOCK_ID, false);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("BlockId", blockId.toString());
        tag.putBoolean("Prepared", prepared);
        return tag;
    }

    public static FlowerCommunityRestoration fromTag(CompoundTag tag) {
        if (!tag.contains("BlockId")) {
            throw new IllegalArgumentException("Community restoration data is missing BlockId");
        }
        return new FlowerCommunityRestoration(
                FlowerDefinitionValidator.parseNamespacedId("Community restoration BlockId", tag.getString("BlockId")),
                tag.getBoolean("Prepared")
        );
    }
}

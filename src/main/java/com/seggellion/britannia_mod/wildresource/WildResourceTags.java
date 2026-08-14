package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Data-pack-extensible environmental categories used by wild resources. */
public final class WildResourceTags {
    public static final TagKey<Block> BLACK_LIPPED_OYSTER_SUBSTRATES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "black_lipped_oyster_substrates")
    );

    private WildResourceTags() {
    }
}

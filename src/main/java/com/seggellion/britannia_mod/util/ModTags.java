package com.seggellion.britannia_mod.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.seggellion.britannia_mod.BritanniaMod;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> LOGS = createTag("logs");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name));
        }
    }
}

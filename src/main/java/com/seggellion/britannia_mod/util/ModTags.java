package com.seggellion.britannia_mod.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import com.seggellion.britannia_mod.BritanniaMod;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> LOGS = createTag("logs");
        public static final TagKey<Block> FRUIT_TREE_LOGS = createTag("fruit_tree_logs");
        public static final TagKey<Block> FRUIT_TREE_TRUNKS = createTag("fruit_tree_trunks");
        public static final TagKey<Block> FRUIT_TREE_BRANCHES = createTag("fruit_tree_branches");
        public static final TagKey<Block> FRUIT_TREE_LEAVES = createTag("fruit_tree_leaves");
        public static final TagKey<Block> FRUIT_TREE_FRUITS = createTag("fruit_tree_fruits");
        public static final TagKey<Block> FRUIT_TREE_BLOCKS = createTag("fruit_tree_blocks");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> GRAIN_HARVEST_BLADES = createTag("grain_harvest_blades");
        public static final TagKey<Item> ROOT_CROP_SHOVELS = createTag("root_crop_shovels");
        public static final TagKey<Item> SKINNING_KNIVES = createTag("skinning_knives");

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name));
        }
    }
}

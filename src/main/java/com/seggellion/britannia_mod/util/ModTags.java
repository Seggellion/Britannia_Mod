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
        public static final TagKey<Block> ADVENTURE_LADDERS = createTag("adventure_ladders");

        /**
         * Types that <em>may</em> participate in Grabby Hands pickup/placement.
         *
         * <p>Type eligibility is not instance mobility. A block being in this tag says only that the
         * type supports the transport system; whether one particular placed block may actually be
         * moved is answered by {@code GrabbyInstanceState} provenance, never by this tag.
         */
        public static final TagKey<Block> GRABBY_MOVABLE = createTag("grabby_movable");

        /** Types an axe may destroy through Grabby Hands. Same eligibility-is-not-mobility rule. */
        public static final TagKey<Block> GRABBY_AXE_DESTROYABLE = createTag("grabby_axe_destroyable");

        /**
         * House fixtures that arrive with a deed, and are therefore never Grabby Hands content.
         *
         * <p>A hard veto rather than merely an absence from the movable tag. Beds and chandeliers are
         * part of the house a player bought, not furniture they carry around, and a deed is placed
         * under house authority rather than by the ordinary placement gesture. Offering a second way
         * to move them would put two systems in charge of the same object.
         *
         * <p>Expressed as its own tag so the rule is greppable and enforced, instead of surviving only
         * as a gap somebody could close by accident.
         */
        public static final TagKey<Block> GRABBY_DEED_PLACED = createTag("grabby_deed_placed");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> GRAIN_HARVEST_BLADES = createTag("grain_harvest_blades");
        public static final TagKey<Item> ROOT_CROP_SHOVELS = createTag("root_crop_shovels");
        public static final TagKey<Item> SKINNING_KNIVES = createTag("skinning_knives");
        public static final TagKey<Item> FLOWERS = createTag("flowers");
        public static final TagKey<Item> FLOWER_SEEDS = createTag("flower_seeds");

        /**
         * Items with no block form that may be set down in the world through the generic host.
         *
         * <p>Separate from {@code GRABBY_MOVABLE} because that tag enrolls <em>blocks</em>. An item
         * that already has a block form belongs there, not here.
         */
        public static final TagKey<Item> GRABBY_PLACEABLE_ITEMS = createTag("grabby_placeable_items");

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name));
        }
    }
}

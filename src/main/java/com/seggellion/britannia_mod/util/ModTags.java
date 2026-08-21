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
         * The exterior of a house: the wall-base courses that run around the outside.
         *
         * <p>Protected from everybody, the owner included. Membership is decided by what these
         * blocks actually do in the authored structures rather than by their registry names, which
         * are misleading -- every block in this mod with "foundation" in its name is not in here,
         * and the ones that are, are the ones that occur at y>=1 and never at y=0.
         *
         * <p>{@code HouseFloorRoleTest} holds that measurement still.
         */
        public static final TagKey<Block> HOUSE_FOUNDATION = createTag("house_foundation");

        /**
         * The interior of a house: the structural ground-floor slab it ships with.
         *
         * <p>This is the layer an owner cuts down through to reach a basement, so it is the one
         * part of the shipped structure they are meant to be able to break. It is not the same
         * thing as decorative flooring, which was never part of the structure, and it is not the
         * perimeter, which is not theirs to remove.
         *
         * <p>The names mislead in both directions: {@code brick_foundation_spruce} sounds like a
         * perimeter block and is the six small houses' interior floor, laid 49 at a time at y=0 and
         * nowhere else.
         */
        public static final TagKey<Block> HOUSE_FLOOR_FOUNDATION = createTag("house_floor_foundation");


        /**
         * The rock a geological deposit may replace when it is materialised.
         *
         * <p>OreVein milestone 3. The legacy shapes each decided this for themselves and disagreed:
         * two would only write into air, one would write over anything that was not air -- bedrock,
         * a fluid or a chest included -- and the rest asked nothing at all. The decision now belongs
         * to the resource definition, which names this tag, and to {@code MaterializationService},
         * which is the only place a deposit is written.
         *
         * <p>It composes the two vanilla ore-replaceable tags rather than listing blocks, so it
         * follows the stone families Minecraft itself considers ore hosts and picks up any a
         * datapack adds. Silica's sediment hosts will be a tag of their own at milestone 7; that is
         * why the definition names the tag rather than this being a constant.
         */
        public static final TagKey<Block> ORE_HOSTS = createTag("ore_hosts");

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

        /**
         * The shovels authorized to work a managed clay deposit.
         *
         * <p>A tag rather than a class check, because that is how every other harvest category
         * here is expressed ({@code ROOT_CROP_SHOVELS}, {@code GRAIN_HARVEST_BLADES},
         * {@code SKINNING_KNIVES}) and because widening the rule later must be a data change,
         * not a Java edit. It holds the project shovel only: a vanilla shovel is not an
         * authorized tool, exactly as a vanilla sword is not a skinning knife.
         */
        public static final TagKey<Item> CLAY_SHOVELS = createTag("clay_shovels");

        /**
         * The shovels authorized to work a managed silica bed.
         *
         * <p>A tag of its own rather than a reuse of {@code CLAY_SHOVELS}: tags here are named for
         * the job, not for the tool, so that widening one harvest never silently widens another.
         * Both happen to hold the same shovel today.
         */
        public static final TagKey<Item> SILICA_SHOVELS = createTag("silica_shovels");

        /**
         * The pickaxes authorized to work a Mining-catalogued stone or ore resource.
         *
         * <p>OreVein milestone 2. The Mining half had no tag at all — authorization was an
         * {@code instanceof QualityToolItem} predicate in Java, which is why a shovel and a
         * two-handed axe had to be excluded by class rather than by data. This is the pickaxe's
         * side of the same arrangement clay and silica already had, so the pickaxe and the shovel
         * are now parallel: each resource definition names the tag that works it, and neither tool
         * has any authority the data has not granted it.
         *
         * <p>Named for the job like every tag above it, and resource definitions reference it per
         * resource — so a future ore that wants its own pickaxe tag is a data change here and one
         * line in {@code resources.json}, not a Java edit.
         */
        public static final TagKey<Item> MINING_PICKAXES = createTag("mining_pickaxes");
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

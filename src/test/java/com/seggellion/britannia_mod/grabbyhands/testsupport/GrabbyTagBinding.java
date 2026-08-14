package com.seggellion.britannia_mod.grabbyhands.testsupport;

import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Binds the Grabby enrollment tag onto a real vanilla block for tests.
 *
 * <p>The mod has no datagen and unit tests load no datapack, so no block tag is populated at all
 * under a bare {@code Bootstrap}. Binding one here therefore clobbers nothing another test depends
 * on, and it keeps the real {@link com.seggellion.britannia_mod.grabbyhands.GrabbyEligibility} in the
 * code path rather than stubbing eligibility out and testing something weaker.
 *
 * <p>Always pair {@link #enrol(Block)} with {@link #clear()} in {@code @AfterAll}.
 */
public final class GrabbyTagBinding {
    private GrabbyTagBinding() {
    }

    public static void enrol(Block block) {
        Holder<Block> holder = BuiltInRegistries.BLOCK.getResourceKey(block)
                .flatMap(BuiltInRegistries.BLOCK::getHolder)
                .map(reference -> (Holder<Block>) reference)
                .orElseThrow();
        BuiltInRegistries.BLOCK.bindTags(Map.of(
                ModTags.Blocks.GRABBY_MOVABLE, List.of(holder),
                ModTags.Blocks.GRABBY_AXE_DESTROYABLE, List.of(holder)));
    }

    public static void clear() {
        BuiltInRegistries.BLOCK.bindTags(Map.of());
    }

    /** Same trick for the loose-item enrollment tag. */
    public static void enrolItem(Item item) {
        Holder<Item> holder = BuiltInRegistries.ITEM.getResourceKey(item)
                .flatMap(BuiltInRegistries.ITEM::getHolder)
                .map(reference -> (Holder<Item>) reference)
                .orElseThrow();
        BuiltInRegistries.ITEM.bindTags(Map.of(ModTags.Items.GRABBY_PLACEABLE_ITEMS, List.of(holder)));
    }

    public static void clearItems() {
        BuiltInRegistries.ITEM.bindTags(Map.of());
    }
}

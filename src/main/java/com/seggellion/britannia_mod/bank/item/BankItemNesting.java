package com.seggellion.britannia_mod.bank.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The single shared container-nesting-depth limit for shulker-box/bundle structures,
 * enforced by {@link BankItemCodec}, {@link BankItemFingerprint}, and {@link BankItemWeight}
 * -- each with the error-handling convention that fits its own established call-site
 * philosophy, not a shared one, since a legitimate in-memory ItemStack a player is holding
 * (serialize/fingerprint) and an untrusted byte payload from storage (deserialize) are not the
 * same kind of failure.
 *
 * <h2>Why 8</h2>
 * A depth of 1 is a plain stack with no nested container; depth N means a chain of N
 * containers nested inside each other. Real gameplay essentially never nests more than one or
 * two containers deep (a bundle holding a shulker box, or the reverse) -- vanilla itself
 * already blocks several of the deeper combinations directly through its own inventory rules.
 * 8 is far beyond any plausible legitimate scenario, including anything reachable via
 * commands or datapacks, while being a trivially small number of Java stack frames: it
 * forecloses a crafted-NBT stack-overflow attempt with enormous headroom, rather than merely
 * making one less likely. This is not three independent limits that happen to agree -- it is
 * one constant, defined once, that all three classes read.
 */
public final class BankItemNesting {
    public static final int MAX_DEPTH = 8;

    private static final String KEY_COMPONENTS = "components";
    private static final String KEY_CONTAINER = "minecraft:container";
    private static final String KEY_BUNDLE_CONTENTS = "minecraft:bundle_contents";
    private static final String KEY_SLOT_ITEM = "item";

    private BankItemNesting() {
    }

    /**
     * The container-nesting depth of an already-decoded {@code stack}: 1 for a stack with no
     * nested container contents, N for a stack whose deepest real nested container chain is N
     * levels deep. Bounded to at most {@link #MAX_DEPTH} + 2 Java stack frames regardless of
     * how deep the actual structure is -- it stops descending the moment the running depth
     * would exceed the limit, so this method is always safe to call, even on a stack an
     * attacker deliberately built to be extremely deep.
     */
    static int containerNestingDepthOf(ItemStack stack) {
        return containerNestingDepthOf(stack, 1);
    }

    private static int containerNestingDepthOf(ItemStack stack, int depth) {
        if (depth > MAX_DEPTH + 1) {
            return depth;
        }
        int deepest = depth;

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack contained : container.stream().toList()) {
                deepest = Math.max(deepest, containerNestingDepthOf(contained, depth + 1));
                if (deepest > MAX_DEPTH + 1) {
                    return deepest;
                }
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack contained : bundle.items()) {
                deepest = Math.max(deepest, containerNestingDepthOf(contained, depth + 1));
                if (deepest > MAX_DEPTH + 1) {
                    return deepest;
                }
            }
        }
        return deepest;
    }

    /**
     * The same depth walk as {@link #containerNestingDepthOf(ItemStack)}, performed directly
     * on an undecoded NBT {@link Tag} -- the raw "item" payload {@link BankItemCodec#deserialize}
     * has not yet turned into a real ItemStack. This lets a maliciously deep payload be
     * rejected before vanilla's own unbounded {@code ItemStack.CODEC.parse} recursion ever
     * runs on it. Uses the exact wire shape {@code ItemStack.CODEC}/{@code DataComponentPatch}
     * actually produce (verified by reading {@code ItemContainerContents.Slot}'s and
     * {@code BundleContents}' own codecs directly): a "components" compound keyed by
     * registry id, "minecraft:container" as a list of {@code {"slot": N, "item": {...}}},
     * "minecraft:bundle_contents" as a plain list of item tags. A tag that doesn't match this
     * shape simply isn't treated as a nested container here; the real codec will report
     * whatever else is wrong with it.
     */
    static int containerNestingDepthOfTag(Tag itemTag) {
        return containerNestingDepthOfTag(itemTag, 1);
    }

    private static int containerNestingDepthOfTag(Tag itemTag, int depth) {
        if (depth > MAX_DEPTH + 1) {
            return depth;
        }
        if (!(itemTag instanceof CompoundTag itemCompound)) {
            return depth;
        }
        if (!(itemCompound.get(KEY_COMPONENTS) instanceof CompoundTag components)) {
            return depth;
        }
        int deepest = depth;

        if (components.get(KEY_CONTAINER) instanceof ListTag containerList) {
            for (Tag slotTag : containerList) {
                if (slotTag instanceof CompoundTag slotCompound
                        && slotCompound.get(KEY_SLOT_ITEM) instanceof Tag nestedItem) {
                    deepest = Math.max(deepest, containerNestingDepthOfTag(nestedItem, depth + 1));
                    if (deepest > MAX_DEPTH + 1) {
                        return deepest;
                    }
                }
            }
        }
        if (components.get(KEY_BUNDLE_CONTENTS) instanceof ListTag bundleList) {
            for (Tag nestedItem : bundleList) {
                deepest = Math.max(deepest, containerNestingDepthOfTag(nestedItem, depth + 1));
                if (deepest > MAX_DEPTH + 1) {
                    return deepest;
                }
            }
        }
        return deepest;
    }
}

package com.seggellion.britannia_mod.deposit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One kind of managed resource deposit: the block that is one, what may work it, and what a
 * single extraction hands the player.
 *
 * <p>Everything a deposit does <em>after</em> extraction — going empty, being remembered, coming
 * back — is not described here because it is not per-resource. That half already exists and is
 * already material-agnostic: {@code BrokenBlockTracker} records the position and the state, and
 * {@code BlockRestoreHandler} puts it back. The ores have used it since long before this record,
 * and clay uses the same one rather than a second timer of its own.
 *
 * <p>What is per-resource is the front half, and that is exactly what the Mining catalogue could
 * not express. {@code MineableDefinition} has a {@code requiredMining} and a STONE/ORE category,
 * and the tool is not a field at all — it is the pickaxe, three times over, in
 * {@code PickaxeMiningRules}, {@code QualityToolItem} and {@code CustomBlockBreakHandler}. So the
 * tool is a field here.
 *
 * <p>Suppliers rather than resolved objects because a deposit is declared next to the registry
 * and read long after it: blocks and items do not exist yet at class-initialisation time.
 */
public record ManagedDeposit(
        ResourceLocation id,
        Supplier<Block> block,
        TagKey<Item> extractionTool,
        Supplier<Item> extractedItem,
        int extractedCount
) {
    public ManagedDeposit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(extractionTool, "extractionTool");
        Objects.requireNonNull(extractedItem, "extractedItem");
        if (extractedCount <= 0) {
            throw new IllegalArgumentException("a deposit that yields nothing is not a resource");
        }
    }
}

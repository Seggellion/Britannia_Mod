package com.seggellion.britannia_mod.resource.extraction;

import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.util.ModTags;

import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a {@code minecraft:can_break} predicate on every UltimaCraft extraction tool, scoped to
 * exactly the blocks that tool is meant to work — which is what lets an adventure-mode player run
 * Minecraft's own destroy progress on a resource, with no game-mode switch, no {@code mayBuild}
 * grant and no mixin.
 *
 * <h2>Why adventure needs this at all</h2>
 * Adventure refuses block breaking on both sides of the wire through one vanilla question:
 * {@code Player.blockActionRestricted}, answered by the held stack's CAN_BREAK component. The
 * project used to route around that refusal twice over — deposits with an instant left-click
 * harvest, and ores by quietly switching pickaxe-holders into Survival — and both routes bypassed
 * or distorted the real breaking lifecycle. Answering the vanilla question honestly instead
 * restores the crack animation, the per-block dig duration, the server's own progress
 * verification and the {@code BreakEvent} chain, for every family at once.
 *
 * <h2>What each tool is granted, and from which authority</h2>
 * <ul>
 *   <li><b>Catalogue tools</b> (the Britannia pickaxe and shovel): the union of the blocks of
 *       every resource whose configured {@code extraction_tool} tag the stack is in, read from
 *       the one resource catalogue. The pickaxe therefore covers the Mining-family blocks —
 *       vanilla stones and ores included, because those ARE catalogued resources — and the shovel
 *       covers the two sediment beds and nothing else. No block list is maintained here.</li>
 *   <li><b>The two-handed axe</b>: wood and leaves only, the owner's firm rule, expressed as tag
 *       references — vanilla {@code #minecraft:logs} and {@code #minecraft:leaves}, the project's
 *       {@code #britannia_mod:logs} (which adds the weighted wood block), and the fruit-tree
 *       log/trunk/branch/leaf tags. Fruit blocks are deliberately absent: fruit is harvested by
 *       the interaction handlers, not felled. Tag references keep the stack component tiny and
 *       track datapack contents on their own.</li>
 * </ul>
 *
 * <p>The component is a lifecycle key, not an authority: permission to <em>finish</em> a break is
 * still decided server-side ({@code MiningGateHandler}, {@code ManagedDepositExtraction}'s
 * refusal chain, the wood handlers), so a forged or stale predicate changes what a client may
 * animate, never what the server commits.
 *
 * <p>Applied on the player tick, the same way {@code GlobalEventHandler} keeps saplings
 * plantable, and rewritten whenever the desired predicate differs from what the stack carries —
 * so a tool minted before a catalogue change heals itself instead of keeping a stale grant.
 * Without a tooltip line: the predicate is plumbing, not lore.
 */
public final class ExtractionToolPredicates {

    private static final TagKey<Block> BRITANNIA_LOGS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("britannia_mod", "logs"));

    /**
     * Desired predicate per item type. Tag-reference predicates stay live on their own; the
     * catalogue-derived block sets are fixed for the life of the process, which matches the
     * catalogue itself (classpath data, loaded once). Computed lazily on the first player tick so
     * registries and tags are bound before anything is resolved.
     */
    private static final Map<net.minecraft.world.item.Item, Optional<AdventureModePredicate>> DESIRED =
            new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            AdventureModePredicate desired = DESIRED
                    .computeIfAbsent(stack.getItem(), item -> Optional.ofNullable(predicateFor(stack)))
                    .orElse(null);
            if (desired != null && !desired.equals(stack.get(DataComponents.CAN_BREAK))) {
                stack.set(DataComponents.CAN_BREAK, desired);
            }
        }
    }

    /**
     * The blocks this stack's identity authorizes it to animate breaking, as one CAN_BREAK
     * predicate — or null when the item is no extraction tool of ours, in which case any
     * component it carries is left entirely alone.
     */
    public static AdventureModePredicate predicateFor(ItemStack stack) {
        List<BlockPredicate> predicates = new ArrayList<>();

        List<Holder<Block>> catalogueBlocks = new ArrayList<>();
        for (ResourceDefinition resource : ResourceCatalog.instance().all()) {
            if (stack.is(Resources.extractionTag(resource))) {
                for (String blockId : resource.blockIds()) {
                    catalogueBlocks.add(Resources.block(blockId).builtInRegistryHolder());
                }
            }
        }
        if (!catalogueBlocks.isEmpty()) {
            predicates.add(blocks(HolderSet.direct(catalogueBlocks)));
        }

        if (stack.getItem() instanceof TwoHandedAxeItem) {
            for (TagKey<Block> tag : List.of(
                    BlockTags.LOGS, BlockTags.LEAVES, BRITANNIA_LOGS,
                    ModTags.Blocks.FRUIT_TREE_LOGS, ModTags.Blocks.FRUIT_TREE_TRUNKS,
                    ModTags.Blocks.FRUIT_TREE_BRANCHES, ModTags.Blocks.FRUIT_TREE_LEAVES)) {
                predicates.add(blocks(BuiltInRegistries.BLOCK.getOrCreateTag(tag)));
            }
        }

        return predicates.isEmpty() ? null : new AdventureModePredicate(predicates, false);
    }

    private static BlockPredicate blocks(HolderSet<Block> blocks) {
        return new BlockPredicate(Optional.of(blocks), Optional.empty(), Optional.empty());
    }

    /** Test seam: forget every computed predicate, so suites cannot see each other's cache. */
    public static void forgetDesiredPredicates() {
        DESIRED.clear();
    }
}

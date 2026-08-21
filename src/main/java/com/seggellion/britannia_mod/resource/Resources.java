package com.seggellion.britannia_mod.resource;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one Minecraft-facing door into the resource catalogue.
 *
 * <p>Mirrors {@code Mineables}: the catalogue itself is pure Java holding registry-id strings, and
 * this turns those strings into blocks, items, tags and block states. Resolution is backed by an
 * identity-keyed map built once after block registration, because it runs on the break hot path.
 *
 * <p>Registered ids cannot be checked at class-construction time — registration has not run yet —
 * so {@link #validateAgainstRegistries()} re-checks blocks and yield items at server start, and
 * {@link #validateExtractionTagsResolve()} checks the extraction tags once datapacks have loaded.
 * A resource whose tag is empty would silently authorise nobody, which is the "no silent fallback"
 * rule failing quietly rather than loudly, so it fails the load instead.
 */
public final class Resources {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Block, ResourceDefinition> blockLookup;

    private Resources() {
    }

    /** Parse + validate the shipped catalogue now so a data bug fails the load, not a break. */
    public static void init() {
        ResourceCatalog catalog = ResourceCatalog.instance();
        LOGGER.info("Loaded {} resource definitions ({} ore, {} stone, {} sediment, {} generatable)",
                catalog.all().size(),
                catalog.family(ResourceDefinition.Family.ORE).size(),
                catalog.family(ResourceDefinition.Family.STONE).size(),
                catalog.family(ResourceDefinition.Family.SEDIMENT).size(),
                catalog.generatable().size());
    }

    /** The resource governing this block, or empty = not a managed resource. */
    public static Optional<ResourceDefinition> resolve(BlockState state) {
        if (state == null) return Optional.empty();
        return Optional.ofNullable(byBlock().get(state.getBlock()));
    }

    private static Map<Block, ResourceDefinition> byBlock() {
        Map<Block, ResourceDefinition> current = blockLookup;
        if (current != null) {
            return current;
        }
        synchronized (Resources.class) {
            if (blockLookup == null) {
                Map<Block, ResourceDefinition> built = new IdentityHashMap<>();
                ResourceCatalog.instance().blockIds().forEach((blockId, definition) -> {
                    ResourceLocation id = ResourceLocation.parse(blockId);
                    if (BuiltInRegistries.BLOCK.containsKey(id)) {
                        built.put(BuiltInRegistries.BLOCK.get(id), definition);
                    }
                });
                blockLookup = Collections.unmodifiableMap(built);
            }
            return blockLookup;
        }
    }

    /** The item tag that authorises working this resource. */
    public static TagKey<Item> extractionTag(ResourceDefinition definition) {
        return ItemTags.create(ResourceLocation.parse(definition.extractionToolTag()));
    }

    /**
     * Whether this stack may work this resource.
     *
     * <p>The single authorization question for both families. A pickaxe is authorised for an ore
     * because {@code britannia_mod:mining_pickaxes} says so, not because of what class it is; the
     * same shovel is authorised for clay and silica for the same reason; and a tool in neither tag
     * is refused without anything needing to name it.
     */
    public static boolean isAuthorizedTool(ResourceDefinition definition, ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(extractionTag(definition));
    }

    public static Block block(String blockId) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
    }

    /** The single block a sediment bed stands as, or the block generation materialises. */
    public static Block standingBlock(ResourceDefinition definition) {
        return block(definition.generation()
                .map(ResourceDefinition.Generation::blockId)
                .orElseGet(() -> definition.blockIds().get(0)));
    }

    /** The configured yield, for resources whose yield is a plain item (the sediment beds). */
    public static ItemStack yieldStack(ResourceDefinition definition) {
        String itemId = definition.yield().itemId().orElseThrow(() -> new IllegalStateException(
                "Resource " + definition.id() + " does not yield a configured item"));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return new ItemStack(item, definition.yield().count());
    }

    /**
     * The state that replaces the resource once it has been worked.
     *
     * <p>Routed through the definition's declared {@link ResourceDefinition.DepletedState} rather
     * than inlined at each extraction site, so the concept has one implementation and adding a
     * second mode later is a change in one place. The only mode today reproduces exactly what both
     * extraction paths already did.
     */
    public static BlockState depletedState(
            ResourceDefinition definition, LevelReader level, BlockPos pos) {
        return switch (definition.depleted()) {
            case FLUID_AWARE_AIR -> level.getFluidState(pos).createLegacyBlock();
        };
    }

    /**
     * How long this block's restoration must wait, in milliseconds, or empty when the block is not
     * a managed resource and the caller should use its own default.
     */
    public static java.util.OptionalLong regenerationMillis(BlockState state) {
        return resolve(state)
                .map(definition -> java.util.OptionalLong.of(definition.regenerationMillis()))
                .orElseGet(java.util.OptionalLong::empty);
    }

    /** Fails server start if any catalogued block or yield item does not exist in the registries. */
    public static void validateAgainstRegistries() {
        List<String> missing = new ArrayList<>();
        for (ResourceDefinition definition : ResourceCatalog.instance().all()) {
            for (String blockId : definition.blockIds()) {
                if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockId))) {
                    missing.add(definition.id() + " -> block " + blockId);
                }
            }
            definition.yield().itemId().ifPresent(itemId -> {
                if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemId))) {
                    missing.add(definition.id() + " -> item " + itemId);
                }
            });
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Resource catalogue references unregistered content: " + missing);
        }
        LOGGER.info("Resource catalogue block and item ids all resolve against the registries");
    }

    /**
     * Fails once datapacks are loaded if any extraction tag is empty.
     *
     * <p>A tag always "exists" in Minecraft, so an unresolved extraction rule does not throw — it
     * simply authorises nobody, and the resource becomes quietly unminable. That is the failure
     * mode this milestone was told not to allow, so it is checked explicitly.
     */
    public static void validateExtractionTagsResolve() {
        List<String> empty = new ArrayList<>();
        for (ResourceDefinition definition : ResourceCatalog.instance().all()) {
            TagKey<Item> tag = extractionTag(definition);
            if (BuiltInRegistries.ITEM.getTag(tag).map(named -> named.size() == 0).orElse(true)) {
                empty.add(definition.id() + " -> " + definition.extractionToolTag());
            }
        }
        if (!empty.isEmpty()) {
            throw new IllegalStateException("Resource extraction tags authorise no item, so those "
                    + "resources could never be worked: " + empty);
        }
        LOGGER.info("Resource extraction tags all authorise at least one item");
    }
}

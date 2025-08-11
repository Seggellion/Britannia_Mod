package com.seggellion.britannia_mod.registry;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.block.HorizontalFacingBlock; // if this is your custom class; adjust import if needed
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FishRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Keep this separate from your ItemRegistry and BlockRegistry
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "britannia_mod");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "britannia_mod");

    // Lookups if you want to grab holders later by id (e.g., "fire_fish")
    public static final Map<String, DeferredHolder<Block, Block>> FISH_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Item, WeightedFishItem>> FISH_ITEMS = new LinkedHashMap<>();

    // Default block properties for all fish blocks; tweak if you need per-fish props
    private static final BlockBehaviour.Properties FISH_BLOCK_PROPS =
            BlockBehaviour.Properties.ofFullCopy(Blocks.RED_SAND).noOcclusion();

    // Put all ~70 fish ids here. You can also call addFishIds(...) before register(eventBus).
    private static final List<String> FISH_IDS = new ArrayList<>(List.of(
        "fire_fish",
        "kokanee_salmon",
        "amberjack",
        "black_seabass",
        "blue_grouper",
        "bluefish",
        "bluegill_sunfish",
        "bonefish",
        "bonito",
        "brook_trout",
        "cape_cod"
        // add the rest here...
    ));

    private FishRegistry() {}

    public static void register(IEventBus eventBus) {
        for (String id : FISH_IDS) {
            registerFish(id);
        }
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        LOGGER.info("Registered {} fish blocks and items.", FISH_IDS.size());
    }

    /**
     * Optional helper so you can append ids from elsewhere before calling register(eventBus).
     */
    public static void addFishIds(String... ids) {
        for (String id : ids) {
            if (!FISH_IDS.contains(id)) FISH_IDS.add(id);
        }
    }

    private static void registerFish(String id) {
        // 1) Block
        DeferredHolder<Block, Block> blockHolder =
                BLOCKS.register(id, () -> new HorizontalFacingBlock(FISH_BLOCK_PROPS));

        // 2) Item that points at the block above
        DeferredHolder<Item, WeightedFishItem> itemHolder =
                ITEMS.register(id, () -> new WeightedFishItem(blockHolder.get(), new Item.Properties()));

        // Store for later access
        FISH_BLOCKS.put(id, blockHolder);
        FISH_ITEMS.put(id, itemHolder);
    }
}

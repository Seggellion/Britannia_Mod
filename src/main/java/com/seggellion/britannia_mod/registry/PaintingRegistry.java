package com.seggellion.britannia_mod.registry;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.PaintingBlock;
import com.seggellion.britannia_mod.item.PaintingItem;
import com.seggellion.britannia_mod.item.PaintingItem.PaintingSize;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaintingRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, "britannia_mod");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "britannia_mod");

    public static final Map<String, DeferredHolder<Block, Block>> PAINTING_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Item, Item>>   PAINTING_ITEMS  = new LinkedHashMap<>();
    public static final Map<String, PaintingSize>                 PAINTING_SIZES  = new LinkedHashMap<>();

    private static final BlockBehaviour.Properties PAINTING_PROPS =
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion().strength(0.5f, 1.0f);

    // Declare your initial set here. Add up to 10 total later as needed.
    static {
        // id                              size
        add("painting_medium_red_dress",   PaintingSize.MEDIUM);
        add("painting_medium_bruyn",       PaintingSize.MEDIUM);
        add("painting_large_parmigianino", PaintingSize.LARGE);
    }

    private PaintingRegistry() {}

    private static void add(String id, PaintingSize size) {
        PAINTING_SIZES.put(id, size);
    }

    public static void register(IEventBus modBus) {
        for (Map.Entry<String, PaintingSize> entry : PAINTING_SIZES.entrySet()) {
            String id = entry.getKey();
            PaintingSize size = entry.getValue();

            DeferredHolder<Block, Block> blockHolder = BLOCKS.register(id,
                    () -> new PaintingBlock(PAINTING_PROPS));

            DeferredHolder<Item, Item> itemHolder = ITEMS.register(id,
                    () -> new PaintingItem(blockHolder.get(), size, new Item.Properties()));

            PAINTING_BLOCKS.put(id, blockHolder);
            PAINTING_ITEMS.put(id, itemHolder);
        }

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        LOGGER.info("Registered {} painting blocks and items.", PAINTING_SIZES.size());
    }
}

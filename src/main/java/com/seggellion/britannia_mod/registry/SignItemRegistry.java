package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.structure.HouseSignBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class SignItemRegistry {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, "britannia_mod");
  private static final Logger LOGGER = LogUtils.getLogger();


    public static final Map<HouseSignBlock.SignType, DeferredHolder<Item, Item>> STORE_SIGN_ITEMS = new HashMap<>();

    public static void register(IEventBus eventBus) {
        for (HouseSignBlock.SignType signType : HouseSignBlock.SignType.values()) {
            if (!SignBlockRegistry.isStoreSign(signType)) continue;
             

                var blockHolder = SignBlockRegistry.STORE_SIGN_BLOCKS.get(signType);
                if (blockHolder == null) {
                    SignBlockRegistry.STORE_SIGN_BLOCKS.forEach((type, holder) -> {
                    });
                    throw new IllegalStateException("Missing block registration for sign type: " + signType);
                }


            String id = "store_sign_" + signType.name().toLowerCase();
            DeferredHolder<Item, Item> item = ITEMS.register(id,
                () -> new BlockItem(blockHolder.get(), new Item.Properties()));

            STORE_SIGN_ITEMS.put(signType, item);
        }

        ITEMS.register(eventBus);
    }
}

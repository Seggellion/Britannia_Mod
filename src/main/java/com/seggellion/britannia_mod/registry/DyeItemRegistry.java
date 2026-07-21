package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubItem;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DyeItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BritanniaMod.MODID);

    private static final Map<ResourceLocation, PigmentId> ITEM_TO_PIGMENT = createMappings();

    public static final DeferredHolder<Item, DyeTubItem> DYE_TUB = ITEMS.register(
            "dye_tub",
            () -> new DyeTubItem(new Item.Properties()
                    .stacksTo(1)
                    .component(DataComponentRegistry.DYE_TUB_STATE.get(), DyeTubState.empty())));

    public static final DeferredHolder<Item, PigmentItem> MADDER_RED = registerPigment("madder_red");
    public static final DeferredHolder<Item, PigmentItem> WOAD_BLUE = registerPigment("woad_blue");
    public static final DeferredHolder<Item, PigmentItem> VERDIGRIS = registerPigment("verdigris");
    public static final DeferredHolder<Item, PigmentItem> WELD_GOLD = registerPigment("weld_gold");
    public static final DeferredHolder<Item, PigmentItem> SOOT_BLACK = registerPigment("soot_black");
    public static final DeferredHolder<Item, PigmentItem> CHALK_WHITE = registerPigment("chalk_white");
    public static final DeferredHolder<Item, PigmentItem> ICE_BLUE = registerPigment("ice_blue");

    private DyeItemRegistry() {
    }

    private static DeferredHolder<Item, PigmentItem> registerPigment(String path) {
        PigmentId pigmentId = PigmentId.of(BritanniaMod.MODID, path);
        return ITEMS.register(path, () -> new PigmentItem(new Item.Properties(), pigmentId));
    }

    private static Map<ResourceLocation, PigmentId> createMappings() {
        Map<ResourceLocation, PigmentId> mappings = new LinkedHashMap<>();
        for (String path : new String[] {
                "madder_red", "woad_blue", "verdigris", "weld_gold",
                "soot_black", "chalk_white", "ice_blue"
        }) {
            ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
            mappings.put(itemId, new PigmentId(itemId));
        }
        return Collections.unmodifiableMap(mappings);
    }

    public static Map<ResourceLocation, PigmentId> itemToPigmentMappings() {
        return ITEM_TO_PIGMENT;
    }

    public static Optional<PigmentId> pigmentId(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        PigmentId mapped = ITEM_TO_PIGMENT.get(itemId);
        if (mapped == null || !(item instanceof PigmentItem pigmentItem) || !mapped.equals(pigmentItem.pigmentId())) {
            return Optional.empty();
        }
        return Optional.of(mapped);
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

// CreativeTabRegistry.java
package com.seggellion.britannia_mod.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class CreativeTabRegistry {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, "britannia_mod");

  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE__ITEM_TAB = CREATIVE_TABS.register(
            "britannia_item_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.britannia_item_tab"))
                    .icon(() -> ItemRegistry.GARLIC.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ItemRegistry.GARLIC.get());
                        output.accept(ItemRegistry.NIGHTSHADE.get());
                        output.accept(ItemRegistry.BLOOD_MOSS.get());
                        output.accept(ItemRegistry.GINSENG.get());
                        output.accept(ItemRegistry.SPIDERS_SILK.get());
                        output.accept(ItemRegistry.SULPHUROUS_ASH.get());
                        output.accept(ItemRegistry.MANDRAKE_ROOT.get());
                        output.accept(ItemRegistry.MOONGATE_BLOCK_ITEM.get());
                        output.accept(ItemRegistry.MOONGATE_TOP_ITEM.get());
                        output.accept(ItemRegistry.MOONGATE_LINKING_WAND.get());
                        output.accept(ItemRegistry.DUNGEON_MOONGATE_BLOCK_ITEM.get());
                        output.accept(ItemRegistry.DUNGEON_MOONGATE_TOP_ITEM.get());
                        output.accept(ItemRegistry.HORSE_SELLER_SPAWN_EGG.get());
                        output.accept(ItemRegistry.MONGBAT_SPAWN_EGG.get());
                        output.accept(ItemRegistry.DAEMON_SPAWN_EGG.get());
                        output.accept(ItemRegistry.LICH_SPAWN_EGG.get());
                        output.accept(ItemRegistry.WRAITH_SPAWN_EGG.get());
                        output.accept(ItemRegistry.GHOUL_SPAWN_EGG.get());
                        output.accept(ItemRegistry.SHADE_SPAWN_EGG.get());
                        output.accept(ItemRegistry.WISP_SPAWN_EGG.get());
                        output.accept(ItemRegistry.EARTH_ELEMENTAL_SPAWN_EGG.get());
                        output.accept(ItemRegistry.SHADE_SPAWN_BLOCK_ITEM.get());
                        output.accept(ItemRegistry.LICH_SPAWN_BLOCK_ITEM.get());
                        output.accept(ItemRegistry.TWO_HANDED_AXE.get());
                        output.accept(ItemRegistry.ORDER_SHIELD.get());
                        output.accept(ItemRegistry.GOLD_COIN.get());
                    }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_MAGIC_TAB = CREATIVE_TABS.register(
            "britannia_tab_magic", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.britannia_magic_tab"))
                    .icon(() -> ItemRegistry.NIGHT_SIGHT_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ItemRegistry.NIGHT_SIGHT_ITEM.get());
                        output.accept(ItemRegistry.HEAL_ITEM.get());
                        output.accept(ItemRegistry.MAGIC_ARROW_ITEM.get());
                        output.accept(ItemRegistry.CLUMSY_ITEM.get());
                        output.accept(ItemRegistry.WEAKNESS_ITEM.get());
                        output.accept(ItemRegistry.CREATE_FOOD_ITEM.get());
                        output.accept(ItemRegistry.FEEBLEMIND_ITEM.get());
                        output.accept(ItemRegistry.REACTIVE_ARMOR_ITEM.get());
                    }).build());



    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}

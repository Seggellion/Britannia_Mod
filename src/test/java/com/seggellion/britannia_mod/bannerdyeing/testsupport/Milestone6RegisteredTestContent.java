package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubItem;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.GameData;

/** A narrow plain-JUnit registration boundary for real ItemStack codecs without a live game server. */
public final class Milestone6RegisteredTestContent {
    public static final ResourceLocation COMPONENT_ID = id("dye_tub_state");
    public static final ResourceLocation TUB_ID = id("dye_tub");
    private static DataComponentType<DyeTubState> component;
    private static DyeTubItem tub;
    private static Map<ResourceLocation, PigmentItem> pigments;

    private Milestone6RegisteredTestContent() {
    }

    public static synchronized void ensureRegistered() {
        if (component != null) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        component = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                COMPONENT_ID,
                DataComponentRegistry.createDyeTubStateType());
        tub = Registry.register(
                BuiltInRegistries.ITEM,
                TUB_ID,
                new DyeTubItem(new Item.Properties().stacksTo(1).component(component, DyeTubState.empty())));

        Map<ResourceLocation, PigmentItem> registered = new LinkedHashMap<>();
        for (String path : new String[] {
                "madder_red", "woad_blue", "verdigris", "weld_gold",
                "soot_black", "chalk_white", "ice_blue"
        }) {
            ResourceLocation itemId = id(path);
            PigmentItem item = Registry.register(
                    BuiltInRegistries.ITEM,
                    itemId,
                    new PigmentItem(new Item.Properties(), new PigmentId(itemId)));
            registered.put(itemId, item);
        }
        pigments = Map.copyOf(registered);
    }

    public static DataComponentType<DyeTubState> component() {
        ensureRegistered();
        return component;
    }

    public static DyeTubItem tub() {
        ensureRegistered();
        return tub;
    }

    public static Map<ResourceLocation, PigmentItem> pigments() {
        ensureRegistered();
        return pigments;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }
}

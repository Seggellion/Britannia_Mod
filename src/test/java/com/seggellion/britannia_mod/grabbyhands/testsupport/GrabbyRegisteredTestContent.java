package com.seggellion.britannia_mod.grabbyhands.testsupport;

import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.GameData;

/**
 * Registers the handful of real mod items the Grabby tests need, without a live game server.
 *
 * <p>Follows the narrow registration boundary {@code bannerdyeing.testsupport} already established:
 * {@code GameData.unfreezeData()} then {@code Registry.register}. Registering the genuine
 * {@link InteriorDecoratorToolItem} matters here — the gesture rule is an {@code instanceof} check,
 * so a stand-in item would test nothing.
 */
public final class GrabbyRegisteredTestContent {
    private static final ResourceLocation DECORATOR_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "grabby_test_interior_decorator_tool");
    private static final ResourceLocation WINE_DATA_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "wine_data");

    private static InteriorDecoratorToolItem decoratorTool;

    private GrabbyRegisteredTestContent() {
    }

    public static synchronized void ensureRegistered() {
        if (decoratorTool != null) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        decoratorTool = Registry.register(
                BuiltInRegistries.ITEM,
                DECORATOR_ID,
                new InteriorDecoratorToolItem(new Item.Properties().stacksTo(1)));
    }

    public static InteriorDecoratorToolItem decoratorTool() {
        ensureRegistered();
        return decoratorTool;
    }

    /**
     * Registers the real {@code britannia_mod:wine_data} component under its production id.
     *
     * <p>Using the production id matters: {@code DataComponentRegistry.WINE_DATA} is a deferred
     * holder that binds by looking itself up in the registry, so registering under any other name
     * would leave every wine helper throwing.
     */
    public static synchronized void ensureWineDataRegistered() {
        ensureRegistered();
        if (BuiltInRegistries.DATA_COMPONENT_TYPE.get(WINE_DATA_ID) == null) {
            Registry.register(
                    BuiltInRegistries.DATA_COMPONENT_TYPE,
                    WINE_DATA_ID,
                    DataComponentRegistry.createWineDataType());
        }
    }
}

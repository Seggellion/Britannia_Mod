package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

public final class MenuRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BritanniaMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ServiceNpcSpawnMenu>> SERVICE_NPC_SPAWN_MENU =
            MENUS.register("service_npc_spawn_menu", () -> IMenuTypeExtension.create((containerId, inventory, buffer) -> {
                var dimensionId = buffer.readResourceLocation();
                BlockPos pos = buffer.readBlockPos();
                UUID spawnPointId = buffer.readUUID();
                return ServiceNpcSpawnMenu.fromNetwork(containerId, inventory, dimensionId, pos, spawnPointId);
            }));

    private MenuRegistry() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}

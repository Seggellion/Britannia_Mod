package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus; // <--- Import this
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class DataComponentRegistry {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = 
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, BritanniaMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WineData>> WINE_DATA = 
        DATA_COMPONENT_TYPES.register("wine_data", () -> 
            DataComponentType.<WineData>builder()
                .persistent(WineData.CODEC)
                .networkSynchronized(WineData.STREAM_CODEC)
                .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeTubState>> DYE_TUB_STATE =
        DATA_COMPONENT_TYPES.register("dye_tub_state", DataComponentRegistry::createDyeTubStateType);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BannerInstanceState>>
            BANNER_INSTANCE_STATE = DATA_COMPONENT_TYPES.register(
                    "banner_instance_state", DataComponentRegistry::createBannerInstanceStateType);

    public static DataComponentType<DyeTubState> createDyeTubStateType() {
        return DataComponentType.<DyeTubState>builder()
                .persistent(DyeTubState.CODEC)
                .networkSynchronized(DyeTubState.STREAM_CODEC)
                .build();
    }

    public static DataComponentType<BannerInstanceState> createBannerInstanceStateType() {
        return DataComponentType.<BannerInstanceState>builder()
                .persistent(BannerInstanceState.CODEC)
                .networkSynchronized(BannerInstanceState.STREAM_CODEC)
                .build();
    }

    // === ADD THIS METHOD ===
    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}

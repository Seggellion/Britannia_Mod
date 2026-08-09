package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
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

    // Milestone 11 NeoForge Slice 1: the physical bank cheque item's display-only data --
    // see BankChequeData's own docs for why this is never authoritative.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BankChequeData>> BANK_CHEQUE_DATA =
        DATA_COMPONENT_TYPES.register("bank_cheque_data", () ->
            DataComponentType.<BankChequeData>builder()
                .persistent(BankChequeData.CODEC)
                .networkSynchronized(BankChequeData.STREAM_CODEC)
                .build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShrineItemState>>
            SHRINE_INSTANCE_STATE = DATA_COMPONENT_TYPES.register(
                    "shrine_instance_state", DataComponentRegistry::createShrineInstanceStateType);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShrineItemState>>
            MONOLITH_INSTANCE_STATE = DATA_COMPONENT_TYPES.register(
                    "monolith_instance_state", DataComponentRegistry::createShrineInstanceStateType);

    public static DataComponentType<ShrineItemState> createShrineInstanceStateType() {
        return DataComponentType.<ShrineItemState>builder()
                .persistent(ShrineItemState.CODEC)
                .networkSynchronized(ShrineItemState.STREAM_CODEC)
                .build();
    }

    // === ADD THIS METHOD ===
    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}

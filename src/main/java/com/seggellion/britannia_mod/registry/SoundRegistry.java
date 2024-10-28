// SoundRegistry.java
package com.seggellion.britannia_mod.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;

public class SoundRegistry {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.SOUND_EVENT, "britannia_mod");

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}

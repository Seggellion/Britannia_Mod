package com.seggellion.britannia_mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, BritanniaMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MOONGATE_HUM = SOUND_EVENTS.register(
            "moongate_hum", 
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":moongate_hum"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> MOONGATE_TELEPORT = SOUND_EVENTS.register(
            "moongate_teleport", 
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":moongate_teleport"))
    );

public static final DeferredHolder<SoundEvent, SoundEvent> HEAL_SPELL_CAST = SOUND_EVENTS.register(
    "heal_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":heal_spell_cast"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_ARROW_SPELL_CAST = SOUND_EVENTS.register(
    "magic_arrow_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":magic_arrow_spell_cast"))
);


}

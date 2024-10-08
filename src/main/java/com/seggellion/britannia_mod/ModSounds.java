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

public static final DeferredHolder<SoundEvent, SoundEvent> CLUMSY_SPELL_CAST = SOUND_EVENTS.register(
    "clumsy_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":clumsy_spell_cast"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> NIGHT_SIGHT_SPELL_CAST = SOUND_EVENTS.register(
    "night_sight_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":night_sight_spell_cast"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> FEEBLEMIND_SPELL_CAST = SOUND_EVENTS.register(
    "feeblemind_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feeblemind_spell_cast"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> WEAKNESS_SPELL_CAST = SOUND_EVENTS.register(
    "weakness_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":weakness_spell_cast"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> CREATE_FOOD_SPELL_CAST = SOUND_EVENTS.register(
    "create_food_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":create_food_spell_cast"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> REACT_ARMOR_SPELL_CAST = SOUND_EVENTS.register(
    "reactive_armor_spell_cast", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":reactive_armor_spell_cast"))
);

}

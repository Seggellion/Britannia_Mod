package com.seggellion.britannia_mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.bus.api.IEventBus;


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

// Adding Mongbat Sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> MONGBAT_AMBIENT = SOUND_EVENTS.register(
            "mongbat_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mongbat_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> MONGBAT_ANGRY = SOUND_EVENTS.register(
            "mongbat_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mongbat_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> MONGBAT_ATTACK = SOUND_EVENTS.register(
            "mongbat_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mongbat_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> MONGBAT_HURT = SOUND_EVENTS.register(
            "mongbat_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mongbat_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> MONGBAT_DEATH = SOUND_EVENTS.register(
            "mongbat_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mongbat_death"))
    );


    // Adding Mongbat Sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> DAEMON_AMBIENT = SOUND_EVENTS.register(
            "daemon_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":daemon_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> DAEMON_ANGRY = SOUND_EVENTS.register(
            "daemon_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":daemon_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> DAEMON_ATTACK = SOUND_EVENTS.register(
            "daemon_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":daemon_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> DAEMON_HURT = SOUND_EVENTS.register(
            "daemon_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":daemon_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> DAEMON_DEATH = SOUND_EVENTS.register(
            "daemon_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":daemon_death"))
    );

    // Register method to hook into the mod event bus
    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

}

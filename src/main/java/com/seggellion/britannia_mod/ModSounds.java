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

public static final DeferredHolder<SoundEvent, SoundEvent> TRANSACTION = SOUND_EVENTS.register(
    "transaction", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":transaction"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> MINING1 = SOUND_EVENTS.register(
    "mining1", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mining1"))
);

public static final DeferredHolder<SoundEvent, SoundEvent> MINING2 = SOUND_EVENTS.register(
    "mining2", 
    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":mining2"))
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

      // Adding Lich Sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> LICH_AMBIENT = SOUND_EVENTS.register(
            "lich_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lich_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> LICH_ANGRY = SOUND_EVENTS.register(
            "lich_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lich_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> LICH_ATTACK = SOUND_EVENTS.register(
            "lich_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lich_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> LICH_HURT = SOUND_EVENTS.register(
            "lich_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lich_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> LICH_DEATH = SOUND_EVENTS.register(
            "lich_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lich_death"))
    );

         // Adding ghost monster Sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_AMBIENT = SOUND_EVENTS.register(
            "ghost_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":ghost_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_ANGRY = SOUND_EVENTS.register(
            "ghost_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":ghost_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_ATTACK = SOUND_EVENTS.register(
            "ghost_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":ghost_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_HURT = SOUND_EVENTS.register(
            "ghost_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":ghost_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_DEATH = SOUND_EVENTS.register(
            "ghost_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":ghost_death"))
    );

      // Adding Earth Elemental monster Sounds

    public static final DeferredHolder<SoundEvent, SoundEvent> EARTH_ELEMENTAL_AMBIENT = SOUND_EVENTS.register(
            "earth_elemental_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":earth_elemental_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> EARTH_ELEMENTAL_ANGRY = SOUND_EVENTS.register(
            "earth_elemental_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":earth_elemental_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> EARTH_ELEMENTAL_ATTACK = SOUND_EVENTS.register(
            "earth_elemental_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":earth_elemental_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> EARTH_ELEMENTAL_HURT = SOUND_EVENTS.register(
            "earth_elemental_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":earth_elemental_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> EARTH_ELEMENTAL_DEATH = SOUND_EVENTS.register(
            "earth_elemental_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":earth_elemental_death"))
    );


  // Adding Rat Sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> RAT_AMBIENT = SOUND_EVENTS.register(
            "rat_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":rat_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> RAT_ANGRY = SOUND_EVENTS.register(
            "rat_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":rat_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> RAT_ATTACK = SOUND_EVENTS.register(
            "rat_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":rat_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> RAT_HURT = SOUND_EVENTS.register(
            "rat_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":rat_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> RAT_DEATH = SOUND_EVENTS.register(
            "rat_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":rat_death"))
    );

  // Adding Wisp Sounds

    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_AMBIENT = SOUND_EVENTS.register(
            "wisp_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":wisp_ambient"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_ANGRY = SOUND_EVENTS.register(
            "wisp_angry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":wisp_angry"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_ATTACK = SOUND_EVENTS.register(
            "wisp_attack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":wisp_attack"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_HURT = SOUND_EVENTS.register(
            "wisp_hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":wisp_hurt"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_DEATH = SOUND_EVENTS.register(
            "wisp_death",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":wisp_death"))
    );

    // Added gold coin sound

        public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_COIN = SOUND_EVENTS.register(
            "gold_coin",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":gold_coin"))
    );

        // Added catch fish sound

        public static final DeferredHolder<SoundEvent, SoundEvent> CATCH_FISH = SOUND_EVENTS.register(
            "catch_fish",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":catch_fish"))
    );

        // Added tree chop sound

        public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_TREE = SOUND_EVENTS.register(
            "chop_tree",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":chop_tree"))
    );


// structure sounds

        public static final DeferredHolder<SoundEvent, SoundEvent> METAL_DOOR_OPEN = SOUND_EVENTS.register(
            "metal_door_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_metal_open"))
    );

        public static final DeferredHolder<SoundEvent, SoundEvent> METAL_DOOR_CLOSE = SOUND_EVENTS.register(
            "metal_door_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_metal_close"))
    );

    // Register method to hook into the mod event bus
    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

}

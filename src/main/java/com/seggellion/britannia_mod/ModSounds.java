package com.seggellion.britannia_mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.bus.api.IEventBus;

import java.util.Map;
import java.util.HashMap;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, BritanniaMod.MODID);

    // This map lets our base entity classes look up their sounds automatically!
    public static final Map<String, EntitySoundGroup> ENTITY_SOUNDS = new HashMap<>();

    public record EntitySoundGroup(
            DeferredHolder<SoundEvent, SoundEvent> ambient,
            DeferredHolder<SoundEvent, SoundEvent> angry,
            DeferredHolder<SoundEvent, SoundEvent> attack,
            DeferredHolder<SoundEvent, SoundEvent> hurt,
            DeferredHolder<SoundEvent, SoundEvent> death
    ) {}

    // The Magic Helper Method
    private static EntitySoundGroup registerEntitySounds(String name) {
        EntitySoundGroup group = new EntitySoundGroup(
            SOUND_EVENTS.register(name + "_ambient", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name + "_ambient"))),
            SOUND_EVENTS.register(name + "_angry", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name + "_angry"))),
            SOUND_EVENTS.register(name + "_attack", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name + "_attack"))),
            SOUND_EVENTS.register(name + "_hurt", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name + "_hurt"))),
            SOUND_EVENTS.register(name + "_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, name + "_death")))
        );
        
        ENTITY_SOUNDS.put(name, group);
        return group;
    }

    // =========================================
    // AUTOMATED ENTITY SOUND REGISTRATIONS
    // =========================================
    // The Original List
    public static final EntitySoundGroup MONGBAT = registerEntitySounds("mongbat");
    public static final EntitySoundGroup LICH = registerEntitySounds("lich");
    public static final EntitySoundGroup DAEMON = registerEntitySounds("daemon");
    public static final EntitySoundGroup GHOST = registerEntitySounds("ghost");
    public static final EntitySoundGroup EARTH_ELEMENTAL = registerEntitySounds("earth_elemental");
    public static final EntitySoundGroup RAT = registerEntitySounds("rat");
    public static final EntitySoundGroup WISP = registerEntitySounds("wisp");

    // The New Batch
        public static final EntitySoundGroup HIND = registerEntitySounds("hind");
    public static final EntitySoundGroup GREAT_HART = registerEntitySounds("great_hart");
    public static final EntitySoundGroup BEAR_BROWN = registerEntitySounds("bear_brown");
    public static final EntitySoundGroup BEAR_BLACK = registerEntitySounds("bear_black");
    public static final EntitySoundGroup BEAR_GRIZZLY = registerEntitySounds("bear_grizzly");
    public static final EntitySoundGroup BEAR_POLAR = registerEntitySounds("bear_polar");
    public static final EntitySoundGroup GORILLA = registerEntitySounds("gorilla");
    public static final EntitySoundGroup TURKEY = registerEntitySounds("turkey");

    public static final DeferredHolder<SoundEvent, SoundEvent> FLAMINGO_AMBIENT = SOUND_EVENTS.register(
            "flamingo_ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "flamingo_ambient"))
    );


    public static final EntitySoundGroup GIANT_RAT = registerEntitySounds("giant_rat");
    public static final EntitySoundGroup ALLIGATOR = registerEntitySounds("alligator");
    public static final EntitySoundGroup ETTIN = registerEntitySounds("ettin");
    public static final EntitySoundGroup SERPENT_GIANT = registerEntitySounds("serpent_giant");
        public static final EntitySoundGroup SERPENT_SILVER = registerEntitySounds("serpent_silver");
public static final EntitySoundGroup SERPENT_LAVA = registerEntitySounds("serpent_lava");
public static final EntitySoundGroup SERPENT_GIANT_ICE = registerEntitySounds("serpent_giant_ice");
public static final EntitySoundGroup SERPENT_SEA = registerEntitySounds("serpent_sea");
public static final EntitySoundGroup SERPENT_DEEP_SEA = registerEntitySounds("serpent_deep_sea");
public static final EntitySoundGroup SERPENT_CRYSTAL_SEA= registerEntitySounds("serpent_crystal_sea");

    public static final EntitySoundGroup SCORPION = registerEntitySounds("scorpion");
    public static final EntitySoundGroup LIZARDMAN = registerEntitySounds("lizardman");
    public static final EntitySoundGroup RATMAN = registerEntitySounds("ratman");
public static final EntitySoundGroup RATMAN_ARCHER = registerEntitySounds("ratman_archer");
public static final EntitySoundGroup RATMAN_ASSASSIN = registerEntitySounds("ratman_assassin");

    public static final EntitySoundGroup ELEMENTAL_AIR = registerEntitySounds("elemental_air");
public static final EntitySoundGroup ELEMENTAL_FIRE = registerEntitySounds("elemental_fire");
public static final EntitySoundGroup ELEMENTAL_WATER = registerEntitySounds("elemental_water");
public static final EntitySoundGroup ELEMENTAL_POISON = registerEntitySounds("elemental_poison");
public static final EntitySoundGroup ELEMENTAL_ACID = registerEntitySounds("elemental_acid");
public static final EntitySoundGroup ELEMENTAL_BLOOD = registerEntitySounds("elemental_blood");

    public static final EntitySoundGroup HARPY = registerEntitySounds("harpy");
    public static final EntitySoundGroup HEADLESS = registerEntitySounds("headless");
    public static final EntitySoundGroup ORC = registerEntitySounds("orc");
        public static final EntitySoundGroup ORC_LORD = registerEntitySounds("orc_lord");
        public static final EntitySoundGroup ORC_CLUB = registerEntitySounds("orc_club");
        public static final EntitySoundGroup OGRE = registerEntitySounds("ogre");
public static final EntitySoundGroup OGRE_ARCTIC = registerEntitySounds("ogre_arctic");
    public static final EntitySoundGroup OGRE_LORD = registerEntitySounds("ogre_lord");
    public static final EntitySoundGroup OGRE_LORD_ARCTIC = registerEntitySounds("ogre_lord_arctic");
    public static final EntitySoundGroup TROLL = registerEntitySounds("troll");
    public static final EntitySoundGroup GARGOYLE = registerEntitySounds("gargoyle");
public static final EntitySoundGroup GARGOYLE_ENFORCER = registerEntitySounds("gargoyle_enforcer");
public static final EntitySoundGroup GARGOYLE_DESTROYER = registerEntitySounds("gargoyle_destroyer");
public static final EntitySoundGroup GARGOYLE_STONE = registerEntitySounds("gargoyle_stone");
        public static final EntitySoundGroup WRAITH = registerEntitySounds("wraith");
        public static final EntitySoundGroup GHOUL = registerEntitySounds("ghoul");
        public static final EntitySoundGroup SHADE = registerEntitySounds("shade");

    // =========================================
    // ENVIRONMENT, SPELLS, AND UI SOUNDS
    // =========================================
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

    public static final DeferredHolder<SoundEvent, SoundEvent> GOLD_COIN = SOUND_EVENTS.register(
            "gold_coin",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":gold_coin"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> CATCH_FISH = SOUND_EVENTS.register(
            "catch_fish",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":catch_fish"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP_TREE = SOUND_EVENTS.register(
            "chop_tree",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":chop_tree"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> CHEST_OPEN = SOUND_EVENTS.register(
            "chest_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":chest_open"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> CHEST_CLOSE = SOUND_EVENTS.register(
            "chest_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":chest_close"))
    );

    // Bank Box feedback (owner, 2026-08-04). Event names say what happened, not which file
    // plays -- the files are hit02.ogg and leather1.ogg, mapped in sounds.json, and swapping
    // the audio later must not mean renaming a code constant.
    public static final DeferredHolder<SoundEvent, SoundEvent> BANK_DEPOSIT = SOUND_EVENTS.register(
            "bank_deposit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":bank_deposit"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> BANK_WITHDRAW = SOUND_EVENTS.register(
            "bank_withdraw",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":bank_withdraw"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> TRAINING_DUMMY_HIT = SOUND_EVENTS.register(
            "training_dummy_hit",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "training_dummy_hit"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_DOOR_OPEN = SOUND_EVENTS.register(
            "door_metal_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_metal_open"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_DOOR_CLOSE = SOUND_EVENTS.register(
            "door_metal_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_metal_close"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WOOD_DOOR_OPEN = SOUND_EVENTS.register(
            "door_wood_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_wood_open"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WOOD_DOOR_CLOSE = SOUND_EVENTS.register(
            "door_wood_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_wood_close"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_LOCK = SOUND_EVENTS.register(
            "door_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":door_lock"))
    );

        public static final DeferredHolder<SoundEvent, SoundEvent> LOCKPICK_ATTEMPT = SOUND_EVENTS.register(
            "lockpick_attempt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lockpick_attempt"))
    );

    // 1. Lap Harp
    public static final DeferredHolder<SoundEvent, SoundEvent> LAP_HARP_PLAY = SOUND_EVENTS.register(
            "lap_harp_play",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lap_harp_play"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> LAP_HARP_FAIL = SOUND_EVENTS.register(
            "lap_harp_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lap_harp_fail"))
    );

    // 2. Standing Harp
    public static final DeferredHolder<SoundEvent, SoundEvent> STANDING_HARP_PLAY = SOUND_EVENTS.register(
            "standing_harp_play",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":standing_harp_play"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> STANDING_HARP_FAIL = SOUND_EVENTS.register(
            "standing_harp_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":standing_harp_fail"))
    );

    // 3. Lute
    public static final DeferredHolder<SoundEvent, SoundEvent> LUTE_PLAY = SOUND_EVENTS.register(
            "lute_play",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lute_play"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> LUTE_FAIL = SOUND_EVENTS.register(
            "lute_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":lute_fail"))
    );

    // 4. Drums
    public static final DeferredHolder<SoundEvent, SoundEvent> DRUM_PLAY = SOUND_EVENTS.register(
            "drum_play",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":drum_play"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> DRUM_FAIL = SOUND_EVENTS.register(
            "drum_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":drum_fail"))
    );

    // 5. Violin
    public static final DeferredHolder<SoundEvent, SoundEvent> VIOLIN_PLAY = SOUND_EVENTS.register(
            "violin_play",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":violin_play"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> VIOLIN_FAIL = SOUND_EVENTS.register(
            "violin_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":violin_fail"))
    );

    // 6. Tamborine
    public static final DeferredHolder<SoundEvent, SoundEvent> TAMBORINE_PLAY = SOUND_EVENTS.register(
            "tamborine_play",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":tamborine_play"))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> TAMBORINE_FAIL = SOUND_EVENTS.register(
            "tamborine_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":tamborine_fail"))
    );

        public static final DeferredHolder<SoundEvent, SoundEvent> FEET12A = SOUND_EVENTS.register(
            "feet12a",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feet12a"))
    );

public static final DeferredHolder<SoundEvent, SoundEvent> FEET12B = SOUND_EVENTS.register(
            "feet12b",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feet12b"))
    );

            public static final DeferredHolder<SoundEvent, SoundEvent> FEET13A = SOUND_EVENTS.register(
            "feet13a",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feet13a"))
    );

public static final DeferredHolder<SoundEvent, SoundEvent> FEET13B = SOUND_EVENTS.register(
            "feet13b",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feet13b"))
    );

            public static final DeferredHolder<SoundEvent, SoundEvent> FEET15C = SOUND_EVENTS.register(
            "feet15c",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feet15c"))
    );

public static final DeferredHolder<SoundEvent, SoundEvent> FEET15D = SOUND_EVENTS.register(
            "feet15d",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":feet15d"))
    );



    public static final DeferredHolder<SoundEvent, SoundEvent> ANVIL = SOUND_EVENTS.register(
            "anvil",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(BritanniaMod.MODID + ":anvil"))
    );


    // Register method to hook into the mod event bus
    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}

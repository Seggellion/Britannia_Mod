// ModAttributes.java
package com.seggellion.britannia_mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

public class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, BritanniaMod.MODID);

    public static final DeferredHolder<Attribute, Attribute> SCALE = ATTRIBUTES.register(
            "generic.scale",
            () -> new RangedAttribute("attribute.name.britannia_mod.generic.scale", 1.0D, 0.0D, 1024.0D).setSyncable(true)
    );

    public static final DeferredHolder<Attribute, Attribute> GRAVITY = ATTRIBUTES.register(
            "generic.gravity",
            () -> new RangedAttribute("attribute.name.britannia_mod.generic.gravity", 0.08D, -1.0D, 1.0D).setSyncable(true)
    );

        public static final DeferredHolder<Attribute, Attribute> FALL_DAMAGE_MULTIPLIER = ATTRIBUTES.register(
        "generic.fall_damage_multiplier",
        () -> new RangedAttribute("attribute.name.britannia_mod.generic.fall_damage_multiplier", 1.0D, 0.0D, 1024.0D).setSyncable(true)
        );
        

    public static final DeferredHolder<Attribute, Attribute> STEP_HEIGHT = ATTRIBUTES.register(
            "generic.step_height",
            () -> new RangedAttribute("attribute.name.britannia_mod.generic.step_height", 0.6D, 0.0D, 1.0D).setSyncable(true)
    );

    public static final DeferredHolder<Attribute, Attribute> MOVEMENT_EFFICIENCY = ATTRIBUTES.register(
            "generic.movement_efficiency",
            () -> new RangedAttribute("attribute.name.britannia_mod.generic.movement_efficiency", 1.0D, 0.0D, 10.0D).setSyncable(true)
    );

    public static final DeferredHolder<Attribute, Attribute> BURNING_TIME = ATTRIBUTES.register(
            "generic.burning_time",
            () -> new RangedAttribute("attribute.name.britannia_mod.generic.burning_time", 1.0D, 0.0D, 1024.0D).setSyncable(true)
    );

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
    }
}

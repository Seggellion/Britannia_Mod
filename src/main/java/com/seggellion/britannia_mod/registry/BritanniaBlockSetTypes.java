// BritanniaBlockSetTypes.java
package com.seggellion.britannia_mod.registry;

import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType.PressurePlateSensitivity;
import com.seggellion.britannia_mod.ModSounds;

public class BritanniaBlockSetTypes {

    public static final BlockSetType METAL_DOOR = new BlockSetType(
        "metal_door",                         // name
        true,                                  // canOpenByHand
        false,                                 // canOpenByWindCharge (you probably don't want doors flying open from wind, right?)
        false,                                 // canButtonBeActivatedByArrows (false is fine unless you want arrow buttons)
        PressurePlateSensitivity.EVERYTHING,   // how sensitive pressure plates are (use default EVERYTHING)
        SoundType.METAL,                       // fallback sound (for walking, hitting)
        ModSounds.METAL_DOOR_CLOSE.get(),       // doorClose
        ModSounds.METAL_DOOR_OPEN.get(),        // doorOpen
        ModSounds.METAL_DOOR_CLOSE.get(),       // trapdoorClose (reuse close sound)
        ModSounds.METAL_DOOR_OPEN.get(),        // trapdoorOpen (reuse open sound)
        ModSounds.METAL_DOOR_CLOSE.get(),       // pressurePlateClickOff (reuse close)
        ModSounds.METAL_DOOR_OPEN.get(),        // pressurePlateClickOn (reuse open)
        ModSounds.METAL_DOOR_CLOSE.get(),       // buttonClickOff (reuse close)
        ModSounds.METAL_DOOR_OPEN.get()         // buttonClickOn (reuse open)
    );


    public static final BlockSetType WOOD_DOOR = new BlockSetType(
        "wood_door", 
        true,
        false,
        false,
        PressurePlateSensitivity.EVERYTHING,
        SoundType.WOOD,
        ModSounds.WOOD_DOOR_CLOSE.get(),
        ModSounds.WOOD_DOOR_OPEN.get(),
        ModSounds.WOOD_DOOR_CLOSE.get(),
        ModSounds.WOOD_DOOR_OPEN.get(), 
        ModSounds.WOOD_DOOR_CLOSE.get(),
        ModSounds.WOOD_DOOR_OPEN.get(), 
        ModSounds.WOOD_DOOR_CLOSE.get(),
        ModSounds.WOOD_DOOR_OPEN.get()
    );


}

package com.seggellion.britannia_mod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, remap = false)
public class PlayerSleepMixin {

    @Overwrite
    public int getSleepTimer() {
        return 0 ; // Force sleep timer to always be 0 on client
    }



    // Modify constant 100 in tick method
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 100))
    private int modifyTickSleepDuration(int original) {
        return 6000 ;
    }

    // Modify constant 100 in isSleepingLongEnough method
    @ModifyConstant(method = "isSleepingLongEnough", constant = @Constant(intValue = 100))
    private int modifyIsSleepingLongEnough(int original) {
        return 6000 ;
    }

    // Modify constant 100 in stopSleepInBed method
    @ModifyConstant(method = "stopSleepInBed", constant = @Constant(intValue = 100))
    private int modifyStopSleepInBed(int original) {
        return 6000 ;
    }
}
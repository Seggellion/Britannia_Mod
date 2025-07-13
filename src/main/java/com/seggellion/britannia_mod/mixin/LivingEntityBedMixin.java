package com.seggellion.britannia_mod.mixin;

import com.seggellion.britannia_mod.block.DoubleBedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
public class LivingEntityBedMixin {
    
    @Inject(method = "setPosToBed", at = @At("HEAD"), cancellable = true)
    private void preventDoubleBedPositioning(BlockPos bedPos, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        BlockState bedState = entity.level().getBlockState(bedPos);
        if (bedState.getBlock() instanceof DoubleBedBlock) {
            ci.cancel();
        }
    }
}
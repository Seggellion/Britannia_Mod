package com.seggellion.britannia_mod.mixin;

import com.seggellion.britannia_mod.block.WoodenFenceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Narrow exception for the custom edge fence; vanilla path and shovel rules stay intact. */
@Mixin(value = DirtPathBlock.class, remap = false)
public abstract class DirtPathCustomFenceMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void britannia$customFenceSurvival(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (level.getBlockState(pos.above()).getBlock() instanceof WoodenFenceBlock) callback.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void britannia$retainPathUnderFence(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (level.getBlockState(pos.above()).getBlock() instanceof WoodenFenceBlock) callback.cancel();
    }
}

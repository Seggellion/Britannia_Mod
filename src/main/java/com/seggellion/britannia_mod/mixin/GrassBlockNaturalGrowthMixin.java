package com.seggellion.britannia_mod.mixin;

import com.seggellion.britannia_mod.vegetation.ManagedVegetationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses vanilla grass random ticks as a bounded, chunk-local source of natural managed nodes. */
@Mixin(SpreadingSnowyDirtBlock.class)
public abstract class GrassBlockNaturalGrowthMixin {
    @Inject(method = "randomTick", at = @At("TAIL"), remap = false)
    private void britannia$tryNaturalVegetation(
            BlockState state,
            ServerLevel level,
            BlockPos grassPosition,
            RandomSource random,
            CallbackInfo callback
    ) {
        if (state.is(Blocks.GRASS_BLOCK) && level.getBlockState(grassPosition).is(Blocks.GRASS_BLOCK)) {
            ManagedVegetationService.tryRegisterNaturalNode(level, grassPosition.above(), random);
        }
    }
}

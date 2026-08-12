package com.seggellion.britannia_mod.mixin.client;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationCutTools;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets sword attacks reach the server, where managed-node ownership is authoritatively checked. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ManagedVegetationAdventureModeMixin {
    @Redirect(
            method = "startDestroyBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;blockActionRestricted("
                            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/GameType;)Z"
            ),
            remap = false
    )
    private boolean britannia$allowManagedVegetationSwordAttack(
            LocalPlayer player,
            Level level,
            BlockPos position,
            GameType gameType
    ) {
        BlockState state = level.getBlockState(position);
        if (gameType == GameType.ADVENTURE
                && ManagedVegetationCutTools.isSword(player.getMainHandItem())
                && isManagedVegetationCandidate(state)) {
            return false;
        }
        return player.blockActionRestricted(level, position, gameType);
    }

    private static boolean isManagedVegetationCandidate(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(BlockRegistry.MANAGED_FLOWER.get());
    }
}

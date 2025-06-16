package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.BritanniaBlockSetTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;


public class MetalDoorBlock extends DoorBlock {

    public MetalDoorBlock() {
        super(
            BritanniaBlockSetTypes.METAL_DOOR,
            Properties.of()
                      .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                      .strength(5.0F)
                      .noOcclusion()
                      .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                      .sound(SoundType.METAL)
        );
    }

    /* No override of getStateForPlacement() – vanilla handles it */

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {

        if (!this.type().canOpenByHand()) {
            return InteractionResult.PASS;
        }

        boolean open = state.getValue(OPEN);
        state = state.setValue(OPEN, !open);
        level.setBlock(pos, state, 10);

        level.playSound(
            null, pos,
            open ? this.type().doorClose() : this.type().doorOpen(),
            net.minecraft.sounds.SoundSource.BLOCKS,
            1.0F, 1.0F
        );
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
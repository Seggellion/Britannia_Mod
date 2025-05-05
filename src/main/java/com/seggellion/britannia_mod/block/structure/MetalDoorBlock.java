package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.SoundType;
import com.seggellion.britannia_mod.registry.BritanniaBlockSetTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public class MetalDoorBlock extends DoorBlock {
    public MetalDoorBlock() {
        super(
            BritanniaBlockSetTypes.METAL_DOOR,
            BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                .strength(5.0F)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.METAL)
        );
    }

    @Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!this.type().canOpenByHand()) {
        return InteractionResult.PASS;
    }

    boolean currentlyOpen = state.getValue(OPEN);
    BlockState newState = state.setValue(OPEN, !currentlyOpen);
    level.setBlock(pos, newState, 10);

    // ✅ Play the correct custom sound based on open/close
    level.playSound(
        null, pos,
        currentlyOpen ? this.type().doorClose() : this.type().doorOpen(),
        net.minecraft.sounds.SoundSource.BLOCKS,
        1.0f,
        1.0f
    );

    return InteractionResult.sidedSuccess(level.isClientSide);
}

}

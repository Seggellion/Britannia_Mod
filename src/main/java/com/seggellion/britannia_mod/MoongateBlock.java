package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;  // Correct import for Vec3
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.sounds.SoundEvent; // Add this import
import net.minecraft.util.RandomSource; // Update for RandomSource
import net.minecraft.core.particles.ParticleTypes;

import org.slf4j.Logger;


public class MoongateBlock extends Block {
        private static final Logger LOGGER = LogUtils.getLogger();

    public MoongateBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(-1.0F, 3600000.0F) // Unbreakable and explosion-proof
                .noLootTable() // No drops when broken
                .noCollission() // Players can walk through
                .lightLevel((state) -> 15) // Emits maximum light
                .sound(SoundType.GLASS) // Sound type when interacted
        );
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Use custom model rendering
        return RenderShape.MODEL;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
        // Moongate can float in the air
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // Cannot be pushed by pistons
        return PushReaction.BLOCK;
    }



@Override
public void entityInside(BlockState state, Level worldIn, BlockPos pos, net.minecraft.world.entity.Entity entityIn) {
    if (!worldIn.isClientSide && entityIn instanceof ServerPlayer player) {
        // Teleport the player on server side
        MoongateTeleportationHandler.teleportPlayer(player);

        // Log the SoundEvent
        SoundEvent soundEvent = ModSounds.MOONGATE_TELEPORT.get();

        // Check if the SoundEvent is null
        if (soundEvent == null) {
            LOGGER.error("SoundEvent MOONGATE_TELEPORT is null!");
        }

worldIn.playSound(
    null, // No specific player; null will send to all players (but we can restrict it)
    player.getX(), player.getY(), player.getZ(),
    soundEvent,
    net.minecraft.sounds.SoundSource.PLAYERS,
    1.0F,
    1.0F
);
    }
}



@Override
public void animateTick(BlockState stateIn, Level worldIn, BlockPos pos, RandomSource rand) {
    if (rand.nextFloat() < 0.1F) {
        SoundEvent soundEvent = ModSounds.MOONGATE_HUM.get();

        if (soundEvent == null) {
            LOGGER.error("SoundEvent MOONGATE_HUM is null!");
        }
        worldIn.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 1); // Blue-tinted glow particles

        worldIn.playLocalSound(
            pos,
            soundEvent,
            net.minecraft.sounds.SoundSource.BLOCKS,
            0.5F,
            1.0F,
            false
        );
    }
}

}

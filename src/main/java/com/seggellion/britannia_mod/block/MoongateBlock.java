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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MoongateBlock extends Block {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> playersOnMoongate = new HashSet<>();

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
    public void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
        if (!worldIn.isClientSide && entityIn instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();

            // If the player is not directly on the moongate block, remove them from the tracking set
            if (!player.blockPosition().equals(pos)) {
                playersOnMoongate.remove(playerUUID);
                return;
            }

            // If the player is already in the set, do not teleport them again
            if (playersOnMoongate.contains(playerUUID)) {
                return;
            }

            // Add player to the set IMMEDIATELY so they don't trigger this multiple times in the same tick
            playersOnMoongate.add(playerUUID);

            // ==========================================
            // DEFER TELEPORTATION TO AVOID MOVEMENT DESYNC
            // ==========================================
            worldIn.getServer().execute(() -> {
                // Teleport the player safely outside of the collision loop
                MoongateTeleportationHandler.teleportPlayer(player);

                // Log and play the SoundEvent at the destination
                SoundEvent soundEvent = ModSounds.MOONGATE_TELEPORT.get();
                if (soundEvent == null) {
                    LOGGER.error("SoundEvent MOONGATE_TELEPORT is null!");
                } else {
                    worldIn.playSound(
                        null, 
                        player.getX(), player.getY(), player.getZ(),
                        soundEvent,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F,
                        1.0F
                    );
                }
            });
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

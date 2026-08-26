package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.entity.MoongateBlockEntity;
import com.seggellion.britannia_mod.client.sound.MoongateAmbientSoundManager;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MoongateBlock extends Block implements EntityBlock {
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
        // The permanent gate is rendered only by its camera-facing block-entity renderer.
        // Summoning/emergence floor geometry is deliberately not part of this render path.
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoongateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != BlockRegistry.MOONGATE_BLOCK_ENTITY_TYPE.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) ->
                MoongateAmbientSoundManager.tick(tickLevel, tickPos);
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
        if (worldIn.isClientSide) return;

        // Determine if the entity is a player, OR if the entity is a mount being ridden by a player
        ServerPlayer player = null;
        if (entityIn instanceof ServerPlayer sp) {
            player = sp;
        } else if (entityIn.getFirstPassenger() instanceof ServerPlayer sp) {
            player = sp;
        }

        if (player != null) {
            UUID playerUUID = player.getUUID();

            // Check position against the ROOT entity (the player or the horse) touching the portal
            if (!entityIn.blockPosition().equals(pos)) {
                playersOnMoongate.remove(playerUUID);
                return;
            }

            // If the player is already in the set, do not teleport them again
            if (playersOnMoongate.contains(playerUUID)) {
                return;
            }

            // Add player to the set IMMEDIATELY
            playersOnMoongate.add(playerUUID);

            // ==========================================
            // DEFER TELEPORTATION TO AVOID MOVEMENT DESYNC
            // ==========================================
            // FIX: Create a guaranteed final reference for the lambda to use
            final ServerPlayer finalPlayer = player; 

            worldIn.getServer().execute(() -> {
                // Use finalPlayer inside this block instead of player
                MoongateTeleportationHandler.teleportPlayer(finalPlayer);

                SoundEvent soundEvent = ModSounds.MOONGATE_TELEPORT.get();
                if (soundEvent == null) {
                    LOGGER.error("SoundEvent MOONGATE_TELEPORT is null!");
                } else {
                    worldIn.playSound(
                        null, 
                        finalPlayer.getX(), finalPlayer.getY(), finalPlayer.getZ(),
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
            worldIn.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 1); // Blue-tinted glow particles
        }
    }
}

package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.DungeonMoongateBlockEntity;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.teleport.TeleportResult;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;

import org.slf4j.Logger;

public class DungeonMoongateBlock extends Block implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    public DungeonMoongateBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noCollission()
                .lightLevel((state) -> 10)
                .sound(SoundType.GLASS)
        );
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
        // Moongate can float in the air
        return true;
    }

    @Override
public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    super.onPlace(state, level, pos, oldState, isMoving);
    // Place the top block above
    BlockPos abovePos = pos.above();
    level.setBlock(abovePos, BlockRegistry.DUNGEON_MOONGATE_TOP.get().defaultBlockState(), 3);
}


    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DungeonMoongateBlockEntity moongateEntity) {
                TeleportResult result = moongateEntity.teleportPlayer(player);
                if (result.success()) {

                    SoundEvent soundEvent = ModSounds.MOONGATE_TELEPORT.get();
                    if (soundEvent == null) {
                        LOGGER.error("SoundEvent MOONGATE_TELEPORT is null!");
                    } else {
                        level.playSound(
                            null, // No specific player; null will send to all players
                            player.getX(), player.getY(), player.getZ(),
                            soundEvent,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F
                        );
                    }
                }
            }
        }
    }

    @Override
    public void animateTick(BlockState stateIn, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextFloat() < 0.1F) {
            SoundEvent soundEvent = ModSounds.MOONGATE_HUM.get();
            if (soundEvent == null) {
                LOGGER.error("SoundEvent MOONGATE_HUM is null!");
            } else {
                level.playLocalSound(
                    pos,
                    soundEvent,
                    SoundSource.BLOCKS,
                    0.5F,
                    1.0F,
                    false
                );
            }
        }

        // Add particles
        double x = pos.getX() + 0.5 + (rand.nextDouble() - 0.5);
        double y = pos.getY() + 0.5 + rand.nextDouble();
        double z = pos.getZ() + 0.5 + (rand.nextDouble() - 0.5);

        level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0, 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonMoongateBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DungeonMoongateBlockEntity moongateEntity) {
                moongateEntity.unlinkPairedMoongate();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }

    BlockPos abovePos = pos.above();
    BlockState aboveState = level.getBlockState(abovePos);
    if (aboveState.getBlock() == BlockRegistry.DUNGEON_MOONGATE_TOP.get()) {
        level.destroyBlock(abovePos, false);
    }

    }
}

package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.block.DoubleBedBlock;
import com.seggellion.britannia_mod.block.nudgeable.NudgeableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class DoubleBedBlockEntity extends NudgeableBlockEntity {
    private UUID leftSleeper = null;
    private UUID rightSleeper = null;

    public DoubleBedBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.DOUBLE_BED.get(), pos, state);
    }

    public InteractionResult onPlayerInteract(Player player, BlockPos clickedPos, BlockState clickedState) {
        if (!level.dimensionType().bedWorks()) {
            Vec3 center = clickedPos.getCenter();
            level.explode(null, center.x, center.y, center.z, 5.0F, true, Level.ExplosionInteraction.BLOCK);
            return InteractionResult.SUCCESS;
        }

        DoubleBedBlock.BedPart part = clickedState.getValue(DoubleBedBlock.PART);
        boolean isLeftSide = (part == DoubleBedBlock.BedPart.FOOT_LEFT || part == DoubleBedBlock.BedPart.HEAD_LEFT);

        UUID playerUUID = player.getUUID();

        if (playerUUID.equals(leftSleeper) || playerUUID.equals(rightSleeper)) {
            wakeUpPlayer(player);
            return InteractionResult.SUCCESS;
        }

        UUID currentSleeper = isLeftSide ? leftSleeper : rightSleeper;
        if (currentSleeper != null) {
            player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
            return InteractionResult.SUCCESS;
        }

        var result = player.startSleepInBed(clickedPos);
        Player.BedSleepingProblem problem = result.left().orElse(null);

        if (problem != null) {
            if (problem.getMessage() != null) {
                player.displayClientMessage(problem.getMessage(), true);
            }
            return InteractionResult.FAIL;
        } else {
            if (isLeftSide) {
                leftSleeper = playerUUID;
            } else {
                rightSleeper = playerUUID;
            }

            updateOccupiedStates(clickedState);
            setChanged();

            // Position player AFTER setting sleep state
            if (player instanceof ServerPlayer serverPlayer) {
                positionPlayerInBed(serverPlayer, clickedPos, isLeftSide);
            }

            return InteractionResult.SUCCESS;
        }
    }

    private void positionPlayerInBed(ServerPlayer player, BlockPos clickedPos, boolean isLeftSide) {
        BlockState clickedState = level.getBlockState(clickedPos);
        Direction facing = clickedState.getValue(DoubleBedBlock.FACING);
        DoubleBedBlock.BedPart clickedPart = clickedState.getValue(DoubleBedBlock.PART);

        // Get the MASTER position (HEAD_LEFT) and its offset
        BlockPos masterPos = getMasterPosition(clickedPos, clickedState);
        Vec3 masterOffset = Vec3.ZERO;
        BlockEntity masterEntity = level.getBlockEntity(masterPos);
        if (masterEntity instanceof DoubleBedBlockEntity masterBed) {
            masterOffset = masterBed.getOffset();
        }

        // Calculate where the player should be positioned based on what they clicked
        // ALL calculations are relative to the MASTER position + offset
        Direction right = facing.getClockWise();

        BlockPos targetHeadPos;
        switch (clickedPart) {
            case HEAD_LEFT -> targetHeadPos = masterPos; // Master is HEAD_LEFT
            case HEAD_RIGHT -> targetHeadPos = masterPos.relative(right); // HEAD_RIGHT = master + right
            case FOOT_LEFT -> targetHeadPos = masterPos; // Sleep at HEAD_LEFT
            case FOOT_RIGHT -> targetHeadPos = masterPos.relative(right); // Sleep at HEAD_RIGHT
            default -> targetHeadPos = masterPos;
        }

        // Apply master offset to the target head position
        double x = targetHeadPos.getX() + 0.5 + masterOffset.x;
        double y = targetHeadPos.getY() + 0.5625 + masterOffset.y;
        double z = targetHeadPos.getZ() + 0.5 + masterOffset.z;

        player.setPos(x, y, z);

        float yaw = switch (facing) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case EAST -> -90.0f;
            case WEST -> 90.0f;
            default -> 0.0f;
        };
        player.setYRot(yaw);
        player.setXRot(0.0f);
    }

    private BlockPos getMasterPosition(BlockPos clickedPos, BlockState clickedState) {
        Direction facing = clickedState.getValue(DoubleBedBlock.FACING);
        DoubleBedBlock.BedPart part = clickedState.getValue(DoubleBedBlock.PART);

        // Find HEAD_LEFT position (master position)
        return switch (part) {
            case HEAD_LEFT -> clickedPos;
            case HEAD_RIGHT -> clickedPos.relative(facing.getCounterClockWise());
            case FOOT_LEFT -> clickedPos.relative(facing);
            case FOOT_RIGHT -> clickedPos.relative(facing).relative(facing.getCounterClockWise());
        };
    }

    private void wakeUpPlayer(Player player) {
        UUID playerUUID = player.getUUID();

        if (playerUUID.equals(leftSleeper)) {
            leftSleeper = null;
        } else if (playerUUID.equals(rightSleeper)) {
            rightSleeper = null;
        }

        player.stopSleeping();
        updateOccupiedStates(getBlockState());
        setChanged();
    }

    private void updateOccupiedStates(BlockState currentState) {
        Direction facing = currentState.getValue(DoubleBedBlock.FACING);
        DoubleBedBlock.BedPart part = currentState.getValue(DoubleBedBlock.PART);

        // Find HEAD_LEFT position (master position)
        BlockPos headLeft = switch (part) {
            case HEAD_LEFT -> getBlockPos();
            case HEAD_RIGHT -> getBlockPos().relative(facing.getCounterClockWise());
            case FOOT_LEFT -> getBlockPos().relative(facing);
            case FOOT_RIGHT -> getBlockPos().relative(facing).relative(facing.getCounterClockWise());
        };

        Direction right = facing.getClockWise();
        BlockPos[] allPositions = {
                headLeft.relative(facing.getOpposite()),                    // FOOT_LEFT
                headLeft.relative(facing.getOpposite()).relative(right),    // FOOT_RIGHT
                headLeft,                                                   // HEAD_LEFT (master)
                headLeft.relative(right)                                    // HEAD_RIGHT
        };

        boolean anyOccupied = (leftSleeper != null || rightSleeper != null);

        for (BlockPos pos : allPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoubleBedBlock) {
                BlockState newState = state
                        .setValue(DoubleBedBlock.OCCUPIED, anyOccupied)
                        .setValue(DoubleBedBlock.OCCUPIED_LEFT, leftSleeper != null)
                        .setValue(DoubleBedBlock.OCCUPIED_RIGHT, rightSleeper != null);
                level.setBlock(pos, newState, 3);

                if (level.getBlockEntity(pos) instanceof DoubleBedBlockEntity other && other != this) {
                    other.leftSleeper = this.leftSleeper;
                    other.rightSleeper = this.rightSleeper;
                    other.setChanged();
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (leftSleeper != null) tag.putUUID("LeftSleeper", leftSleeper);
        if (rightSleeper != null) tag.putUUID("RightSleeper", rightSleeper);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("LeftSleeper")) leftSleeper = tag.getUUID("LeftSleeper");
        if (tag.hasUUID("RightSleeper")) rightSleeper = tag.getUUID("RightSleeper");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
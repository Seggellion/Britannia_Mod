package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.CarpetTeleporterBlock;
import com.seggellion.britannia_mod.block.DungeonMoongateBlock;
import com.seggellion.britannia_mod.block.entity.CarpetTeleporterBlockEntity;
import com.seggellion.britannia_mod.block.entity.DungeonMoongateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MoongateLinkingWand extends Item {

    private BlockPos firstMoongatePos;

    public MoongateLinkingWand(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        Player player = context.getPlayer();

        if (!world.isClientSide && player != null) {
            if (state.getBlock() instanceof DungeonMoongateBlock) {
                if (firstMoongatePos == null) {
                    firstMoongatePos = pos;
                    player.sendSystemMessage(Component.literal("First moongate selected."));
                } else {
                    // Set the pairing
                    BlockEntity firstEntity = world.getBlockEntity(firstMoongatePos);
                    BlockEntity secondEntity = world.getBlockEntity(pos);
                    if (firstEntity instanceof DungeonMoongateBlockEntity firstMoongate && secondEntity instanceof DungeonMoongateBlockEntity secondMoongate) {
                        firstMoongate.setPairedMoongatePos(pos);
                        secondMoongate.setPairedMoongatePos(firstMoongatePos);
                        firstMoongate.setChanged();
                        secondMoongate.setChanged();
                        player.sendSystemMessage(Component.literal("Moongates linked successfully."));
                        firstMoongatePos = null;
                    } else {
                        player.sendSystemMessage(Component.literal("Error: One of the moongates is invalid."));
                    }
                }
                return InteractionResult.SUCCESS;

} else if (state.getBlock() instanceof CarpetTeleporterBlock) {
    if (firstMoongatePos == null) {
        firstMoongatePos = pos;
        player.sendSystemMessage(Component.literal("First carpet teleporter selected."));
    } else {
        BlockEntity firstEntity = world.getBlockEntity(firstMoongatePos);
        BlockEntity secondEntity = world.getBlockEntity(pos);
        if (firstEntity instanceof CarpetTeleporterBlockEntity firstTeleporter &&
            secondEntity instanceof CarpetTeleporterBlockEntity secondTeleporter) {

            firstTeleporter.setTarget(pos); // One-way only
            firstTeleporter.setChanged();

            player.sendSystemMessage(Component.literal("Carpet teleporters linked (one-way)."));
            firstMoongatePos = null;
        } else {
            player.sendSystemMessage(Component.literal("Error: One of the teleporters is invalid."));
        }
    }
    return InteractionResult.SUCCESS;



            } else {

                
                player.sendSystemMessage(Component.literal("This is not a dungeon moongate, or teleporter."));
            }
        }
        return InteractionResult.PASS;
    }
}

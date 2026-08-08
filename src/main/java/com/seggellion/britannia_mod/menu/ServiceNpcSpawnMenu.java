package com.seggellion.britannia_mod.menu;

import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public final class ServiceNpcSpawnMenu extends AbstractContainerMenu {
    public static final double MAX_DISTANCE_SQUARED = 64.0D;

    private final UUID ownerId;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final UUID spawnPointId;

    public ServiceNpcSpawnMenu(
            int containerId,
            Inventory inventory,
            ResourceKey<Level> dimension,
            BlockPos pos,
            UUID spawnPointId
    ) {
        super(MenuRegistry.SERVICE_NPC_SPAWN_MENU.get(), containerId);
        this.ownerId = inventory.player.getUUID();
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos").immutable();
        this.spawnPointId = Objects.requireNonNull(spawnPointId, "spawnPointId");
    }

    public static ServiceNpcSpawnMenu fromNetwork(
            int containerId,
            Inventory inventory,
            ResourceLocation dimensionId,
            BlockPos pos,
            UUID spawnPointId
    ) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new ServiceNpcSpawnMenu(containerId, inventory, dimension, pos, spawnPointId);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!ownerId.equals(player.getUUID())) return false;
        if (!dimension.equals(player.level().dimension())) return false;
        if (player.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        ) > MAX_DISTANCE_SQUARED) return false;
        if (player.level().getBlockState(pos).getBlock() != BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get()) return false;
        // The authoritative UUID is deliberately not synchronized through block-entity NBT.
        if (player.level().isClientSide) return true;
        if (!(player.level().getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity)) return false;
        if (!spawnPointId.equals(blockEntity.getSpawnPointId())) return false;
        return !(player instanceof ServerPlayer serverPlayer) || serverPlayer.hasPermissions(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    public UUID ownerId() { return ownerId; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos pos() { return pos; }
    public UUID spawnPointId() { return spawnPointId; }
}

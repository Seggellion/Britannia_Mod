package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnMenuRequestValidator;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnConfigurationValidator;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public final class ServiceNpcSpawnPayloadHandler {
    private ServiceNpcSpawnPayloadHandler() {
    }

    public static void handleConfigure(ServiceNpcSpawnConfigureC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SessionValidation session = validateSession(
                    player,
                    payload.containerId(),
                    payload.pos(),
                    payload.spawnPointId(),
                    payload.expectedConfigurationRevision()
            );
            ServiceNpcSpawnValidationError error = session.error();
            TypeSelection selection = null;
            if (error == ServiceNpcSpawnValidationError.NONE) {
                selection = resolveTypeSelection(payload.cityPublicId(), payload.serviceNpcTypeKey());
                error = selection.error();
            }
            if (error == ServiceNpcSpawnValidationError.NONE && session.blockEntity() != null) {
                error = session.blockEntity().applyConfiguration(
                        player.serverLevel(),
                        payload.cityPublicId(),
                        selection.storedKey(),
                        payload.enabled(),
                        payload.expectedConfigurationRevision()
                );
            }
            sendState(player, session.menu(), error);
        });
    }

    public static void handleResync(ServiceNpcSpawnResyncC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SessionValidation session = validateSession(
                    player,
                    payload.containerId(),
                    payload.pos(),
                    payload.spawnPointId(),
                    payload.expectedConfigurationRevision()
            );
            sendState(player, session.menu(), session.error());
        });
    }

    private static SessionValidation validateSession(
            ServerPlayer player,
            int containerId,
            BlockPos submittedPos,
            UUID submittedSpawnPointId,
        long expectedRevision
    ) {
        ServiceNpcSpawnMenu menu = player.containerMenu instanceof ServiceNpcSpawnMenu found ? found : null;
        boolean dimensionMatches = menu != null && menu.dimension().equals(player.level().dimension());
        boolean mayInspectBoundPosition = menu != null && dimensionMatches;
        BlockState state = mayInspectBoundPosition
                ? player.serverLevel().getBlockState(menu.pos())
                : Blocks.AIR.defaultBlockState();
        ServiceNpcSpawnBlockEntity blockEntity = mayInspectBoundPosition
                && player.serverLevel().getBlockEntity(menu.pos())
                instanceof ServiceNpcSpawnBlockEntity found ? found : null;
        boolean correctMenu = menu != null;
        boolean withinDistance = menu != null && player.distanceToSqr(
                menu.pos().getX() + 0.5D,
                menu.pos().getY() + 0.5D,
                menu.pos().getZ() + 0.5D
        ) <= ServiceNpcSpawnMenu.MAX_DISTANCE_SQUARED;
        boolean uuidMatches = menu != null
                && menu.spawnPointId().equals(submittedSpawnPointId)
                && blockEntity != null
                && submittedSpawnPointId.equals(blockEntity.getSpawnPointId());
        ServiceNpcSpawnValidationError error = ServiceNpcSpawnMenuRequestValidator.validate(
                new ServiceNpcSpawnMenuRequestValidator.Facts(
                        true,
                        player.hasPermissions(2),
                        correctMenu,
                        menu != null && menu.ownerId().equals(player.getUUID()),
                        menu != null && menu.containerId == containerId,
                        dimensionMatches,
                        withinDistance,
                        menu != null && menu.pos().equals(submittedPos),
                        !state.isAir(),
                        state.getBlock() == BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get(),
                        blockEntity != null,
                        uuidMatches,
                        menu != null && menu.stillValid(player),
                        blockEntity != null && blockEntity.getConfigurationRevision() == expectedRevision
                )
        );
        return new SessionValidation(error, menu, blockEntity);
    }

    private static ServiceNpcSpawnValidationError validateRegistrySelection(UUID cityPublicId, String typeKey) {
        return ServiceNpcSpawnConfigurationValidator.validate(
                BootstrapCityRegistryCache.snapshot(),
                ServiceNpcRegistryCache.snapshot(),
                cityPublicId,
                typeKey
        );
    }

    /**
     * Vendor/Trader Milestone 5: a submitted type key may name either
     * specialization. The Service registry wins for plain keys; on
     * TYPE_UNAVAILABLE/REGISTRY_UNAVAILABLE the Economic registry is consulted,
     * and an accepted economic key is stored in its prefixed pipeline form
     * (see {@link com.seggellion.britannia_mod.service.EconomicNpcTypeKeys}).
     */
    record TypeSelection(ServiceNpcSpawnValidationError error, String storedKey) {}

    static TypeSelection resolveTypeSelection(UUID cityPublicId, String submittedKey) {
        boolean forcedEconomic =
                com.seggellion.britannia_mod.service.EconomicNpcTypeKeys.isEconomic(submittedKey);
        String candidate =
                com.seggellion.britannia_mod.service.EconomicNpcTypeKeys.strip(submittedKey);

        if (!forcedEconomic) {
            ServiceNpcSpawnValidationError serviceError =
                    validateRegistrySelection(cityPublicId, candidate);
            if (serviceError == ServiceNpcSpawnValidationError.NONE) {
                return new TypeSelection(ServiceNpcSpawnValidationError.NONE, candidate);
            }
            if (serviceError != ServiceNpcSpawnValidationError.TYPE_UNAVAILABLE
                    && serviceError != ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE) {
                return new TypeSelection(serviceError, null);
            }
        }

        if (candidate == null
                || candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        > ServiceNpcSpawnConfigurationValidator.MAX_TYPE_KEY_BYTES
                || !candidate.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")) {
            return new TypeSelection(ServiceNpcSpawnValidationError.OVERSIZED_FIELD, null);
        }
        var cities = BootstrapCityRegistryCache.snapshot();
        if (!cities.available() || cities.cities().isEmpty()) {
            return new TypeSelection(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE, null);
        }
        if (cities.find(cityPublicId) == null) {
            return new TypeSelection(ServiceNpcSpawnValidationError.CITY_UNAVAILABLE, null);
        }
        var definition = com.seggellion.britannia_mod.service.EconomicNpcRegistryCache
                .snapshot().economicNpcTypes().get(candidate);
        if (definition == null) {
            return new TypeSelection(ServiceNpcSpawnValidationError.TYPE_UNAVAILABLE, null);
        }
        if (!definition.active()) {
            return new TypeSelection(ServiceNpcSpawnValidationError.TYPE_INACTIVE, null);
        }
        if (!definition.spawnable()) {
            return new TypeSelection(ServiceNpcSpawnValidationError.TYPE_NOT_SPAWNABLE, null);
        }
        return new TypeSelection(
                ServiceNpcSpawnValidationError.NONE,
                com.seggellion.britannia_mod.service.EconomicNpcTypeKeys.prefixed(candidate)
        );
    }

    private static void sendState(
            ServerPlayer player,
            ServiceNpcSpawnMenu menu,
            ServiceNpcSpawnValidationError error
    ) {
        if (menu != null) ServiceNpcSpawnStateS2CPayload.send(player, menu, error);
    }

    private record SessionValidation(
            ServiceNpcSpawnValidationError error,
            ServiceNpcSpawnMenu menu,
            ServiceNpcSpawnBlockEntity blockEntity
    ) {}
}

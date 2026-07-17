package com.seggellion.britannia_mod.block.entity;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnAcknowledgementReceipt;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaim;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaimData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnIdentityResolver;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnLocation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingOperation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingDisposition;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnReceiptReconciler;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnStateMachine;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnItemDataSanitizer;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class ServiceNpcSpawnBlockEntity extends BlockEntity {
    public static final String SPAWN_POINT_ID_KEY = "SpawnPointId";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable private UUID spawnPointId;
    @Nullable private UUID cityPublicId;
    @Nullable private String serviceNpcTypeKey;
    private boolean enabled = true;
    private long configurationRevision;
    private ServiceNpcSpawnRegistrationState registrationState = ServiceNpcSpawnRegistrationState.UNCONFIGURED;
    @Nullable private String lastErrorCode;

    @Nullable private UUID assignedNpcPublicId;
    @Nullable private String assignedNpcDisplayName;
    private long assignmentRevision;
    @Nullable private Long lastSuccessfulSyncEpochMillis;

    @Nullable private UUID lastAcknowledgedOperationId;
    @Nullable private Long lastAcknowledgedRecordedAtEpochMillis;

    @Nullable private ServiceNpcSpawnLocation identityOrigin;
    private boolean identityReconciled;
    private boolean destructionHandled;

    public ServiceNpcSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.SERVICE_NPC_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void initializeNewPlacement(ServerLevel level) {
        if (identityReconciled) return;
        resetAsNewPost(UUID.randomUUID(), currentLocation(level));
        ServiceNpcSpawnClaimData.ClaimResult result = ServiceNpcSpawnClaimData.get(level)
                .claim(spawnPointId, identityOrigin);
        if (result == ServiceNpcSpawnClaimData.ClaimResult.READ_ONLY_SCHEMA
                || result == ServiceNpcSpawnClaimData.ClaimResult.CONFLICT) {
            markIdentityError("claim_store_error");
            return;
        }
        identityReconciled = true;
        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!identityReconciled) {
            reconcileIdentity(serverLevel);
        }
        if (identityReconciled) {
            ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock(serverLevel, this);
        }
    }

    public void ensureIdentity(ServerLevel level) {
        if (!identityReconciled) reconcileIdentity(level);
    }

    private void reconcileIdentity(ServerLevel level) {
        ServiceNpcSpawnLocation current = currentLocation(level);
        if (spawnPointId == null) {
            resetAsNewPost(UUID.randomUUID(), current);
        }

        ServiceNpcSpawnClaimData claims = ServiceNpcSpawnClaimData.get(level);
        ServiceNpcSpawnClaim existing = claims.find(spawnPointId);
        if (ServiceNpcSpawnIdentityResolver.decide(current, existing, identityOrigin)
                == ServiceNpcSpawnIdentityResolver.Decision.REKEY_COPY) {
            UUID copiedId = spawnPointId;
            ServiceNpcSpawnLocation canonical = existing != null ? existing.location() : identityOrigin;
            UUID replacement = UUID.randomUUID();
            resetAsNewPost(replacement, current);
            LOGGER.warn(
                    "Rekeyed copied Service NPC spawn post original_uuid={} canonical_location={} copied_location={} replacement_uuid={}",
                    copiedId,
                    canonical,
                    current,
                    replacement
            );
        } else if (identityOrigin == null) {
            identityOrigin = current;
        }

        ServiceNpcSpawnClaimData.ClaimResult result = claims.claim(spawnPointId, current);
        if (result == ServiceNpcSpawnClaimData.ClaimResult.CONFLICT) {
            UUID copiedId = spawnPointId;
            ServiceNpcSpawnClaim canonical = claims.find(copiedId);
            UUID replacement = UUID.randomUUID();
            resetAsNewPost(replacement, current);
            ServiceNpcSpawnClaimData.ClaimResult replacementResult = claims.claim(replacement, current);
            if (replacementResult == ServiceNpcSpawnClaimData.ClaimResult.READ_ONLY_SCHEMA
                    || replacementResult == ServiceNpcSpawnClaimData.ClaimResult.CONFLICT) {
                markIdentityError("claim_store_error");
                return;
            }
            LOGGER.warn(
                    "Rekeyed racing copied Service NPC spawn post original_uuid={} canonical_location={} copied_location={} replacement_uuid={}",
                    copiedId,
                    canonical == null ? "unknown" : canonical.location(),
                    current,
                    replacement
            );
        } else if (result == ServiceNpcSpawnClaimData.ClaimResult.READ_ONLY_SCHEMA) {
            markIdentityError("claim_store_future_schema");
            return;
        }

        identityOrigin = current;
        identityReconciled = true;
        setChanged();
    }

    private void resetAsNewPost(UUID newId, ServiceNpcSpawnLocation current) {
        spawnPointId = newId;
        cityPublicId = null;
        serviceNpcTypeKey = null;
        enabled = true;
        configurationRevision = 0L;
        registrationState = ServiceNpcSpawnRegistrationState.UNCONFIGURED;
        lastErrorCode = null;
        assignedNpcPublicId = null;
        assignedNpcDisplayName = null;
        assignmentRevision = 0L;
        lastSuccessfulSyncEpochMillis = null;
        lastAcknowledgedOperationId = null;
        lastAcknowledgedRecordedAtEpochMillis = null;
        identityOrigin = current;
        destructionHandled = false;
    }

    private void markIdentityError(String code) {
        registrationState = ServiceNpcSpawnRegistrationState.ERROR;
        lastErrorCode = code;
        identityReconciled = true;
        setChanged();
    }

    public ServiceNpcSpawnValidationError applyConfiguration(
            ServerLevel level,
            UUID newCityPublicId,
            String newServiceNpcTypeKey,
            boolean newEnabled,
            long expectedRevision
    ) {
        ensureIdentity(level);
        if (spawnPointId == null) return ServiceNpcSpawnValidationError.CLAIM_STORE_ERROR;
        ServiceNpcSpawnClaim claim = ServiceNpcSpawnClaimData.get(level).find(spawnPointId);
        ServiceNpcSpawnLocation current = currentLocation(level);
        if (claim == null || !claim.location().equals(current)) {
            return ServiceNpcSpawnValidationError.CLAIM_STORE_ERROR;
        }
        if (expectedRevision != configurationRevision) return ServiceNpcSpawnValidationError.STALE_REVISION;
        if (newCityPublicId.equals(cityPublicId)
                && newServiceNpcTypeKey.equals(serviceNpcTypeKey)
                && newEnabled == enabled) {
            return ServiceNpcSpawnValidationError.NO_CHANGE;
        }

        final long newRevision;
        try {
            newRevision = ServiceNpcSpawnStateMachine.nextRevision(configurationRevision);
        } catch (ArithmeticException overflow) {
            return ServiceNpcSpawnValidationError.REVISION_OVERFLOW;
        }

        ServiceNpcSpawnRegistrationState nextState =
                ServiceNpcSpawnStateMachine.afterAcceptedChange(registrationState);
        ServiceNpcSpawnPendingRecord pending = new ServiceNpcSpawnPendingRecord(
                ServiceNpcSpawnPendingOperation.UPSERT,
                spawnPointId,
                ModConfig.SHARD_NAME,
                current,
                newCityPublicId,
                newServiceNpcTypeKey,
                newEnabled,
                newRevision,
                System.currentTimeMillis()
        );
        ServiceNpcSpawnPendingData.MutationResult result = ServiceNpcSpawnPendingData.get(level).put(pending);
        if (result != ServiceNpcSpawnPendingData.MutationResult.ACCEPTED
                && result != ServiceNpcSpawnPendingData.MutationResult.IDEMPOTENT) {
            registrationState = ServiceNpcSpawnRegistrationState.ERROR;
            lastErrorCode = result == ServiceNpcSpawnPendingData.MutationResult.READ_ONLY_SCHEMA
                    ? "pending_store_future_schema"
                    : "pending_store_rejected";
            setChanged();
            return ServiceNpcSpawnValidationError.PENDING_STORE_ERROR;
        }

        cityPublicId = newCityPublicId;
        serviceNpcTypeKey = newServiceNpcTypeKey;
        enabled = newEnabled;
        configurationRevision = newRevision;
        registrationState = nextState;
        lastErrorCode = null;
        lastAcknowledgedOperationId = null;
        lastAcknowledgedRecordedAtEpochMillis = null;
        setChanged();
        return ServiceNpcSpawnValidationError.NONE;
    }

    public boolean recordTrueDestruction(ServerLevel level) {
        if (destructionHandled) return true;
        ensureIdentity(level);
        if (spawnPointId == null) return false;
        ServiceNpcSpawnLocation current = currentLocation(level);
        ServiceNpcSpawnPendingRecord remove = new ServiceNpcSpawnPendingRecord(
                ServiceNpcSpawnPendingOperation.REMOVE,
                spawnPointId,
                ModConfig.SHARD_NAME,
                current,
                cityPublicId,
                serviceNpcTypeKey,
                enabled,
                configurationRevision,
                System.currentTimeMillis()
        );
        ServiceNpcSpawnPendingData.MutationResult result = ServiceNpcSpawnPendingData.get(level).put(remove);
        if (result != ServiceNpcSpawnPendingData.MutationResult.ACCEPTED
                && result != ServiceNpcSpawnPendingData.MutationResult.IDEMPOTENT) {
            registrationState = ServiceNpcSpawnRegistrationState.ERROR;
            lastErrorCode = "remove_store_rejected";
            setChanged();
            LOGGER.error("Could not persist Service NPC spawn REMOVE uuid={} location={} result={}",
                    spawnPointId, current, result);
            return false;
        }
        if (!ServiceNpcSpawnClaimData.get(level).releaseIfMatches(spawnPointId, current)) {
            LOGGER.warn("Service NPC spawn claim was not released because it no longer matched uuid={} location={}",
                    spawnPointId, current);
        }
        destructionHandled = true;
        return true;
    }

    public ServiceNpcSpawnLocation currentLocation(ServerLevel level) {
        return new ServiceNpcSpawnLocation(
                level.getServer().getWorldData().getLevelName(),
                level.dimension().location(),
                worldPosition
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (spawnPointId != null) tag.putUUID(SPAWN_POINT_ID_KEY, spawnPointId);
        if (cityPublicId != null) tag.putUUID("CityPublicId", cityPublicId);
        if (serviceNpcTypeKey != null && !serviceNpcTypeKey.isBlank()) {
            tag.putString("ServiceNpcTypeKey", serviceNpcTypeKey);
        }
        tag.putBoolean("Enabled", enabled);
        tag.putLong("ConfigurationRevision", configurationRevision);
        tag.putString("RegistrationState", registrationState.name());
        if (lastErrorCode != null) tag.putString("LastErrorCode", lastErrorCode);
        if (assignedNpcPublicId != null) tag.putUUID("AssignedNpcPublicId", assignedNpcPublicId);
        if (assignedNpcDisplayName != null) tag.putString("AssignedNpcDisplayName", assignedNpcDisplayName);
        if (assignmentRevision != 0L) tag.putLong("AssignmentRevision", assignmentRevision);
        if (lastSuccessfulSyncEpochMillis != null) {
            tag.putLong("LastSuccessfulSyncEpochMillis", lastSuccessfulSyncEpochMillis);
        }
        if (lastAcknowledgedOperationId != null) {
            tag.putUUID("LastAcknowledgedOperationId", lastAcknowledgedOperationId);
        }
        if (lastAcknowledgedRecordedAtEpochMillis != null) {
            tag.putLong("LastAcknowledgedRecordedAtEpochMillis", lastAcknowledgedRecordedAtEpochMillis);
        }
        if (identityOrigin != null) {
            tag.putString("IdentityWorldName", identityOrigin.worldName());
            tag.putString("IdentityDimension", identityOrigin.dimension().toString());
            tag.putInt("IdentityX", identityOrigin.pos().getX());
            tag.putInt("IdentityY", identityOrigin.pos().getY());
            tag.putInt("IdentityZ", identityOrigin.pos().getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        spawnPointId = tag.hasUUID(SPAWN_POINT_ID_KEY) ? tag.getUUID(SPAWN_POINT_ID_KEY) : null;
        cityPublicId = tag.hasUUID("CityPublicId") ? tag.getUUID("CityPublicId") : null;
        serviceNpcTypeKey = tag.contains("ServiceNpcTypeKey") ? tag.getString("ServiceNpcTypeKey") : null;
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        configurationRevision = Math.max(0L, tag.getLong("ConfigurationRevision"));
        String storedState = tag.getString("RegistrationState");
        registrationState = ServiceNpcSpawnRegistrationState.decode(storedState);
        lastErrorCode = tag.contains("LastErrorCode") ? tag.getString("LastErrorCode") : null;
        if (registrationState == ServiceNpcSpawnRegistrationState.ERROR
                && !storedState.isBlank()
                && !storedState.toUpperCase(Locale.ROOT).equals(ServiceNpcSpawnRegistrationState.ERROR.name())) {
            lastErrorCode = "unsupported_registration_state";
        }
        assignedNpcPublicId = tag.hasUUID("AssignedNpcPublicId") ? tag.getUUID("AssignedNpcPublicId") : null;
        assignedNpcDisplayName = tag.contains("AssignedNpcDisplayName")
                ? tag.getString("AssignedNpcDisplayName") : null;
        assignmentRevision = Math.max(0L, tag.getLong("AssignmentRevision"));
        lastSuccessfulSyncEpochMillis = tag.contains("LastSuccessfulSyncEpochMillis")
                ? tag.getLong("LastSuccessfulSyncEpochMillis") : null;
        lastAcknowledgedOperationId = tag.hasUUID("LastAcknowledgedOperationId")
                ? tag.getUUID("LastAcknowledgedOperationId") : null;
        lastAcknowledgedRecordedAtEpochMillis = tag.contains("LastAcknowledgedRecordedAtEpochMillis")
                ? Math.max(0L, tag.getLong("LastAcknowledgedRecordedAtEpochMillis")) : null;
        ResourceLocation originDimension = tag.contains("IdentityDimension")
                ? ResourceLocation.tryParse(tag.getString("IdentityDimension")) : null;
        if (originDimension != null && tag.contains("IdentityWorldName")
                && tag.contains("IdentityX") && tag.contains("IdentityY") && tag.contains("IdentityZ")) {
            try {
                identityOrigin = new ServiceNpcSpawnLocation(
                        tag.getString("IdentityWorldName"),
                        originDimension,
                        new BlockPos(tag.getInt("IdentityX"), tag.getInt("IdentityY"), tag.getInt("IdentityZ"))
                );
            } catch (IllegalArgumentException exception) {
                identityOrigin = null;
                registrationState = ServiceNpcSpawnRegistrationState.ERROR;
                lastErrorCode = "invalid_identity_origin";
            }
        } else {
            identityOrigin = null;
        }
        identityReconciled = false;
        destructionHandled = false;
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        ServiceNpcSpawnItemDataSanitizer.strip(tag);
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider provider) {
        // Intentionally do not attach block-entity data or cached components to block items.
    }

    @Nullable public UUID getSpawnPointId() { return spawnPointId; }
    @Nullable public UUID getCityPublicId() { return cityPublicId; }
    @Nullable public String getServiceNpcTypeKey() { return serviceNpcTypeKey; }
    public boolean isEnabled() { return enabled; }
    public long getConfigurationRevision() { return configurationRevision; }
    public ServiceNpcSpawnRegistrationState getRegistrationState() { return registrationState; }
    @Nullable public String getLastErrorCode() { return lastErrorCode; }
    @Nullable public UUID getAssignedNpcPublicId() { return assignedNpcPublicId; }
    @Nullable public String getAssignedNpcDisplayName() { return assignedNpcDisplayName; }
    public long getAssignmentRevision() { return assignmentRevision; }
    @Nullable public Long getLastSuccessfulSyncEpochMillis() { return lastSuccessfulSyncEpochMillis; }
    @Nullable public ServiceNpcSpawnLocation getIdentityOrigin() { return identityOrigin; }
    @Nullable public UUID getLastAcknowledgedOperationId() { return lastAcknowledgedOperationId; }
    @Nullable public Long getLastAcknowledgedRecordedAtEpochMillis() {
        return lastAcknowledgedRecordedAtEpochMillis;
    }

    public boolean matchesAcknowledgement(
            ServiceNpcSpawnAcknowledgementReceipt receipt, ServerLevel serverLevel
    ) {
        return spawnPointId != null
            && spawnPointId.equals(receipt.spawnPointId())
            && configurationRevision == receipt.configurationRevision()
            && currentLocation(serverLevel).equals(receipt.location())
            && receipt.operation() == ServiceNpcSpawnPendingOperation.UPSERT
            && receipt.registrationState() == com.seggellion.britannia_mod.service.spawn
                .ServiceNpcSpawnProtocolResponse.RegistrationState.LIVE
            && (receipt.outcome() == com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOutcome.APPLIED
                || receipt.outcome() == com.seggellion.britannia_mod.service.spawn
                    .ServiceNpcSpawnOutcome.ALREADY_APPLIED);
    }

    public boolean hasAcknowledgementMarker(ServiceNpcSpawnAcknowledgementReceipt receipt) {
        return receipt.operationId().equals(lastAcknowledgedOperationId)
            && Objects.equals(receipt.recordedAtEpochMillis(), lastAcknowledgedRecordedAtEpochMillis);
    }

    public boolean applyAcknowledgement(ServiceNpcSpawnAcknowledgementReceipt receipt) {
        if (hasAcknowledgementMarker(receipt)) return false;
        registrationState = ServiceNpcSpawnRegistrationState.REGISTERED;
        lastErrorCode = null;
        lastSuccessfulSyncEpochMillis = receipt.acknowledgedAtEpochMillis();
        lastAcknowledgedOperationId = receipt.operationId();
        lastAcknowledgedRecordedAtEpochMillis = receipt.recordedAtEpochMillis();
        setChanged();
        syncAuthoritativeState();
        return true;
    }

    public boolean matchesPending(ServiceNpcSpawnPendingRecord pending, ServerLevel serverLevel) {
        return spawnPointId != null
            && spawnPointId.equals(pending.spawnPointId())
            && configurationRevision == pending.configurationRevision()
            && currentLocation(serverLevel).equals(pending.location());
    }

    public boolean applyPendingDeliveryState(ServiceNpcSpawnPendingRecord pending) {
        ServiceNpcSpawnRegistrationState nextState;
        String nextError;
        if (pending.disposition() == ServiceNpcSpawnPendingDisposition.PERMANENT_FAILURE) {
            nextState = ServiceNpcSpawnRegistrationState.ERROR;
            nextError = pending.lastFailureCode() == null
                ? "invalid_local_operation" : pending.lastFailureCode();
        } else if (pending.disposition() == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR) {
            nextState = ServiceNpcSpawnRegistrationState.ERROR;
            nextError = "uuid_collision_pending_repair";
        } else {
            nextState = lastSuccessfulSyncEpochMillis != null
                || registrationState == ServiceNpcSpawnRegistrationState.REGISTERED
                || registrationState == ServiceNpcSpawnRegistrationState.PENDING_UPDATE
                    ? ServiceNpcSpawnRegistrationState.PENDING_UPDATE
                    : ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION;
            nextError = pending.lastFailureCode();
        }
        if (registrationState == nextState && Objects.equals(lastErrorCode, nextError)) return false;
        registrationState = nextState;
        lastErrorCode = nextError;
        setChanged();
        syncAuthoritativeState();
        return true;
    }

    private void syncAuthoritativeState() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockState state = getBlockState();
        serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof ServiceNpcSpawnMenu menu
                    && menu.dimension().equals(serverLevel.dimension())
                    && menu.pos().equals(worldPosition)
                    && spawnPointId != null
                    && menu.spawnPointId().equals(spawnPointId)) {
                ServiceNpcSpawnStateS2CPayload.send(
                    player, menu, ServiceNpcSpawnValidationError.NONE
                );
            }
        }
    }
}

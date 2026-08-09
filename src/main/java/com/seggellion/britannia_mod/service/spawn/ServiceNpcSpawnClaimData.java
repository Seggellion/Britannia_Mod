package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServiceNpcSpawnClaimData extends SavedData {
    public enum ClaimResult {
        CLAIMED,
        ALREADY_CLAIMED_HERE,
        CONFLICT,
        READ_ONLY_SCHEMA
    }

    public static final String DATA_NAME = "britannia_service_npc_spawn_claims";
    public static final int SCHEMA_VERSION = 1;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkedHashMap<UUID, ServiceNpcSpawnClaim> claims = new LinkedHashMap<>();
    private final List<CompoundTag> quarantinedClaims = new ArrayList<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;

    public static ServiceNpcSpawnClaimData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServiceNpcSpawnClaimData::new, ServiceNpcSpawnClaimData::load),
                DATA_NAME
        );
    }

    public static ServiceNpcSpawnClaimData load(CompoundTag tag, HolderLookup.Provider provider) {
        ServiceNpcSpawnClaimData data = new ServiceNpcSpawnClaimData();
        int schema = tag.getInt("SchemaVersion");
        if (schema != SCHEMA_VERSION) {
            data.readOnlyFutureSchema = true;
            data.futureRoot = tag.copy();
            LOGGER.error("Service NPC spawn claim data schema {} is unsupported; store is read-only", schema);
            return data;
        }
        ListTag list = tag.getList("Claims", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag claimTag = list.getCompound(index);
            try {
                ServiceNpcSpawnClaim claim = ServiceNpcSpawnClaim.fromNbt(claimTag);
                if (data.claims.putIfAbsent(claim.spawnPointId(), claim) != null) {
                    throw new IllegalArgumentException("duplicate SpawnPointId");
                }
            } catch (RuntimeException exception) {
                data.quarantinedClaims.add(claimTag.copy());
                LOGGER.error("Quarantined corrupt Service NPC spawn claim at index {}", index, exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readOnlyFutureSchema && futureRoot != null) return futureRoot.copy();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag list = new ListTag();
        claims.values().forEach(claim -> list.add(claim.toNbt()));
        quarantinedClaims.forEach(claim -> list.add(claim.copy()));
        tag.put("Claims", list);
        return tag;
    }

    public ClaimResult claim(UUID spawnPointId, ServiceNpcSpawnLocation location) {
        if (readOnlyFutureSchema) return ClaimResult.READ_ONLY_SCHEMA;
        ServiceNpcSpawnClaim existing = claims.get(spawnPointId);
        if (existing != null) {
            return existing.location().equals(location)
                    ? ClaimResult.ALREADY_CLAIMED_HERE
                    : ClaimResult.CONFLICT;
        }
        claims.put(spawnPointId, new ServiceNpcSpawnClaim(spawnPointId, location));
        setDirty();
        return ClaimResult.CLAIMED;
    }

    public boolean releaseIfMatches(UUID spawnPointId, ServiceNpcSpawnLocation location) {
        if (readOnlyFutureSchema) return false;
        ServiceNpcSpawnClaim existing = claims.get(spawnPointId);
        if (existing == null || !existing.location().equals(location)) return false;
        claims.remove(spawnPointId);
        setDirty();
        return true;
    }

    public ServiceNpcSpawnClaim find(UUID spawnPointId) {
        return claims.get(spawnPointId);
    }

    public boolean isUuidAvailable(UUID spawnPointId) {
        return spawnPointId != null && !claims.containsKey(spawnPointId);
    }

    public boolean claimMatches(UUID spawnPointId, ServiceNpcSpawnLocation location) {
        ServiceNpcSpawnClaim claim = claims.get(spawnPointId);
        return claim != null && claim.location().equals(location);
    }

    public Map<UUID, ServiceNpcSpawnClaim> snapshot() {
        return Map.copyOf(claims);
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }
}

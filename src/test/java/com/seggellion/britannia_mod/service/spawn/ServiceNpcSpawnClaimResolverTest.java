package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceNpcSpawnClaimResolverTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test
    void matchingClaimMakesCurrentLocationCanonical() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnLocation current = location(1);
        assertEquals(ServiceNpcSpawnIdentityResolver.Decision.CANONICAL,
                ServiceNpcSpawnIdentityResolver.decide(
                        current,
                        new ServiceNpcSpawnClaim(id, current),
                        location(2)
                ));
    }

    @Test
    void differentClaimOrOriginRekeysTheCopy() {
        UUID id = UUID.randomUUID();
        assertEquals(ServiceNpcSpawnIdentityResolver.Decision.REKEY_COPY,
                ServiceNpcSpawnIdentityResolver.decide(
                        location(2),
                        new ServiceNpcSpawnClaim(id, location(1)),
                        location(1)
                ));
        assertEquals(ServiceNpcSpawnIdentityResolver.Decision.REKEY_COPY,
                ServiceNpcSpawnIdentityResolver.decide(location(2), null, location(1)));
    }

    @Test
    void firstLegacyClaimantIsCanonical() {
        assertEquals(ServiceNpcSpawnIdentityResolver.Decision.CANONICAL,
                ServiceNpcSpawnIdentityResolver.decide(location(1), null, null));
    }

    @Test
    void worldNameOnlyChangeDoesNotRekey() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnLocation renamed = new ServiceNpcSpawnLocation("renamed-world", OVERWORLD, new BlockPos(1, 64, 0));
        assertEquals(ServiceNpcSpawnIdentityResolver.Decision.CANONICAL,
                ServiceNpcSpawnIdentityResolver.decide(
                        renamed,
                        new ServiceNpcSpawnClaim(id, location(1)),
                        location(1)
                ));
        assertEquals(ServiceNpcSpawnIdentityResolver.Decision.CANONICAL,
                ServiceNpcSpawnIdentityResolver.decide(renamed, null, location(1)));
    }

    @Test
    void claimCodecPersistsAndFutureSchemaIsReadOnly() {
        UUID id = UUID.randomUUID();
        ServiceNpcSpawnClaim claim = new ServiceNpcSpawnClaim(id, location(3));
        assertEquals(claim, ServiceNpcSpawnClaim.fromNbt(claim.toNbt()));

        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", ServiceNpcSpawnClaimData.SCHEMA_VERSION);
        ListTag claims = new ListTag();
        claims.add(claim.toNbt());
        root.put("Claims", claims);
        assertEquals(claim, ServiceNpcSpawnClaimData.load(root, null).find(id));

        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", 2);
        ServiceNpcSpawnClaimData futureData = ServiceNpcSpawnClaimData.load(future, null);
        assertTrue(futureData.isReadOnlyFutureSchema());
        assertEquals(ServiceNpcSpawnClaimData.ClaimResult.READ_ONLY_SCHEMA,
                futureData.claim(UUID.randomUUID(), location(4)));
    }

    private static ServiceNpcSpawnLocation location(int x) {
        return new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(x, 64, 0));
    }
}

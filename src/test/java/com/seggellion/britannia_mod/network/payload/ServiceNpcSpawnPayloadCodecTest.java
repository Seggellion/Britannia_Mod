package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnEligibility;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNpcSpawnPayloadCodecTest {
    @Test
    void configurationPayloadRoundTripsWithinBounds() {
        ServiceNpcSpawnConfigureC2SPayload payload = new ServiceNpcSpawnConfigureC2SPayload(
                4, new BlockPos(1, 64, 2), UUID.randomUUID(), 3L, UUID.randomUUID(), "bank_teller", false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void oversizedTypeKeyCannotBeEncoded() {
        ServiceNpcSpawnConfigureC2SPayload payload = new ServiceNpcSpawnConfigureC2SPayload(
                4, BlockPos.ZERO, UUID.randomUUID(), 0L, UUID.randomUUID(), "a".repeat(65), true
        );
        assertThrows(IllegalArgumentException.class, () ->
                ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC.encode(
                        new FriendlyByteBuf(Unpooled.buffer()), payload));
    }

    @Test
    void authoritativeStateRoundTripsEnabledPendingAndStatusFields() {
        UUID spawnPointId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID assignedNpcId = UUID.randomUUID();
        ServiceNpcSpawnStateS2CPayload payload = new ServiceNpcSpawnStateS2CPayload(
                8,
                new BlockPos(2, 70, 3),
                spawnPointId,
                true,
                ServiceNpcSpawnValidationError.STALE_REVISION,
                true,
                true,
                List.of(new ServiceNpcSpawnStateS2CPayload.CityOption(cityId, "Britain")),
                List.of(new ServiceNpcSpawnStateS2CPayload.ServiceTypeOption("bank_teller", "Bank Teller")),
                cityId,
                "bank_teller",
                true,
                true,
                false,
                4L,
                ServiceNpcSpawnRegistrationState.PENDING_UPDATE,
                "waiting_for_delivery",
                assignedNpcId,
                "Geoffrey",
                2L,
                123456789L,
                // Guildmaster milestone 2. Deliberately no defaulting constructor overload on this
                // record, unlike the domain records: a wire payload that silently fills in fields
                // is how an encode/decode pair drifts out of sync without a test noticing.
                List.of("Swordsmanship", "Tactics"),
                ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM,
                List.of(
                        new ServiceNpcSpawnStateS2CPayload.SupplyLine("food", 200.0, 143.5, true),
                        // measured=false is the UNKNOWN case, and has to survive the round trip
                        // distinguishably from a real zero.
                        new ServiceNpcSpawnStateS2CPayload.SupplyLine("silver", 5.0, 0.0, false)
                ),
                "World sync FAILED: malformed_change: x must be an integer"
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        ServiceNpcSpawnStateS2CPayload.STREAM_CODEC.encode(buffer, payload);

        ServiceNpcSpawnStateS2CPayload decoded = ServiceNpcSpawnStateS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertEquals(List.of("Swordsmanship", "Tactics"), decoded.taughtSkillLabels());
        assertEquals(ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM, decoded.eligibilityStatus());
        assertFalse(decoded.supplyRequirements().get(0).satisfied(), "143.5 does not meet 200");
        assertFalse(decoded.supplyRequirements().get(1).measured(), "an unmeasured supply must stay unmeasured");
        assertEquals(
                "World sync FAILED: malformed_change: x must be an integer",
                decoded.worldStateSyncStatus(),
                "the sync line is the last field on the wire and must not be dropped"
        );
    }
}

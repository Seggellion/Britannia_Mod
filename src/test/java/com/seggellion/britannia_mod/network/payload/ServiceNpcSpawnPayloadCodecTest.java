package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                123456789L
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        ServiceNpcSpawnStateS2CPayload.STREAM_CODEC.encode(buffer, payload);

        assertEquals(payload, ServiceNpcSpawnStateS2CPayload.STREAM_CODEC.decode(buffer));
    }
}

package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The two identifiers {@code banking/open} needs, both server-authoritative:
 * {@code playerUuid} must come from {@code ServerPlayer.getStringUUID()} on the
 * authoritative connected player object, and {@code worldNpcPublicId} from the live
 * {@link com.seggellion.britannia_mod.entity.ServiceNpcEntity}'s own persistent field.
 * Nothing client-supplied is ever accepted into either field — see
 * {@link BankingProxyService#resolve}.
 */
public record BankingOpenRequest(UUID playerUuid, UUID worldNpcPublicId) {
    public BankingOpenRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
    }
}

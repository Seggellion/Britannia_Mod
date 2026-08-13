package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.network.payload.grabby.S2COpenGrabbyDestructionPromptPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Server-to-client sends for Grabby Hands.
 *
 * <p>Kept out of the transaction and service classes so those stay testable without a network stack.
 */
public final class ClientNetworkDispatch {
    private ClientNetworkDispatch() {
    }

    public static void openGrabbyDestructionPrompt(
            ServerPlayer player, UUID sessionId, String objectName, int occupiedSlots) {
        PacketDistributor.sendToPlayer(
                player,
                new S2COpenGrabbyDestructionPromptPayload(sessionId, objectName, occupiedSlots));
    }
}

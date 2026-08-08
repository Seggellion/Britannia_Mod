package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;

import java.util.UUID;

/**
 * "The player says they want to withdraw this bank item, through this teller" -- nothing more.
 * Mirrors {@link BankDepositRequestC2SPayload}'s trust model exactly: {@code bankItemPublicId}
 * is only ever a selection reference (which of the account's own items the player picked from
 * the list {@code bank.open} already sent), never something the server trusts for identity or
 * ownership -- {@code banking/withdrawal/prepare} itself re-resolves and re-scopes the item to
 * the requesting account server (Rails) -side, and {@link
 * com.seggellion.britannia_mod.service.banking.BankingWithdrawalProxyService} independently
 * re-derives and verifies the reconstructed stack's fingerprint against what Rails sends back.
 * No client-supplied fingerprint, weight, or payload is ever carried here.
 */
public record BankWithdrawalRequestC2SPayload(int entityId, UUID bankItemPublicId) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_withdrawal_request");
    public static final Type<BankWithdrawalRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankWithdrawalRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BankWithdrawalRequestC2SPayload decode(FriendlyByteBuf buf) {
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            UUID bankItemPublicId = buf.readUUID();
            return new BankWithdrawalRequestC2SPayload(entityId, bankItemPublicId);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BankWithdrawalRequestC2SPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            buf.writeUUID(payload.bankItemPublicId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

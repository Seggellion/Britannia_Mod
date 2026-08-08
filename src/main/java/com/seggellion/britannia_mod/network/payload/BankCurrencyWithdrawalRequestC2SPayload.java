package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;

/**
 * "The player says they want to withdraw this much of this denomination, through this teller"
 * -- nothing more. Milestone 10 Slice 2's counterpart to {@link BankWithdrawalRequestC2SPayload}:
 * currency has no per-unit identity to reference (unlike a bank item's {@code
 * bankItemPublicId}), so the request identifies what it wants purely by denomination key and
 * amount, mirroring Rails' own currency withdrawal prepare request shape exactly
 * (docs/banking_currency_transfer.md).
 *
 * <p>Mirrors every other bank transfer payload's trust model: {@code currencyKey} and {@code
 * amount} are only ever a request, never something the server trusts as fact -- {@link
 * com.seggellion.britannia_mod.service.banking.BankingCurrencyWithdrawalProxyService}
 * independently re-checks local inventory capacity and lets Rails independently re-check and
 * lock the account's real available balance; nothing here is trusted at face value.
 *
 * <p>{@code amount} is rejected at decode time if not positive, mirroring {@code
 * BankDepositRequestC2SPayload}'s own established "reject malformed values during decode"
 * precedent for a field this packet is the first to expose to real network input.
 */
public record BankCurrencyWithdrawalRequestC2SPayload(int entityId, String currencyKey, int amount) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_currency_withdrawal_request");
    public static final Type<BankCurrencyWithdrawalRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankCurrencyWithdrawalRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BankCurrencyWithdrawalRequestC2SPayload decode(FriendlyByteBuf buf) {
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            String currencyKey = ByteBufCodecs.stringUtf8(16).decode(buf);
            int amount = ByteBufCodecs.VAR_INT.decode(buf);
            if (amount <= 0) throw new IllegalArgumentException("Invalid currency withdrawal amount");
            return new BankCurrencyWithdrawalRequestC2SPayload(entityId, currencyKey, amount);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BankCurrencyWithdrawalRequestC2SPayload payload) {
            if (payload.amount <= 0) throw new IllegalArgumentException("Invalid currency withdrawal amount");
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            ByteBufCodecs.stringUtf8(16).encode(buf, payload.currencyKey);
            ByteBufCodecs.VAR_INT.encode(buf, payload.amount);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

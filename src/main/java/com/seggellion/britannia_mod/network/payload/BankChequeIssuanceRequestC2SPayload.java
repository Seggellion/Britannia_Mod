package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;

/**
 * "The player says they want to issue a cheque for this much, through this teller" -- nothing
 * more. Milestone 11 NeoForge Slice 1's counterpart to {@link BankCurrencyWithdrawalRequestC2SPayload}:
 * a cheque has no per-unit identity to reference, so the request identifies what it wants purely
 * by amount. {@code amount} is expressed in copper -- the wire-level unit Rails' {@code
 * ChequePayloadValidator}/{@code BankCheque} actually validate and store against
 * (ADR-018/ADR-019), not gold; the client screen converts the player's gold-denominated input to
 * copper before sending, via {@link com.seggellion.britannia_mod.economy.CoinConversion}.
 *
 * <p>Mirrors every other bank transfer payload's trust model: {@code amount} is only ever a
 * request, never something the server trusts as fact -- {@link
 * com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceProxyService} independently
 * re-checks the approved range and local inventory capacity, and Rails independently re-checks
 * and locks the account's real available gold balance.
 *
 * <p>{@code amount} is rejected at decode time if not positive, mirroring {@code
 * BankCurrencyWithdrawalRequestC2SPayload}'s own established "reject malformed values during
 * decode" precedent.
 */
public record BankChequeIssuanceRequestC2SPayload(int entityId, int amount, String currencyKey)
        implements CustomPacketPayload {
    /** Comfortably past {@code "silver"}, and short enough that a hostile value costs nothing. */
    private static final int MAX_CURRENCY_KEY_LENGTH = 16;

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_cheque_issuance_request");
    public static final Type<BankChequeIssuanceRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankChequeIssuanceRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BankChequeIssuanceRequestC2SPayload decode(FriendlyByteBuf buf) {
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            int amount = ByteBufCodecs.VAR_INT.decode(buf);
            if (amount <= 0) throw new IllegalArgumentException("Invalid cheque issuance amount");
            // Bounded before it is read as a key: an unbounded readUtf from a modified client is a
            // memory cost the server pays before any validation, so the length cap comes first and
            // the value is checked against the supported set immediately after. Same
            // reject-malformed-values-during-decode precedent the amount check above follows.
            String currencyKey = buf.readUtf(MAX_CURRENCY_KEY_LENGTH);
            if (CurrencyItemRegistry.copperUnitFor(currencyKey).isEmpty()) {
                throw new IllegalArgumentException("Invalid cheque issuance currency key");
            }
            return new BankChequeIssuanceRequestC2SPayload(entityId, amount, currencyKey);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BankChequeIssuanceRequestC2SPayload payload) {
            if (payload.amount <= 0) throw new IllegalArgumentException("Invalid cheque issuance amount");
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            ByteBufCodecs.VAR_INT.encode(buf, payload.amount);
            buf.writeUtf(payload.currencyKey, MAX_CURRENCY_KEY_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

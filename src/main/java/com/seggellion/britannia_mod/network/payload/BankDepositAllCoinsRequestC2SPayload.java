package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;

/**
 * "The player pressed Deposit All Coins while talking to this teller" -- and deliberately nothing
 * else.
 *
 * <p>No totals, no denominations, no slot list, no counts. Architecture Milestone 1 D10 fixed this
 * shape before either half of Milestone 6 was built, precisely so the client could never become a
 * source of truth for how much money it is about to be credited. The server sweeps the live
 * inventory itself ({@code CoinSweep}), and a modified client that wanted to inflate a deposit
 * would have to inflate its actual inventory first -- at which point the coins genuinely exist and
 * depositing them is correct.
 *
 * <p>This is the smallest banking packet in the mod, and that is the security property, not an
 * accident of it. {@code entityId} is a selection reference like every other banking packet's:
 * {@link com.seggellion.britannia_mod.service.banking.BankingTransferPacketService} re-resolves
 * the teller fresh through {@code BankingProxyService.resolve}, which independently re-checks
 * liveness, distance and {@code bank.open} capability.
 */
public record BankDepositAllCoinsRequestC2SPayload(int entityId) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_deposit_all_coins_request");
    public static final Type<BankDepositAllCoinsRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankDepositAllCoinsRequestC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BankDepositAllCoinsRequestC2SPayload decode(FriendlyByteBuf buf) {
                    return new BankDepositAllCoinsRequestC2SPayload(ByteBufCodecs.VAR_INT.decode(buf));
                }

                @Override
                public void encode(FriendlyByteBuf buf, BankDepositAllCoinsRequestC2SPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

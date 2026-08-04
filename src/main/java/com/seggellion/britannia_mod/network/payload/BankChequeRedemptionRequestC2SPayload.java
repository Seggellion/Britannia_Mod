package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;

/**
 * Milestone 17 gate corrective: "the player says they want to CASH the cheque in this inventory
 * slot, through this teller" -- redemption's own explicit request, replacing the deposit
 * packet's automatic cheque routing.
 *
 * <p>ADR-016 made redemption deposit-shaped: any cheque reaching the deposit packet was cashed.
 * The owner overrode that at the Milestone 17 gate -- players must be able to <em>store</em>
 * cheques in the Bank Box, so a deposited cheque now stores like any item, and cashing is this
 * separate, deliberate gesture (double-click in the Bank Box's pack grid). The distinction is
 * intent, and intent cannot be inferred server-side from the slot's contents any more -- so it
 * travels in its own packet.
 *
 * <p>Same trust model as {@link BankDepositRequestC2SPayload}, word for word: {@code entityId}
 * and {@code slotIndex} are selection references only. The server re-resolves the teller fresh,
 * and {@code BankingChequeRedemptionProxyService} re-reads the live slot and rejects locally
 * (EMPTY_SLOT, NOT_A_CHEQUE) if it does not actually hold a cheque -- a modified client
 * pointing this packet at a diamond gets a clean local rejection, never a misrouted disposal.
 * The same negative-index decode guard applies, for the same traced reason.
 */
public record BankChequeRedemptionRequestC2SPayload(int entityId, int slotIndex) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_cheque_redemption_request");
    public static final Type<BankChequeRedemptionRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankChequeRedemptionRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BankChequeRedemptionRequestC2SPayload decode(FriendlyByteBuf buf) {
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            int slotIndex = ByteBufCodecs.VAR_INT.decode(buf);
            if (slotIndex < 0) throw new IllegalArgumentException("Invalid cheque redemption slot index");
            return new BankChequeRedemptionRequestC2SPayload(entityId, slotIndex);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BankChequeRedemptionRequestC2SPayload payload) {
            if (payload.slotIndex < 0) throw new IllegalArgumentException("Invalid cheque redemption slot index");
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            ByteBufCodecs.VAR_INT.encode(buf, payload.slotIndex);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

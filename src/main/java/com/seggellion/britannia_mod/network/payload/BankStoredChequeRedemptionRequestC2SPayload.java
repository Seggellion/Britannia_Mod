package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;

import java.util.UUID;

/**
 * "The player says they want to cash the cheque stored in this vault row" -- the double-click on
 * a stored cheque in the Bank Box grid.
 *
 * <p>Carries the <b>bank item's</b> public id, never the cheque's: Rails derives the cheque from
 * the row it already holds, and the client could not name it honestly anyway (the id lives inside
 * the opaque stored payload). This is the same public-id-not-grid-index rule every stored-item
 * action follows (design §9.6) -- a grid position would be wrong the instant a refresh reordered
 * the vault, and this action destroys value.
 *
 * <p>Distinct from {@link BankChequeRedemptionRequestC2SPayload}, which cashes a cheque the
 * player is <em>carrying</em> and names an inventory slot. Two gestures, two packets, two Rails
 * endpoints; the only thing they share is the result channel.
 */
public record BankStoredChequeRedemptionRequestC2SPayload(int entityId, UUID bankItemPublicId)
        implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_stored_cheque_redemption_request");
    public static final Type<BankStoredChequeRedemptionRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankStoredChequeRedemptionRequestC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BankStoredChequeRedemptionRequestC2SPayload decode(FriendlyByteBuf buf) {
                    int entityId = ByteBufCodecs.VAR_INT.decode(buf);
                    UUID bankItemPublicId = buf.readUUID();
                    return new BankStoredChequeRedemptionRequestC2SPayload(entityId, bankItemPublicId);
                }

                @Override
                public void encode(FriendlyByteBuf buf, BankStoredChequeRedemptionRequestC2SPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
                    buf.writeUUID(payload.bankItemPublicId);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

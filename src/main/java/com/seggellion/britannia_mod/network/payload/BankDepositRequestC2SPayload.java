package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.BritanniaMod;

/**
 * "The player says they want to deposit whatever is in this inventory slot, through this
 * teller" -- nothing more. Mirrors {@code SellItemsC2SPayload}'s trust model exactly: {@code
 * entityId} and {@code slotIndex} are only ever a selection reference, never something the
 * server treats as fact. The server (via {@link
 * com.seggellion.britannia_mod.service.banking.BankingTransferPacketService}) re-resolves the
 * teller fresh from {@code entityId} exactly the way {@code bank.open}'s own {@code interactAt}
 * flow already does ({@code BankingProxyService.resolve}), and {@link
 * com.seggellion.britannia_mod.service.banking.BankingDepositProxyService} re-derives the
 * fingerprint/weight/payload of whatever is actually, live, in {@code slotIndex} at prepare and
 * revalidation time -- never trusting a client-supplied fingerprint or weight, because none is
 * ever sent here.
 *
 * <p>{@code slotIndex} is rejected at decode time if negative -- {@code Inventory#getItem}'s
 * real source (traced, not assumed) safely returns {@code ItemStack.EMPTY} for a positive
 * out-of-range index (falling through every compartment with nothing left to check), but a
 * <i>negative</i> index matches the first compartment's own bounds check and reaches {@code
 * NonNullList#get} directly, throwing {@code IndexOutOfBoundsException}. This packet is what
 * first exposes {@code slotIndex} to real network input at all (every prior caller was
 * test-only code passing a hardcoded, valid value), so this decode-time guard is what keeps
 * that pre-existing, previously-unreachable crash from becoming reachable by a modified
 * client -- mirroring {@code SellItemsC2SPayload}'s own established "reject malformed values
 * during decode" precedent, not a change to the deposit proxy's own logic.
 */
public record BankDepositRequestC2SPayload(int entityId, int slotIndex) implements CustomPacketPayload {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_deposit_request");
    public static final Type<BankDepositRequestC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankDepositRequestC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BankDepositRequestC2SPayload decode(FriendlyByteBuf buf) {
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            int slotIndex = ByteBufCodecs.VAR_INT.decode(buf);
            if (slotIndex < 0) throw new IllegalArgumentException("Invalid deposit slot index");
            return new BankDepositRequestC2SPayload(entityId, slotIndex);
        }

        @Override
        public void encode(FriendlyByteBuf buf, BankDepositRequestC2SPayload payload) {
            if (payload.slotIndex < 0) throw new IllegalArgumentException("Invalid deposit slot index");
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
            ByteBufCodecs.VAR_INT.encode(buf, payload.slotIndex);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import com.seggellion.britannia_mod.BritanniaMod;

import java.util.Objects;

/**
 * Tells {@code BankScreen} how a just-triggered deposit/withdrawal ended, for the two outcomes
 * that leave the same screen instance on-screen needing to update in place: a clean rejection
 * (nothing happened; re-enable selection) or {@code RECONCILIATION_REQUIRED} (something
 * happened that Rails itself can no longer auto-resolve; a visibly more serious message).
 *
 * <p>{@code CONFIRMED} is deliberately <b>not</b> a case here: on a clean confirm, {@link
 * com.seggellion.britannia_mod.service.banking.BankingTransferPacketService} re-runs {@code
 * BankingProxyService.handle} to push a fresh {@link BankAccountOpenedS2CPayload}, which
 * replaces the screen with genuinely correct new server state -- the refresh itself is the
 * success signal, matching Slice 3a's own requirement ("refresh... rather than requiring the
 * player to close and reopen") rather than a separate banner layered on top of stale data.
 *
 * <p>Carries only a closed, pre-written outcome pair -- never a raw server-side message string
 * -- mirroring {@code BankingProxyService}'s own {@code REJECTED_MESSAGE}/{@code
 * SERVICE_UNAVAILABLE_MESSAGE} constants: the client selects its own diegetic wording for
 * {@code (operation, kind)}, exactly like every other banking outcome this codebase surfaces.
 */
public record BankTransferResultS2CPayload(Operation operation, Kind kind) implements CustomPacketPayload {
    public enum Operation {
        DEPOSIT,
        WITHDRAWAL,
        /** Milestone 11 NeoForge Slice 1 -- kept distinct from WITHDRAWAL: cheque issuance's own outcome vocabulary genuinely differs (see {@link Kind#PENDING_DELIVERY}). */
        CHEQUE_ISSUANCE,
        /** Milestone 11 NeoForge Slice 2 -- kept distinct from DEPOSIT (which routes it here): redemption's own rejection vocabulary genuinely differs (see the four {@code CHEQUE_*} {@link Kind} values). */
        CHEQUE_REDEMPTION
    }

    public enum Kind {
        CLEAN_REJECTION,
        RECONCILIATION_REQUIRED,
        /**
         * Milestone 11 NeoForge Slice 1 only: Rails already confirmed (gold debited, the cheque
         * is real), but the physical item was not delivered this attempt and a safe, automatic
         * retry is still expected -- deliberately distinct from {@link #RECONCILIATION_REQUIRED},
         * which means nothing may auto-resolve. See {@code BankingChequeIssuanceResult.PendingDelivery}'s
         * own docs for why this is a genuinely different, non-alarming state.
         */
        PENDING_DELIVERY,
        /**
         * Milestone 11 NeoForge Slice 2 (Operation.CHEQUE_REDEMPTION only): the presented cheque
         * UUID does not correspond to any real Rails-side {@code BankCheque}. Rendered as its
         * own distinct message per Codex Prompt 11's own "clearly render invalid/redeemed/
         * cancelled outcomes" requirement, not folded into {@link #CLEAN_REJECTION}.
         */
        CHEQUE_NOT_FOUND,
        /** Milestone 11 NeoForge Slice 2 (Operation.CHEQUE_REDEMPTION only): the cheque was already redeemed -- by this account or a different one. */
        CHEQUE_ALREADY_REDEEMED,
        /** Milestone 11 NeoForge Slice 2 (Operation.CHEQUE_REDEMPTION only): the cheque was cancelled. */
        CHEQUE_CANCELLED,
        /** Milestone 11 NeoForge Slice 2 (Operation.CHEQUE_REDEMPTION only): the cheque was voided. */
        CHEQUE_VOIDED
    }

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_transfer_result");
    public static final Type<BankTransferResultS2CPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, BankTransferResultS2CPayload> STREAM_CODEC =
            StreamCodec.of(BankTransferResultS2CPayload::encode, BankTransferResultS2CPayload::decode);

    public BankTransferResultS2CPayload {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(kind, "kind");
    }

    public static void send(ServerPlayer player, Operation operation, Kind kind) {
        PacketDistributor.sendToPlayer(player, new BankTransferResultS2CPayload(operation, kind));
    }

    private static void encode(FriendlyByteBuf buffer, BankTransferResultS2CPayload payload) {
        buffer.writeEnum(payload.operation);
        buffer.writeEnum(payload.kind);
    }

    private static BankTransferResultS2CPayload decode(FriendlyByteBuf buffer) {
        Operation operation = buffer.readEnum(Operation.class);
        Kind kind = buffer.readEnum(Kind.class);
        return new BankTransferResultS2CPayload(operation, kind);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

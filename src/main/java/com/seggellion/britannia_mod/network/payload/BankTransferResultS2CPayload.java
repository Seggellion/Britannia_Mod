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
 * <p><b>Both enums are encoded by ordinal</b> ({@code writeEnum}/{@code readEnum}), so new
 * constants must always be <b>appended</b>, never inserted. A client running an older build of
 * this mod against a newer server would fail to decode an unknown ordinal, which is the mixed-
 * version case §1.1 rule 3 keeps in view even for a packet whose two ends are both NeoForge.
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
        CHEQUE_VOIDED,
        /**
         * Bank interface rebuild, Milestone 6b: a Deposit All Coins sweep found no coins.
         *
         * <p>Its own kind rather than {@link #CLEAN_REJECTION} because nothing was rejected and
         * nothing went wrong -- the player's purse was simply empty. Design §15 lists "nothing to
         * deposit" as its own outcome category for exactly this reason: "there was nothing to
         * give" and "the teller will not take it" are different sentences, and collapsing them
         * tells a player their bank is refusing them when it is not.
         */
        NOTHING_TO_DEPOSIT,
        /**
         * Bank interface rebuild, Milestone 6b: crediting the sweep would push a denomination's
         * balance past what the account can hold. Design §15's "insufficient bank capacity",
         * applied to currency.
         *
         * <p>Distinct from {@link #CLEAN_REJECTION} because it is actionable: the player can
         * withdraw or spend and try again, which a generic refusal would not tell them.
         */
        BALANCE_CAPACITY_EXCEEDED,
        /**
         * Bank interface rebuild, Milestone 15: a withdrawal was refused because the player's
         * inventory has no room -- design §15's "player inventory full", the category Milestone 0
         * §3.4 found being flattened into {@link #CLEAN_REJECTION}.
         *
         * <p>The server handles this case impeccably -- the capacity pre-check runs before any
         * receipt is written, Rails' reservation is cancelled, nothing is created or lost -- and
         * then told the player the teller "can't complete that right now", which is
         * indistinguishable from a dead teller or a stale item. It is the most actionable
         * rejection in the whole system: drop something and press the button again. Appended,
         * never inserted -- both enums travel by ordinal.
         */
        INVENTORY_FULL,
        /**
         * Bank interface rebuild, Milestone 16: the account's balance in the requested
         * denomination cannot cover the withdrawal. Design §15's "insufficient balance" as a
         * server truth -- until now it existed only as the client's own pre-check against a
         * possibly-stale snapshot (the Milestone 0 §4.2 note), and the real refusal arrived as
         * the generic rejection. A modified client that skips the pre-check now gets the same
         * readable answer an honest one shows itself.
         */
        INSUFFICIENT_BALANCE,
        /**
         * Bank interface rebuild, Milestone 17: the deposited item is one the bank refuses on
         * principle -- currency nested in a container, a quest-bound item, an unrecognised mod
         * origin. Design §15's "ineligible item". Actionable in the negative: stop trying, no
         * amount of retrying or making room changes the answer.
         */
        INELIGIBLE_ITEM,
        /**
         * Bank interface rebuild, Milestone 17: the vault's weight limit cannot absorb the
         * deposited item. Design §15's "insufficient bank capacity" -- the deposit-side sibling
         * of {@link #INVENTORY_FULL}, and just as actionable: withdraw something and retry.
         * Distinct from {@link #BALANCE_CAPACITY_EXCEEDED}, which is the coin-count ceiling.
         */
        BANK_CAPACITY_EXCEEDED,
        /**
         * Bank interface rebuild, Milestone 17: the stored item named by the withdrawal no
         * longer exists in the account -- most plausibly withdrawn moments ago by another client
         * holding the same account open. Design §15's "stored item no longer available". Nothing
         * to act on: the refresh shows the truth.
         */
        STORED_ITEM_UNAVAILABLE
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

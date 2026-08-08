package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation;

/**
 * Which sound a successful banking mutation makes (owner, 2026-08-04): coins jingle, items thud
 * or creak, cheques say nothing.
 *
 * <h2>Why the operation alone cannot answer this</h2>
 * {@link Operation} is deliberately coarse -- a currency deposit and an item deposit are both
 * {@code DEPOSIT}, because the mutation lock never needed to tell them apart (Milestone 2). The
 * sound does. The missing bit comes from the <b>sender</b>, which always knows what it is
 * moving: the coin buttons and Deposit All Coins move currency by construction, and a drag knows
 * from the stack under the cursor. It is captured when the lock is claimed and read back when
 * the refresh confirms success.
 *
 * <p>Plain and Minecraft-free (Architecture Decision 0): this answers with a cue, and the one
 * client-side handler maps that cue to a registered {@code SoundEvent}. The decision is testable;
 * the playback is three lines with nothing to get wrong.
 */
public enum BankMutationCue {
    /** Coins moved, either direction -- {@code gold_coin}. */
    COIN,
    /** An item went into the vault -- {@code hit02}. */
    ITEM_DEPOSITED,
    /** An item came back out -- {@code leather1}. */
    ITEM_WITHDRAWN,
    /** Nothing to say: cheque issuance and redemption have no sound of their own. */
    SILENT;

    /**
     * @param operation     what the server reported the mutation as
     * @param movesCurrency what the sender knew it was moving when it claimed the lock
     */
    public static BankMutationCue forSuccess(Operation operation, boolean movesCurrency) {
        if (operation == null) return SILENT;
        return switch (operation) {
            case DEPOSIT -> movesCurrency ? COIN : ITEM_DEPOSITED;
            case WITHDRAWAL -> movesCurrency ? COIN : ITEM_WITHDRAWN;
            // A cheque is written or cashed, never carried in or out. Both already produce their
            // own visible outcome (the item appears, or the balance rises); adding an item thud
            // would claim something moved that did not.
            case CHEQUE_ISSUANCE, CHEQUE_REDEMPTION -> SILENT;
        };
    }
}

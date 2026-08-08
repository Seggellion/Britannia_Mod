package com.seggellion.britannia_mod.bank.transfer;

/**
 * Mirrors Rails' {@code BankTransferOperation.OPERATION_TYPES} vocabulary exactly
 * (Milestone 8 Rails Slice 1) rather than inventing a parallel naming scheme -- this receipt
 * is a local echo of that same operation, not a different concept.
 *
 * <p>{@code CHEQUE_ISSUANCE} (Milestone 11 NeoForge Slice 1) reuses {@link BankTransferReceipt}'s
 * existing currency-amount slot unchanged -- see that class's own docs for why a cheque
 * issuance receipt needs no new field at all, unlike {@code bankItemPublicId}'s own addition.
 *
 * <p>{@code CHEQUE_REDEMPTION} (Milestone 11 NeoForge Slice 2) does NOT reuse that shape --
 * unlike issuance, a redemption receipt carries neither an item payload nor a currency amount
 * at all (see {@link BankTransferReceipt}'s own docs for the real, three-way schema change this
 * required).
 */
public enum BankTransferOperationType {
    DEPOSIT,
    WITHDRAWAL,
    CHEQUE_ISSUANCE,
    CHEQUE_REDEMPTION,
    /**
     * Bank interface rebuild, Milestone 6b: one Deposit All Coins sweep, credited as a single
     * Rails {@code bulk_currency_deposit} operation.
     *
     * <p><b>This needs no {@link BankTransferReceipt} schema change, and deliberately does not
     * take one.</b> A bulk receipt is currency-shaped -- {@code currencyAmount} carries the
     * sweep's total value in copper, {@code itemPayload}/{@code bankItemPublicId}/{@code
     * worldNpcPublicId} all absent -- so it satisfies the existing exactly-one-of invariant
     * unchanged. What tells it apart from a single-denomination currency deposit is this enum
     * value, not field presence, which is why the three per-denomination totals do not need
     * persisting: a resume only ever re-sends {@code confirm} with the operation id, and Rails
     * already holds the amounts. The stored total is diagnostic, never read back to reconstruct
     * the operation.
     *
     * <p>That distinction is worth more than it looks. {@link
     * BankTransferReceiptStore#SCHEMA_VERSION} is fail-closed by design: bumping it makes every
     * receipt written under the old version surface as {@code UnreadableEntry}, so a shard
     * carrying pending reconciliations at upgrade time would lose the ability to resume them.
     * Adding an enum value costs nothing and keeps them readable.
     */
    BULK_CURRENCY_DEPOSIT
}

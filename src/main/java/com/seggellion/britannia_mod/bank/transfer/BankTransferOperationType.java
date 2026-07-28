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
    CHEQUE_REDEMPTION
}

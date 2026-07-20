package com.seggellion.britannia_mod.bank.transfer;

/**
 * Mirrors Rails' {@code BankTransferOperation.OPERATION_TYPES} vocabulary exactly
 * (Milestone 8 Rails Slice 1) rather than inventing a parallel naming scheme -- this receipt
 * is a local echo of that same operation, not a different concept.
 */
public enum BankTransferOperationType {
    DEPOSIT,
    WITHDRAWAL
}

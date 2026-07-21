package com.seggellion.britannia_mod.service.banking;

/**
 * Which HTTP call a withdrawal failure happened during -- mirrors {@link
 * BankingDepositStage}. Only two stages, matching deposit: insertion itself is never a network
 * call, so there is no "transport failure during insertion" to represent here -- an insertion
 * problem is always reported via {@link BankingWithdrawalResult.Aborted} (before confirm) or
 * folded into the confirm-stage handling (after insertion, confirm still runs), never this
 * enum.
 */
public enum BankingWithdrawalStage {
    PREPARE,
    CONFIRM
}

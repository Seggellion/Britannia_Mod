package com.seggellion.britannia_mod.service.banking;

/**
 * Which HTTP call a cheque issuance failure happened during -- mirrors {@link
 * BankingWithdrawalStage}. Only two stages: insertion itself is never a network call, and
 * (unlike withdrawal) it always happens strictly after confirm here, never in between, so there
 * is no third stage to represent.
 */
public enum BankingChequeIssuanceStage {
    PREPARE,
    CONFIRM
}

package com.seggellion.britannia_mod.service.banking;

/**
 * Which HTTP call a deposit failure happened during -- matters because the two stages leave
 * the world in very different states: a {@link #PREPARE} failure means nothing physical has
 * happened yet (the item is still in the player's inventory, no receipt exists); a {@link
 * #CONFIRM} failure means the item has already been removed and a durable local receipt has
 * already been written (protocol steps 5-6, docs/banking_item_transfer.md), so the receipt is
 * deliberately left unresolved for startup reconciliation to find later.
 */
public enum BankingDepositStage {
    PREPARE,
    CONFIRM
}

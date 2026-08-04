package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.bank.item.BankItemEligibility;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * Milestone 11: whether an inventory stack would be accepted if the player deposited it -- the
 * shading hint on the Bank Box's player grid, and nothing more.
 *
 * <p>The disjunction mirrors {@code BankingTransferPacketService.handleDeposit}'s routing order
 * exactly: a bare coin stack goes to the currency protocol, everything else through {@link
 * BankItemEligibility}. A cheque is depositable too -- since the Milestone 17 gate corrective
 * that means STORED, not cashed (the explicit cheque arm below predates the override and is now
 * merely a fast path; a cheque passes {@code checkEligible} anyway). The legacy {@code
 * BankScreen.isDepositable} carried the same checks privately; that screen dies at Milestone
 * 19, and Milestone 13's drag engine needs this same answer, so it lives here once.
 *
 * <p><b>UX only, never a boundary.</b> A modified client that ignores the shading and sends an
 * ineligible slot is rejected by the server's own already-tested path -- the same trust model
 * every banking surface in this mod uses. Nothing reads this server-side.
 */
public final class BankDepositHint {

    private BankDepositHint() {
    }

    public static boolean isDepositable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (CurrencyItemRegistry.isCurrencyStack(stack)) return true;
        if (stack.getItem() == ItemRegistry.BANK_CHEQUE.get()) return true;
        try {
            BankItemEligibility.checkEligible(stack);
            return true;
        } catch (BankItemEligibility.IneligibleItemException ineligible) {
            return false;
        }
    }
}

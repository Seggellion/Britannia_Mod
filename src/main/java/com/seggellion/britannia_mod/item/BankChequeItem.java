package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The physical bank cheque item -- Milestone 11 NeoForge Slice 1. Its tooltip is the "inspect
 * its display data" surface Codex Prompt 11's own manual verification calls for, built entirely
 * from {@link BankChequeData}, which is display-only by construction (see that class's own
 * docs) -- this class never reads anything else, and nothing downstream ever trusts what this
 * tooltip shows as authoritative.
 */
public class BankChequeItem extends Item {
    public BankChequeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BankChequeData data = stack.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
        if (data == null) return;

        // Milestone 8c: displayAmount is a coin count of the cheque's own denomination, so it is
        // shown as written. It used to be divided out of a copper value, which was right only for
        // gold -- a 500-silver cheque read as "5 gold".
        tooltip.add(Component.translatable(
                "item.britannia_mod.bank_cheque.amount",
                String.format(java.util.Locale.ROOT, "%,d", data.displayAmount()),
                Component.translatable("screen.britannia_mod.bank.cheque.denomination." + data.currencyKey())
        ).withStyle(ChatFormatting.GOLD));
        if (!data.issuerText().isBlank()) {
            tooltip.add(Component.literal("Issued by " + data.issuerText()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Cheque ID: " + data.chequeId()).withStyle(ChatFormatting.DARK_GRAY));
    }
}

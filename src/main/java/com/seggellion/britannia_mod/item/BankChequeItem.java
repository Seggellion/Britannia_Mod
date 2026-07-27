package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.economy.CoinConversion;
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

        long gold = data.displayAmount() / CoinConversion.COPPER_PER_GOLD;
        tooltip.add(Component.literal(gold + " gold").withStyle(ChatFormatting.GOLD));
        if (!data.issuerText().isBlank()) {
            tooltip.add(Component.literal("Issued by " + data.issuerText()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Cheque ID: " + data.chequeId()).withStyle(ChatFormatting.DARK_GRAY));
    }
}

package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.farming.FertilizedSoilService;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class FertilizedDirtItem extends Item {
    public FertilizedDirtItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemInteractionResult result = FertilizedSoilService.apply(context.getLevel(), context.getClickedPos(),
                context.getPlayer(), context.getItemInHand());
        return result == ItemInteractionResult.FAIL ? InteractionResult.FAIL
                : InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}

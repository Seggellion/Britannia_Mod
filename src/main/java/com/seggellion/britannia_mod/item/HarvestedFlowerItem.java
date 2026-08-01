package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import com.seggellion.britannia_mod.farming.FruitProvenance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Ordinary harvested flower produce with the established crop-to-seed activation. */
public final class HarvestedFlowerItem extends Item {
    public HarvestedFlowerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!CropSeedExtractor.shouldExtract(player, hand)) {
            return InteractionResultHolder.pass(held);
        }
        ResourceLocation harvestedId = BuiltInRegistries.ITEM.getKey(held.getItem());
        FlowerDefinition definition = FlowerRegistry.initial().byHarvestedItemId(harvestedId).orElse(null);
        if (definition == null) {
            return InteractionResultHolder.pass(held);
        }
        return CropSeedExtractor.tryExtractSeed(level, player, hand, ignored ->
                new ItemStack(BuiltInRegistries.ITEM.get(definition.seedItemId()))
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (CropQualityCalculator.hasQuality(stack)) {
            tooltip.add(CropQualityCalculator.qualityTooltip(stack));
        }
        FruitProvenance.appendTooltip(stack, tooltip);
    }
}

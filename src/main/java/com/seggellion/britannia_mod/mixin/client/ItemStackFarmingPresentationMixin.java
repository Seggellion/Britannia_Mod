package com.seggellion.britannia_mod.mixin.client;

import com.seggellion.britannia_mod.client.farming.ClientFarmingPlantingItemPresentation;
import com.seggellion.britannia_mod.farming.FarmingPlantingItemPresentation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Client-only viewer-local name and tooltip projection. The target ItemStack is never modified. */
@Mixin(ItemStack.class)
public abstract class ItemStackFarmingPresentationMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true, remap = false)
    private void britannia$viewerSpecificPlantingName(CallbackInfoReturnable<Component> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        var presentation = ClientFarmingPlantingItemPresentation.resolve(
                stack, callback.getReturnValue(), FarmingPlantingItemPresentation.Surface.ITEM_NAME
        );
        if (presentation.applicable()) {
            callback.setReturnValue(presentation.displayName());
        }
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true, remap = false)
    private void britannia$viewerSpecificPlantingTooltip(
            Item.TooltipContext context,
            @Nullable Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> callback
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        List<Component> current = callback.getReturnValue();
        if (current.isEmpty()) {
            // Respect HIDE_TOOLTIP instead of manufacturing a viewer-visible line.
            return;
        }
        Component existingName = current.getFirst();
        var presentation = ClientFarmingPlantingItemPresentation.resolve(
                stack, existingName, FarmingPlantingItemPresentation.Surface.TOOLTIP
        );
        if (!presentation.applicable() || presentation.identified()) {
            return;
        }

        List<Component> masked = new ArrayList<>();
        masked.add(Component.empty().append(presentation.displayName()).withStyle(stack.getRarity().getStyleModifier()));
        if (flag.isAdvanced()) {
            // Approved administrative/debug boundary: retain registry/component diagnostics only.
            masked.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
            int components = stack.getComponents().size();
            if (components > 0) {
                masked.add(Component.translatable("item.components", components).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        callback.setReturnValue(List.copyOf(masked));
    }
}

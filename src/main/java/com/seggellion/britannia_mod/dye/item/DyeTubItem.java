package com.seggellion.britannia_mod.dye.item;

import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.dye.preview.DyePreviewRuntime;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class DyeTubItem extends Item {
    public DyeTubItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tubStack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(tubStack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(tubStack, true);
        }

        ItemStack pigmentStack = player.getOffhandItem();
        if (DyeItemRegistry.pigmentId(pigmentStack.getItem()).isEmpty()
                && pigmentStack.getItem() == BannerItemRegistry.BANNER.get()
                && player instanceof ServerPlayer serverPlayer) {
            DyePreviewRuntime.openPreview(serverPlayer);
            return InteractionResultHolder.sidedSuccess(tubStack, false);
        }
        DyeTubLoadPlan plan = DyeTubLoadingService.plan(
                tubStack,
                this,
                pigmentStack,
                DyeItemRegistry::pigmentId,
                BannerDataRegistries.current(),
                BannerDataRegistries.isAvailable(),
                DataComponentRegistry.DYE_TUB_STATE.get());
        DyeTubLoadResult result = DyeTubLoadingService.apply(
                plan,
                tubStack,
                pigmentStack,
                player.hasInfiniteMaterials(),
                DataComponentRegistry.DYE_TUB_STATE.get());

        sendFeedback(player, result);
        if (result.emitsSuccessEffects() && level instanceof ServerLevel serverLevel) {
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.BOTTLE_FILL,
                    SoundSource.PLAYERS,
                    0.7F,
                    1.0F);
            serverLevel.sendParticles(
                    ParticleTypes.SPLASH,
                    player.getX(),
                    player.getY() + 0.8D,
                    player.getZ(),
                    8,
                    0.25D,
                    0.2D,
                    0.25D,
                    0.02D);
        }
        return InteractionResultHolder.sidedSuccess(tubStack, false);
    }

    private static void sendFeedback(Player player, DyeTubLoadResult result) {
        String key = switch (result) {
            case LOADED -> "message.britannia_mod.dye_tub.loaded";
            case REPLACED -> "message.britannia_mod.dye_tub.replaced";
            case ALREADY_CONTAINS -> "message.britannia_mod.dye_tub.already_contains";
            case EMPTY_OFF_HAND -> "message.britannia_mod.dye_tub.requires_off_hand";
            case INVALID_OFF_HAND_ITEM -> "message.britannia_mod.dye_tub.invalid_off_hand";
            case REGISTRY_UNAVAILABLE -> "message.britannia_mod.dye_tub.registry_unavailable";
            case PIGMENT_DEFINITION_MISSING, PIGMENT_DISABLED -> "message.britannia_mod.dye_tub.missing_pigment";
            case INVALID_TUB, STATE_COMPONENT_FAILURE -> "message.britannia_mod.dye_tub.state_failure";
        };
        player.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.addAll(DyeTubTooltip.lines(DyeTubStateAccess.read(stack), BannerDataRegistries.current()));
    }
}

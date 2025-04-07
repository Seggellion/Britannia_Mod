package com.seggellion.britannia_mod.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TwoHandedAxeItem extends AxeItem {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "attack_speed");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "attack_damage");

    public TwoHandedAxeItem(Tier tier, Item.Properties properties) {
        super(tier, properties); // Pass only the tier and properties to the parent constructor
    }

    @Override
    public boolean isCorrectToolForDrops(net.minecraft.world.item.ItemStack stack, BlockState state) {
        // Allow breaking logs in Adventure mode
        return state.is(BlockTags.LOGS) || super.isCorrectToolForDrops(stack, state);
    }

  @Override
    public boolean canAttackBlock(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        // Custom logic to simulate slower swing
        if (player.isCreative()) return true;
        if (state.is(BlockTags.LOGS)) {
            player.swingTime = 40; // Increase swing time to slow the animation
        }
        return super.canAttackBlock(state, level, pos, player);
    }

       @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return 2.0F; // Reduced block breaking speed for logs
        }
        return super.getDestroySpeed(stack, state);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ATTACK_DAMAGE_MODIFIER, 10.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.HAND) // Use EquipmentSlotGroup.HAND for both hands
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(ATTACK_SPEED_MODIFIER, -3.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.HAND) // Use EquipmentSlotGroup.HAND for both hands
                .build();
    }



@Override
public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
    super.inventoryTick(stack, world, entity, slot, selected);

    if (!world.isClientSide && entity instanceof Player player && selected) {
        // Only reset attack strength if the player is holding this specific item
        if (player.getMainHandItem() == stack) {
            player.resetAttackStrengthTicker(); // Safely reset swing strength
        }
    }
}



}

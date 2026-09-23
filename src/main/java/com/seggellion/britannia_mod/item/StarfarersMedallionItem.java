package com.seggellion.britannia_mod.item;

import java.util.List;

import com.seggellion.britannia_mod.blessed.BlessedItemLifecycleMetadata;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

/**
 * The Starfarer's Medallion: an owner-only wearable commemorative collectible.
 *
 * <p>Given to recipients of a physical UltimaCraft business card who claimed it at
 * ultimacraft.com/starfarer. Its whole value is provenance, so it deliberately does
 * nothing beyond being worn: no attribute modifiers, no food properties, no recipe, no durability, no
 * economic weight, no progression. A player carrying one is exactly as capable as a
 * player who is not, which is the point -- it must be desirable because of what it
 * records, never because of what it does.
 *
 * <p>Phase 2 makes this a vanilla CHEST-slot wearable. The blessed owner stamp is checked
 * on both right-click and armor-slot insertion. It is not armor and grants no protection;
 * occupying CHEST intentionally precludes wearing a chestplate or elytra at the same time.
 *
 * <p>Two properties are load-bearing rather than decorative:
 *
 * <p>{@code stacksTo(1)} is a safety property, not flavour. Blessed items are stamped
 * with identical custom data, so two copies of one medallion are byte-identical and a
 * stackable item would silently MERGE them -- which is exactly how the offhand
 * duplication characterized in M1 hides itself, growing a stack from one to two with
 * nothing visible on screen. A single-stack item cannot merge, so a duplicate has to
 * occupy its own slot where it can be seen and diagnosed.
 *
 * <p>{@code Rarity.EPIC} colours the name in the tooltip. It confers no capability of
 * any kind; it is presentation only.
 *
 * <p>Deliberately NOT set: {@code fireResistant()}. Protecting a blessed item from
 * lava is rescue behaviour, and rescue belongs to the later lifecycle milestone where
 * it can be applied to every blessed item and tested, rather than smuggled in here as
 * a property of one.
 */
public class StarfarersMedallionItem extends Item implements Equipable {

    public StarfarersMedallionItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        return slot == EquipmentSlot.CHEST && entity instanceof Player player
                && BlessedItemLifecycleMetadata.ownerOf(stack)
                        .filter(player.getUUID()::equals).isPresent();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canEquip(stack, EquipmentSlot.CHEST, player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "item.britannia_mod.starfarers_medallion.owner_only"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        // Always shown, not gated on the advanced tooltip: the lore IS the item.
        lines.add(Component.translatable("item.britannia_mod.starfarers_medallion.lore")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.britannia_mod.starfarers_medallion.provenance")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.britannia_mod.starfarers_medallion.occasion")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

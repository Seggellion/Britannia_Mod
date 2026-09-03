package com.seggellion.britannia_mod.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

/**
 * The Starfarer's Medallion: a commemorative collectible, and nothing else.
 *
 * <p>Given to recipients of a physical UltimaCraft business card who claimed it at
 * ultimacraft.com/starfarer. Its whole value is provenance, so it deliberately does
 * nothing: no attribute modifiers, no food properties, no recipe, no durability, no
 * economic weight, no progression. A player carrying one is exactly as capable as a
 * player who is not, which is the point -- it must be desirable because of what it
 * records, never because of what it does.
 *
 * <p>Wearability is explicitly out of scope for this project. There is no accessory
 * or curio slot here and none should be added for this item.
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
public class StarfarersMedallionItem extends Item {

    public StarfarersMedallionItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        // Always shown, not gated on the advanced tooltip: the lore IS the item.
        lines.add(Component.translatable("item.britannia_mod.starfarers_medallion.lore")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.britannia_mod.starfarers_medallion.occasion")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

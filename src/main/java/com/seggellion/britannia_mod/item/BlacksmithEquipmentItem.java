package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.registry.WeaponProfile;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.skill.crafting.ArmorProfileRegistry;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.ResistanceProfile;
import com.seggellion.britannia_mod.skill.crafting.ShieldProfileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Placeholder-capable catalogue output. Its stable recipe definition supplies all gameplay metadata. */
public class BlacksmithEquipmentItem extends Item {
    private final CraftableDef definition;

    public BlacksmithEquipmentItem(CraftableDef definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    public CraftableDef definition() { return definition; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlacksmithItemData.appendTooltip(stack, tooltip);
        tooltip.add(Component.literal("Weight: " + definition.baseWeight()).withStyle(ChatFormatting.GRAY));

        WeaponProfile weapon = WeaponRegistry.getProfile(definition.weaponProfileId());
        if (weapon != null) {
            tooltip.add(Component.literal("Damage: " + weapon.minimumDamage() + "-" + weapon.maximumDamage()));
            tooltip.add(Component.literal("Speed: " + weapon.speed() + "  Strength: " + weapon.minimumStrength()));
            tooltip.add(Component.literal(weapon.twoHanded() ? "Two-handed" : "One-handed"));
        }
        ResistanceProfile armor = ArmorProfileRegistry.get(definition.armorProfileId());
        if (armor != null) appendResistance(tooltip, armor);
        ShieldProfileRegistry.ShieldProfile shield = ShieldProfileRegistry.get(definition.shieldProfileId());
        if (shield != null) {
            appendResistance(tooltip, shield.bonus());
            tooltip.add(Component.literal("Minimum Strength: " + shield.minimumStrength()));
        }
        if (definition.provisional() && flag.hasShiftDown()) {
            tooltip.add(Component.literal("Provisional blacksmithing data").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendResistance(List<Component> tooltip, ResistanceProfile r) {
        tooltip.add(Component.literal("Resist: Physical " + r.physical() + ", Fire " + r.fire()
                + ", Ice " + r.ice() + ", Poison " + r.poison() + ", Magic " + r.magic()));
    }
}

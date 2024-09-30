package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class NightSightSpell extends Spell {

    @Override
    protected int getManaCost() {
        return 4;
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(BritanniaMod.SPIDERS_SILK.get()),
            new ItemStack(BritanniaMod.SULPHUROUS_ASH.get())
        };
    }

    @Override
    protected void applyEffect(Player target) {
        target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 24000)); // 20 minutes of Night Vision
    }

    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.NIGHT_SIGHT_ITEM.get();
    }
}

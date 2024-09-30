package com.seggellion.britannia_mod.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public abstract class SpellBase {
    protected String name;
    protected int manaCost;
    protected int range;
    protected int requiredLevel;

    public SpellBase(String name, int manaCost, int range, int requiredLevel) {
        this.name = name;
        this.manaCost = manaCost;
        this.range = range;
        this.requiredLevel = requiredLevel;
    }

    public boolean canCast(Player player) {
        return player.experienceLevel >= requiredLevel && ManaHandler.useMana(player, manaCost);
    }

    public abstract void castSelf(Player player);
    public abstract void castTarget(Player caster, Player target);
}

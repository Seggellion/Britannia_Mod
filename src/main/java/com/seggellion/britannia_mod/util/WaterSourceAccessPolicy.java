package com.seggellion.britannia_mod.util;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** World-border and spawn-protection gate for non-destructive unlimited water access. */
public final class WaterSourceAccessPolicy {
    private WaterSourceAccessPolicy() {
    }

    public static Decision evaluate(Level level, BlockPos source, Player player) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(player, "player");
        return fromWorldPermission(level.mayInteract(player, source));
    }

    static Decision fromWorldPermission(boolean allowed) {
        return allowed ? Decision.ALLOWED : Decision.DENIED_WORLD;
    }

    public enum Decision {
        ALLOWED,
        DENIED_WORLD
    }
}

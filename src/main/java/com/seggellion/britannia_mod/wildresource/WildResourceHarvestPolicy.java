package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import com.seggellion.britannia_mod.structure.HouseBuildRights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Server authority shared by every economy-producing WildResource harvest strategy. */
public final class WildResourceHarvestPolicy {
    private WildResourceHarvestPolicy() {
    }

    public static Assessment evaluate(ServerLevel level, BlockPos position, ServerPlayer player) {
        ManagedExtractionPolicy.Actor actor = ManagedExtractionPolicy.actorOf(player);
        boolean creative = actor == ManagedExtractionPolicy.Actor.PLAYER
                && ManagedExtractionPolicy.isCreativeGameMode(player);
        boolean withinReach = player.canInteractWithBlock(position, 0.0D);
        boolean worldAllows = level.mayInteract(player, position);
        HouseBuildRights.Decision house = HouseBuildRights.evaluateBreak(level, position, player);
        Decision decision = decide(actor, creative, withinReach, worldAllows, house.permitted());
        return new Assessment(decision, decision == Decision.DENIED_HOUSE ? house.message() : null);
    }

    static Decision decide(
            ManagedExtractionPolicy.Actor actor,
            boolean creative,
            boolean withinReach,
            boolean worldAllows,
            boolean houseAllows
    ) {
        if (actor == ManagedExtractionPolicy.Actor.NON_PLAYER) {
            return Decision.DENIED_NON_PLAYER;
        }
        if (actor == ManagedExtractionPolicy.Actor.FAKE_PLAYER) {
            return Decision.DENIED_FAKE_PLAYER;
        }
        if (creative) {
            return Decision.DENIED_CREATIVE;
        }
        if (!withinReach) {
            return Decision.DENIED_REACH;
        }
        if (!worldAllows) {
            return Decision.DENIED_WORLD;
        }
        return houseAllows ? Decision.ALLOWED : Decision.DENIED_HOUSE;
    }

    public enum Decision {
        ALLOWED,
        DENIED_NON_PLAYER,
        DENIED_FAKE_PLAYER,
        DENIED_CREATIVE,
        DENIED_REACH,
        DENIED_WORLD,
        DENIED_HOUSE
    }

    public record Assessment(Decision decision, @Nullable String message) {
        public boolean allowed() {
            return decision == Decision.ALLOWED;
        }

        public void explain(ServerPlayer player) {
            if (message != null) {
                player.displayClientMessage(Component.literal(message), true);
            }
        }
    }
}

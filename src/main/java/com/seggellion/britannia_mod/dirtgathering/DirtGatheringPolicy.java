package com.seggellion.britannia_mod.dirtgathering;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
import com.seggellion.britannia_mod.structure.HouseBuildRights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

/** The permission boundary for turning unchanged world dirt into a portable commodity. */
public final class DirtGatheringPolicy {
    private DirtGatheringPolicy() {
    }

    public static Assessment evaluate(ServerLevel level, BlockPos position, ServerPlayer player) {
        ManagedExtractionPolicy.Actor actor = ManagedExtractionPolicy.actorOf(player);
        GameType gameMode = player.gameMode.getGameModeForPlayer();
        boolean withinReach = player.canInteractWithBlock(position, 0.0D);
        boolean worldAllows = level.mayInteract(player, position);
        HouseBuildRights.Decision house = HouseBuildRights.evaluateBreak(level, position, player);
        Decision decision = decide(
                actor,
                gameMode == GameType.CREATIVE,
                gameMode == GameType.SPECTATOR,
                withinReach,
                worldAllows,
                house.permitted());
        return new Assessment(decision, decision == Decision.DENIED_HOUSE ? house.message() : null);
    }

    static Decision decide(
            ManagedExtractionPolicy.Actor actor,
            boolean creative,
            boolean spectator,
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
        if (spectator) {
            return Decision.DENIED_SPECTATOR;
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
        DENIED_SPECTATOR,
        DENIED_REACH,
        DENIED_WORLD,
        DENIED_HOUSE
    }

    public record Assessment(Decision decision, @Nullable String houseMessage) {
        public boolean allowed() {
            return decision == Decision.ALLOWED;
        }

        public Component feedback() {
            return switch (decision) {
                case DENIED_CREATIVE, DENIED_SPECTATOR ->
                        Component.translatable("message.britannia_mod.dirt_gather.denied_mode");
                case DENIED_REACH ->
                        Component.translatable("message.britannia_mod.dirt_gather.out_of_reach");
                case DENIED_WORLD ->
                        Component.translatable("message.britannia_mod.dirt_gather.denied_world");
                case DENIED_HOUSE -> houseMessage == null ? null : Component.literal(houseMessage);
                default -> null;
            };
        }
    }
}

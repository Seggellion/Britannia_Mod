package com.seggellion.britannia_mod.entity.ai;

import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.PathfinderMob;
import org.slf4j.Logger;
import java.util.EnumSet;
import java.util.UUID;

public class EscortPlayerGoal extends Goal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PathfinderMob mob;
    private Player targetPlayer;
    private final double speedModifier;
    private final float stopDistance;
    private final float teleportDistance; // Teleports NPC if the player runs too fast

    public EscortPlayerGoal(PathfinderMob mob, double speedModifier, float stopDistance, float teleportDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.stopDistance = stopDistance;
        this.teleportDistance = teleportDistance;
        // Tells the AI that this goal controls movement and looking
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        UUID escortId = getEscortUuidFromTags();
        if (escortId == null) return false;

        // Dynamically find the player by UUID every time the AI evaluates
        Player player = mob.level().getPlayerByUUID(escortId);
        if (player == null || !player.isAlive()) return false;
        if (!hasActiveQuestAssignment(player)) return false;
        
        // Don't calculate paths if we are already standing next to them
        if (mob.distanceToSqr(player) < (stopDistance * stopDistance)) return false;

        this.targetPlayer = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPlayer != null 
            && targetPlayer.isAlive() 
            && mob.distanceToSqr(targetPlayer) > (stopDistance * stopDistance)
            && getEscortUuidFromTags() != null
            && hasActiveQuestAssignment(targetPlayer);
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(targetPlayer, speedModifier);
    }

    @Override
    public void stop() {
        targetPlayer = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetPlayer == null) return;

        mob.getLookControl().setLookAt(targetPlayer, 10.0F, (float)mob.getMaxHeadXRot());
        
        double distanceSq = mob.distanceToSqr(targetPlayer);
        
        // If the player uses a mount or flies, snap the NPC to them to prevent failing the escort
        if (distanceSq > (teleportDistance * teleportDistance)) {
            mob.teleportTo(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ());
            mob.getNavigation().stop();
        } else if (distanceSq > (stopDistance * stopDistance)) {
            // Re-calculate path occasionally as the player moves
            mob.getNavigation().moveTo(targetPlayer, speedModifier);
        }
    }

    /**
     * Parses the NeoForge entity tags to extract the player's UUID
     */
    private UUID getEscortUuidFromTags() {
        for (String tag : mob.getTags()) {
            if (tag.startsWith("quest_escort_")) {
                try {
                    return UUID.fromString(tag.substring("quest_escort_".length()));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean hasActiveQuestAssignment(Player player) {
        String questStateId = tagValue("quest_state_id_");
        if (!mob.getTags().contains("escort_active") || questStateId.isBlank()) {
            clearInvalidEscortAssignment("missing quest_state_id");
            return false;
        }

        if (mob.level().isClientSide()) {
            return true;
        }

        boolean active = ServerQuestTable.hasActiveQuestState(player.getUUID(), questStateId);
        if (!active) {
            clearInvalidEscortAssignment("inactive quest_state_id");
        }
        return active;
    }

    private String tagValue(String prefix) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(prefix)) {
                return tag.substring(prefix.length());
            }
        }
        return "";
    }

    private void clearInvalidEscortAssignment(String reason) {
        if (mob.level().isClientSide()) return;

        boolean changed = false;
        for (String tag : java.util.List.copyOf(mob.getTags())) {
            if (tag.equals("escort_active")
                    || tag.startsWith("quest_escort_")
                    || tag.startsWith("quest_state_id_")
                    || tag.startsWith("quest_id_")
                    || tag.startsWith("quest_key_")) {
                changed |= mob.removeTag(tag);
            }
        }
        if (changed) {
            mob.getNavigation().stop();
            LOGGER.warn("Cleared invalid escort assignment reason={} entity={}", reason, mob.getStringUUID());
        }
    }
}

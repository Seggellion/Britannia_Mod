package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.network.payload.SpawnEscortC2SPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class QuestPayloadHandler {

    public static void handleSpawnEscort(final SpawnEscortC2SPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();

            // 1. Find and DELETE the original NPC
            Entity oldEntity = level.getEntity(payload.npcUuid());
            String npcName = "Traveler";

            if (oldEntity instanceof com.seggellion.britannia_mod.entity.QuestGiverEntity oldQg) {
                npcName = oldQg.getName().getString(); // Safely gets the stripped name (e.g., "Mitexi")
                oldEntity.discard(); // Deleting this frees up the Spawner Block to generate a new escort!
            }

            // 2. Spawn a NEW QuestGiverEntity so it visually matches your mod
            com.seggellion.britannia_mod.entity.QuestGiverEntity escort = com.seggellion.britannia_mod.registry.EntityRegistry.QUEST_GIVER.get().create(level);
            
            if (escort != null) {
                // Move it to the player
                escort.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                
                // Set the exact same name
                escort.setPersonalName(npcName);
                
                // Apply our tracking tags
                escort.addTag("quest_escort_" + player.getUUID().toString());
                escort.addTag("quest_id_" + payload.questId());
                escort.setPersistenceRequired();

                // 3. INJECT THE FOLLOW AI GOAL!
                escort.goalSelector.addGoal(2, new FollowPlayerGoal(escort, player, 1.2D, 5.0F, 2.0F));

                level.addFreshEntity(escort);
                player.sendSystemMessage(Component.literal("§e" + npcName + " joins your side. Lead the way."));
            }
        });
    }

    // --- CUSTOM AI: Makes the NPC follow the player ---
    public static class FollowPlayerGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final net.minecraft.world.entity.Mob mob;
        private final net.minecraft.world.entity.player.Player player;
        private final double speedModifier;
        private final float startDist;
        private final float stopDist;

        public FollowPlayerGoal(net.minecraft.world.entity.Mob mob, net.minecraft.world.entity.player.Player player, double speed, float startDist, float stopDist) {
            this.mob = mob;
            this.player = player;
            this.speedModifier = speed;
            this.startDist = startDist;
            this.stopDist = stopDist;
            this.setFlags(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.player.isAlive()) return false;
            return this.mob.distanceToSqr(this.player) > (this.startDist * this.startDist);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && this.mob.distanceToSqr(this.player) > (this.stopDist * this.stopDist);
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.mob.getLookControl().setLookAt(this.player, 10.0F, (float)this.mob.getMaxHeadXRot());
            this.mob.getNavigation().moveTo(this.player, this.speedModifier);
        }
    }
}
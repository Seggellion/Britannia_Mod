package com.seggellion.britannia_mod.farming;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

/** Shared server transaction for economic harvests; callers own species-specific lifecycle commits. */
public final class FarmingHarvestService {
    private static final ThreadLocal<Set<Root>> ACTIVE = ThreadLocal.withInitial(HashSet::new);
    private record Root(ServerLevel level, BlockPos pos) {}
    public enum Outcome {
        REFUSED(false, false), FAILURE(false, false), SUCCESS(true, false), FREE_SUCCESS(true, true);
        private final boolean success;
        private final boolean free;
        Outcome(boolean success, boolean free) { this.success=success; this.free=free; }
        public boolean successful() { return success; }
        public boolean free() { return free; }
    }
    private FarmingHarvestService() {}
    public static boolean isCommitting(ServerLevel level, BlockPos root) {
        return ACTIVE.get().contains(new Root(level,root));
    }

    public static double successChance(float skill, float required) {
        if (!Float.isFinite(skill) || !Float.isFinite(required) || skill < required) return 0;
        return Math.min(.95, .75 + .01 * ((double)skill - required));
    }

    /** The sole outcome draw. A refusal or approved administrator bypass never invokes the supplier. */
    public static Outcome roll(FarmingCultivationGate.Evaluation eligibility, DoubleSupplier random) {
        if (eligibility.type()==FarmingCultivationGate.ResultType.APPROVED_BYPASS) return Outcome.FREE_SUCCESS;
        if (eligibility.type()!=FarmingCultivationGate.ResultType.ELIGIBLE
                || !Float.isFinite(eligibility.currentFarmingSkill())) return Outcome.REFUSED;
        double draw=random.getAsDouble();
        if (!Double.isFinite(draw) || draw<0 || draw>=1) return Outcome.REFUSED;
        return draw < successChance(eligibility.currentFarmingSkill(),eligibility.requiredFarmingSkill())
                ? Outcome.SUCCESS : Outcome.FAILURE;
    }

    public static Outcome execute(Level level, BlockPos root, Player actor, Item plantingItem,
                                  BooleanSupplier livePreconditions, Function<Outcome,Boolean> commit,
                                  Runnable practice) {
        return execute(level,root,actor,plantingItem,livePreconditions,commit,practice,()->level.getRandom().nextDouble());
    }

    /** Injectable draw keeps probability and commit tests independent of yield, quality and practice RNG. */
    public static Outcome execute(Level level, BlockPos root, Player actor, Item plantingItem,
                                  BooleanSupplier livePreconditions, Function<Outcome,Boolean> commit,
                                  Runnable practice, DoubleSupplier random) {
        if (!(level instanceof ServerLevel server) || !(actor instanceof ServerPlayer player)
                || player.isSpectator() || !level.hasChunkAt(root) || !level.mayInteract(player,root)) return Outcome.REFUSED;
        Root key=new Root(server,root.immutable());
        if (!ACTIVE.get().add(key)) return Outcome.REFUSED;
        try {
            if (!livePreconditions.getAsBoolean()) return Outcome.REFUSED;
            var eligibility=FarmingCultivationGate.evaluate(player,plantingItem);
            if (!eligibility.permitsPlanting()) {
                FarmingCultivationGate.sendDenialFeedback(player,eligibility);
                FarmingCultivationGate.synchronizeDeniedInteraction(player,level,root);
                return Outcome.REFUSED;
            }
            Outcome outcome=roll(eligibility,random);
            // Injected callbacks and future hooks cannot commit a now-stale root.
            if (outcome==Outcome.REFUSED || !livePreconditions.getAsBoolean() || !commit.apply(outcome)) return Outcome.REFUSED;
            if (!outcome.free()) practice.run();
            if (outcome.successful()) NeoForge.EVENT_BUS.post(new FarmingHarvestCommittedEvent(
                    player,key.pos(),eligibility.resolvedRequirement().orElseThrow().speciesId(),outcome.free()));
            return outcome;
        } finally {
            ACTIVE.get().remove(key);
            if (ACTIVE.get().isEmpty()) ACTIVE.remove();
        }
    }
}

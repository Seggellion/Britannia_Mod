package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.util.PlayerDamageAttribution;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * UltimaCraft parrots cannot be killed. By anything.
 *
 * <p>Not player protection — total. Wild or tamed, whoever owns them: no weapon, projectile, mob,
 * explosion, falling anvil, hazard or fall into the void takes a parrot's health, and no parrot
 * dies. The one deliberate exception is an operator removing one on purpose; see
 * {@link #isOperatorRemoval(DamageSource)}.
 *
 * <p>The rule lives at the damage layer rather than in weapons, projectiles or the entity itself so
 * that future content inherits it without being told to, and so that no per-parrot state has to be
 * applied, migrated or kept in sync. A parrot restored from an old save is protected because the
 * rule is behavioural, not because anything was stamped on it.
 *
 * <h2>The four listeners</h2>
 *
 * <ol>
 *   <li>{@link AttackEntityEvent} — the melee short-circuit. Cancelling makes {@code Player#attack}
 *       return before it computes damage, so no attack cooldown is spent and no weapon durability
 *       is lost. It also keeps the most spammable attack off the path below, where NeoForge
 *       21.1.72's {@code LivingEntity#hurt} pushes a {@code DamageContainer} onto a per-entity
 *       stack <em>before</em> posting the damage event and never pops it when cancelled.
 *   <li>{@link LivingIncomingDamageEvent} — <b>the guarantee.</b> It fires inside
 *       {@code LivingEntity#hurt} after the invulnerability checks and before any damage
 *       processing; cancelling returns {@code false} from {@code hurt} at once, so health is never
 *       touched, no knockback or hurt animation plays, and {@code die()} is unreachable. Every
 *       in-world way to injure a parrot funnels through {@code hurt}, including the two that are
 *       easy to mistake for something else: {@code LivingEntity#onBelowWorld} is
 *       {@code hurt(fellOutOfWorld, 4.0F)}, and {@code LivingEntity#kill} — which
 *       {@code Entity#kill} does <em>not</em> resemble — is {@code hurt(genericKill, MAX_VALUE)}.
 *   <li>{@link LivingDeathEvent} — a safety net. It should be unreachable; if it fires, something
 *       bypassed the gate above, so the death is refused, health is restored and it logs.
 *   <li>{@link EntityTickEvent.Pre} — the floor under everything else, because
 *       {@code LivingEntity#baseTick} calls {@code tickDeath()} whenever health is at or below
 *       zero and {@code tickDeath} removes the entity <em>without</em> calling {@code die()}. A
 *       stray {@code setHealth(0)} — from a command, or a mod reaching past the damage pipeline —
 *       would otherwise delete a parrot with no event fired anywhere. The same listener lifts a
 *       parrot back out of the void, since a parrot that no longer takes fall-out-of-world damage
 *       would otherwise fall for ever, which is indistinguishable from death from where the owner
 *       is standing.
 * </ol>
 *
 * <p>All four run at {@link EventPriority#HIGHEST} so the decision lands before any other listener,
 * Britannia's or another mod's, acts on a hit that is not going to happen.
 *
 * <p>Server-authoritative: {@code LivingEntity#hurt} returns early on the logical client before the
 * damage event is posted, and the tick guard checks the side explicitly. The melee listener is
 * deliberately not side-guarded — {@code AttackEntityEvent} also fires client-side, and refusing
 * the swing there keeps the client's prediction in step rather than briefly disagreeing.
 */
public final class ParrotProtectionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How far below the world a parrot has to be before it is lifted out. Vanilla's own
     * out-of-world threshold is {@code minBuildHeight - 64}; acting sooner means the rescue happens
     * before {@code onBelowWorld} starts firing every tick.
     */
    private static final double VOID_RESCUE_DEPTH = 32.0;

    /** Melee: refuse the swing outright rather than letting it reach the damage pipeline. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof Parrot) {
            event.setCanceled(true);
        }
    }

    /** The load-bearing gate: no damage of any origin reaches a parrot's health. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Parrot parrot) || isOperatorRemoval(event.getSource())) {
            return;
        }
        event.setCanceled(true);

        // Parrot#hurt clears the sit order before it delegates to LivingEntity#hurt, so by the time
        // this event fires a sitting parrot has already been told to stand — for a hit that is not
        // going to happen. The sitting *pose* is a separate synced flag that SitWhenOrderedToGoal
        // only drops on its next tick, so it still reads true here and tells us the parrot was
        // sitting a moment ago. Putting the order back inside the same tick means a stray arrow or
        // a passing zombie cannot be used to shoo somebody's parrot off its perch.
        if (parrot.isInSittingPose() && !parrot.isOrderedToSit()) {
            parrot.setOrderedToSit(true);
        }
    }

    /**
     * Safety net. Reaching here means damage got past
     * {@link #onIncomingDamage(LivingIncomingDamageEvent)}. The death is refused and the parrot is
     * put back to full health, because a parrot left alive on zero health is worse than either
     * outcome. Resurrection is emphatically not the mechanism; the log exists so a bypass is
     * diagnosed rather than silently papered over.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Parrot parrot) || isOperatorRemoval(event.getSource())) {
            return;
        }
        event.setCanceled(true);
        reviveAtFullHealth(parrot);
        LOGGER.warn(
                "Parrot protection: a death reached LivingDeathEvent and was refused."
                        + " damageType={} responsiblePlayer={}. The incoming-damage gate should have"
                        + " stopped this; the bypassing damage path needs investigating.",
                event.getSource().getMsgId(),
                describe(PlayerDamageAttribution.responsiblePlayer(event.getSource())));
    }

    /**
     * The floor. Runs before {@code Entity#tick}, so a parrot that is somehow at zero health is
     * back at full before {@code baseTick} can reach {@code tickDeath}, and a parrot that has left
     * the bottom of the world is put back on the surface before it falls out of reach.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Parrot parrot)
                || parrot.isRemoved()
                || parrot.level().isClientSide()) {
            return;
        }

        if (parrot.getHealth() <= 0.0F) {
            // An operator's /kill lands here mid-removal; getLastDamageSource is retained for 40
            // ticks, which is comfortably longer than the death animation, so an authorised removal
            // is never undone.
            if (isOperatorRemoval(parrot.getLastDamageSource())) {
                return;
            }
            reviveAtFullHealth(parrot);
            LOGGER.warn(
                    "Parrot protection: a parrot was found at zero health with no damage event to"
                            + " account for it and was restored. Something is writing health"
                            + " directly; the responsible path needs investigating.");
        }

        rescueFromTheVoid(parrot);
    }

    /**
     * Operators keep the ability to remove a parrot deliberately. {@code /kill} reaches
     * {@code LivingEntity#kill}, which is {@code hurt(genericKill, Float.MAX_VALUE)} — so without
     * this exception a parrot would be not merely unkillable but unremovable, and a thousand of
     * them spawned by accident or malice would be permanent. Matched on the damage type alone and
     * deliberately narrow: {@code fellOutOfWorld} and {@code outOfBorder} share
     * {@code BYPASSES_INVULNERABILITY} with {@code genericKill} but are ordinary world hazards and
     * stay blocked.
     *
     * <p>Delete this method's body (return {@code false}) to make the rule absolute with no
     * exception at all.
     */
    private static boolean isOperatorRemoval(@Nullable DamageSource source) {
        return source != null && source.is(DamageTypes.GENERIC_KILL);
    }

    private static void reviveAtFullHealth(Parrot parrot) {
        parrot.setHealth(parrot.getMaxHealth());
        // tickDeath may already have begun counting; leaving it set would play a death animation on
        // a live parrot and start the countdown again the next time anything touches its health.
        parrot.deathTime = 0;
    }

    /**
     * A parrot below the world is no longer killed by the fall — {@code fellOutOfWorld} is damage
     * and damage is blocked — so without this it would fall for ever and be lost in an unloaded
     * chunk. Puts it back on the surface directly above where it went under.
     */
    private static void rescueFromTheVoid(Parrot parrot) {
        Level level = parrot.level();
        double floor = level.getMinBuildHeight() - VOID_RESCUE_DEPTH;
        if (parrot.getY() >= floor) {
            return;
        }
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, parrot.blockPosition());
        double y = Math.max(surface.getY(), level.getMinBuildHeight() + 1);
        parrot.teleportTo(parrot.getX(), y, parrot.getZ());
        parrot.setDeltaMovement(Vec3.ZERO);
        parrot.resetFallDistance();
        LOGGER.debug("Parrot protection: lifted a parrot out of the void back to y={}", y);
    }

    private static String describe(@Nullable Player player) {
        return player == null ? "none" : player.getGameProfile().getName();
    }
}

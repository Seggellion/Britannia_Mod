package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.util.PlayerDamageAttribution;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.slf4j.Logger;

/**
 * UltimaCraft parrots are protected from all player-caused damage.
 *
 * <p>The rule is absolute and independent of taming: a wild parrot, a player's own parrot and
 * somebody else's parrot are all equally untouchable, by hand, by weapon and by anything a player
 * shoots or throws. Environmental hazards and wild mobs are deliberately left alone — the invariant
 * is about players, not immortality.
 *
 * <p>The protection lives here, at the damage layer, rather than in weapons or projectiles, so that
 * any future weapon respects it without being told to. Three listeners, in the order the game
 * reaches them:
 *
 * <ol>
 *   <li>{@link AttackEntityEvent} — the earliest point on the melee path. Cancelling makes
 *       {@code Player#attack} return before it computes damage at all, so no attack cooldown is
 *       spent, no weapon durability is lost and {@code LivingEntity#hurt} is never entered. This is
 *       not the guarantee, but it is not merely decoration either: NeoForge 21.1.72's
 *       {@code LivingEntity#hurt} pushes a {@code DamageContainer} onto a per-entity stack
 *       <em>before</em> posting the damage event and does not pop it on the cancelled path, so
 *       every cancellation there costs a small permanent allocation on that entity. Short-circuiting
 *       melee — by far the most spammable attack — keeps a griefer hammering a parrot from growing
 *       that stack a hit at a time.
 *   <li>{@link LivingIncomingDamageEvent} — <b>the guarantee.</b> It fires inside
 *       {@code LivingEntity#hurt} after the invulnerability checks and before any damage
 *       processing; cancelling returns {@code false} from {@code hurt} immediately, so health is
 *       never touched, no hurt animation or knockback plays, and {@code die()} is unreachable. This
 *       catches every path — melee, arrows, tridents, fireworks, thrown potions, player-lit
 *       explosions, and anything a mod routes through {@code hurt}.
 *   <li>{@link LivingDeathEvent} — a safety net only. It should be unreachable; if it ever fires
 *       something bypassed the gate above and that is worth knowing about, so it logs.
 * </ol>
 *
 * <p>All three run at {@link EventPriority#HIGHEST} so the cancellation lands before any other
 * listener — Britannia's or another mod's — gets to act on a hit that is not going to happen.
 *
 * <p>Server-authoritative by construction: {@code LivingEntity#hurt} returns early on the logical
 * client before the damage event is posted, so the guarantee can only ever be decided on the
 * server. The melee listener is deliberately not side-guarded — {@code AttackEntityEvent} also
 * fires client-side, and refusing the swing there too keeps the client's local prediction in step
 * with the server instead of briefly disagreeing with it.
 *
 * <p>Operators keep {@code /kill}: it calls {@code Entity#kill()}, which removes the entity through
 * {@code RemovalReason.KILLED} without going near {@code hurt} or {@code die}, so nothing here can
 * or should stand in its way.
 */
public final class ParrotProtectionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Melee: refuse the swing outright rather than letting it reach the damage pipeline. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof Parrot) {
            event.setCanceled(true);
        }
    }

    /**
     * The load-bearing gate. Anything a player is responsible for — directly or through a
     * projectile, pet or explosion they own — is cancelled before health changes.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Parrot parrot)) {
            return;
        }
        if (!PlayerDamageAttribution.isPlayerAttributable(event.getSource())) {
            return;
        }
        event.setCanceled(true);

        // Parrot#hurt clears the sit order before it delegates to LivingEntity#hurt, so by the time
        // this event fires a sitting parrot has already been told to stand — for a hit that is not
        // going to happen. The sitting *pose* is a separate synced flag that SitWhenOrderedToGoal
        // only drops on its next tick, so it still reads true here and tells us the parrot was
        // sitting a moment ago. Putting the order back inside the same tick means an arrow cannot
        // be used to shoo somebody's parrot off its perch.
        if (parrot.isInSittingPose() && !parrot.isOrderedToSit()) {
            parrot.setOrderedToSit(true);
        }
    }

    /**
     * Safety net. Reaching here means a player-attributable hit got past
     * {@link #onIncomingDamage(LivingIncomingDamageEvent)} — for instance a mod calling
     * {@code setHealth(0)} directly. The death is refused and the parrot is put back to full
     * health, because a parrot left alive on zero health is worse than either outcome. Resurrection
     * is emphatically not the mechanism; the log line exists so a bypass is diagnosed rather than
     * silently papered over.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Parrot parrot)) {
            return;
        }
        Player responsible = PlayerDamageAttribution.responsiblePlayer(event.getSource());
        if (responsible == null) {
            return;
        }
        event.setCanceled(true);
        parrot.setHealth(parrot.getMaxHealth());
        LOGGER.warn(
                "Parrot protection: a player-caused death reached LivingDeathEvent and was refused."
                        + " player={} damageType={}. The incoming-damage gate should have stopped"
                        + " this; the bypassing damage path needs investigating.",
                responsible.getGameProfile().getName(),
                event.getSource().getMsgId());
    }
}

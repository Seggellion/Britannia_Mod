package com.seggellion.britannia_mod.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a {@link DamageSource} back to the player who is responsible for it, if any.
 *
 * <p>This is the single place Britannia decides "was a player behind this hit?". It exists because
 * {@code damageSource.getEntity() instanceof Player} alone is not that question. In 1.21.1 a
 * {@code DamageSource} carries two independent references (see
 * {@code net.minecraft.world.damagesource.DamageSource}):
 *
 * <ul>
 *   <li>{@link DamageSource#getDirectEntity()} — {@code directEntity}, the thing that physically
 *       landed the hit. For {@code DamageSources.arrow(arrow, shooter)} that is the <em>arrow</em>.
 *   <li>{@link DamageSource#getEntity()} — {@code causingEntity}, the thing held responsible. For
 *       the same arrow that is the <em>shooter</em>.
 * </ul>
 *
 * <p>For melee both are the player ({@code DamageSources.playerAttack(player)} passes one entity to
 * the two-argument constructor). For every player-owned projectile vanilla already threads the
 * owner through as {@code causingEntity}: {@code arrow}, {@code trident}, {@code fireworks},
 * {@code windCharge}, {@code thrown}, {@code indirectMagic} and {@code explosion} all take the
 * owner as their second argument. So {@code getEntity()} carries the common cases — but this class
 * checks the direct entity too, and then walks ownership from either, so that a source built
 * without a causing entity (a mod, or a projectile whose owner was resolved late) still resolves.
 *
 * <p>Ownership is followed through two relationships:
 *
 * <ul>
 *   <li>{@link Projectile#getOwner()} — the shooter/thrower of an arrow, trident, potion, rocket.
 *   <li>{@link OwnableEntity#getOwner()} — the tamer of a tamed animal. NeoForge's own
 *       {@code LivingEntity#hurt} patch assigns kill credit the same way ("{@code else if (entity
 *       instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame())} … {@code
 *       this.lastHurtByPlayer = player}"), so a player's pet is already treated by the game as the
 *       player's doing. Wild mobs have no owner and so are never player-attributable.
 * </ul>
 *
 * <p>The walk is bounded rather than recursive-until-null so a cyclic or self-referential owner
 * chain (a trident with no owner names <em>itself</em> as its causing entity) cannot spin.
 */
public final class PlayerDamageAttribution {

    /**
     * Chains longer than this are not real gameplay. Two hops covers every vanilla case
     * (projectile → tamed wolf → owner); the extra headroom is for modded sources.
     */
    private static final int MAX_OWNERSHIP_HOPS = 4;

    private PlayerDamageAttribution() {
    }

    /**
     * {@return the player responsible for {@code source}, or {@code null} if no player is}
     *
     * <p>Both the causing entity and the direct entity are consulted, in that order, and ownership
     * is followed from each.
     */
    @Nullable
    public static Player responsiblePlayer(@Nullable DamageSource source) {
        if (source == null) {
            return null;
        }
        Player causing = responsibleFor(source.getEntity());
        return causing != null ? causing : responsibleFor(source.getDirectEntity());
    }

    /** {@return whether {@code source} can be attributed to a player} */
    public static boolean isPlayerAttributable(@Nullable DamageSource source) {
        return responsiblePlayer(source) != null;
    }

    /**
     * {@return the player behind {@code entity} — itself, or whoever owns it — or {@code null}}
     */
    @Nullable
    public static Player responsibleFor(@Nullable Entity entity) {
        Entity current = entity;
        for (int hop = 0; current != null && hop <= MAX_OWNERSHIP_HOPS; hop++) {
            if (current instanceof Player player) {
                return player;
            }
            Entity owner = ownerOf(current);
            if (owner == current) {
                // A trident with no thrower names itself; treat that as "nobody".
                return null;
            }
            current = owner;
        }
        return null;
    }

    @Nullable
    private static Entity ownerOf(Entity entity) {
        if (entity instanceof Projectile projectile) {
            return projectile.getOwner();
        }
        if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwner();
        }
        return null;
    }
}

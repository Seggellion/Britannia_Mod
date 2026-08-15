package com.seggellion.britannia_mod.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The headless half of the {@link PlayerDamageAttribution} contract: the shape of a
 * {@code DamageSource} and the cases that resolve to nobody.
 *
 * <p>Anything that needs a live {@code Player}, {@code Projectile} or tamed animal needs a level to
 * exist in, so the positive cases — arrow, trident, firework, pet, two players — are proven in
 * {@code ParrotProtectionGameTests} against a real server instead of being faked here. What this
 * test does carry is the half that has no business needing a world: a source with no entities, and
 * a null source, must never be attributed to a player, because a false positive there would make
 * cactuses and lava stop working on every parrot in Britannia.
 */
class PlayerDamageAttributionTest {

    /**
     * Assigned after the bootstrap, not in a field initialiser: touching {@link DamageType} loads
     * {@link DamageEffects}, which loads {@code SoundEvents}, which throws "Not bootstrapped" if
     * the class initialiser runs before {@link Bootstrap#bootStrap()}.
     */
    private static Holder<DamageType> environmental;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        environmental = Holder.direct(new DamageType("britannia_test", DamageScaling.NEVER, 0.0F,
                DamageEffects.HURT, DeathMessageType.DEFAULT));
    }

    @Test
    void aSourceWithNoEntitiesBelongsToNobody() {
        DamageSource hazard = new DamageSource(environmental);

        assertNull(PlayerDamageAttribution.responsiblePlayer(hazard));
        assertFalse(PlayerDamageAttribution.isPlayerAttributable(hazard),
                "an entity-less hazard was attributed to a player; environmental damage would stop"
                        + " working on protected entities");
        assertNull(hazard.getEntity());
        assertNull(hazard.getDirectEntity());
    }

    @Test
    void aNullSourceIsNotAnAttribution() {
        assertNull(PlayerDamageAttribution.responsiblePlayer(null));
        assertFalse(PlayerDamageAttribution.isPlayerAttributable(null));
        assertNull(PlayerDamageAttribution.responsibleFor(null));
    }

    @Test
    void theSingleEntityConstructorPointsBothReferencesAtTheSameThing() {
        // Melee's shape: DamageSources#playerAttack uses the one-argument constructor, which fans
        // the attacker out to both the direct and the causing reference. Checking it with null is
        // the most this can say without a level, but it does pin the delegation down.
        DamageSource melee = new DamageSource(environmental, (Entity) null);

        assertSame(melee.getEntity(), melee.getDirectEntity(),
                "the single-entity constructor must set both references to the same thing");
        assertFalse(PlayerDamageAttribution.isPlayerAttributable(melee));
    }
}

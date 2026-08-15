package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The in-world contract for {@code ParrotProtectionHandler}: a player must never be able to damage
 * or kill a parrot, by any route, whoever owns it.
 *
 * <p>These run on a dedicated game-test server with the real event bus, real players, real
 * projectiles and the real vanilla {@code DamageSource} factories — which is the only place the
 * question "does {@code getEntity()} actually carry the shooter?" can be answered honestly.
 *
 * <p>Two shapes of attack are used deliberately. {@code player.attack(parrot)} exercises the whole
 * melee path end to end; {@code parrot.hurt(source, amount)} pins down one specific damage source
 * at a time with a damage figure large enough that an unprotected parrot (6 max health) would die
 * outright, so a passing test cannot be a rounding artefact.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ParrotProtectionGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Well past a parrot's 6.0 max health, so any leak through the gate is fatal and visible. */
    private static final float LETHAL = 1000.0F;

    private ParrotProtectionGameTests() {
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Parrot parrot(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.PARROT, new BlockPos(1, 1, 1));
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void checkUnharmed(Parrot parrot, String what) {
        check(parrot.isAlive(), what + ": the parrot died");
        check(parrot.getHealth() == parrot.getMaxHealth(),
                what + ": the parrot lost health (" + parrot.getHealth() + "/"
                        + parrot.getMaxHealth() + ")");
    }

    /** Clears the post-hit invulnerability window so a loop really lands every blow. */
    private static void allowAnotherHit(Parrot parrot) {
        parrot.invulnerableTime = 0;
    }

    // ------------------------------------------------------------------
    // 1-2. Melee, bare-handed and armed
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void bareHandedPlayerMeleeDoesNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        player.attack(parrot);

        checkUnharmed(parrot, "bare-handed melee");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyWeaponAndToolMeleeDoesNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);

        for (Item weapon : new Item[] {
                Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_PICKAXE,
                Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.TRIDENT, Items.MACE}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
            allowAnotherHit(parrot);
            player.attack(parrot);
            checkUnharmed(parrot, "melee with " + weapon);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void thePlayerAttackDamageSourceItselfIsRefused(GameTestHelper helper) {
        // Straight at the damage layer, bypassing AttackEntityEvent entirely: this is the listener
        // that carries the guarantee, so it has to hold on its own.
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);

        boolean hurt = parrot.hurt(helper.getLevel().damageSources().playerAttack(player), LETHAL);

        check(!hurt, "LivingEntity#hurt reported that a player attack landed on a parrot");
        checkUnharmed(parrot, "playerAttack damage source");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3-4. Ranged: the paths where the player is the causing, not the direct, entity
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void playerFiredArrowDoesNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);

        boolean hurt = parrot.hurt(level.damageSources().arrow(arrow, player), LETHAL);

        check(!hurt, "a player-fired arrow landed on a parrot");
        checkUnharmed(parrot, "player-fired arrow");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerFiredArrowIsRefusedEvenWithNoCausingEntity(GameTestHelper helper) {
        // The bypass the brief warns about: a source that names only the projectile. The arrow
        // still knows its owner, so the ownership walk has to find the player from the direct
        // entity alone.
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        DamageSource directOnly = level.damageSources().source(DamageTypes.ARROW, arrow, null);

        check(directOnly.getEntity() == null, "the fixture was supposed to have no causing entity");
        boolean hurt = parrot.hurt(directOnly, LETHAL);

        check(!hurt, "an arrow with no causing entity got through");
        checkUnharmed(parrot, "arrow with no causing entity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerThrownTridentDoesNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        ThrownTrident trident = new ThrownTrident(level, player, new ItemStack(Items.TRIDENT));

        boolean hurt = parrot.hurt(level.damageSources().trident(trident, player), LETHAL);

        check(!hurt, "a player-thrown trident landed on a parrot");
        checkUnharmed(parrot, "player-thrown trident");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerLaunchedFireworkAndCrossbowAndPotionDoNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        FireworkRocketEntity rocket =
                new FireworkRocketEntity(level, new ItemStack(Items.FIREWORK_ROCKET), player);
        Arrow bolt = new Arrow(level, player, new ItemStack(Items.ARROW),
                new ItemStack(Items.CROSSBOW));

        record Case(String name, DamageSource source) {}
        for (Case attack : new Case[] {
                new Case("firework", level.damageSources().fireworks(rocket, player)),
                new Case("crossbow bolt", level.damageSources().arrow(bolt, player)),
                new Case("wind charge", level.damageSources().windCharge(rocket, player)),
                new Case("thrown object", level.damageSources().thrown(rocket, player)),
                new Case("splash potion", level.damageSources().indirectMagic(rocket, player)),
                new Case("player-lit explosion", level.damageSources().explosion(rocket, player)),
                new Case("magic", level.damageSources().source(DamageTypes.MAGIC, rocket, player))}) {
            allowAnotherHit(parrot);
            boolean hurt = parrot.hurt(attack.source(), LETHAL);
            check(!hurt, "a player-owned " + attack.name() + " landed on a parrot");
            checkUnharmed(parrot, "player-owned " + attack.name());
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. Ownership is irrelevant
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void neitherTheOwnerNorAStrangerCanHarmATamedParrot(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer owner = player(helper);
        ServerPlayer stranger = player(helper);
        parrot.tame(owner);
        ServerLevel level = helper.getLevel();

        check(parrot.isTame(), "the fixture parrot did not tame");
        check(!owner.getUUID().equals(stranger.getUUID()), "the two mock players are the same player");

        owner.attack(parrot);
        checkUnharmed(parrot, "the owner's own melee");
        allowAnotherHit(parrot);

        stranger.attack(parrot);
        checkUnharmed(parrot, "a stranger's melee on somebody else's parrot");
        allowAnotherHit(parrot);

        Arrow ownersArrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
        parrot.hurt(level.damageSources().arrow(ownersArrow, owner), LETHAL);
        checkUnharmed(parrot, "the owner's own arrow");
        allowAnotherHit(parrot);

        Arrow strangersArrow = new Arrow(level, stranger, new ItemStack(Items.ARROW), null);
        parrot.hurt(level.damageSources().arrow(strangersArrow, stranger), LETHAL);
        checkUnharmed(parrot, "a stranger's arrow");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWildParrotIsProtectedExactlyLikeATamedOne(GameTestHelper helper) {
        Parrot wild = parrot(helper);
        ServerPlayer player = player(helper);

        check(!wild.isTame(), "the fixture parrot was unexpectedly tame");
        wild.hurt(helper.getLevel().damageSources().playerAttack(player), LETHAL);

        checkUnharmed(wild, "a wild parrot");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. Attrition
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void twoHundredAttacksStillLeaveTheParrotAtFullHealth(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer first = player(helper);
        ServerPlayer second = player(helper);
        ServerLevel level = helper.getLevel();
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));

        for (int i = 0; i < 200; i++) {
            allowAnotherHit(parrot);
            first.attack(parrot);
            allowAnotherHit(parrot);
            parrot.hurt(level.damageSources().playerAttack(second), LETHAL);
            allowAnotherHit(parrot);
            Arrow arrow = new Arrow(level, second, new ItemStack(Items.ARROW), null);
            parrot.hurt(level.damageSources().arrow(arrow, second), LETHAL);
        }

        checkUnharmed(parrot, "600 player attacks");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 7. The rule is behavioural, so a reloaded parrot is born protected
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aParrotRestoredFromSavedDataIsStillProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        Parrot original = parrot(helper);
        original.tame(owner);
        original.hurt(level.damageSources().playerAttack(owner), LETHAL);
        checkUnharmed(original, "the parrot before the save/reload round trip");

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();

        Parrot reloaded = EntityType.PARROT.create(level);
        check(reloaded != null, "the parrot entity type could not create a reload target");
        reloaded.load(saved);
        level.addFreshEntity(reloaded);

        check(reloaded.isTame(), "taming did not survive the NBT round trip");
        check(owner.getUUID().equals(reloaded.getOwnerUUID()),
                "ownership did not survive the NBT round trip");

        reloaded.hurt(level.damageSources().playerAttack(owner), LETHAL);
        checkUnharmed(reloaded, "a parrot restored from saved data");
        allowAnotherHit(reloaded);
        owner.attack(reloaded);
        checkUnharmed(reloaded, "melee against a parrot restored from saved data");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 8-9. The blast radius: everything that is not a player-hit parrot is untouched
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void otherAnimalsAreStillKillableByPlayers(GameTestHelper helper) {
        Chicken chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(2, 1, 1));
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();

        boolean melee = chicken.hurt(level.damageSources().playerAttack(player), 1.0F);
        check(melee, "a player melee attack on a chicken was refused");
        check(chicken.getHealth() < chicken.getMaxHealth(), "the chicken took no damage");

        Chicken second = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(4, 1, 1));
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        check(second.hurt(level.damageSources().arrow(arrow, player), LETHAL),
                "a player-fired arrow at a chicken was refused");
        check(!second.isAlive(), "a lethal player arrow did not kill a chicken");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void environmentalDamageStillHurtsParrots(GameTestHelper helper) {
        // The brief's explicit boundary: this is player protection, not immortality.
        Parrot parrot = parrot(helper);
        ServerLevel level = helper.getLevel();

        record Case(String name, DamageSource source) {}
        for (Case hazard : new Case[] {
                new Case("cactus", level.damageSources().cactus()),
                new Case("lava", level.damageSources().lava()),
                new Case("sweet berry bush", level.damageSources().sweetBerryBush()),
                new Case("freezing", level.damageSources().freeze()),
                new Case("drowning", level.damageSources().drown()),
                new Case("starvation", level.damageSources().starve())}) {
            Parrot subject = parrot(helper);
            check(subject.hurt(hazard.source(), 1.0F),
                    hazard.name() + " damage was refused on a parrot");
            check(subject.getHealth() < subject.getMaxHealth(),
                    hazard.name() + " damage did not reduce a parrot's health");
        }

        // Fall damage is deliberately absent from that list: parrots are in vanilla's
        // FALL_DAMAGE_IMMUNE entity-type tag and Entity#isInvulnerableTo short-circuits before any
        // event is posted. Asserting it here keeps the exclusion honest — if that ever stops being
        // vanilla's doing, this fails rather than quietly looking like Britannia's doing.
        Parrot dropped = parrot(helper);
        check(!dropped.hurt(level.damageSources().fall(), 1.0F),
                "a parrot took fall damage; vanilla's FALL_DAMAGE_IMMUNE tag no longer covers it and"
                        + " the environmental exclusion above needs revisiting");

        check(parrot.hurt(level.damageSources().genericKill(), LETHAL),
                "an operator kill was refused on a parrot");
        check(!parrot.isAlive(), "an operator kill did not kill a parrot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wildMobsAndOwnerlessProjectilesStillHurtParrots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 1));

        Parrot bitten = parrot(helper);
        check(bitten.hurt(level.damageSources().mobAttack(zombie), 1.0F),
                "a wild mob's attack on a parrot was refused");
        check(bitten.getHealth() < bitten.getMaxHealth(), "a wild mob's attack did no damage");

        // A dispenser-fired arrow has no owner and so is nobody's doing.
        Parrot shot = parrot(helper);
        Arrow ownerless = new Arrow(level, 0.0, 0.0, 0.0, new ItemStack(Items.ARROW), null);
        check(ownerless.getOwner() == null, "the fixture arrow unexpectedly had an owner");
        check(shot.hurt(level.damageSources().arrow(ownerless, null), 1.0F),
                "an ownerless arrow was refused on a parrot");
        check(shot.getHealth() < shot.getMaxHealth(), "an ownerless arrow did no damage");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPlayersTamedWolfCannotBeUsedToKillAParrot(GameTestHelper helper) {
        // Deliberate: a pet is its owner's doing. NeoForge's own LivingEntity#hurt patch assigns
        // kill credit to a tamed animal's owner, so "set your wolf on it" is a player-controlled
        // damage path and is closed like any other.
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        Wolf pet = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(2, 1, 1));
        pet.tame(owner);
        check(pet.isTame() && pet.getOwner() == owner, "the fixture wolf did not tame to the player");

        Parrot parrot = parrot(helper);
        check(!parrot.hurt(level.damageSources().mobAttack(pet), LETHAL),
                "a player's tamed wolf landed a hit on a parrot");
        checkUnharmed(parrot, "a player's tamed wolf");

        Wolf stray = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(3, 1, 1));
        Parrot bitten = parrot(helper);
        check(bitten.hurt(level.damageSources().mobAttack(stray), 1.0F),
                "an untamed wolf's attack on a parrot was refused");
        check(bitten.getHealth() < bitten.getMaxHealth(), "an untamed wolf's attack did no damage");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Normal parrot behaviour is untouched
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void tamingSittingAndOwnershipStillWork(GameTestHelper helper) {
        // The protection lives entirely in event listeners; the Parrot entity itself is stock. This
        // pins that down rather than assuming it.
        Parrot parrot = parrot(helper);
        ServerPlayer owner = player(helper);

        check(parrot.getType() == EntityType.PARROT, "the world contains a substituted parrot type");
        check(parrot.getClass() == Parrot.class,
                "the parrot was replaced by a custom entity class: " + parrot.getClass().getName());

        parrot.tame(owner);
        check(parrot.isTame(), "taming stopped working");
        check(parrot.getOwner() == owner, "owner lookup stopped working");

        parrot.setOrderedToSit(true);
        check(parrot.isOrderedToSit(), "sitting stopped working");
        parrot.setOrderedToSit(false);
        check(!parrot.isOrderedToSit(), "standing back up stopped working");

        check(parrot.getMaxHealth() == 6.0F,
                "the parrot's max health was altered: " + parrot.getMaxHealth());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBlockedHitLeavesASittingParrotSitting(GameTestHelper helper) {
        // Parrot#hurt clears the sit order before LivingEntity#hurt is ever entered, so without the
        // handler putting it back an arrow would be a free "stand up" button on somebody's pet.
        Parrot parrot = parrot(helper);
        ServerPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        parrot.tame(owner);
        parrot.setOrderedToSit(true);
        parrot.setInSittingPose(true);

        Arrow arrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
        parrot.hurt(level.damageSources().arrow(arrow, owner), LETHAL);

        checkUnharmed(parrot, "an arrow at a sitting parrot");
        check(parrot.isOrderedToSit(), "a blocked hit still knocked the parrot out of its sit order");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void operatorsCanStillRemoveAParrot(GameTestHelper helper) {
        // /kill goes through Entity#kill, which removes the entity outright and never touches the
        // damage pipeline. Administration must not be collateral damage of the gameplay rule.
        Parrot parrot = parrot(helper);
        player(helper);

        parrot.kill();

        check(!parrot.isAlive(), "an operator could not remove a parrot with /kill semantics");
        helper.succeed();
    }
}

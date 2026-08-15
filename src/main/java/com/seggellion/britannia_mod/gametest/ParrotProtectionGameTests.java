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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The in-world contract: nothing kills a parrot.
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

    /** Asserts one damage source is refused outright and takes nothing off the parrot. */
    private static void checkRefused(Parrot parrot, DamageSource source, String what) {
        allowAnotherHit(parrot);
        check(!parrot.hurt(source, LETHAL), what + ": LivingEntity#hurt reported that it landed");
        checkUnharmed(parrot, what);
    }

    private record Case(String name, DamageSource source) {}

    // ------------------------------------------------------------------
    // Melee, bare-handed and armed
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
        // that carries the guarantee, so it has to hold on its own. Sweep attacks take exactly this
        // route and never touch AttackEntityEvent.
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);

        checkRefused(parrot, helper.getLevel().damageSources().playerAttack(player),
                "playerAttack damage source");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Ranged: the paths where the player is the causing, not the direct, entity
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void everyPlayerOwnedProjectileDoesNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        Arrow bolt = new Arrow(level, player, new ItemStack(Items.ARROW),
                new ItemStack(Items.CROSSBOW));
        ThrownTrident trident = new ThrownTrident(level, player, new ItemStack(Items.TRIDENT));
        FireworkRocketEntity rocket =
                new FireworkRocketEntity(level, new ItemStack(Items.FIREWORK_ROCKET), player);

        for (Case attack : new Case[] {
                new Case("arrow", level.damageSources().arrow(arrow, player)),
                new Case("crossbow bolt", level.damageSources().arrow(bolt, player)),
                new Case("trident", level.damageSources().trident(trident, player)),
                new Case("firework", level.damageSources().fireworks(rocket, player)),
                new Case("wind charge", level.damageSources().windCharge(rocket, player)),
                new Case("thrown object", level.damageSources().thrown(rocket, player)),
                new Case("splash potion", level.damageSources().indirectMagic(rocket, player)),
                new Case("player-lit explosion", level.damageSources().explosion(rocket, player)),
                new Case("magic", level.damageSources().source(DamageTypes.MAGIC, rocket, player))}) {
            checkRefused(parrot, attack.source(), "a player-owned " + attack.name());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aProjectileWithNoCausingEntityIsStillRefused(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        DamageSource directOnly = level.damageSources().source(DamageTypes.ARROW, arrow, null);

        check(directOnly.getEntity() == null, "the fixture was supposed to have no causing entity");
        checkRefused(parrot, directOnly, "an arrow with no causing entity");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Ownership is irrelevant
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

        Arrow ownersArrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
        checkRefused(parrot, level.damageSources().arrow(ownersArrow, owner), "the owner's own arrow");

        Arrow strangersArrow = new Arrow(level, stranger, new ItemStack(Items.ARROW), null);
        checkRefused(parrot, level.damageSources().arrow(strangersArrow, stranger),
                "a stranger's arrow");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWildParrotIsProtectedExactlyLikeATamedOne(GameTestHelper helper) {
        Parrot wild = parrot(helper);
        ServerPlayer player = player(helper);

        check(!wild.isTame(), "the fixture parrot was unexpectedly tame");
        checkRefused(wild, helper.getLevel().damageSources().playerAttack(player), "a wild parrot");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Attrition
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void sixHundredAttacksStillLeaveTheParrotAtFullHealth(GameTestHelper helper) {
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

        checkUnharmed(parrot, "600 attacks");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The rule is behavioural, so a reloaded parrot is born protected
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aParrotRestoredFromSavedDataIsStillProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        Parrot original = parrot(helper);
        original.tame(owner);
        checkRefused(original, level.damageSources().playerAttack(owner),
                "the parrot before the save/reload round trip");

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();

        Parrot reloaded = EntityType.PARROT.create(level);
        check(reloaded != null, "the parrot entity type could not create a reload target");
        reloaded.load(saved);
        level.addFreshEntity(reloaded);

        check(reloaded.isTame(), "taming did not survive the NBT round trip");
        check(owner.getUUID().equals(reloaded.getOwnerUUID()),
                "ownership did not survive the NBT round trip");

        checkRefused(reloaded, level.damageSources().playerAttack(owner),
                "a parrot restored from saved data");
        allowAnotherHit(reloaded);
        owner.attack(reloaded);
        checkUnharmed(reloaded, "melee against a parrot restored from saved data");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The edge cases: everything that used to get through
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void fallingBlocksAndOwnerlessExplosivesDoNothing(GameTestHelper helper) {
        // The paths Minecraft exposes no player for: a dropped anvil, a creeper somebody lit, a TNT
        // minecart, a shot end crystal. Under player-only protection every one of these worked.
        Parrot parrot = parrot(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        FallingBlockEntity anvil =
                FallingBlockEntity.fall(level, pos, Blocks.ANVIL.defaultBlockState());
        Creeper creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(3, 1, 1));

        for (Case attack : new Case[] {
                new Case("falling anvil", level.damageSources().anvil(anvil)),
                new Case("falling block", level.damageSources().fallingBlock(anvil)),
                new Case("falling stalactite", level.damageSources().fallingStalactite(anvil)),
                new Case("creeper explosion", level.damageSources().explosion(creeper, creeper)),
                new Case("ownerless explosion", level.damageSources().explosion(null, null)),
                new Case("lightning", level.damageSources().lightningBolt()),
                new Case("sonic boom", level.damageSources().sonicBoom(creeper))}) {
            checkRefused(parrot, attack.source(), "a " + attack.name());
        }
        anvil.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyEnvironmentalHazardDoesNothing(GameTestHelper helper) {
        Parrot parrot = parrot(helper);
        ServerLevel level = helper.getLevel();

        for (Case hazard : new Case[] {
                new Case("cactus", level.damageSources().cactus()),
                new Case("lava", level.damageSources().lava()),
                new Case("fire", level.damageSources().inFire()),
                new Case("burning", level.damageSources().onFire()),
                new Case("hot floor", level.damageSources().hotFloor()),
                new Case("suffocation", level.damageSources().inWall()),
                new Case("cramming", level.damageSources().cramming()),
                new Case("drowning", level.damageSources().drown()),
                new Case("starvation", level.damageSources().starve()),
                new Case("sweet berry bush", level.damageSources().sweetBerryBush()),
                new Case("freezing", level.damageSources().freeze()),
                new Case("stalagmite", level.damageSources().stalagmite()),
                new Case("wither", level.damageSources().wither()),
                new Case("dragon breath", level.damageSources().dragonBreath()),
                new Case("magic", level.damageSources().magic()),
                new Case("drying out", level.damageSources().dryOut()),
                new Case("flying into a wall", level.damageSources().flyIntoWall()),
                new Case("the world border", level.damageSources().outOfBorder()),
                new Case("falling out of the world", level.damageSources().fellOutOfWorld()),
                new Case("generic", level.damageSources().generic())}) {
            checkRefused(parrot, hazard.source(), hazard.name());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noMobTamedOrWildCanHarmAParrot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        Parrot parrot = parrot(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        Wolf pet = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(3, 1, 1));
        Wolf stray = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(4, 1, 1));
        pet.tame(owner);
        check(pet.isTame(), "the fixture wolf did not tame to the player");

        for (Case attack : new Case[] {
                new Case("wild zombie's bite", level.damageSources().mobAttack(zombie)),
                new Case("untamed wolf's bite", level.damageSources().mobAttack(stray)),
                new Case("player's tamed wolf", level.damageSources().mobAttack(pet)),
                new Case("mob's projectile", level.damageSources().mobProjectile(zombie, zombie)),
                new Case("bee sting", level.damageSources().sting(zombie)),
                new Case("thorns", level.damageSources().thorns(zombie))}) {
            checkRefused(parrot, attack.source(), "a " + attack.name());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aParrotAtZeroHealthIsBackToFullBeforeItCanDie(GameTestHelper helper) {
        // tickDeath removes an entity at zero health without ever calling die(), so nothing writing
        // health directly would fire a single event. The tick guard is the floor under that.
        Parrot parrot = parrot(helper);
        parrot.setHealth(0.0F);

        helper.runAfterDelay(3, () -> {
            checkUnharmed(parrot, "a parrot whose health was zeroed outside the damage pipeline");
            check(parrot.deathTime == 0, "the parrot was left mid-death-animation");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aParrotBelowTheWorldIsLiftedBackOut(GameTestHelper helper) {
        // fellOutOfWorld is damage and damage is blocked, so without the rescue the parrot would
        // simply fall for ever — not killed, but gone, which is the same thing to the owner.
        ServerLevel level = helper.getLevel();
        Parrot parrot = parrot(helper);
        double belowTheWorld = level.getMinBuildHeight() - 100.0;
        parrot.teleportTo(parrot.getX(), belowTheWorld, parrot.getZ());

        helper.runAfterDelay(3, () -> {
            check(parrot.isAlive(), "a parrot dropped below the world did not survive");
            check(parrot.getY() > level.getMinBuildHeight(),
                    "a parrot dropped below the world was not lifted back out (y="
                            + parrot.getY() + ")");
            checkUnharmed(parrot, "a parrot rescued from the void");
            parrot.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // The blast radius: everything that is not a parrot is untouched
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void otherAnimalsAreStillKillable(GameTestHelper helper) {
        Chicken chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(2, 1, 1));
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();

        check(chicken.hurt(level.damageSources().playerAttack(player), 1.0F),
                "a player melee attack on a chicken was refused");
        check(chicken.getHealth() < chicken.getMaxHealth(), "the chicken took no damage");

        Chicken second = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(4, 1, 1));
        check(second.hurt(level.damageSources().cactus(), LETHAL),
                "environmental damage to a chicken was refused");
        check(!second.isAlive(), "lethal environmental damage did not kill a chicken");

        Chicken third = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(5, 1, 1));
        third.setHealth(0.0F);
        helper.runAfterDelay(3, () -> {
            check(third.getHealth() == 0.0F,
                    "the parrot health floor was applied to a chicken as well");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void operatorsCanStillRemoveAParrot(GameTestHelper helper) {
        // /kill reaches LivingEntity#kill, which is hurt(genericKill, MAX_VALUE) — the one
        // deliberate exception, so an unkillable parrot is not also an unremovable one.
        Parrot parrot = parrot(helper);

        parrot.kill();

        helper.runAfterDelay(3, () -> {
            check(!parrot.isAlive(), "an operator could not remove a parrot with /kill");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theOperatorExceptionIsNarrow(GameTestHelper helper) {
        // genericKill is exempt; the damage types that merely share its BYPASSES_INVULNERABILITY
        // tag are ordinary world hazards and must not be.
        Parrot parrot = parrot(helper);
        ServerLevel level = helper.getLevel();

        checkRefused(parrot, level.damageSources().fellOutOfWorld(), "falling out of the world");
        checkRefused(parrot, level.damageSources().outOfBorder(), "the world border");
        check(parrot.hurt(level.damageSources().genericKill(), LETHAL),
                "an operator kill was refused on a parrot");
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
        // handler putting it back a passing zombie would be a free "stand up" button on a pet.
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
}

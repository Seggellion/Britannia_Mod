package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import com.seggellion.britannia_mod.skill.crafting.MetalProgression;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The <em>material</em> half of the Blacksmithy requirement: how hard the metal is, as distinct
 * from how hard the shape is.
 *
 * <h2>What this protects</h2>
 * A Blacksmithy 0.0 smith could forge a Valorite dagger. Not because a check regressed — because
 * the question was never asked. {@code processCraftRequest} enforced the recipe's own requirement
 * and then accepted whatever ingot was in the offhand, and the recipe half was already covered by
 * {@link BlacksmithySkillGateGameTests}, which crafts exclusively in iron. Every metal above iron
 * was therefore free to anyone who could obtain an ingot.
 *
 * <p>The fixture is deliberately {@code dagger}: its own Blacksmithy requirement is <b>0</b>, so a
 * refusal here can only have come from the metal. Three ingots, no learned recipe, no race or
 * gender restriction — nothing else in the chain can be mistaken for the gate under test.
 *
 * <h2>Why calling the service directly is the server-authority test</h2>
 * {@code BlacksmithCrafting.processCraftRequest} is exactly what the {@code
 * CraftBlacksmithItemC2SPayload} handler invokes, and the payload carries only a recipe id — never
 * a metal and never a skill value. Driving the service directly therefore <em>is</em> the
 * "modified client attempts an unauthorized craft" case: the metal is read from the server's own
 * copy of the offhand and the skill from the server's own table, so no client can assert either.
 *
 * <p>Acceptance is asserted where it is deterministic. Success and Exceptional are random rolls,
 * but ingredient consumption happens only after every eligibility check has passed, so a consumed
 * offhand means accepted and an untouched offhand means refused.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BlacksmithyMetalGateGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "blacksmithy_metal_gate";

    /** Blacksmithy 0, three ingots, no other restriction: the metal is the only variable. */
    private static final String UNGATED_RECIPE = "dagger";
    private static final int OFFHAND_INGOTS = 64;

    private BlacksmithyMetalGateGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A smith at the anvil holding the named metal in the offhand. */
    private static ServerPlayer smith(GameTestHelper helper, String name, float blacksmithy, Item ingot) {
        ServerLevel level = helper.getLevel();
        // An anvil is a FALLING block; a solid footing keeps the fixture standing.
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.ANVIL);
        BlockPos beside = helper.absolutePos(new BlockPos(2, 1, 1));

        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.teleportTo(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.BLACKSMITH_HAMMER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ingot, OFFHAND_INGOTS));
        SkillManager.applyConfirmedValue(player, MetalProgression.SKILL_ID, blacksmithy);
        return player;
    }

    private static CraftableDef ungatedRecipe() {
        CraftableDef def = CraftableRegistry.get(UNGATED_RECIPE);
        check(def != null, "fixture recipe " + UNGATED_RECIPE + " must exist");
        check(def.skillRequirements().stream().allMatch(requirement -> requirement.minValue() == 0.0f),
                UNGATED_RECIPE + " must carry no recipe-level requirement, or it cannot isolate the metal gate");
        return def;
    }

    private static boolean consumed(ServerPlayer player) {
        return player.getOffhandItem().getCount() < OFFHAND_INGOTS;
    }

    /** One attempt, reported as accepted (materials spent) or refused (materials intact). */
    private static boolean attempt(GameTestHelper helper, String name, float blacksmithy, Item ingot) {
        ServerPlayer player = smith(helper, name, blacksmithy, ingot);
        BlacksmithCrafting.processCraftRequest(player, ungatedRecipe());
        return consumed(player);
    }

    // ---------------------------------------------------------------- Valorite, the headline case

    /**
     * The reported defect, in one test: a novice holding Valorite gets nothing, and — because the
     * refusal lands before consumption, like every other precondition — loses nothing either.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void noviceCannotWorkValoriteAndLosesNothingTrying(GameTestHelper helper) {
        check(!attempt(helper, "smith-val-0", 0.0f, ItemRegistry.VALORITE_INGOT.get()),
                "Blacksmithy 0.0 must not be able to work Valorite, even on a recipe requiring 0");
        helper.succeed();
    }

    /** Just below the requirement is still a refusal; the boundary is not approximate. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void valoriteIsRefusedJustBelowItsRequirement(GameTestHelper helper) {
        float required = MetalProgression.requiredBlacksmithy(
                com.seggellion.britannia_mod.item.UOMetalToolMaterial.VALORITE);
        check(!attempt(helper, "smith-val-below", required - 0.1f, ItemRegistry.VALORITE_INGOT.get()),
                "Valorite must be refused just below " + required);
        helper.succeed();
    }

    /** And exactly at the requirement it is allowed: the ladder is inclusive, as Mining's is. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void valoriteIsWorkableExactlyAtItsRequirement(GameTestHelper helper) {
        float required = MetalProgression.requiredBlacksmithy(
                com.seggellion.britannia_mod.item.UOMetalToolMaterial.VALORITE);
        check(attempt(helper, "smith-val-at", required, ItemRegistry.VALORITE_INGOT.get()),
                "Valorite must be workable at exactly " + required);
        helper.succeed();
    }

    // ------------------------------------------------------------------- the rest of the ladder

    /** The entry tier is untouched: a brand-new smith can still make an iron dagger. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void noviceCanStillWorkIron(GameTestHelper helper) {
        check(attempt(helper, "smith-iron-0", 0.0f, Items.IRON_INGOT),
                "Iron must remain workable at Blacksmithy 0.0 -- the beginner path must not close");
        helper.succeed();
    }

    /**
     * A mid-tier metal enforces its own rung, which is what makes this a ladder rather than a
     * Valorite special case. Copper sits at 75, level with the Mining requirement for copper ore.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void copperEnforcesItsOwnRungBelowAndAtTheRequirement(GameTestHelper helper) {
        float required = MetalProgression.requiredBlacksmithy(
                com.seggellion.britannia_mod.item.UOMetalToolMaterial.COPPER);
        check(!attempt(helper, "smith-cu-below", required - 0.1f, ItemRegistry.COPPER_INGOT.get()),
                "Copper must be refused just below " + required);
        check(attempt(helper, "smith-cu-at", required, ItemRegistry.COPPER_INGOT.get()),
                "Copper must be workable at " + required);
        helper.succeed();
    }

    /**
     * A smith good enough for one rung is not thereby good enough for the next. Catches a future
     * "any high skill works any metal" collapse, which a single-metal suite would not see.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void skillForOneRungDoesNotUnlockTheNext(GameTestHelper helper) {
        float copper = MetalProgression.requiredBlacksmithy(
                com.seggellion.britannia_mod.item.UOMetalToolMaterial.COPPER);
        check(attempt(helper, "smith-rung-cu", copper, ItemRegistry.COPPER_INGOT.get()),
                "a smith at the copper rung must be able to work copper");
        check(!attempt(helper, "smith-rung-val", copper, ItemRegistry.VALORITE_INGOT.get()),
                "the same smith must not be able to work Valorite");
        helper.succeed();
    }
}

package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Blacksmithy half of the skill-progression remediation: crafting eligibility genuinely
 * depends on the player's current Blacksmithy value, on the server, through the canonical
 * {@code blacksmithy} key.
 *
 * <p>{@code BlacksmithCrafting.processCraftRequest} is exactly what the C2S payload handler
 * invokes, so driving it directly IS the "client attempts an unauthorized craft" case: whatever a
 * stale or manipulated client believes its list contains, this method re-reads the authoritative
 * server-side skill and refuses.
 *
 * <p>The fixture is {@code chainmail_coif}: Blacksmithy 14.5, ten ingots, no learned-recipe, no
 * race or gender restriction. Success and Exceptional quality are random rolls, so acceptance is
 * asserted where it is deterministic — an accepted attempt consumes its ingots (consumption
 * happens only after every eligibility check has passed), a refused attempt consumes nothing.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BlacksmithySkillGateGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String GATED_RECIPE = "chainmail_coif";
    private static final float GATED_REQUIREMENT = 14.5f;
    private static final int GATED_INGOTS = 10;
    private static final int OFFHAND_INGOTS = 64;

    private BlacksmithySkillGateGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A smith at the anvil: hammer in hand, iron in the offhand, standing where the anvil is. */
    private static ServerPlayer smith(GameTestHelper helper, String name, float blacksmithy) {
        ServerLevel level = helper.getLevel();
        // The empty template is pure air and an anvil is a FALLING block: freshly placed, it
        // schedules its fall exactly two ticks later, which is precisely when the delayed tests
        // look for it. A solid footing keeps the fixture standing for every test shape.
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.ANVIL);
        BlockPos beside = helper.absolutePos(new BlockPos(2, 1, 1));

        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.teleportTo(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.BLACKSMITH_HAMMER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, OFFHAND_INGOTS));
        SkillManager.applyConfirmedValue(player, "blacksmithy", blacksmithy);
        return player;
    }

    private static CraftableDef gatedRecipe() {
        CraftableDef def = CraftableRegistry.get(GATED_RECIPE);
        check(def != null, "fixture recipe " + GATED_RECIPE + " must exist");
        check(def.skillRequirements().stream().anyMatch(requirement ->
                        requirement.skillKey().equals("blacksmithy")
                                && requirement.minValue() == GATED_REQUIREMENT),
                GATED_RECIPE + " must be gated on blacksmithy " + GATED_REQUIREMENT);
        return def;
    }

    private static int offhandIngots(ServerPlayer player) {
        return player.getOffhandItem().getCount();
    }

    /** Everything the craft preconditions read, for a failure message that names the culprit. */
    private static String diagnose(GameTestHelper helper, ServerPlayer player) {
        boolean anvilNearby = false;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (helper.getLevel().getBlockState(pos).is(Blocks.ANVIL)) {
                anvilNearby = true;
                break;
            }
        }
        return "skill=" + SkillManager.getSkill(player, "blacksmithy")
                + " mainHand=" + player.getMainHandItem().getItem()
                + " offhand=" + player.getOffhandItem().getItem() + "x" + player.getOffhandItem().getCount()
                + " anvilNearby=" + anvilNearby
                + " gameMode=" + player.gameMode.getGameModeForPlayer()
                + " pos=" + player.blockPosition().toShortString();
    }

    @GameTest(template = TEMPLATE, batch = "blacksmithy_gate", timeoutTicks = 60)
    public static void skillZeroIsRefusedAndConsumesNothing(GameTestHelper helper) {
        ServerPlayer player = smith(helper, "smith-zero", 0.0f);
        BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
        check(offhandIngots(player) == OFFHAND_INGOTS,
                "a refused craft must not consume materials");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "blacksmithy_gate", timeoutTicks = 60)
    public static void skillJustBelowRequirementIsRefused(GameTestHelper helper) {
        ServerPlayer player = smith(helper, "smith-fourteen-four", GATED_REQUIREMENT - 0.1f);
        BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
        check(offhandIngots(player) == OFFHAND_INGOTS,
                "14.4 against a 14.5 recipe must be refused before any consumption");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "blacksmithy_gate", timeoutTicks = 60)
    public static void skillExactlyAtRequirementIsAccepted(GameTestHelper helper) {
        ServerPlayer player = smith(helper, "smith-exact", GATED_REQUIREMENT);
        BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
        check(offhandIngots(player) == OFFHAND_INGOTS - GATED_INGOTS,
                "14.5 against a 14.5 recipe must be accepted (inclusive threshold) and consume its ingots");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "blacksmithy_gate", timeoutTicks = 60)
    public static void skillAboveRequirementIsAccepted(GameTestHelper helper) {
        ServerPlayer player = smith(helper, "smith-master", 100.0f);
        BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
        check(offhandIngots(player) == OFFHAND_INGOTS - GATED_INGOTS,
                "an over-qualified smith's attempt must be accepted");
        helper.succeed();
    }

    /**
     * Eligibility follows the CURRENT value: the same player, refused at 0.0, is accepted the
     * moment the authoritative value crosses the requirement — same session, no reconnect. This
     * is the server half of "changing the skill changes what can be crafted"; the client list
     * recomputes from the synced {@code ClientSkillTable} revision, which bumps on this same
     * {@code applyConfirmedValue} path.
     */
    @GameTest(template = TEMPLATE, batch = "blacksmithy_gate", timeoutTicks = 60)
    public static void raisingTheSkillTakesEffectImmediately(GameTestHelper helper) {
        ServerPlayer player = smith(helper, "smith-rising", 0.0f);
        BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
        check(offhandIngots(player) == OFFHAND_INGOTS, "precondition: 0.0 is refused");

        SkillManager.applyConfirmedValue(player, "blacksmithy", GATED_REQUIREMENT);
        helper.runAfterDelay(2, () -> {
            BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
            check(offhandIngots(player) == OFFHAND_INGOTS - GATED_INGOTS,
                    "crossing the requirement must take effect on the very next attempt; "
                            + diagnose(helper, player));
            helper.succeed();
        });
    }

    /**
     * The legacy alias is one skill, not two: a value written under {@code blacksmith} is read
     * back under the canonical {@code blacksmithy}, and it satisfies a recipe requirement — the
     * spelling can no longer split progression into two independent skills.
     */
    @GameTest(template = TEMPLATE, batch = "blacksmithy_gate", timeoutTicks = 60)
    public static void legacyBlacksmithAliasResolvesToBlacksmithy(GameTestHelper helper) {
        ServerPlayer player = smith(helper, "smith-legacy", 0.0f);
        SkillManager.applyConfirmedValue(player, "blacksmith", GATED_REQUIREMENT);

        check(SkillManager.getSkill(player, "blacksmithy") == GATED_REQUIREMENT,
                "a value written under the legacy alias must be the canonical skill's value");
        check(SkillManager.getSkill(player, "Blacksmith") == GATED_REQUIREMENT,
                "capitalization must not create a second skill either");

        helper.runAfterDelay(2, () -> {
            BlacksmithCrafting.processCraftRequest(player, gatedRecipe());
            check(offhandIngots(player) == OFFHAND_INGOTS - GATED_INGOTS,
                    "skill earned under the alias must satisfy the canonical requirement");
            helper.succeed();
        });
    }
}

package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MineableCatalog;
import com.seggellion.britannia_mod.mining.MineableDefinition;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The managed destroy-time ladder: every mod-owned mining block carries an intentional dig
 * duration, ordered by the Mining rank the catalogue states, and they have not collapsed back to
 * one shared default.
 *
 * <p>This is the guard the previous phase was missing. The architecture supported differentiated
 * hardness, but all fourteen {@code BaseOreBlock} registrations still passed the same 3.0, so a
 * Mining-10 coal seam dug exactly as long as a Mining-99 valorite vein. The ordering is re-derived
 * here from {@code required_mining} rather than restated, so the ladder in {@code BlockRegistry}
 * and the rank in {@code mineables.json} cannot drift apart silently.
 *
 * <p>Only mod-owned blocks are ranked. Vanilla members of the catalogue — stone, deepslate, the
 * vanilla iron and gold ores — keep the hardness Minecraft gives them; the project does not
 * restat vanilla, and a vanilla block appearing harder than a low mod tier is Mojang's balance,
 * not a break in ours.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningHardnessLadderGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    /** Below this a dig is over in under a fifth of a second, which reads as an instant break. */
    private static final float INSTANT_BREAK_FLOOR = 0.5f;

    private MiningHardnessLadderGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Every ACTIVE mineable this mod owns the block for, in catalogue rank order.
     *
     * <p>Two exclusions, both deliberate. Vanilla-owned blocks are filtered by namespace: the
     * ladder is a statement about our own registrations, and the project does not restat
     * Minecraft's. SEDIMENT is filtered because it is not on this ladder at all — the clay and
     * silica beds are soft shovel work (0.6 and 0.5), deliberately quick so that gathering a
     * house's worth of clay is not a chore, and they are gated by tool family rather than by
     * rank. Ranking a bed against a valorite vein would assert a relationship the design does not
     * claim: silica sits at Mining 5 beside the sandstone quarry face at the same requirement, yet
     * one is meant to be the quickest dig in the game and the other a rock.
     */
    private static Map<MineableDefinition, Block> modOwnedByRank() {
        List<MineableDefinition> ranked = new ArrayList<>(MineableCatalog.instance().active().stream()
                .filter(definition -> definition.category() != MineableDefinition.Category.SEDIMENT)
                .toList());
        ranked.sort(Comparator.comparing(MineableDefinition::requiredMining)
                .thenComparing(MineableDefinition::id));
        Map<MineableDefinition, Block> owned = new LinkedHashMap<>();
        for (MineableDefinition definition : ranked) {
            for (String blockId : definition.blockIds()) {
                ResourceLocation id = ResourceLocation.parse(blockId);
                if (!id.getNamespace().equals(BritanniaMod.MODID)) {
                    continue;
                }
                owned.put(definition, BuiltInRegistries.BLOCK.get(id));
                break;
            }
        }
        return owned;
    }

    /* ------------------------------------------------------------------ */
    /*  The ladder exists and is intentional                               */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, batch = "mining_hardness", timeoutTicks = 60)
    public static void everyModOwnedResourceHasAnIntentionalDestroyTime(GameTestHelper helper) {
        Map<MineableDefinition, Block> owned = modOwnedByRank();
        check(owned.size() >= 13,
                "expected the mod-owned mining family, found " + owned.size());
        for (Map.Entry<MineableDefinition, Block> entry : owned.entrySet()) {
            float time = entry.getValue().defaultDestroyTime();
            check(time > 0.0f, entry.getKey().id() + " must expose a non-zero destroy time");
            check(time <= 6.0f, entry.getKey().id() + " is an endurance mechanic at " + time);
        }
        helper.succeed();
    }

    /**
     * The anti-collapse guard. Fourteen registrations sharing one figure is exactly the defect
     * this ladder replaced, so a healthy spread of distinct values is asserted directly — and no
     * single value may dominate the family the way 3.0 once did.
     */
    @GameTest(template = TEMPLATE, batch = "mining_hardness", timeoutTicks = 60)
    public static void theLadderHasNotCollapsedToOneSharedDefault(GameTestHelper helper) {
        Map<MineableDefinition, Block> owned = modOwnedByRank();
        Set<Float> distinct = new TreeSet<>();
        Map<Float, Integer> census = new LinkedHashMap<>();
        for (Block block : owned.values()) {
            float time = block.defaultDestroyTime();
            distinct.add(time);
            census.merge(time, 1, Integer::sum);
        }
        check(distinct.size() >= 10,
                "the ladder must keep a real spread of destroy times, found " + distinct);
        int mostShared = census.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        check(mostShared <= 2,
                "no single destroy time may dominate the family (that was the 3.0 defect): " + census);
        helper.succeed();
    }

    /** Rank order is the catalogue's; the ladder must never contradict it. */
    @GameTest(template = TEMPLATE, batch = "mining_hardness", timeoutTicks = 60)
    public static void destroyTimeNeverFallsAsMiningRankRises(GameTestHelper helper) {
        float previousTime = 0.0f;
        float previousRank = -1.0f;
        String previousId = "-";
        for (Map.Entry<MineableDefinition, Block> entry : modOwnedByRank().entrySet()) {
            float time = entry.getValue().defaultDestroyTime();
            float rank = entry.getKey().requiredMining();
            check(time >= previousTime,
                    "destroy time fell as Mining rank rose: " + previousId + " (req " + previousRank
                            + ") = " + previousTime + " but " + entry.getKey().id() + " (req "
                            + rank + ") = " + time);
            previousTime = time;
            previousRank = rank;
            previousId = entry.getKey().id();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  It survives the real destroy-progress arithmetic                   */
    /* ------------------------------------------------------------------ */

    /**
     * Configured floats are not the deliverable — felt duration is. This drives the whole
     * mod-owned family through vanilla's own {@code getDestroyProgress} with the project pickaxe
     * and asserts the resulting per-tick progress is strictly ordered the other way, always real
     * progress, and never an instant break.
     */
    @GameTest(template = TEMPLATE, batch = "mining_hardness", timeoutTicks = 120)
    public static void realDestroyProgressFollowsTheLadder(GameTestHelper helper) {
        ServerPlayer miner = ManagedResourceTestPlayers.survival(helper.getLevel(), "ladder-miner");
        miner.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        SkillManager.applyConfirmedValue(miner, MiningSkill.SKILL_ID, 100.0f);
        BlockPos absolute = helper.absolutePos(NODE);

        float previousProgress = Float.MAX_VALUE;
        String previousId = "-";
        for (Map.Entry<MineableDefinition, Block> entry : modOwnedByRank().entrySet()) {
            helper.setBlock(NODE, entry.getValue());
            float progress = helper.getLevel().getBlockState(absolute)
                    .getDestroyProgress(miner, helper.getLevel(), absolute);
            String id = entry.getKey().id();

            check(progress > 0.0f, id + " must make real progress with an authorized pickaxe");
            check(progress < INSTANT_BREAK_FLOOR,
                    id + " breaks effectively instantly at " + progress + " per tick");
            check(progress <= previousProgress,
                    "a higher-ranked resource progressed faster: " + previousId + " = "
                            + previousProgress + " but " + id + " = " + progress);
            previousProgress = progress;
            previousId = id;
        }
        helper.succeed();
    }

    /** The three named tiers, end to end: bottom digs measurably faster than the summit. */
    @GameTest(template = TEMPLATE, batch = "mining_hardness", timeoutTicks = 60)
    public static void lowMidAndHighTierAreVisiblyDifferent(GameTestHelper helper) {
        ServerPlayer miner = ManagedResourceTestPlayers.survival(helper.getLevel(), "tier-miner");
        miner.setItemInHand(InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3));
        BlockPos absolute = helper.absolutePos(NODE);

        float coal = progress(helper, miner, absolute, BlockRegistry.COAL_ORE.get());
        float silver = progress(helper, miner, absolute, BlockRegistry.SILVER_ORE.get());
        float valorite = progress(helper, miner, absolute, BlockRegistry.VALORITE_ORE.get());

        check(coal > silver && silver > valorite,
                "coal > silver > valorite expected, got " + coal + " / " + silver + " / " + valorite);
        // Perceptible, not punishing: the summit is meaningfully slower but within one order.
        check(coal / valorite >= 2.0f,
                "the ladder must be felt: coal is only " + (coal / valorite) + "x valorite");
        check(coal / valorite <= 6.0f,
                "the ladder must stay playable: coal is " + (coal / valorite) + "x valorite");
        helper.succeed();
    }

    private static float progress(GameTestHelper helper, ServerPlayer miner, BlockPos absolute,
            Block block) {
        helper.setBlock(NODE, block);
        return helper.getLevel().getBlockState(absolute)
                .getDestroyProgress(miner, helper.getLevel(), absolute);
    }
}

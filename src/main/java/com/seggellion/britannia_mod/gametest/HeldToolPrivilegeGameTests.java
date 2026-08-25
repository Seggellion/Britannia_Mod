package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.structure.SurvivalZoneHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * A held item is never a permission.
 *
 * <h2>The defect</h2>
 * {@code StructureProtectionHandler} used to return outright for anyone holding a
 * {@code QualityToolItem} or a {@code TwoHandedAxeItem}, before it had asked
 * {@code HouseBuildRights} anything at all. A stranger carrying a project pickaxe therefore walked
 * through every house rule: ownership, the perimeter foundation and the lot block alike. The
 * exemption predated {@code HouseBuildRights} and existed to let miners — whom the retired
 * {@code CityGameModeHandler} force-switched into survival — past a survival refusal that no
 * longer applies to anybody.
 *
 * <p>The invariant these tests hold down is the whole authorization equation:
 *
 * <pre>
 *   valid tool target AND valid resource/skill AND valid location/ownership
 *       = authorized extraction
 * </pre>
 *
 * <p>Every case is driven through the real event bus rather than by calling a service, because the
 * claim is about how the registered handlers compose at their real priorities. That is also what
 * makes the {@code can_break} half meaningful: the tools here carry the genuine predicate the tick
 * handler maintains, so a refusal proves the server said no, not that the client could not ask.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HeldToolPrivilegeGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos TARGET = new BlockPos(1, 1, 1);

    private HeldToolPrivilegeGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    private static ItemStack shovel() {
        return ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3);
    }

    private static ItemStack axe() {
        return new ItemStack(ItemRegistry.TWO_HANDED_AXE.get());
    }

    /** A player carrying the genuine client-side authorization for that tool. */
    private static ServerPlayer holder(ServerLevel level, String name, ItemStack tool) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, name);
        player.setGameMode(GameType.ADVENTURE);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, 100.0f);
        return equip(player, tool);
    }

    /**
     * Re-arms an existing player with another tool, predicate and all.
     *
     * <p>Suites reuse one player across a matrix rather than joining a fresh one per row: a
     * GameTest mock player joins for real and never disconnects, and the server writes every
     * joined player's NBT on the tick thread at each autosave, so a suite that joins a player per
     * assertion degrades the whole run for everybody after it.
     */
    private static ServerPlayer equip(ServerPlayer player, ItemStack tool) {
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(tool);
        if (predicate != null) {
            tool.set(DataComponents.CAN_BREAK, predicate);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    private static StructureRecord houseAround(GameTestHelper helper, UUID owner, ServerLevel level) {
        AABB box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 5, 6))));
        return new StructureRecord(owner, box, box, UUID.randomUUID(),
                "small", "SMALL_BRICK", null, 0, level.dimension());
    }

    /**
     * One break attempt through the real bus, judged by the world rather than by the event flag.
     *
     * <p>The cancel flag cannot answer this question: every managed resource path cancels the
     * event precisely <em>because</em> it succeeded and took the break over itself, so "cancelled"
     * covers both a refusal and a completed extraction. What separates them is whether the block
     * is still standing afterwards, which is also the thing a player would see.
     *
     * <p>Mirrors what the server does next: if nothing cancelled the event, vanilla removes the
     * block, so the test performs that too.
     */
    private static boolean blockSurvived(ServerLevel level, BlockPos absolute, ServerPlayer player) {
        BlockState state = level.getBlockState(absolute);
        Block before = state.getBlock();
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, absolute, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            level.destroyBlock(absolute, true, player);
        }
        return level.getBlockState(absolute).is(before);
    }

    private static List<ItemEntity> dropsNear(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    private record Attempt(String label, ItemStack tool, Block block) {}

    /** Pickaxe, shovel and axe, each against the resource it is genuinely authorized for. */
    private static List<Attempt> everyToolAgainstItsOwnResource() {
        return List.of(
                new Attempt("a pickaxe on stone", pickaxe(), Blocks.STONE),
                new Attempt("a pickaxe on managed ore", pickaxe(), BlockRegistry.SILVER_ORE.get()),
                new Attempt("a shovel on clay", shovel(), BlockRegistry.CLAY_DEPOSIT.get()),
                new Attempt("a shovel on silica", shovel(), BlockRegistry.SILICA_SAND_DEPOSIT.get()),
                new Attempt("an axe on a log", axe(), Blocks.OAK_LOG),
                new Attempt("an axe on leaves", axe(), Blocks.OAK_LEAVES));
    }

    /* ------------------------------------------------------------------ */
    /*  Somebody else's house refuses every tool                           */
    /* ------------------------------------------------------------------ */

    /**
     * The defect, closed for the whole cross product. Each tool is the authorized one for its
     * target and each holder is fully skilled, so the only thing that can refuse them is the
     * house — which is exactly the point.
     */
    @GameTest(template = TEMPLATE, batch = "held_tool_privilege", timeoutTicks = 120)
    public static void noToolReachesIntoSomebodyElsesHouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);
        StructureRegionManager.registerStructure(house);
        try {
            ServerPlayer stranger = holder(level, "trespasser", ItemStack.EMPTY);
            for (Attempt attempt : everyToolAgainstItsOwnResource()) {
                helper.setBlock(TARGET, attempt.block());
                dropsNear(level, absolute).forEach(ItemEntity::discard);
                equip(stranger, attempt.tool());

                check(blockSurvived(level, absolute, stranger),
                        attempt.label() + " got through somebody else's house");
                helper.assertBlockPresent(attempt.block(), TARGET);
                check(dropsNear(level, absolute).isEmpty(),
                        attempt.label() + " produced a drop inside somebody else's house");
                check(!BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute),
                        attempt.label() + " enrolled restoration debt for a refused break");
                check(stranger.getMainHandItem().getDamageValue() == 0,
                        attempt.label() + " charged durability for a refused break");
            }
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /**
     * The predicate is a lifecycle key, not a permission.
     *
     * <p>The stranger's pickaxe genuinely authorizes stone — that is the whole point of
     * {@code can_break} — and the server refuses anyway because the stone is somebody's wall.
     */
    @GameTest(template = TEMPLATE, batch = "held_tool_privilege", timeoutTicks = 60)
    public static void canBreakDoesNotOverrideHouseProtection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        helper.setBlock(TARGET, Blocks.STONE);

        ItemStack tool = pickaxe();
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(tool);
        check(predicate != null && predicate.test(
                        new net.minecraft.world.level.block.state.pattern.BlockInWorld(
                                level, absolute, false)),
                "precondition: the pickaxe's predicate really does authorize this stone");

        StructureRecord house = houseAround(helper, UUID.randomUUID(), level);
        StructureRegionManager.registerStructure(house);
        try {
            ServerPlayer stranger = holder(level, "predicate-trespasser", tool);
            check(blockSurvived(level, absolute, stranger),
                    "an authorizing can_break predicate must not override house protection");
            helper.assertBlockPresent(Blocks.STONE, TARGET);
        } finally {
            StructureRegionManager.unregisterStructure(house);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Outside any house the same attempts still work                     */
    /* ------------------------------------------------------------------ */

    /**
     * The other half of the removal: taking the exemption away must not have made legitimate
     * gathering fail. The identical cross product, on open ground, must all proceed.
     */
    @GameTest(template = TEMPLATE, batch = "held_tool_privilege", timeoutTicks = 120)
    public static void outsideAnyHouseEveryToolStillWorks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(TARGET);
        ServerPlayer miner = holder(level, "free-miner", ItemStack.EMPTY);
        for (Attempt attempt : everyToolAgainstItsOwnResource()) {
            helper.setBlock(TARGET, attempt.block());
            dropsNear(level, absolute).forEach(ItemEntity::discard);
            equip(miner, attempt.tool());

            check(!blockSurvived(level, absolute, miner),
                    attempt.label() + " was refused on open ground, where nothing owns it");
            helper.assertBlockNotPresent(attempt.block(), TARGET);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  A tool grants nothing on its own                                   */
    /* ------------------------------------------------------------------ */

    /**
     * No extraction tool moves a player's game mode or lends them build rights. The zone rule is
     * driven directly, which is what production runs every tick.
     */
    @GameTest(template = TEMPLATE, batch = "held_tool_privilege", timeoutTicks = 60)
    public static void noToolGrantsGameModeOrBuildRights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "unprivileged");
        for (ItemStack tool : List.of(pickaxe(), shovel(), axe())) {
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);

            SurvivalZoneHandler.applyTo(player);

            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    tool.getItem() + " moved a player's game mode");
            check(!player.getAbilities().mayBuild,
                    tool.getItem() + " lent build rights outside any house");
            check(!SurvivalZoneHandler.holdsLease(player.getUUID()),
                    tool.getItem() + " earned a housing lease");
        }
        helper.succeed();
    }

    /**
     * The owner's side of the same rule: the lease comes from where they are standing, and the
     * tool in hand neither creates it nor takes it away.
     */
    @GameTest(template = TEMPLATE, batch = "held_tool_privilege", timeoutTicks = 60)
    public static void theOwnersLeaseIsUnaffectedByWhatTheyHold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ManagedResourceTestPlayers.survival(level, "house-owner");
        StructureRecord house = houseAround(helper, owner.getUUID(), level);
        StructureRegionManager.registerStructure(house);
        try {
            owner.teleportTo(helper.absolutePos(new BlockPos(3, 1, 3)).getX() + 0.5,
                    helper.absolutePos(new BlockPos(3, 1, 3)).getY(),
                    helper.absolutePos(new BlockPos(3, 1, 3)).getZ() + 0.5);

            for (ItemStack tool : List.of(ItemStack.EMPTY, pickaxe(), shovel(), axe())) {
                owner.setItemInHand(InteractionHand.MAIN_HAND, tool);
                SurvivalZoneHandler.applyTo(owner);
                check(owner.getAbilities().mayBuild,
                        "the owner lost their lease while holding " + tool.getItem());
                check(owner.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                        "the owner's game mode changed while holding " + tool.getItem());
            }
        } finally {
            StructureRegionManager.unregisterStructure(house);
            SurvivalZoneHandler.forgetLentRights();
            owner.getAbilities().mayBuild = false;
        }
        helper.succeed();
    }
}

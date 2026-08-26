package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.commands.PopulateOresCommand;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.event.ManagedResourceExplosionHandler;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * OreVein remediation milestone 1: the mutation routes that could destroy a managed resource
 * outside its extraction transaction are closed, and each closure is proved by driving the real
 * path rather than by reading the source.
 *
 * <p>The four routes, and what each test pins:
 *
 * <ol>
 *   <li><b>Wrong tool.</b> The skill gate had no tool policy, so a sufficiently skilled player
 *       reached vanilla breaking with anything in hand.</li>
 *   <li><b>Pistons.</b> Managed blocks were {@code PushReaction.NORMAL}, so a deposit could be
 *       shoved away from the restoration record that owned its position.</li>
 *   <li><b>Explosions.</b> They do not fire {@code BreakEvent}, so no gate ever saw them.</li>
 *   <li><b>Restoration into fluid.</b> Water and lava are {@code .replaceable()} in 1.21.1, so the
 *       occupancy guard let a returning node delete them.</li>
 * </ol>
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class OreVeinContainmentGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private OreVeinContainmentGameTests() {
    }

    private static ServerPlayer miner(GameTestHelper helper, float miningSkill, ItemStack tool) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, miningSkill);
        return player;
    }

    private static List<ItemEntity> dropsNear(GameTestHelper helper, BlockPos absolute) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(4.0));
    }

    private static boolean scheduled(ServerLevel level, BlockPos absolute) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    // ------------------------------------------------------------------ 1. wrong tool

    /**
     * Every wrong tool must be inert against a managed resource, however skilled the miner.
     *
     * @param tool what the miner is holding
     * @param node the managed block under test
     * @param what a name for the failure message
     */
    private static void wrongToolIsInert(
            GameTestHelper helper, ItemStack tool, Block node, String what) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, node);

        // Skill is deliberately at the top of the ladder: the point is that satisfying the
        // requirement must not be sufficient on its own.
        ServerPlayer miner = miner(helper, 150.0f, tool);

        // Assert the *reason*, not just the outcome. A bare hand and a vanilla pickaxe are also
        // refused incidentally by StructureProtectionHandler, so a test that only checked "the
        // break failed" would pass even with the defect still present. This pins the Mining gate
        // itself as the authority, which is what the two-handed axe route needed and never had.
        check(MiningBreakGate.evaluate(
                        miner, level.getBlockState(absolute), level, absolute).type()
                        == MiningBreakGate.ResultType.WRONG_TOOL,
                "the Mining gate itself must refuse " + what + ", not another handler by accident");

        boolean broke = miner.gameMode.destroyBlock(absolute);

        check(!broke, what + " must not be able to break a managed resource");
        helper.assertBlockPresent(node, NODE);
        check(dropsNear(helper, absolute).isEmpty(),
                what + " must not produce a drop of any kind");
        check(!scheduled(level, absolute),
                what + " must not create a restoration record");
        check(MiningSkill.checkMiningAttempt(miner, level.getBlockState(absolute), absolute)
                        .skillGained() == 0.0f,
                what + " must award no Mining");
    }

    /**
     * The exploit milestone 0 identified, driven end to end.
     *
     * <p>The retired {@code CityGameModeHandler} counted a two-handed axe a special tool and put
     * its holder in survival; {@code StructureProtectionHandler} counts it an allowed tool and
     * declines to refuse. Nothing else stood between the gate and vanilla breaking, and the
     * Britannia ore blocks have no loot table — so a maxed miner with an axe deleted a vein
     * outright, gaining nothing and leaving no debt to bring it back. The survival switch is gone
     * now, but the gate's tool refusal this test pins is what keeps the same exploit closed for
     * any break path that still reaches the event.
     */
    @GameTest(template = TEMPLATE)
    public static void aTwoHandedAxeCannotDestroyAGatedOreAtFullSkill(GameTestHelper helper) {
        wrongToolIsInert(helper, new ItemStack(ItemRegistry.TWO_HANDED_AXE.get()),
                BlockRegistry.VERITE_ORE.get(), "a two-handed axe");
        helper.succeed();
    }

    /** The Britannia shovel works deposits, not ore; it must not be a back door into the ladder. */
    @GameTest(template = TEMPLATE)
    public static void aBritanniaShovelCannotDestroyAGatedOreAtFullSkill(GameTestHelper helper) {
        wrongToolIsInert(helper, new ItemStack(ToolRegistry.SHOVEL.get()),
                BlockRegistry.VERITE_ORE.get(), "a Britannia shovel");
        helper.succeed();
    }

    /**
     * The vanilla-ore half of the same defect. {@code minecraft:iron_ore} is a catalogued Mining
     * resource but keeps its vanilla loot table, so this route did not merely delete the block —
     * it paid out raw iron outside the economy entirely.
     */
    @GameTest(template = TEMPLATE)
    public static void aVanillaPickaxeCannotMineCataloguedVanillaIronOre(GameTestHelper helper) {
        wrongToolIsInert(helper, new ItemStack(Items.DIAMOND_PICKAXE),
                Blocks.IRON_ORE, "a vanilla diamond pickaxe");
        helper.succeed();
    }

    /** Gold is the other vanilla-block resource on the ladder. */
    @GameTest(template = TEMPLATE)
    public static void aVanillaPickaxeCannotMineCataloguedVanillaGoldOre(GameTestHelper helper) {
        wrongToolIsInert(helper, new ItemStack(Items.NETHERITE_PICKAXE),
                Blocks.GOLD_ORE, "a vanilla netherite pickaxe");
        helper.succeed();
    }

    /** A bare hand is refused by the gate itself now, not incidentally by another handler. */
    @GameTest(template = TEMPLATE)
    public static void anEmptyHandCannotDestroyAGatedOreAtFullSkill(GameTestHelper helper) {
        wrongToolIsInert(helper, ItemStack.EMPTY, BlockRegistry.VERITE_ORE.get(), "a bare hand");
        helper.succeed();
    }

    /**
     * The other side of the invariant: closing the wrong-tool route must not have narrowed the
     * right one. A project pickaxe at sufficient skill still runs the whole managed flow.
     */
    @GameTest(template = TEMPLATE)
    public static void theProjectPickaxeStillCompletesTheManagedExtraction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.VERITE_ORE.get());

        ServerPlayer miner = miner(helper, 150.0f, new ItemStack(ToolRegistry.PICKAXE.get()));
        miner.gameMode.destroyBlock(absolute);

        check(!level.getBlockState(absolute).is(BlockRegistry.VERITE_ORE.get()),
                "an authorized break must empty the cell");
        check(!dropsNear(helper, absolute).isEmpty(),
                "an authorized break must hand over its managed yield");
        check(scheduled(level, absolute),
                "an authorized break must schedule the node's return");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 2. pistons

    /**
     * A managed resource must refuse to be pushed.
     *
     * <p>Asserted on the live block state, which is the value {@code PistonBaseBlock.isPushable}
     * itself consults, so this is the engine's own decision rather than a description of it.
     */
    @GameTest(template = TEMPLATE)
    public static void managedResourceBlocksRefusePistonMovement(GameTestHelper helper) {
        List<Block> immovable = List.of(
                BlockRegistry.VERITE_ORE.get(),
                BlockRegistry.SILVER_ORE.get(),
                BlockRegistry.AGAPITE_ORE.get(),
                BlockRegistry.TIN_ORE.get(),
                BlockRegistry.COPPER_ORE.get(),
                BlockRegistry.SHADOW_IRON_ORE.get(),
                BlockRegistry.VALORITE_ORE.get(),
                BlockRegistry.SANDSTONE_DEPOSIT.get(),
                BlockRegistry.CLAY_DEPOSIT.get(),
                BlockRegistry.SILICA_SAND_DEPOSIT.get());
        for (Block block : immovable) {
            check(block.defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK,
                    block.getName().getString() + " must refuse to be moved by a piston");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 3. explosions

    /** An explosion must leave a managed ore standing, with no drop and no bogus debt. */
    @GameTest(template = TEMPLATE)
    public static void anExplosionCannotDestroyAManagedOre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.VERITE_ORE.get());

        check(ManagedResourceExplosionHandler.isProtectedFromExplosions(level, absolute),
                "a managed ore must be removed from the blast");

        level.explode(null, absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5,
                4.0F, ServerLevel.ExplosionInteraction.BLOCK);

        helper.assertBlockPresent(BlockRegistry.VERITE_ORE.get(), NODE);
        check(dropsNear(helper, absolute).isEmpty(), "an explosion must not pay out a managed ore");
        check(!scheduled(level, absolute), "an explosion must not create a restoration record");
        helper.succeed();
    }

    /** The same policy covers the deposit beds, which are the other managed resource system. */
    @GameTest(template = TEMPLATE)
    public static void anExplosionCannotDestroyAManagedDeposit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        for (Block bed : List.of(
                BlockRegistry.CLAY_DEPOSIT.get(), BlockRegistry.SILICA_SAND_DEPOSIT.get())) {
            helper.setBlock(NODE, bed);
            check(ManagedResourceExplosionHandler.isProtectedFromExplosions(level, absolute),
                    bed.getName().getString() + " must be removed from the blast");

            level.explode(null, absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5,
                    4.0F, ServerLevel.ExplosionInteraction.BLOCK);

            helper.assertBlockPresent(bed, NODE);
            check(!scheduled(level, absolute),
                    bed.getName().getString() + " must not gain a restoration record");
        }
        helper.succeed();
    }

    /**
     * The stated boundary of the explosion policy, pinned so that widening it has to be a decision.
     *
     * <p>Ordinary terrain — stone, deepslate, granite and the rest of the STONE category — is
     * catalogued so that working it trains Mining, but it is not a placed deposit. Protecting all
     * of it would end TNT excavation and leave creeper craters with floating walls, which is a
     * change to how the world behaves rather than a containment fix.
     */
    @GameTest(template = TEMPLATE)
    public static void ordinaryStoneTerrainIsNotExplosionProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        for (Block terrain : List.of(Blocks.STONE, Blocks.GRANITE, Blocks.DEEPSLATE, Blocks.TUFF)) {
            helper.setBlock(NODE, terrain);
            check(!ManagedResourceExplosionHandler.isProtectedFromExplosions(level, absolute),
                    terrain.getName().getString() + " is terrain, not a deposit, and must stay blastable");
        }
        helper.succeed();
    }

    /** A player's own wall is construction; an explosion must not be stricter than a pickaxe. */
    @GameTest(template = TEMPLATE)
    public static void playerPlacedOreIsNotExplosionProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.IRON_ORE);

        check(ManagedResourceExplosionHandler.isProtectedFromExplosions(level, absolute),
                "a natural iron ore is a managed resource");

        MiningProvenance.markPlayerPlaced(level, absolute);
        check(!ManagedResourceExplosionHandler.isProtectedFromExplosions(level, absolute),
                "an iron ore the player placed themselves is their construction");

        MiningProvenance.forget(level, absolute);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 4. restoration occupancy

    /** Water must block a node's return instead of being deleted by it. */
    @GameTest(template = TEMPLATE)
    public static void waterBlocksRestoration(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.WATER);
        check(!BlockRestoreHandler.canRestoreInto(helper.getLevel(), absolute),
                "a node must not return by deleting the water standing in its cell");
        helper.succeed();
    }

    /** And lava, for the Nether-native resources the catalogue already covers. */
    @GameTest(template = TEMPLATE)
    public static void lavaBlocksRestoration(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.LAVA);
        check(!BlockRestoreHandler.canRestoreInto(helper.getLevel(), absolute),
                "a node must not return by deleting the lava standing in its cell");
        helper.succeed();
    }

    /** The ordinary case must be untouched: an empty cell still takes its node back. */
    @GameTest(template = TEMPLATE)
    public static void anEmptyCellStillAcceptsRestoration(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.AIR);
        check(BlockRestoreHandler.canRestoreInto(helper.getLevel(), absolute),
                "an empty cell must still accept the node back");
        helper.succeed();
    }

    /** A block entity is never overwritten, even though many report a replaceable state. */
    @GameTest(template = TEMPLATE)
    public static void aBlockEntityBlocksRestoration(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.CHEST);
        check(!BlockRestoreHandler.canRestoreInto(helper.getLevel(), absolute),
                "a container must not be overwritten by a returning node");
        helper.succeed();
    }

    /**
     * A blocked cell keeps its debt. The record must survive the refusal, because dropping it
     * would silently retire the deposit.
     */
    @GameTest(template = TEMPLATE)
    public static void aBlockedCellKeepsItsPendingRecord(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, BlockRegistry.VERITE_ORE.get());

        BrokenBlockTracker.recordBrokenBlock(
                level, absolute, level.getBlockState(absolute), java.util.UUID.randomUUID());
        helper.setBlock(NODE, Blocks.WATER);

        check(!BlockRestoreHandler.canRestoreInto(level, absolute), "water must block");
        check(scheduled(level, absolute),
                "a blocked cell must keep its pending record rather than lose the deposit");

        BrokenBlockTracker.removeBlock(level, absolute);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 5. legacy command scope

    /**
     * Coal is placeable again, on the terms milestone 1 set.
     *
     * <p>Milestone 1 withdrew coal because it placed {@code minecraft:coal_ore}, which the Mining
     * catalogue did not govern: it broke with vanilla drops, required no skill and scheduled no
     * restoration — a managed generation path manufacturing unmanaged economic material. The test
     * that replaced it said coal must stay out "until it has a canonical resource definition".
     *
     * <p>Milestone 11 gave it one. So this now asserts the other half of that sentence, and the
     * condition that made the withdrawal necessary is asserted separately and still holds: what
     * coal places is {@code britannia_mod:coal_ore}, never the vanilla block.
     */
    @GameTest(template = TEMPLATE)
    public static void coalIsPlaceableNowThatItHasACanonicalDefinition(GameTestHelper helper) {
        check(PopulateOresCommand.placeableOreTypes().contains("coal"),
                "coal has a canonical resource definition at milestone 11 and should be placeable");
        // Nine ladder ores, silica since milestone 7, and coal since milestone 11. What this guards
        // is that the placeable set is exactly the catalogued resources, not that the number is 11.
        check(PopulateOresCommand.placeableOreTypes().size() == 11,
                "expected the nine ladder ores plus silica and coal to be placeable, found "
                        + PopulateOresCommand.placeableOreTypes());
        check(PopulateOresCommand.placeableOreTypes().contains("silica_sand_deposit"),
                "silica gained a generation shape at milestone 7 and should be placeable");
        helper.succeed();
    }

    /**
     * The reason coal was withdrawn must not come back with it.
     *
     * <p>Managed coal is {@code britannia_mod:coal_ore}. If the definition ever named the vanilla
     * block instead, every legacy chunk's coal would become economic terrain overnight and the
     * placement route would once again be manufacturing material the catalogue does not govern.
     */
    @GameTest(template = TEMPLATE)
    public static void managedCoalIsNeverTheVanillaBlock(GameTestHelper helper) {
        ResourceDefinition coal = ResourceCatalog.instance().byPath("coal").orElseThrow();
        check(coal.blockIds().equals(java.util.List.of("britannia_mod:coal_ore")),
                "managed coal must govern exactly britannia_mod:coal_ore, found " + coal.blockIds());
        check(coal.generation().orElseThrow().blockId().equals("britannia_mod:coal_ore"),
                "coal must generate its own block, never minecraft:coal_ore");
        helper.succeed();
    }

    /**
     * The two retired operations refuse, and refusing costs nothing.
     *
     * <p>{@code /populateores clear} used to walk 41x41 chunks through {@code level.getChunk},
     * which generates any chunk that does not exist yet, and read every block from the world floor
     * to its ceiling — about 165 million reads on the server thread — then replace anything whose
     * type appeared in its own table with stone, unable to tell a placed deposit from natural
     * terrain or a player's wall. {@code /undoores} read a static map with no dimension key that
     * was empty after every restart.
     *
     * <p>The test asserts both refuse <em>and</em> that a resource standing beside the operator is
     * untouched, which is what "refusing costs nothing" has to mean here: no scan ran.
     */
    @GameTest(template = TEMPLATE)
    public static void theLegacyClearAndUndoOperationsAreDisabled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(NODE, BlockRegistry.VERITE_ORE.get());

        var dispatcher = level.getServer().getCommands().getDispatcher();
        var source = level.getServer().createCommandSourceStack()
                .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(NODE)));

        for (String command : List.of(
                "populateores clear", "populateores clear verite", "undoores")) {
            int result;
            try {
                result = dispatcher.execute(command, source);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new GameTestAssertException(
                        "/" + command + " must still parse so the operator gets an explanation: "
                                + exception.getMessage());
            }
            check(result == 0, "/" + command + " must refuse rather than act");
        }

        helper.assertBlockPresent(BlockRegistry.VERITE_ORE.get(), NODE);
        helper.succeed();
    }

    /**
     * Vanilla coal ore stays ordinary, decorative terrain.
     *
     * <p>Milestone 11 gave coal a managed identity of its own, and this is the other side of that:
     * {@code minecraft:coal_ore} in a legacy chunk is not retro-claimed by the economy. It carries
     * no managed policy, so it breaks the way it always has.
     */
    @GameTest(template = TEMPLATE)
    public static void vanillaCoalOreItselfIsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(NODE);
        helper.setBlock(NODE, Blocks.COAL_ORE);
        helper.assertBlockPresent(Blocks.COAL_ORE, NODE);
        check(!ManagedResourceExplosionHandler.isProtectedFromExplosions(level, absolute),
                "coal is not a managed resource, so no managed policy may attach to it");
        helper.succeed();
    }
}

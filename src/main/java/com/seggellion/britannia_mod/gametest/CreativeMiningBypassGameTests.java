package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.Resources;
import com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates;
import com.seggellion.britannia_mod.skill.SkillManager;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creative players break catalogued blocks like any other block unless they attack with the
 * Britannia pickaxe, proved at the server's real entry point for a creative swing.
 *
 * <h2>The defect</h2>
 * A creative swing is one packet: the server fires {@code LeftClickBlock} and, unless something
 * cancelled it, destroys the block at once through the same {@code BreakEvent} chain a survival
 * dig ends in. The Mining gate cancelled that first event for anyone whose main hand was not an
 * authorised pickaxe (WRONG_TOOL) or whose Mining was below the block's requirement, and a
 * creative guard cancelled every creative break of a sited deposit outright — so an
 * administrator in creative could not dig through stone with an empty hand, and could not clear
 * a misplaced vein at all. The rule now is that a creative player who is not attacking with the
 * Britannia pickaxe is administering, every managed path stands aside, and the block breaks as
 * vanilla creative breaks it; a creative player attacking with the Britannia pickaxe is a tester
 * and gets the whole managed flow, ladder included.
 *
 * <h2>How these tests drive it</h2>
 * The creative rows call {@code ServerPlayerGameMode.handleBlockBreakAction} with
 * {@code START_DESTROY_BLOCK}, which is exactly what the packet handler calls: the
 * {@code LeftClickBlock} preflight, the reach and height checks, and the creative destroy all run
 * in their real order, so a cancellation anywhere on that path fails the test the way it fails a
 * player. Survival and adventure rows use the preflight hook and {@code destroyBlock} the way the
 * lifecycle suites do, because a survival dig does not complete inside its first packet.
 *
 * <p>Each player joins over an embedded channel this class keeps hold of, so what the server
 * <em>said</em> to them is readable: "no mining messages" is asserted on the client-bound chat
 * packets themselves, and the survival control proves the capture works before any creative row
 * relies on its silence.
 *
 * <p>The ordinary item is a stick, not a sword: vanilla itself refuses to let a creative player
 * break blocks with a sword, trident, mace or debug stick ({@code Item.canAttackBlock}), and that
 * restriction is not this system's and is not touched.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeMiningBypassGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "creative_mining_bypass";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private static final String INSUFFICIENT = "message.britannia_mod.mining.insufficient";
    private static final String MINING_WRONG_TOOL = "message.britannia_mod.mining.wrong_tool";
    private static final String DEPOSIT_WRONG_TOOL = "message.britannia_mod.deposit.wrong_tool";

    private static int sequence = 1;

    private CreativeMiningBypassGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Rig                                                                */
    /* ------------------------------------------------------------------ */

    /** A joined player and the channel the server talks to them on. */
    private record Rig(ServerPlayer player, EmbeddedChannel channel) {

        Rig mode(GameType mode) {
            player.setGameMode(mode);
            return this;
        }

        Rig mining(float skill) {
            SkillManager.applyConfirmedValue(player, MiningSkill.SKILL_ID, skill);
            return this;
        }

        Rig holding(ItemStack stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return this;
        }

        Rig offhand(ItemStack stack) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            return this;
        }

        /** Every chat line the server has sent since the last drain: translation key or text. */
        List<String> said() {
            // The server suspends flushing on every connection for the length of a tick
            // (MinecraftServer.tickChildren), and a GameTest runs inside one, so anything sent
            // since the last drain is still sitting unflushed in the channel's outbound buffer.
            channel.flushOutbound();
            List<String> lines = new ArrayList<>();
            Object message;
            while ((message = channel.outboundMessages().poll()) != null) {
                collect(message, lines);
            }
            return lines;
        }

        private static void collect(Object message, List<String> into) {
            if (message instanceof ClientboundBundlePacket bundle) {
                for (Packet<?> sub : bundle.subPackets()) {
                    collect(sub, into);
                }
            } else if (message instanceof ClientboundSystemChatPacket chat) {
                into.add(describe(chat.content()));
            }
        }

        private static String describe(Component content) {
            return content.getContents() instanceof TranslatableContents translatable
                    ? translatable.getKey()
                    : content.getString();
        }
    }

    /** Mirrors {@code ManagedResourceTestPlayers.survival}, keeping the channel. */
    private static Rig join(ServerLevel level, String name, GameType mode) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        return new Rig(player, channel);
    }

    private static ItemStack pickaxe() {
        return ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3);
    }

    /** The pickaxe an adventure player actually carries: predicate and all. */
    private static ItemStack adventurePickaxe() {
        ItemStack tool = pickaxe();
        AdventureModePredicate predicate = ExtractionToolPredicates.predicateFor(tool);
        check(predicate != null, "the Britannia pickaxe must earn a catalogue CAN_BREAK predicate");
        tool.set(DataComponents.CAN_BREAK, predicate);
        return tool;
    }

    private static Block managedCoal() {
        return Resources.block(ResourceCatalog.instance().byPath("coal").orElseThrow()
                .generation().orElseThrow().blockId());
    }

    /**
     * One creative swing, exactly as the packet handler performs it. Stands the player beside the
     * block first, because the handler refuses a swing from out of reach before it does anything.
     */
    private static void swing(Rig rig, BlockPos target) {
        ServerPlayer player = rig.player();
        player.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.0D);
        player.gameMode.handleBlockBreakAction(target,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.NORTH,
                player.level().getMaxBuildHeight(), sequence++);
    }

    /** The first-swing preflight on its own, for the modes where a swing only starts a dig. */
    private static boolean firstSwingRefused(Rig rig, BlockPos target) {
        PlayerInteractEvent.LeftClickBlock event = CommonHooks.onLeftClickBlock(rig.player(), target,
                Direction.NORTH, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        return event.isCanceled();
    }

    private static List<ItemStack> takeDrops(ServerLevel level, BlockPos around) {
        List<ItemEntity> entities =
                level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3.0D));
        List<ItemStack> stacks = entities.stream().map(ItemEntity::getItem).toList();
        entities.forEach(ItemEntity::discard);
        return stacks;
    }

    private static int debtCount(ServerLevel level) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().size();
    }

    private static boolean hasDebtAt(ServerLevel level, BlockPos absolute) {
        return BrokenBlockDataStorage.get(level).getBrokenBlocks().containsKey(absolute);
    }

    /** Sets the block, clears the floor and the chat, and hands back the absolute position. */
    private static BlockPos plant(GameTestHelper helper, Rig rig, Block block) {
        helper.setBlock(NODE, block);
        BlockPos absolute = helper.absolutePos(NODE);
        takeDrops(helper.getLevel(), absolute);
        rig.said();
        return absolute;
    }

    /** The whole ledger of an ordinary creative removal: gone, silent, unaccounted. */
    private static void assertPlainCreativeRemoval(GameTestHelper helper, Rig rig, Block block,
            BlockPos absolute, int debtBefore, float skillBefore, String label) {
        ServerLevel level = helper.getLevel();
        check(level.getBlockState(absolute).isAir(),
                label + ": the block did not break; the mining system is still intercepting an"
                        + " ordinary creative break of " + block.getDescriptionId());
        List<ItemStack> drops = takeDrops(level, absolute);
        check(drops.isEmpty(), label + ": a creative break dropped " + drops);
        check(debtCount(level) == debtBefore && !hasDebtAt(level, absolute),
                label + ": a creative break filed restoration debt");
        check(SkillManager.getSkill(rig.player(), MiningSkill.SKILL_ID) == skillBefore,
                label + ": a creative break moved Mining");
        List<String> said = rig.said();
        check(said.isEmpty(), label + ": the server spoke to a bypassing creative player: " + said);
        check(MiningBreakGate.lastDenialKey(rig.player()).isEmpty(),
                label + ": a Mining denial was recorded for a bypassing creative player: "
                        + MiningBreakGate.lastDenialKey(rig.player()));
    }

    /* ------------------------------------------------------------------ */
    /*  Creative without the Britannia pickaxe                             */
    /* ------------------------------------------------------------------ */

    /**
     * Empty hand, an ordinary item, another pickaxe — on a sited ore, the ladder's top rung,
     * ambient stone, managed coal and a sediment bed — at Mining 0.0, where every one of those
     * would have refused a miner. All of it breaks, silently, and nothing is minted or filed.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void creativeWithoutThePickaxeBreaksEveryMineableLikeAnyBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rig rig = join(level, "creative-builder", GameType.CREATIVE).mining(0.0f);

        record Hand(String label, ItemStack stack) {
        }
        List<Hand> hands = List.of(
                new Hand("empty hand", ItemStack.EMPTY),
                new Hand("a stick", new ItemStack(Items.STICK)),
                new Hand("a diamond pickaxe", new ItemStack(Items.DIAMOND_PICKAXE)));
        List<Block> targets = List.of(
                BlockRegistry.SILVER_ORE.get(), BlockRegistry.VALORITE_ORE.get(), Blocks.STONE,
                managedCoal(), BlockRegistry.CLAY_DEPOSIT.get());

        for (Hand hand : hands) {
            for (Block target : targets) {
                rig.holding(hand.stack().copy());
                BlockPos absolute = plant(helper, rig, target);
                int debtBefore = debtCount(level);

                swing(rig, absolute);

                assertPlainCreativeRemoval(helper, rig, target, absolute, debtBefore, 0.0f,
                        "creative with " + hand.label() + " on " + target.getDescriptionId());
            }
        }
        helper.succeed();
    }

    /** A Britannia pickaxe anywhere but the attacking hand is not the testing exception. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aPickaxeInTheOffhandOrTheInventoryDoesNotMakeATester(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rig rig = join(level, "creative-offhand", GameType.CREATIVE).mining(0.0f);
        Block silver = BlockRegistry.SILVER_ORE.get();

        rig.holding(ItemStack.EMPTY).offhand(pickaxe());
        BlockPos absolute = plant(helper, rig, silver);
        int debtBefore = debtCount(level);
        swing(rig, absolute);
        assertPlainCreativeRemoval(helper, rig, silver, absolute, debtBefore, 0.0f,
                "empty main hand, Britannia pickaxe in the offhand");

        rig.holding(new ItemStack(Items.STICK));
        absolute = plant(helper, rig, silver);
        swing(rig, absolute);
        assertPlainCreativeRemoval(helper, rig, silver, absolute, debtBefore, 0.0f,
                "a stick in the main hand, Britannia pickaxe in the offhand");

        rig.holding(ItemStack.EMPTY).offhand(ItemStack.EMPTY);
        rig.player().getInventory().setItem(5, pickaxe());
        absolute = plant(helper, rig, silver);
        swing(rig, absolute);
        assertPlainCreativeRemoval(helper, rig, silver, absolute, debtBefore, 0.0f,
                "empty hands, Britannia pickaxe in the hotbar");
        helper.succeed();
    }

    /**
     * Nothing that would stop a miner stops the bypass: a resource far above the player's Mining,
     * a cell that already owes a restoration, and an armed denial cooldown.
     *
     * <p>Depletion in this implementation replaces the worked cell with air, so there is no
     * "depleted block" to break; the nearest live state is a cell whose restoration is still
     * pending, which is what the middle case plants.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void skillDepletionAndCooldownCannotBlockTheBypass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rig rig = join(level, "creative-blocked-by-nothing", GameType.CREATIVE)
                .mining(0.0f).holding(ItemStack.EMPTY);

        // Valorite wants 99.0; this player has 0.0.
        Block valorite = BlockRegistry.VALORITE_ORE.get();
        BlockPos absolute = plant(helper, rig, valorite);
        int debtBefore = debtCount(level);
        swing(rig, absolute);
        assertPlainCreativeRemoval(helper, rig, valorite, absolute, debtBefore, 0.0f,
                "Mining 0.0 on valorite");

        // A cell with a restoration already owed on it, and a block standing there anyway.
        Block silver = BlockRegistry.SILVER_ORE.get();
        absolute = plant(helper, rig, silver);
        BlockState standing = level.getBlockState(absolute);
        BrokenBlockTracker.recordBrokenBlock(level, absolute, standing, UUID.randomUUID());
        int debtWithPending = debtCount(level);
        check(hasDebtAt(level, absolute), "precondition: a restoration is pending at the cell");
        swing(rig, absolute);
        check(level.getBlockState(absolute).isAir(),
                "a pending restoration stopped a creative player breaking the block standing in the cell");
        check(takeDrops(level, absolute).isEmpty(), "a creative break over a pending cell dropped something");
        check(debtCount(level) == debtWithPending && hasDebtAt(level, absolute),
                "a creative break over a pending cell changed the restoration ledger");
        check(rig.said().isEmpty(), "a creative break over a pending cell was spoken to");
        BrokenBlockDataStorage.get(level).remove(absolute);

        // Arm the denial cooldown as a survival miner would, then come back to creative.
        rig.mode(GameType.SURVIVAL);
        absolute = plant(helper, rig, silver);
        check(firstSwingRefused(rig, absolute), "precondition: survival bare-handed is refused");
        String armed = MiningBreakGate.lastDenialKey(rig.player());
        check(!armed.isEmpty(), "precondition: the refusal armed the denial cooldown");
        rig.mode(GameType.CREATIVE);
        rig.said();
        debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).isAir(),
                "an armed denial cooldown stopped a creative player breaking the block");
        check(takeDrops(level, absolute).isEmpty() && debtCount(level) == debtBefore,
                "a creative break under an armed cooldown dropped or filed something");
        check(rig.said().isEmpty(), "a creative break under an armed cooldown was spoken to");
        check(MiningBreakGate.lastDenialKey(rig.player()).equals(armed),
                "a creative break touched the denial cooldown state");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Creative with the Britannia pickaxe                                */
    /* ------------------------------------------------------------------ */

    /**
     * The tester gets the real thing: refused at the first swing below the requirement, told why,
     * and paid, depleted and put on the restoration ledger at it — and still the wrong tool for a
     * bed. No automatic success, no free resource, no creative bonus.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void creativeWithThePickaxeGetsTheRealMiningFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rig rig = join(level, "creative-tester", GameType.CREATIVE).holding(pickaxe());
        Block silver = BlockRegistry.SILVER_ORE.get();

        // Below silver's 55.0: the first swing is refused, and the client is told.
        rig.mining(0.0f);
        BlockPos absolute = plant(helper, rig, silver);
        int debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).is(silver),
                "a creative tester below the requirement broke silver; creative must not bypass"
                        + " the ladder for the pickaxe holder");
        check(takeDrops(level, absolute).isEmpty() && debtCount(level) == debtBefore,
                "a refused creative tester was paid or filed for");
        List<String> said = rig.said();
        check(said.contains(INSUFFICIENT), "a refused creative tester was not told why: " + said);
        check(MiningBreakGate.lastDenialKey(rig.player()).startsWith("INSUFFICIENT_SKILL"),
                "the refusal was not the ladder's: " + MiningBreakGate.lastDenialKey(rig.player()));

        // At the ceiling: the managed extraction, exactly as for a survival miner.
        rig.mining(100.0f);
        absolute = plant(helper, rig, silver);
        debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).isAir(), "a qualified creative tester did not deplete silver");
        List<ItemStack> drops = takeDrops(level, absolute);
        check(drops.size() == 1 && drops.get(0).getItem() instanceof PurityOreItem,
                "a qualified creative tester was not paid exactly one purity ore: " + drops);
        check(debtCount(level) == debtBefore + 1 && hasDebtAt(level, absolute),
                "a creative tester's extraction did not file its one restoration debt");
        said = rig.said();
        check(said.stream().anyMatch(line -> line.startsWith("You mined")),
                "a creative tester's extraction was not announced: " + said);
        BrokenBlockDataStorage.get(level).remove(absolute);

        // Ambient stone excavates deterministically for a qualified miner: graded stone, debt.
        absolute = plant(helper, rig, Blocks.STONE);
        debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).isAir(), "a creative tester did not excavate stone");
        drops = takeDrops(level, absolute);
        check(drops.size() == 1 && drops.get(0).getItem() instanceof GradeStoneItem,
                "a creative tester was not paid exactly one graded stone: " + drops);
        check(debtCount(level) == debtBefore + 1 && hasDebtAt(level, absolute),
                "a creative tester's stone extraction did not file its one restoration debt");
        BrokenBlockDataStorage.get(level).remove(absolute);

        // A bed wants the shovel. The pickaxe that makes them a tester is still the wrong tool.
        Block clay = BlockRegistry.CLAY_DEPOSIT.get();
        absolute = plant(helper, rig, clay);
        debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).is(clay),
                "a creative tester's pickaxe removed a clay bed; the tool rule must still hold");
        check(takeDrops(level, absolute).isEmpty() && debtCount(level) == debtBefore,
                "a creative tester's pickaxe was paid or filed for a clay bed");
        said = rig.said();
        check(said.contains(MINING_WRONG_TOOL) || said.contains(DEPOSIT_WRONG_TOOL),
                "a creative tester was not told the pickaxe is the wrong tool for a bed: " + said);
        helper.succeed();
    }

    /** Off the catalogue, the pickaxe is just a pickaxe: an ordinary creative break, and quiet. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void creativeWithThePickaxeBreaksANonMineableNormally(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rig rig = join(level, "creative-tester-bricks", GameType.CREATIVE)
                .mining(0.0f).holding(pickaxe());

        BlockPos absolute = plant(helper, rig, Blocks.BRICKS);
        int debtBefore = debtCount(level);
        swing(rig, absolute);
        assertPlainCreativeRemoval(helper, rig, Blocks.BRICKS, absolute, debtBefore, 0.0f,
                "creative with the Britannia pickaxe on bricks");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The decision is live state, never remembered                       */
    /* ------------------------------------------------------------------ */

    /**
     * Nothing is decided at the first swing and carried to the break. A survival dig that was
     * permitted to start completes as a plain creative removal if its digger switched to creative
     * and put the pickaxe away; the same player picking the pickaxe back up is a tester on the
     * next swing; and a tester refused for skill becomes a builder the moment the hand is empty.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theDecisionIsReadFreshAtEverySwingAndEveryBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block silver = BlockRegistry.SILVER_ORE.get();
        Rig rig = join(level, "mode-switcher", GameType.SURVIVAL).mining(100.0f).holding(pickaxe());

        // A permitted survival start...
        BlockPos absolute = plant(helper, rig, silver);
        check(!firstSwingRefused(rig, absolute), "precondition: a qualified survival start is permitted");
        // ...completed by a creative player with an empty hand.
        rig.mode(GameType.CREATIVE).holding(ItemStack.EMPTY);
        rig.said();
        int debtBefore = debtCount(level);
        rig.player().gameMode.destroyBlock(absolute);
        assertPlainCreativeRemoval(helper, rig, silver, absolute, debtBefore, 100.0f,
                "a dig started in survival and completed in creative bare-handed");

        // The same player, pickaxe back in hand: a tester, paid and filed.
        rig.holding(pickaxe());
        absolute = plant(helper, rig, silver);
        debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).isAir()
                        && takeDrops(level, absolute).stream().anyMatch(s -> s.getItem() instanceof PurityOreItem)
                        && debtCount(level) == debtBefore + 1,
                "picking the pickaxe back up did not make the creative player a tester");
        BrokenBlockDataStorage.get(level).remove(absolute);

        // A tester refused for skill, then the hand emptied: the next swing breaks plainly.
        rig.mining(0.0f);
        absolute = plant(helper, rig, silver);
        swing(rig, absolute);
        check(level.getBlockState(absolute).is(silver), "precondition: the tester is refused at 0.0");
        rig.holding(ItemStack.EMPTY);
        rig.said();
        // The refusal just above legitimately recorded a denial; the plain removal must not add one.
        String recorded = MiningBreakGate.lastDenialKey(rig.player());
        debtBefore = debtCount(level);
        swing(rig, absolute);
        check(level.getBlockState(absolute).isAir(),
                "emptying the hand after a refusal did not return the creative player to plain breaking");
        check(takeDrops(level, absolute).isEmpty() && debtCount(level) == debtBefore,
                "the plain removal after a refusal dropped or filed something");
        check(rig.said().isEmpty(), "the plain removal after a refusal was spoken to");
        check(MiningBreakGate.lastDenialKey(rig.player()).equals(recorded),
                "the plain removal after a refusal recorded a new denial");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Survival and adventure are untouched                               */
    /* ------------------------------------------------------------------ */

    /**
     * The controls, and the proof that the chat capture works: a survival player without the
     * pickaxe is refused at the first swing and told so in as many words, one below the
     * requirement is refused for skill, and a qualified one extracts. Adventure, pickaxe and
     * predicate in hand, extracts too; adventure bare-handed is refused.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void survivalAndAdventureKeepTheirRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block silver = BlockRegistry.SILVER_ORE.get();
        Rig rig = join(level, "survival-control", GameType.SURVIVAL).mining(100.0f).holding(ItemStack.EMPTY);

        // Bare-handed: WRONG_TOOL at the first swing, spoken, and the block stands.
        BlockPos absolute = plant(helper, rig, silver);
        check(firstSwingRefused(rig, absolute), "a survival player without the pickaxe was not refused");
        List<String> said = rig.said();
        check(said.contains(MINING_WRONG_TOOL),
                "the wrong-tool refusal was not spoken (or the chat capture is broken): " + said);
        check(!rig.player().gameMode.destroyBlock(absolute) && level.getBlockState(absolute).is(silver),
                "a survival player without the pickaxe broke silver");

        // Under-skilled with the pickaxe: INSUFFICIENT_SKILL, spoken, and the block stands.
        rig.holding(pickaxe()).mining(0.0f);
        absolute = plant(helper, rig, silver);
        check(firstSwingRefused(rig, absolute), "an under-skilled survival miner was not refused");
        said = rig.said();
        check(said.contains(INSUFFICIENT), "the skill refusal was not spoken: " + said);
        check(!rig.player().gameMode.destroyBlock(absolute) && level.getBlockState(absolute).is(silver),
                "an under-skilled survival miner broke silver");

        // Qualified: the managed extraction.
        rig.mining(100.0f);
        absolute = plant(helper, rig, silver);
        check(!firstSwingRefused(rig, absolute), "a qualified survival miner was refused at the first swing");
        int debtBefore = debtCount(level);
        rig.player().gameMode.destroyBlock(absolute);
        check(level.getBlockState(absolute).isAir(), "a qualified survival miner did not deplete silver");
        List<ItemStack> drops = takeDrops(level, absolute);
        check(drops.size() == 1 && drops.get(0).getItem() instanceof PurityOreItem,
                "a qualified survival miner was not paid exactly one purity ore: " + drops);
        check(debtCount(level) == debtBefore + 1, "a survival extraction did not file its restoration debt");
        BrokenBlockDataStorage.get(level).remove(absolute);

        // Adventure with the real pickaxe: permitted and extracted; bare-handed: refused.
        rig.mode(GameType.ADVENTURE).holding(adventurePickaxe());
        absolute = plant(helper, rig, silver);
        check(!firstSwingRefused(rig, absolute), "a qualified adventure miner was refused at the first swing");
        debtBefore = debtCount(level);
        rig.player().gameMode.destroyBlock(absolute);
        check(level.getBlockState(absolute).isAir(), "a qualified adventure miner did not deplete silver");
        drops = takeDrops(level, absolute);
        check(drops.size() == 1 && drops.get(0).getItem() instanceof PurityOreItem,
                "a qualified adventure miner was not paid exactly one purity ore: " + drops);
        check(debtCount(level) == debtBefore + 1, "an adventure extraction did not file its restoration debt");
        BrokenBlockDataStorage.get(level).remove(absolute);

        rig.holding(ItemStack.EMPTY);
        absolute = plant(helper, rig, silver);
        check(firstSwingRefused(rig, absolute), "an adventure player without the pickaxe was not refused");
        check(!rig.player().gameMode.destroyBlock(absolute) && level.getBlockState(absolute).is(silver),
                "an adventure player without the pickaxe broke silver");
        helper.succeed();
    }
}

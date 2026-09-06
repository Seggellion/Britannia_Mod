package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.grabbyhands.blockentity.GrabbyPlacedItemBlockEntity;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.GrabbyRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The gesture through the server's own packet handler, not through a hand-posted event.
 *
 * <h2>The coverage gap this file closes</h2>
 *
 * <p>{@link GrabbyInteractionPathGameTests} calls {@code CommonHooks.onRightClickBlock} directly. It
 * therefore starts one layer <em>below</em> where a real interaction starts, and every gate above
 * that point is untested: spawn protection, the world border, server-side reach, build height, and
 * the pending-teleport guard. All five live in
 * {@code ServerGamePacketListenerImpl.handleUseItemOn}, all five drop the packet without a word to
 * the player or the log, and when any of them trips
 * {@code PlayerInteractEvent.RightClickBlock} is never posted at all — so
 * {@code GrabbyInteractionHandler} cannot run, cannot refuse, and cannot explain itself. That is
 * exactly what "Grabby Hands does nothing on the server" looks like from the outside, and no test
 * that posts the event itself can ever produce it.
 *
 * <p>These tests feed a real {@code ServerboundUseItemOnPacket} into a real
 * {@code ServerGamePacketListenerImpl} belonging to a real joined {@code ServerPlayer}, and assert
 * on the world and the inventory rather than on a return value — because the packet handler returns
 * nothing, which is the whole problem.
 *
 * <h2>What this still does not prove</h2>
 *
 * <p>It runs on a {@code GameTestServer}, which extends {@code MinecraftServer}, not
 * {@code DedicatedServer}. {@code MinecraftServer.isUnderSpawnProtection} is a bare
 * {@code return false}, so the one gate that behaves differently on a dedicated server is
 * structurally unreachable from here and always answers "allowed". That rule is pinned in
 * {@code GrabbySpawnProtectionTest} instead, and reported live by {@code /grabby env}. Nothing in
 * this repository can substitute for a real dedicated server with a real remote client.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrabbyServerPacketPathGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Enough to cover any plausible teleport counter without ever repeating an id. */
    private static final int TELEPORT_ACK_ATTEMPTS = 8;

    private static int sequence = 1;

    private GrabbyServerPacketPathGameTests() {
    }

    /**
     * Throws {@link GameTestAssertException} and nothing else, for the reason documented at length in
     * {@link GrabbyInteractionPathGameTests}: any other throwable escaping a sequence callback ends
     * the entire GameTest run rather than one test.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /**
     * An ordinary Adventure-mode player, joined properly, standing where they can reach {@code standAt}.
     *
     * <p>Two details matter and neither is cosmetic.
     *
     * <p>First, the player must be joined with {@code placeNewPlayer} so that {@code player.connection}
     * is a real {@code ServerGamePacketListenerImpl}; a mock player has none, which is why no existing
     * test could reach the packet layer.
     *
     * <p>Second, {@code placeNewPlayer} ends by calling {@code connection.teleport(...)}, which arms
     * {@code awaitingPositionFromClient}. Until a client acknowledges that teleport, {@code
     * handleUseItemOn} silently ignores <em>every</em> use packet. There is no client here to
     * acknowledge it, so the test has to — and a rig that skipped this step would report the feature
     * as broken for a reason that has nothing to do with the feature. Teleporting to the standing
     * position first means the acknowledgement lands the player exactly where the test wants them.
     */
    private static ServerPlayer joinedAdventurePlayer(GameTestHelper helper, BlockPos standAt) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "grabby-net-" + UUID.randomUUID().toString().substring(0, 8)),
                false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);

        player.setGameMode(GameType.ADVENTURE);
        player.getAbilities().mayBuild = false;
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();

        player.connection.teleport(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0.0F, 0.0F);
        acknowledgePendingTeleport(player);
        return player;
    }

    /**
     * Clears {@code awaitingPositionFromClient} without knowing the teleport id.
     *
     * <p>Each id is sent at most once. A non-matching id is a no-op; the one that matches clears the
     * wait. Sending a matching id twice would disconnect the player as an invalid movement, which is
     * why this counts up rather than retrying.
     */
    private static void acknowledgePendingTeleport(ServerPlayer player) {
        for (int id = 1; id <= TELEPORT_ACK_ATTEMPTS; id++) {
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
        }
    }

    /**
     * Sends the packet the client sends: main hand, top face, one sequence number.
     *
     * <p>Deliberately returns nothing. {@code handleUseItemOn} returns nothing either, and pretending
     * otherwise is how the previous test rig ended up asserting on a value production never sees.
     */
    private static void useItemOnTopOf(ServerPlayer player, BlockPos pos) {
        useItemOnTopOf(player, pos, InteractionHand.MAIN_HAND);
    }

    private static void useItemOnTopOf(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false);
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(hand, hit, sequence++));
    }

    /**
     * One empty-handed sneak-click, as the client actually transmits it: two packets, not one.
     *
     * <p>{@code Minecraft.startUseItem} loops over both hands and stops at the first whose
     * <em>client-side</em> prediction consumes the action. With both hands empty nothing consumes:
     * NeoForge's patched {@code MultiPlayerGameMode.performUseItemOn} computes
     * {@code flag1 = isSecondaryUseActive() && !doesSneakBypassUse(...)} as true for two empty
     * hands, so the block-use branch is skipped and the item branch returns {@code PASS}. The client
     * then tries the off hand and sends a second {@code ServerboundUseItemOnPacket}.
     *
     * <p>So the pickup gesture always arrives at the server twice. That is the reason
     * {@code GrabbyInteractionHandler} filters on {@code MAIN_HAND}, and the reason this rig sends
     * both packets: a test that sent only the main hand could not tell a correct single transaction
     * apart from one that runs twice.
     */
    private static void sneakEmptyHandedClickOnTopOf(ServerPlayer player, BlockPos pos) {
        useItemOnTopOf(player, pos, InteractionHand.MAIN_HAND);
        useItemOnTopOf(player, pos, InteractionHand.OFF_HAND);
    }

    private static void disconnect(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /**
     * A solid floor cell to build on. Returns the absolute position of the floor block itself.
     *
     * <p>Every test in this file uses the same cell, deliberately. {@code service_npc_spawn_test_empty}
     * is 7x5x7, and each {@code @GameTest} is given its own instance of it, so sharing coordinates
     * costs nothing. Spreading tests along the X axis instead is what puts writes outside the
     * structure and into whichever instance the runner laid down next - which is a flaky test that
     * fails depending on batch order rather than on the code under test.
     */
    private static BlockPos floorAt(GameTestHelper helper, int x, int z) {
        BlockPos floor = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        return floor;
    }

    private static void emptyHandsAndSneak(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
    }

    private static int countInInventory(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Moves the one chair the test owns into the main hand, wherever the pickup happened to put it.
     *
     * <p>Removing it from its slot rather than copying it is the point: the chair the player places
     * on lap two has to be the chair they picked up on lap one, or a duplication bug would place a
     * copy and leave the original sitting in the pack.
     */
    private static boolean moveOnlyChairToMainHand(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ItemRegistry.WOODEN_CHAIR_ITEM.get())) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return true;
            }
        }
        return false;
    }

    private static List<ItemEntity> droppedAround(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4.0));
    }

    // ------------------------------------------------------------------
    // The production acceptance case, ten times over
    // ------------------------------------------------------------------

    /**
     * The exact reported scenario: Adventure, non-operator, both hands empty, sneaking, a chair.
     *
     * <p>Ten round trips rather than one, because the failure mode a single round trip cannot see is
     * duplication — a chair that comes back as an item without leaving the world, or a stack that
     * grows. The inventory count and the floor are both asserted every lap.
     */
    @GameTest(template = TEMPLATE)
    public static void aChairIsPlacedAndTakenBackTenTimesThroughTheServerPacketPath(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos target = floor.above();
        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            check(!player.hasPermissions(2), "the test player is an operator, which is not the reported case");
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "the test player is not in Adventure mode");

            // Exactly one chair enters the test, and the same one has to survive all ten laps. Handing
            // out a fresh chair each lap would hide the duplication this test exists to catch.
            player.getInventory().add(new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get()));

            for (int lap = 1; lap <= 10; lap++) {
                check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 1,
                        "lap " + lap + ": the pack should hold exactly one chair before placing, held "
                                + countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()));
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                check(moveOnlyChairToMainHand(player), "lap " + lap + ": the chair vanished from the pack");

                useItemOnTopOf(player, floor);
                check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                        "lap " + lap + ": the chair was not placed through the packet path");
                check(GrabbyProvenanceAccess.grabbyManaged(helper.getLevel(), target),
                        "lap " + lap + ": the placed chair carries no player provenance, so it can never come back");

                emptyHandsAndSneak(player);
                sneakEmptyHandedClickOnTopOf(player, target);

                check(helper.getLevel().getBlockState(target).isAir(),
                        "lap " + lap + ": the chair is still standing after the pickup gesture");
                check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 1,
                        "lap " + lap + ": expected exactly one chair in the pack, found "
                                + countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()));
                check(droppedAround(helper, target).isEmpty(),
                        "lap " + lap + ": the pickup also dropped the chair on the floor");
            }
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The other Grabby categories, so this is not a chair-only claim
    // ------------------------------------------------------------------

    /** A metadata-bearing object: the bottle must come back carrying its wine. */
    @GameTest(template = TEMPLATE)
    public static void aWineBottleKeepsItsWineThroughTheServerPacketPath(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos target = floor.above();
        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            ItemStack bottle = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
            WineBottleBlockItem.setWineData(bottle, "Britannia Vineyards", "Verdant", 271, 88, "Yew", "red");
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);

            useItemOnTopOf(player, floor);
            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get()),
                    "the bottle was not placed through the packet path");

            emptyHandsAndSneak(player);
            sneakEmptyHandedClickOnTopOf(player, target);

            check(helper.getLevel().getBlockState(target).isAir(), "the bottle survived its own pickup");
            ItemStack recovered = player.getInventory().getItem(0);
            check(recovered.is(ItemRegistry.WINE_BOTTLE_GREEN.get()),
                    "the bottle did not come back; slot 0 held " + recovered);
            WineData wine = WineBottleBlockItem.getWineData(recovered);
            check("Britannia Vineyards".equals(wine.wineryName()) && wine.year() == 271 && wine.quality() == 88,
                    "the recovered bottle lost its wine data: " + wine);
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /** The generic host: an item with no block form of its own, set down and taken back. */
    @GameTest(template = TEMPLATE)
    public static void aLooseItemRoundTripsThroughTheServerPacketPath(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos target = floor.above();
        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.CHEESE.get()));

            useItemOnTopOf(player, floor);
            check(helper.getLevel().getBlockState(target).is(GrabbyRegistry.PLACED_ITEM.get()),
                    "the loose-item host was not placed through the packet path");
            check(helper.getLevel().getBlockEntity(target) instanceof GrabbyPlacedItemBlockEntity host
                            && host.grabbyPayload().is(ItemRegistry.CHEESE.get()),
                    "the host did not take the cheese as its payload");

            emptyHandsAndSneak(player);
            sneakEmptyHandedClickOnTopOf(player, target);

            check(helper.getLevel().getBlockState(target).isAir(), "the host survived its own pickup");
            check(countInInventory(player, ItemRegistry.CHEESE.get()) == 1,
                    "the cheese did not come back exactly once");
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Negative cases: the gesture, and the protection
    // ------------------------------------------------------------------

    /** Standing up is not the pickup gesture, on this path as on every other. */
    @GameTest(template = TEMPLATE)
    public static void notSneakingTakesNothingThroughTheServerPacketPath(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos target = floor.above();
        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get()));
            useItemOnTopOf(player, floor);
            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "the chair was not placed");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            useItemOnTopOf(player, target);

            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "an upright empty-handed click carried the chair off");
            check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 0,
                    "an upright empty-handed click put a chair in the pack");
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * Scenery is still scenery on this path.
     *
     * <p>The packet path is a wider door than the event path, so the protection claim has to be
     * re-made here rather than inherited. A chair the world placed carries no provenance and must
     * refuse every ordinary player, however they reach it.
     */
    @GameTest(template = TEMPLATE)
    public static void worldPlacedSceneryRefusesTheServerPacketPath(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos scenery = floor.above();
        helper.getLevel().setBlockAndUpdate(scenery, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());

        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            check(!GrabbyProvenanceAccess.grabbyManaged(helper.getLevel(), scenery),
                    "the scenery chair was already marked player-placed, so this proves nothing");

            emptyHandsAndSneak(player);
            sneakEmptyHandedClickOnTopOf(player, scenery);

            check(helper.getLevel().getBlockState(scenery).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "a chair the world placed was carried off through the packet path");
            check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 0,
                    "scenery arrived in an ordinary player's pack");
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * The production reproduction, exactly: the off hand was not empty.
     *
     * <p>This is what "Grabby Hands does nothing on the dedicated server" actually was. The player
     * emptied the hand they could see, sneaked, and clicked their own chair. The off hand held
     * something they had stopped noticing, so {@code isPickupGesture} was false, {@code isAxeGesture}
     * was false, {@code handlePlacement} found an empty main hand and returned - and the whole
     * interaction produced no message, no log line and no change. Emptying the off hand fixed it.
     *
     * <p>The rule is not relaxed here and this test does not ask for it to be: it pins that an
     * occupied off hand still takes nothing, and that the identical gesture one item later works.
     * The behaviour change that accompanies it is that the near miss now speaks
     * ({@code message.britannia_mod.grabby.pickup.off_hand_occupied}), which a GameTest cannot
     * observe - {@code GrabbyGestureTest} covers the rule that decides when it is said.
     */
    @GameTest(template = TEMPLATE)
    public static void anOccupiedOffHandTakesNothingAndEmptyingItWorks(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos target = floor.above();
        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get()));
            useItemOnTopOf(player, floor);
            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "the chair was not placed");
            check(GrabbyProvenanceAccess.grabbyManaged(helper.getLevel(), target),
                    "the placed chair carries no player provenance, which would confound this test");

            // The reported state: main hand empty, sneaking, something in the off hand.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
            player.setShiftKeyDown(true);
            sneakEmptyHandedClickOnTopOf(player, target);

            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "an occupied off hand picked the chair up; the gesture rule has changed");
            check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 0,
                    "the chair reached the pack despite the occupied off hand");

            // One item later, nothing else different.
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            sneakEmptyHandedClickOnTopOf(player, target);

            check(helper.getLevel().getBlockState(target).isAir(),
                    "emptying the off hand did not make the documented gesture work");
            check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 1,
                    "the chair did not come back exactly once");
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * Scenery refuses an administrator too, which rules out one theory of the production report.
     *
     * <h2>What this test was written to prove, and what it actually proved</h2>
     *
     * <p>It was written expecting the opposite. {@code GrabbyPolicy.mayMutate} contains
     * {@code if (!grabbyManaged) return administrator}, so a creative or operator-level-2 player
     * appeared able to move authored scenery - which would have neatly explained why single-player
     * testing passed while an ordinary Adventure player on the dedicated server was refused.
     *
     * <p>It does not. Both live transactions check provenance <em>before</em> they consult policy:
     * {@code GrabbyPickupTransaction} returns {@code NOT_GRABBY_MANAGED} and
     * {@code GrabbyDestructionTransaction} returns its equivalent, several statements above the
     * {@code mayMutate} call. That administrator clause is unreachable from either path, and nothing
     * in production calls the world-aware {@code mayMove}/{@code mayDestroy} entry points that could
     * reach it. {@code GrabbyPolicyTest} exercises the rule as a function, which is why the gap was
     * not visible there.
     *
     * <p>So the protection is stronger than the policy function alone suggests, and "the tester was
     * an operator" cannot by itself explain a chair that works in single player and not on the
     * server. The one administrator difference that <em>is</em> reachable on the pickup path is
     * {@code insideForeignStructure}, covered as a rule by
     * {@code GrabbyPolicyTest.insideSomebodyElsesStructureOrdinaryPlayersAreRefusedButStaffAreNot}.
     */
    @GameTest(template = TEMPLATE)
    public static void sceneryRefusesEvenAnAdministratorThroughTheServerPacketPath(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos scenery = floor.above();
        helper.getLevel().setBlockAndUpdate(scenery, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());

        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            check(!GrabbyProvenanceAccess.grabbyManaged(helper.getLevel(), scenery),
                    "the scenery chair was already player-placed, so this proves nothing");

            // The single-player tester's standing, and nothing else about the scenario changes.
            player.setGameMode(GameType.CREATIVE);
            check(player.isCreative(), "the test player did not actually become an administrator");

            emptyHandsAndSneak(player);
            sneakEmptyHandedClickOnTopOf(player, scenery);

            check(helper.getLevel().getBlockState(scenery).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "an administrator carried authored scenery off through Grabby Hands; the "
                            + "provenance-before-policy ordering in GrabbyPickupTransaction has been "
                            + "changed and scenery protection is now weaker than it was");
            check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 0,
                    "scenery arrived in an administrator's pack");
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * A refusal from the packet layer is completely silent, which is the defect class itself.
     *
     * <p>Out of reach is the one packet-layer gate this rig can actually trip — spawn protection
     * needs a {@code DedicatedServer} and the world border cannot be moved without disturbing every
     * other test. The point is not the reach rule; it is that {@code handleUseItemOn} drops the
     * packet with no event, no refusal message and no log line, so the player sees a dead feature.
     * Any future gate added above the event bus will fail this test's shape rather than production.
     */
    @GameTest(template = TEMPLATE)
    public static void aPacketLayerRefusalNeverReachesGrabbyAndSaysNothing(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 3, 3);
        BlockPos target = floor.above();
        ServerPlayer player = joinedAdventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.WOODEN_CHAIR_ITEM.get()));
            useItemOnTopOf(player, floor);
            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "the chair was not placed while the player was in range");

            // Same player, same chair, same gesture - only the distance changes.
            BlockPos faraway = floor.offset(40, 0, 40);
            player.connection.teleport(faraway.getX() + 0.5, faraway.getY(), faraway.getZ() + 0.5, 0.0F, 0.0F);
            acknowledgePendingTeleport(player);
            check(!player.canInteractWithBlock(target, 1.0),
                    "the player is still within reach, so this test proves nothing");

            emptyHandsAndSneak(player);
            sneakEmptyHandedClickOnTopOf(player, target);

            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "an out-of-reach pickup took the chair anyway");
            check(countInInventory(player, ItemRegistry.WOODEN_CHAIR_ITEM.get()) == 0,
                    "an out-of-reach pickup filled the pack");
        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }
}

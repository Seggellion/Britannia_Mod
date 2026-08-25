package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The gesture, through the real event, end to end.
 *
 * <h2>Why this file exists</h2>
 *
 * <p>Grabby Hands shipped with 27 GameTests and 240 unit tests, and every one of them calls a
 * transaction directly. Nothing exercised {@code GrabbyInteractionHandler} — the code that decides,
 * from a real {@code PlayerInteractEvent.RightClickBlock}, which transaction a click even means. A
 * feature can therefore be entirely unusable in game while its whole test suite is green, which is
 * exactly the shape of the "grabby hands is completely broken" report: the transactions were fine
 * and the loose-item host was simply never enrolled, so pickup refused it and said nothing.
 *
 * <p>These tests post the event the server itself posts, through
 * {@code CommonHooks.onRightClickBlock}, so the handler's own registration, priority, hand filter
 * and gesture rules are all in the path. A gesture regression fails here rather than in production.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrabbyInteractionPathGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";


    private GrabbyInteractionPathGameTests() {
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException} or
     * {@link AssertionError}. When a check runs inside a {@code succeedWhen} or sequence callback --
     * directly or through any helper called from one -- {@code GameTestSequence.tickAndContinue}
     * swallows only that one type, which is how a polled condition retries until it holds.
     * {@code GameTestInfo} ticks its sequences outside any try/catch, so anything else escapes into
     * the server tick loop and crashes the whole GameTest server, ending the run and every result in
     * it. {@code AssertionError} is worse still: being an Error rather than an Exception, it is not
     * caught by the {@code catch (Exception)} that guards a test body either.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /**
     * An ordinary Adventure-mode player: not creative, not an operator, and unable to build.
     *
     * <p>Hand-built rather than {@code makeMockServerPlayerInLevel()}, which hard-codes
     * {@code isCreative()} to true and would make every policy question answer "administrator".
     */
    private static ServerPlayer adventurePlayer(GameTestHelper helper, BlockPos standAt) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "grabby-path-" + UUID.randomUUID().toString().substring(0, 8)),
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
        // Beside the target, never on it: BlockItem.place runs isUnobstructed, and a player standing
        // in the destination legitimately blocks their own placement.
        player.setPos(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        return player;
    }

    /**
     * Sends the mock player home again.
     *
     * <p>A player joined with {@code placeNewPlayer} stays on the server for the rest of the run,
     * and a player keeps the chunks around them loaded. Four of them left standing changes chunk
     * residency and the per-tick player loop for every test that runs afterwards -- which is not
     * hypothetical: a pre-existing silica test asserts its own chunks are already loaded, and
     * pre-existing banking rigs are sensitive to what else is being ticked. Leaving no trace is
     * this file's business, not theirs.
     */
    private static void disconnect(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** A solid floor cell to build on. Returns the absolute position of the floor block itself. */
    private static BlockPos floorAt(GameTestHelper helper, int x, int z) {
        BlockPos floor = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        return floor;
    }

    /**
     * Exactly what {@code ServerPlayerGameMode.useItemOn} posts, on the same bus, for the main hand.
     *
     * @return whether the interaction was consumed, which is what the server reads back
     */
    private static boolean rightClickTopOf(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event =
                CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        return event.isCanceled();
    }

    // ------------------------------------------------------------------
    // The production acceptance case: a wine bottle set down and taken back
    // ------------------------------------------------------------------

    /**
     * Place a wine bottle on the ground, then pick it back up with the documented gesture.
     *
     * <p>Both halves go through the event, and the bottle carries wine state, so this also holds
     * the state-preservation contract at the level a player actually experiences it.
     */
    @GameTest(template = TEMPLATE)
    public static void aWineBottleIsSetDownAndTakenBackThroughTheRealGesture(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 2, 2);
        BlockPos target = floor.above();
        ServerPlayer player = adventurePlayer(helper, floor.offset(2, 0, 2));
        try {

            ItemStack bottle = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
            WineBottleBlockItem.setWineData(bottle, "Britannia Vineyards", "Verdant", 271, 88, "Yew", "red");
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);

            check(rightClickTopOf(player, floor), "the placement gesture was not consumed by Grabby Hands");
            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get()),
                    "right-clicking the ground with a wine bottle did not place it");
            check(GrabbyProvenanceAccess.grabbyManaged(helper.getLevel(), target),
                    "the placed bottle was not marked player-placed, so it can never be picked up again");

            // The documented pickup gesture: sneak, both hands empty.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);

            check(rightClickTopOf(player, target), "the pickup gesture was not consumed by Grabby Hands");
            check(helper.getLevel().getBlockState(target).isAir(),
                    "the bottle is still standing there after a pickup");

            ItemStack recovered = player.getInventory().getItem(0);
            check(recovered.is(ItemRegistry.WINE_BOTTLE_GREEN.get()),
                    "the bottle did not come back as an item; inventory slot 0 held " + recovered);
            WineData wine = WineBottleBlockItem.getWineData(recovered);
            check("Britannia Vineyards".equals(wine.wineryName()) && wine.year() == 271 && wine.quality() == 88,
                    "the recovered bottle lost its wine data: " + wine);

        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * A loose item set down on the generic host, and taken back.
     *
     * <p>This is the case that was broken: {@code britannia_mod:grabby_placed_item} was not in
     * {@code grabby_movable}, so the pickup refused it as {@code TYPE_NOT_ENROLLED} — silently,
     * because refusals said nothing. Setting an item down was a one-way trip.
     */
    @GameTest(template = TEMPLATE)
    public static void aLooseItemIsSetDownOnTheHostAndTakenBack(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 5, 2);
        BlockPos target = floor.above();
        ServerPlayer player = adventurePlayer(helper, floor.offset(2, 0, 2));
        try {

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.CHEESE.get()));

            check(rightClickTopOf(player, floor), "the host placement gesture was not consumed");
            check(helper.getLevel().getBlockState(target).is(GrabbyRegistry.PLACED_ITEM.get()),
                    "right-clicking the ground with cheese did not place the loose-item host");
            check(helper.getLevel().getBlockEntity(target) instanceof GrabbyPlacedItemBlockEntity host
                            && host.grabbyPayload().is(ItemRegistry.CHEESE.get()),
                    "the host did not take the cheese as its payload");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);

            check(rightClickTopOf(player, target), "the loose-item pickup gesture was not consumed");
            check(helper.getLevel().getBlockState(target).isAir(), "the host survived its own pickup");
            check(player.getInventory().contains(new ItemStack(ItemRegistry.CHEESE.get())),
                    "the cheese did not come back");

        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * Not sneaking is not the pickup gesture, and must leave the object where it is.
     *
     * <p>The gesture rules only matter if the wrong one does nothing. An empty-handed click that
     * silently removed furniture would be a far worse defect than one that refuses.
     */
    @GameTest(template = TEMPLATE)
    public static void anEmptyHandedClickWithoutSneakingTakesNothing(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 8, 2);
        BlockPos target = floor.above();
        ServerPlayer player = adventurePlayer(helper, floor.offset(2, 0, 2));
        try {

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get()));
            check(rightClickTopOf(player, floor), "the placement gesture was not consumed");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(false);
            rightClickTopOf(player, target);

            check(helper.getLevel().getBlockState(target).is(BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get()),
                    "an ordinary empty-handed click picked the bottle up");

        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }

    /**
     * Scenery is not pickable, through the gesture, by an ordinary player.
     *
     * <p>The protection claim is only worth as much as the path it is asserted on. This is the same
     * refusal {@code sceneryStaysImmovableForEveryone} makes against the transaction, made here
     * against the gesture, so widening the gesture cannot quietly widen the protection hole.
     */
    @GameTest(template = TEMPLATE)
    public static void worldPlacedSceneryRefusesTheGesture(GameTestHelper helper) {
        BlockPos floor = floorAt(helper, 11, 2);
        BlockPos scenery = floor.above();
        helper.getLevel().setBlockAndUpdate(scenery, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());

        ServerPlayer player = adventurePlayer(helper, floor.offset(2, 0, 2));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);

            rightClickTopOf(player, scenery);
            check(helper.getLevel().getBlockState(scenery).is(BlockRegistry.WOODEN_CHAIR.get()),
                    "a chair the world placed was carried off by an ordinary player");

        } finally {
            disconnect(helper, player);
        }
        helper.succeed();
    }
}

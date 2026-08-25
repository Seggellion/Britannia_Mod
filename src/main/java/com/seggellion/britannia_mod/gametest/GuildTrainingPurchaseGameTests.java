package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.economy.CoinConversion;
import com.seggellion.britannia_mod.economy.MerchantEconomyService;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.guild.GuildTrainingService;
import com.seggellion.britannia_mod.service.guild.GuildTrainingWriteResult;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Guildmaster milestone 5's money-at-stake paths.
 *
 * <p>The pricing and charge <em>decisions</em> are already covered by {@code GuildTrainingQuoteTest}
 * and {@code GuildTrainingChargeTest}, which are pure. What those cannot reach is the wiring
 * between them — whether a refusal, a transport failure or a short grant actually results in the
 * right number of coins leaving a real inventory. That is what this asserts, through the
 * {@code useClientForTesting} seam, so no socket is ever opened.
 *
 * <p>Every case here answers the same question: after this outcome, how much gold is left?
 *
 * <p>Timing note: {@code purchase} completes its future onto the server thread via
 * {@code server.execute(...)}, so assertions run a tick later through
 * {@code helper.runAfterDelay(...)} rather than immediately.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GuildTrainingPurchaseGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String TYPE_KEY = "warrior_guildmaster";
    private static final String SKILL = "swordsmanship";
    private static final int START_GOLD = 500;

    private GuildTrainingPurchaseGameTests() {
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void aRefusalChargesNothing(GameTestHelper helper) {
        run(helper, new GuildTrainingWriteResult.Rejected("skill_not_taught"), START_GOLD,
                "a refusal must never take coins");
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void aTransportFailureChargesNothing(GameTestHelper helper) {
        // Rails may or may not have committed. Charging would risk taking gold for nothing, so the
        // ordering fails toward the player keeping it.
        run(helper, new GuildTrainingWriteResult.TransportFailure("timeout"), START_GOLD,
                "a transport failure must never take coins");
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void aReplayChargesNothing(GameTestHelper helper) {
        // ALREADY_APPLIED decodes to Applied with a zero grant: this process cannot know whether
        // the original attempt took the coins, so it does not take them again.
        run(helper, new GuildTrainingWriteResult.Applied(0, 0, 0.0D), START_GOLD,
                "a replay must never take coins twice");
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void aGrantChargesExactlyWhatRailsGranted(GameTestHelper helper) {
        // Rails clamped to real headroom: 40 granted, not whatever the quote asked for. Charging
        // the request would bill for training never given.
        run(helper, new GuildTrainingWriteResult.Applied(40, 40, 4.0D), START_GOLD - 40,
                "the charge must follow the granted amount");
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void aGrantBeyondThePurseIsAbandoned(GameTestHelper helper) {
        // The player cannot cover what Rails granted, so nothing is taken rather than draining
        // whatever remains.
        run(helper, new GuildTrainingWriteResult.Applied(9_000, 9_000, 40.0D), START_GOLD,
                "an unaffordable grant must take nothing at all");
    }

    // ---------- Milestone 7 item 5: transaction isolation ----------

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void aDoubleClickSubmitsOnlyOnePurchase(GameTestHelper helper) {
        withRegistry(() -> {
            java.util.concurrent.atomic.AtomicInteger submissions =
                    new java.util.concurrent.atomic.AtomicInteger();
            // Deliberately a future that never completes, modelling the real client: an HTTP call
            // resolves on another thread some ticks later. An already-completed future would fire
            // whenComplete inline, and MinecraftServer.execute runs inline when already on the
            // server thread, so the in-flight window would collapse to zero and this would pass
            // for the wrong reason - it would be measuring timing rather than the guard.
            GuildTrainingService.useClientForTesting((server, request) -> {
                submissions.incrementAndGet();
                return new CompletableFuture<>();
            });

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ServiceNpcEntity guildmaster = guildmaster(helper);
            player.moveTo(guildmaster.getX(), guildmaster.getY(), guildmaster.getZ());
            giveGold(player, START_GOLD);

            GuildTrainingService.purchase(player, guildmaster, SKILL);
            GuildTrainingService.purchase(player, guildmaster, SKILL);

            // One submission is the guard's actual contract. Each purchase mints its own
            // idempotency key, so a second submission is not a retry Rails would deduplicate -
            // it is a distinct purchase it would correctly bill for.
            check(submissions.get() == 1,
                    "a double click must submit one purchase, submitted " + submissions.get());
            check(goldOf(player) == START_GOLD,
                    "nothing may be charged before Rails answers (found " + goldOf(player) + " gold)");
            GuildTrainingService.resetClientForTesting();
            helper.succeed();
        });
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 100)
    public static void oneBuyerDoesNotBlockAnother(GameTestHelper helper) {
        withRegistry(() -> {
            GuildTrainingService.useClientForTesting((server, request) ->
                    CompletableFuture.completedFuture(new GuildTrainingWriteResult.Applied(40, 40, 4.0D)));

            ServiceNpcEntity guildmaster = guildmaster(helper);
            ServerPlayer first = helper.makeMockServerPlayerInLevel();
            ServerPlayer second = helper.makeMockServerPlayerInLevel();
            for (ServerPlayer player : List.of(first, second)) {
                player.moveTo(guildmaster.getX(), guildmaster.getY(), guildmaster.getZ());
                giveGold(player, START_GOLD);
            }

            // The guard is keyed on the player, not held globally: one buyer in flight must never
            // stop a different buyer from training at the same Guildmaster.
            GuildTrainingService.purchase(first, guildmaster, SKILL);
            GuildTrainingService.purchase(second, guildmaster, SKILL);

            helper.runAfterDelay(3L, () -> {
                check(goldOf(first) == START_GOLD - 40,
                        "the first buyer was not charged (found " + goldOf(first) + " gold)");
                check(goldOf(second) == START_GOLD - 40,
                        "the second buyer was blocked by the first (found " + goldOf(second) + " gold)");
                GuildTrainingService.resetClientForTesting();
                helper.succeed();
            });
        });
    }

    // ---------- Harness ----------

    private static void run(
            GameTestHelper helper, GuildTrainingWriteResult outcome, int expectedGoldAfter, String message
    ) {
        withRegistry(() -> {
            GuildTrainingService.useClientForTesting(
                    (server, request) -> CompletableFuture.completedFuture(outcome));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ServiceNpcEntity guildmaster = guildmaster(helper);
            // Adjacent, so GuildmasterProxyService.resolve's distance check passes.
            player.moveTo(guildmaster.getX(), guildmaster.getY(), guildmaster.getZ());

            giveGold(player, START_GOLD);
            check(goldOf(player) == START_GOLD, "setup did not give the player its starting gold");

            GuildTrainingService.purchase(player, guildmaster, SKILL);

            // purchase() marshals its completion through server.execute, so the charge lands on a
            // later tick than this call.
            helper.runAfterDelay(3L, () -> {
                int actual = goldOf(player);
                check(actual == expectedGoldAfter,
                        message + " (expected " + expectedGoldAfter + " gold, found " + actual + ")");
                GuildTrainingService.resetClientForTesting();
                helper.succeed();
            });
        });
    }

    private static ServiceNpcEntity guildmaster(GameTestHelper helper) {
        ServiceNpcEntity entity = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
        entity.setWorldNpcPublicId(UUID.randomUUID());
        entity.setPersonalName("Marcus");
        entity.setServiceNpcTypeKey(TYPE_KEY);
        return entity;
    }

    private static void giveGold(ServerPlayer player, int gold) {
        int remaining = gold;
        while (remaining > 0) {
            int stack = Math.min(remaining, 99);
            player.getInventory().add(new ItemStack(ItemRegistry.GOLD_COIN.get(), stack));
            remaining -= stack;
        }
    }

    private static int goldOf(ServerPlayer player) {
        return MerchantEconomyService.countCoins(player) / CoinConversion.COPPER_PER_GOLD;
    }

    private static void withRegistry(Runnable body) {
        ServiceNpcRegistrySnapshot previous = ServiceNpcRegistryCache.snapshot();
        try {
            ServiceNpcRegistryCache.replace(new ServiceNpcRegistrySnapshot(
                    1, 1L, Map.of(),
                    Map.of(TYPE_KEY, new ServiceNpcTypeDefinition(
                            TYPE_KEY, "Warrior Guildmaster", TYPE_KEY, "britannia_mod:service_npc",
                            TYPE_KEY + "_default", List.of("guild.train"), List.of(SKILL),
                            Map.of(), true, true, 1L)),
                    Map.of()
            ));
            body.run();
        } finally {
            ServiceNpcRegistryCache.replace(previous);
        }
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

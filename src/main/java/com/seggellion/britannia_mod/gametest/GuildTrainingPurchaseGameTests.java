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

    // ---------- Harness ----------

    private static void run(
            GameTestHelper helper, GuildTrainingWriteResult outcome, int expectedGoldAfter, String message
    ) {
        withRegistry(() -> {
            GuildTrainingService.useClientForTesting(
                    (server, request) -> CompletableFuture.completedFuture(outcome));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ServiceNpcEntity guildmaster = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            guildmaster.setWorldNpcPublicId(UUID.randomUUID());
            guildmaster.setPersonalName("Marcus");
            guildmaster.setServiceNpcTypeKey(TYPE_KEY);
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

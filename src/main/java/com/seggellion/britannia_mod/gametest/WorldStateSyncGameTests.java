package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.worldstate.WorldStateSyncOutcome;
import com.seggellion.britannia_mod.worldstate.WorldStateSyncPoller;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Proves the one part of Milestone 13 NeoForge Slice 1 that plain JUnit structurally cannot:
 * that {@link WorldStateSyncPoller}'s server-keyed static registry ({@code start}/{@code stop}/
 * {@code tick(MinecraftServer)}) is really wired to this mod's real server lifecycle events,
 * against a real {@link MinecraftServer} -- not a substituted seam. Cadence math, jitter
 * randomization, HTTP parsing, and response validation are already fully covered by {@code
 * WorldStateSyncPollerTest}/{@code WorldStateChangesClientTest}/{@code
 * WorldStateSyncValidatorTest} via {@link WorldStateSyncPoller#newForTest} and are deliberately
 * not re-proven here.
 *
 * A GameTest server runs exactly one real {@link MinecraftServer} for the entire test session,
 * shared by every @GameTest structure that runs within it -- and {@link WorldStateSyncPoller}'s
 * production registry is keyed by that one server, not per-structure. An earlier version of this
 * class split the assertions below across two separate @GameTest methods; that failed for real,
 * not hypothetically: {@code stop(server)} in one method permanently removed the one shared
 * production poller for the rest of the session, so whichever sibling method's tick happened to
 * land afterward found no poller registered at all and failed with a misleading "ServerStartedEvent
 * never fired" reading. Every assertion here therefore stays in one continuous, ordered method
 * against the one shared instance, ending with the only {@code stop()} call this session gets.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class WorldStateSyncGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private WorldStateSyncGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void realServerLifecycleRegistersTicksAndDetachesTheOneSharedPoller(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        // By the time any @GameTest method runs, the real dedicated server has already fired
        // ServerStartedEvent (BritanniaMod.onServerStarted calls WorldStateSyncPoller.start()
        // there), so a poller being active here -- without this test ever calling start() itself
        // -- is direct, passive proof the real event really invokes it.
        check(WorldStateSyncPoller.activePollerCount() >= 1,
                "no poller was active -- ServerStartedEvent did not really invoke WorldStateSyncPoller.start() for this real dedicated server");
        WorldStateSyncPoller active = WorldStateSyncPoller.activePollerForTest(server);
        check(active != null, "no real poller instance is registered for this real server");
        int activeBefore = WorldStateSyncPoller.activePollerCount();

        int countdownBefore = active.ticksUntilNextPollForTest();
        helper.runAfterDelay(5, () -> {
            // Five genuinely real elapsed server ticks, driven purely by the real game loop --
            // this test never calls tick() itself here -- with the countdown observed decreasing
            // is direct proof BritanniaMod.onServerTick's real ServerTickEvent.Post wiring really
            // reaches WorldStateSyncPoller.tick(server), not just that the tick() method works
            // when invoked.
            int countdownAfterRealTicks = active.ticksUntilNextPollForTest();
            check(countdownAfterRealTicks < countdownBefore,
                    "poller's internal tick countdown did not decrease across 5 real elapsed server ticks "
                            + "(before=" + countdownBefore + " after=" + countdownAfterRealTicks
                            + ") -- ServerTickEvent.Post does not appear to really be reaching WorldStateSyncPoller.tick(server)");

            // Waiting out the real 6000-tick (5 real minute) cadence from here is impractical for
            // a test, so this uses the exact same time-acceleration convention
            // ServiceNpcSpawnGameTests already established: manually driving the same static
            // tick(server) entry point BritanniaMod.onServerTick calls, in a tight loop, against
            // this same real per-server registry. It still proves the real pipeline end-to-end --
            // real registry lookup, real firePoll(), the real production WorldStateChangesClient
            // (real ServerAuthRegistry/ServerHttpExecutor collaborators, not test doubles), and
            // completion scheduled back via the real server::execute -- it just collapses the
            // wait, exactly as the established convention does. No live Rails endpoint exists in
            // this GameTest environment, so the honest, expected result is a transport failure
            // (most likely missing credentials); what matters is that the pipeline genuinely ran.
            int cadence = WorldStateSyncPoller.BASE_CADENCE_TICKS + WorldStateSyncPoller.MAX_JITTER_TICKS;
            for (int tick = 0; tick < cadence; tick++) {
                WorldStateSyncPoller.tick(server);
            }

            // A few more real ticks so the completion this forced poll scheduled via
            // server.execute(...) has genuinely run before asserting on it, rather than racing
            // its own async hop back to the tick thread.
            helper.runAfterDelay(3, () -> {
                check(!(active.lastOutcomeForTest() instanceof WorldStateSyncOutcome.NeverPolled),
                        "real tick(server) invocations never reached firePoll() -- outcome is still NeverPolled");
                check(active.lastOutcomeForTest() instanceof WorldStateSyncOutcome.TransportFailure,
                        "expected a transport failure outcome (no Rails endpoint exists in this GameTest environment), got "
                                + active.lastOutcomeForTest());

                WorldStateSyncPoller.stop(server);
                check(WorldStateSyncPoller.activePollerCount() == activeBefore - 1,
                        "stop() did not detach the real poller from the registry");
                check(WorldStateSyncPoller.activePollerForTest(server) == null,
                        "stop() left the poller reachable through the registry");
                check(active.isStopped(), "the stopped instance does not report itself as stopped");

                for (int tick = 0; tick < 50; tick++) {
                    WorldStateSyncPoller.tick(server);
                }
                check(WorldStateSyncPoller.activePollerCount() == activeBefore - 1,
                        "ticking after stop() somehow changed the active poller count -- state leaked past shutdown");
                helper.succeed();
            });
        });
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

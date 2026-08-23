package com.seggellion.britannia_mod.worldstate;

import javax.annotation.Nullable;

/**
 * One short, screen-safe line describing {@link WorldStateSyncPoller}'s most recent outcome, for
 * the Service NPC spawn block's own readout.
 *
 * <h2>Why this exists</h2>
 * Every way the delta channel can fail -- a rejected response, a rejected batch, a transport
 * failure, a deferred full-bootstrap recovery -- is <em>permanent</em> until something else
 * intervenes: a rejected batch never advances {@code lastAppliedWorldStateVersion}, so the next
 * poll re-fetches and re-rejects exactly the same rows, forever. All of it is logged at INFO/WARN
 * and none of it is visible to an admin standing at the post, who sees only "awaiting staffing
 * reconciliation" and has no way to tell "Rails has not staffed this yet" apart from "Rails
 * staffed it and this server cannot accept the news". This turns the poller's own already-recorded
 * outcome into that missing sentence.
 *
 * <h2>Kept out of the payload record and off the client</h2>
 * Formatting happens server-side, exactly like {@code taughtSkillLabels} and the supply lines:
 * the client is handed a finished string and renders it, holding no opinion about what a sync
 * outcome means. That also keeps this testable as plain Java -- no {@code MinecraftServer}, no
 * network buffer, no screen.
 */
public final class WorldStateSyncStatusText {
    /**
     * Reasons carry UUIDs ("assignment &lt;uuid&gt; references missing spawn point &lt;uuid&gt;"),
     * which are both far wider than the panel and useless at a glance -- the operator needs to
     * know <em>which kind</em> of failure this is, then goes to the log for the identifiers. The
     * cap is on the reason alone, never the whole line, so the leading "World sync FAILED:" is
     * never what gets eaten.
     */
    static final int MAX_REASON_CHARS = 44;

    private WorldStateSyncStatusText() {
    }

    /**
     * Healthy means the last poll actually landed -- either an applied delta or a completed
     * full-bootstrap recovery. Everything else, including a poller that has never run yet, is
     * something an admin looking at an unstaffed post deserves to be told about.
     */
    public static boolean isHealthy(@Nullable WorldStateSyncOutcome outcome) {
        return outcome instanceof WorldStateSyncOutcome.Accepted
                || outcome instanceof WorldStateSyncOutcome.FullBootstrapApplied;
    }

    public static String of(@Nullable WorldStateSyncOutcome outcome) {
        if (outcome == null) return "World sync: unavailable";
        return switch (outcome) {
            case WorldStateSyncOutcome.NeverPolled ignored -> "World sync: not run yet";
            case WorldStateSyncOutcome.Accepted accepted ->
                    "World sync: OK at v" + accepted.response().toVersion();
            case WorldStateSyncOutcome.FullBootstrapApplied applied ->
                    "World sync: refreshed at v" + applied.version();
            case WorldStateSyncOutcome.Rejected rejected ->
                    "World sync FAILED: " + truncate(rejected.reason());
            case WorldStateSyncOutcome.ApplyRejected rejected ->
                    "World sync FAILED: " + truncate(rejected.reason());
            case WorldStateSyncOutcome.TransportFailure failure ->
                    "World sync FAILED: " + truncate(failure.safeCode());
            case WorldStateSyncOutcome.FullBootstrapDeferred deferred ->
                    "World sync deferred: " + truncate(deferred.reason());
        };
    }

    private static String truncate(@Nullable String reason) {
        if (reason == null || reason.isBlank()) return "unknown";
        if (reason.length() <= MAX_REASON_CHARS) return reason;
        return reason.substring(0, MAX_REASON_CHARS - 1) + "\u2026";
    }
}

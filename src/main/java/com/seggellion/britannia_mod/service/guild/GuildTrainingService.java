package com.seggellion.britannia_mod.service.guild;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.economy.MerchantEconomyService;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guildmaster milestone 5: one training purchase, start to finish.
 *
 * <h2>The ordering is the design</h2>
 * <pre>
 *   quote  -&gt;  Rails commits  -&gt;  re-count coins  -&gt;  charge
 * </pre>
 * Rails first, always. {@code SkillManager.setSkillAdmin} writes locally and fire-and-forgets its
 * POST, which for a <em>paid</em> purchase would let a Rails outage take a player's gold and lose
 * the skill at their next login. Here nothing is charged until Rails has committed the skill, the
 * treasury credit and the ledger row together.
 *
 * <p>The residual risk is deliberate and documented: a crash between Rails committing and the coins
 * being taken gives free training. That is the recoverable direction. Charging for a skill that was
 * never persisted is not.
 *
 * <p>Every decision that can be wrong in a way that costs money lives in
 * {@link GuildTrainingQuote} and {@link GuildTrainingCharge}, both pure and both tested. What is
 * left here is threading, revalidation and I/O — the parts a JUnit harness cannot reach anyway.
 */
public final class GuildTrainingService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static GuildTrainingSkillSetClientPort client = new GuildTrainingClient();

    /**
     * Players with a purchase in flight. Without it, a double-click dispatches two purchases with
     * two different idempotency keys — which Rails would correctly treat as two distinct purchases
     * and charge for both.
     */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private GuildTrainingService() {
    }

    /** Test seam, mirroring {@code BankingProxyService.useClientForTesting}. */
    public static void useClientForTesting(GuildTrainingSkillSetClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new GuildTrainingClient();
        IN_FLIGHT.clear();
    }

    /**
     * Buys as much of {@code skillSlug} as the player's gold and the ceiling allow — pure-UO
     * "takes what it takes", with no requested amount.
     */
    public static void purchase(ServerPlayer player, ServiceNpcEntity guildmaster, String skillSlug) {
        GuildmasterProxyService.ResolvedGuildmaster resolved =
                GuildmasterProxyService.resolve(player, guildmaster);
        if (resolved == null) return;

        if (!GuildmasterCapability.teaches(resolved.serviceNpcTypeKey(), skillSlug)) {
            // A client naming a skill this guild does not teach is refused here rather than priced;
            // the slug came off the wire and is never trusted.
            reply(player, GuildTrainingOutcome.refused(skillSlug, NOT_TAUGHT_MESSAGE));
            return;
        }

        GuildTrainingQuote quote = GuildTrainingQuote.of(
                skillSlug,
                true,
                Math.round(SkillManager.getSkill(player, skillSlug) * 10.0F),
                skillMaxTenths(skillSlug),
                MerchantEconomyService.countCoins(player)
        );
        if (!quote.purchasable()) {
            reply(player, GuildTrainingOutcome.refused(skillSlug, refusalMessage(quote)));
            return;
        }

        UUID playerId = player.getUUID();
        if (!IN_FLIGHT.add(playerId)) {
            LOGGER.info("Ignoring guild.train purchase for {}: one is already in flight", playerId);
            return;
        }

        MinecraftServer server = player.server;
        UUID entityUuid = resolved.entityUuid();
        GuildTrainingRequest request = GuildTrainingRequest.of(
                resolved.worldNpcPublicId(), playerId, player.getGameProfile().getName(), quote);

        final CompletableFuture<GuildTrainingWriteResult> future;
        try {
            future = client.submit(server, request);
        } catch (RuntimeException submissionFailure) {
            // submit() throwing before returning a future would otherwise strand this player in
            // IN_FLIGHT forever, since the completion callback is only attached below.
            IN_FLIGHT.remove(playerId);
            LOGGER.warn("guild_training submission threw synchronously for {}", playerId, submissionFailure);
            reply(player, GuildTrainingOutcome.unavailable(skillSlug, "synchronous_submission_failure"));
            return;
        }

        future.whenComplete((result, failure) -> server.execute(() -> {
            IN_FLIGHT.remove(playerId);
            if (server.getPlayerList().getPlayer(playerId) != player) return;
            if (failure != null || result == null) {
                reply(player, GuildTrainingOutcome.unavailable(skillSlug, "unexpected_client_error"));
                return;
            }
            // Revalidated fresh: the Guildmaster may have been discarded, reassigned or moved out
            // of range by the assignment reconciler while the call was in flight.
            Entity current = player.serverLevel().getEntity(entityUuid);
            if (!(current instanceof ServiceNpcEntity live) || !live.isAlive()
                    || GuildmasterProxyService.resolve(player, live) == null) {
                LOGGER.info("Discarding guild_training result: Guildmaster {} is no longer available", entityUuid);
                return;
            }
            applyResult(player, skillSlug, result);
        }));
    }

    private static void applyResult(ServerPlayer player, String skillSlug, GuildTrainingWriteResult result) {
        switch (result) {
            case GuildTrainingWriteResult.Applied applied -> {
                // Re-counted now, not at quote time: an HTTP round trip has happened and the purse
                // may have changed. Charge follows what Rails granted, never what was requested.
                GuildTrainingCharge.Plan plan = GuildTrainingCharge.decide(
                        applied.goldCharged(), MerchantEconomyService.countCoins(player));
                if (!plan.charge()) {
                    LOGGER.warn("guild_training committed for {} but the charge was abandoned (granted={})",
                            player.getStringUUID(), applied.goldCharged());
                    reply(player, GuildTrainingOutcome.fundsChanged(skillSlug));
                    return;
                }

                chargeCoins(player, plan.copper());
                SkillManager.applyConfirmedValue(player, skillSlug, (float) applied.skillValue());
                int finalTenths = Math.round((float) applied.skillValue() * 10.0F);
                reply(player, GuildTrainingOutcome.trained(
                        skillSlug, applied.grantedTenths(), plan.gold(), finalTenths));
            }
            case GuildTrainingWriteResult.Rejected rejected ->
                    reply(player, GuildTrainingOutcome.refused(skillSlug, REJECTED_MESSAGE));
            case GuildTrainingWriteResult.TransportFailure transportFailure ->
                    reply(player, GuildTrainingOutcome.unavailable(skillSlug, transportFailure.reason()));
        }
    }

    /**
     * Takes exactly {@code copper} through the existing coin idiom — count everything, remove
     * everything, hand back the difference — rather than a second inventory scanner.
     */
    private static void chargeCoins(ServerPlayer player, int copper) {
        int available = MerchantEconomyService.countCoins(player);
        MerchantEconomyService.removeAllCoins(player);
        MerchantEconomyService.giveChange(player, available - copper);
    }

    private static int skillMaxTenths(String skillSlug) {
        return Math.round(SkillManager.maxValueForSlug(skillSlug) * 10.0F);
    }

    private static String refusalMessage(GuildTrainingQuote quote) {
        return switch (quote.rejection()) {
            case ALREADY_AT_CAP -> AT_CAP_MESSAGE;
            case NOT_ENOUGH_GOLD -> NOT_ENOUGH_GOLD_MESSAGE;
            case NOT_TAUGHT -> NOT_TAUGHT_MESSAGE;
            case NONE -> NOT_ENOUGH_GOLD_MESSAGE;
        };
    }

    private static void reply(ServerPlayer player, GuildTrainingOutcome outcome) {
        player.displayClientMessage(Component.literal(outcome.playerMessage()), false);
    }

    static final String AT_CAP_MESSAGE =
            "The guildmaster shakes their head: \"I can teach thee no more of this.\"";
    static final String NOT_ENOUGH_GOLD_MESSAGE =
            "The guildmaster eyes thy purse: \"Thou hast not gold enough for a lesson.\"";
    static final String NOT_TAUGHT_MESSAGE =
            "The guildmaster frowns: \"That is not a skill of this guild.\"";
    static final String REJECTED_MESSAGE =
            "The guildmaster turns away: \"I cannot teach thee that just now.\"";
}

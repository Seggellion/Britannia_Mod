package com.seggellion.britannia_mod.service.guild;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.economy.CoinConversion;
import com.seggellion.britannia_mod.economy.MerchantEconomyService;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.GuildTrainingOpenS2CPayload;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guildmaster milestone 3: the server-side entry point for {@code guild.train}, structurally
 * mirroring {@link com.seggellion.britannia_mod.service.banking.BankingProxyService} — revalidate
 * fresh, never trusting anything the caller captured earlier.
 *
 * <h2>Why this one is much smaller than the banking equivalent</h2>
 * BankingProxyService dispatches an HTTP call off the tick thread and marshals the result back.
 * This does not, and deliberately so: every input a training <em>quote</em> needs is already on
 * this side. The taught-skill set comes from the registry cache, the player's current skill values
 * from {@link SkillManager}'s per-login cache, and the price is pure arithmetic. There is nothing
 * to ask Rails for until the player actually commits a purchase, which is Milestone 5's job.
 *
 * <p>So there is no {@code ServerHttpExecutor}, no {@code CompletableFuture} and no in-flight
 * dedup set here yet. Adding them now would be unexercised machinery around a call that does not
 * exist. Milestone 5 introduces the confirmed {@code skills/set} write and will need all three.
 *
 * <h2>What it sends</h2>
 * Milestone 6 replaced Milestone 3's placeholder chat with the real training screen. Every offer
 * is priced here, on the server, because the client has neither the skill definitions nor the
 * guild's taught-skill set to price from — and none of it is authoritative anyway: the screen
 * sends back a slug, and {@link GuildTrainingService} recomputes the whole quote before a coin
 * moves.
 */
public final class GuildmasterProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Matches BankingProxyService.MAX_INTERACTION_DISTANCE_SQR exactly (8 blocks, squared). */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    /** The UO NPC-training ceiling, in tenths. 40.0 skill == 400 tenths == 400 gold from zero. */
    public static final int GUILDMASTER_MAX_TENTHS = 400;

    private GuildmasterProxyService() {
    }

    /**
     * Fresh, from-scratch revalidation of the player/entity pairing. Returns {@code null} for any
     * reason the interaction should silently produce nothing at all: wrong side, dead entity,
     * distant player, missing world identity, or a type whose live capability does not currently
     * permit {@code guild.train}.
     *
     * <p>Mirrors {@code BankingProxyService.resolve} check for check, including the World NPC id
     * requirement — an entity with no Rails identity is one the reconciler has not finished
     * setting up, and it must not transact.
     */
    @Nullable
    public static ResolvedGuildmaster resolve(@Nullable ServerPlayer player, @Nullable ServiceNpcEntity entity) {
        if (player == null || entity == null) return null;
        if (player.level().isClientSide) return null;
        if (!entity.isAlive()) return null;
        if (player.distanceToSqr(entity) > MAX_INTERACTION_DISTANCE_SQR) return null;
        UUID worldNpcPublicId = entity.getWorldNpcPublicId();
        if (worldNpcPublicId == null) return null;

        String typeKey = entity.getServiceNpcTypeKey();
        if (!GuildmasterCapability.supportsGuildTrain(typeKey)) return null;
        List<String> taughtSkillSlugs = GuildmasterCapability.taughtSkillSlugs(typeKey);
        if (taughtSkillSlugs.isEmpty()) return null;

        return new ResolvedGuildmaster(entity.getUUID(), worldNpcPublicId, typeKey, taughtSkillSlugs);
    }

    public static void handle(ServerPlayer player, ServiceNpcEntity entity) {
        ResolvedGuildmaster resolved = resolve(player, entity);
        if (resolved == null) return;

        LOGGER.info(
                "guild.train interaction player={} type={} skills={}",
                player.getStringUUID(), resolved.serviceNpcTypeKey(), resolved.taughtSkillSlugs().size()
        );

        // Milestone 6 replaces Milestone 3's placeholder chat lines with the real screen. Every
        // figure is derived here, on the server, because the client has neither the skill
        // definitions nor the guild's taught-skill set to derive them from.
        List<GuildTrainingOpenS2CPayload.Offer> offers = new ArrayList<>(resolved.taughtSkillSlugs().size());
        int availableGold = MerchantEconomyService.countCoins(player) / CoinConversion.COPPER_PER_GOLD;
        for (String slug : resolved.taughtSkillSlugs()) {
            int currentTenths = Math.round(SkillManager.getSkill(player, slug) * 10.0F);
            int capTenths = Math.min(
                    GUILDMASTER_MAX_TENTHS, Math.round(SkillManager.maxValueForSlug(slug) * 10.0F));
            int headroom = Math.max(0, capTenths - currentTenths);
            offers.add(new GuildTrainingOpenS2CPayload.Offer(
                    slug,
                    SkillManager.displayNameForSlug(slug),
                    currentTenths,
                    capTenths,
                    // Pre-minimised so the screen never decides affordability: one gold per tenth
                    // makes the affordable count and the price the same number.
                    Math.min(headroom, availableGold)
            ));
        }

        NetworkHandler.sendToPlayer(player, new GuildTrainingOpenS2CPayload(
                entity.getId(), guildTitle(resolved), entity.getPersonalName(), offers));
    }

    private static String guildTitle(ResolvedGuildmaster resolved) {
        return GuildmasterCapability.roleTitle(resolved.serviceNpcTypeKey())
                .orElse("Guildmaster");
    }

    /** Fixed-point rendering; never {@code float} formatting, per the tenths discipline. */
    static String formatTenths(int tenths) {
        return (tenths / 10) + "." + Math.abs(tenths % 10);
    }

    public record ResolvedGuildmaster(
            UUID entityUuid,
            UUID worldNpcPublicId,
            String serviceNpcTypeKey,
            List<String> taughtSkillSlugs
    ) {
        public ResolvedGuildmaster {
            taughtSkillSlugs = List.copyOf(taughtSkillSlugs);
        }
    }
}

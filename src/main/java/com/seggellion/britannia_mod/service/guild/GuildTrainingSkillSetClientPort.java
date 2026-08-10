package com.seggellion.britannia_mod.service.guild;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The seam through which a Guildmaster purchase writes a skill to Rails.
 *
 * <p>Guildmaster milestone 5. Exists as an interface for the same reason
 * {@code BankingOpenClientPort} does: a GameTest must be able to substitute a fake so a purchase
 * can be exercised end to end without opening a socket, and — more importantly here — so the
 * failure paths can be tested at all. "Rails said no" and "Rails never answered" are the two cases
 * that decide whether a player keeps their gold, and neither is reachable against a live server.
 */
@FunctionalInterface
public interface GuildTrainingSkillSetClientPort {
    /**
     * Writes {@code newValueTenths} as the player's absolute value for {@code skillSlug}.
     *
     * <p>Absolute, not a delta, because the endpoint this reuses ({@code POST /api/skills/set}) is
     * absolute. That is why the caller must re-read the authoritative current value immediately
     * before computing it — a stale cached value would otherwise be written back over a newer one.
     *
     * <p>The returned future must never complete exceptionally for an ordinary rejection; a
     * refusal is a {@link GuildTrainingWriteResult} value. Only genuine transport failure completes
     * exceptionally, and the caller treats that identically to a refusal: no coins move.
     */
    CompletableFuture<GuildTrainingWriteResult> submit(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            String skillSlug,
            int newValueTenths
    );
}

package com.seggellion.britannia_mod.service.guild;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * The seam through which a Guildmaster purchase reaches Rails.
 *
 * <p>An interface so a test can substitute a fake and never open a socket — but the real reason is
 * the failure paths. "Rails refused" and "Rails never answered" are what decide whether a player
 * keeps their gold, and neither is reachable against a live server.
 *
 * <p>Named for {@code skills/set} when the plan was to reuse that endpoint; it now targets the
 * purpose-built {@code guild_training} operation, which was needed once a purchase had to credit
 * the city treasury in the same transaction as the skill write.
 */
@FunctionalInterface
public interface GuildTrainingSkillSetClientPort {
    /**
     * Submits one purchase.
     *
     * <p>The returned future must never complete exceptionally for an ordinary rejection — a
     * refusal is a {@link GuildTrainingWriteResult.Rejected} value. Only genuine transport failure
     * completes exceptionally, and the caller treats that identically to a refusal: no coins move.
     */
    CompletableFuture<GuildTrainingWriteResult> submit(MinecraftServer server, GuildTrainingRequest request);
}

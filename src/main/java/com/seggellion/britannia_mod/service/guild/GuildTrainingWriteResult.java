package com.seggellion.britannia_mod.service.guild;

/**
 * What Rails said about a Guildmaster skill write.
 *
 * <p>Guildmaster milestone 5. A sealed result rather than a boolean because the three outcomes are
 * not interchangeable to an operator reading a log: a refusal means the request was wrong and
 * retrying it will not help, while a transport failure means nothing is known about whether the
 * write landed. Both mean the player is not charged, but only one of them is a bug.
 */
public sealed interface GuildTrainingWriteResult {
    /**
     * Rails accepted and committed the new value. {@code POST /api/skills/set} answers
     * {@code 204 No Content}, so there is no body to carry.
     *
     * <p>This is the only outcome that permits taking the player's coins.
     */
    record Applied() implements GuildTrainingWriteResult {
    }

    /**
     * Rails understood the request and refused it — an unknown skill slug (404) or a value over the
     * skill's cap (422). Retrying is pointless; the caller stops and charges nothing.
     */
    record Rejected(int statusCode, String detail) implements GuildTrainingWriteResult {
    }

    /**
     * The request never produced an answer: timeout, connection failure, missing shard credentials.
     *
     * <p>Deliberately distinct from {@link Rejected}, because the write may or may not have landed
     * on the Rails side. The caller charges nothing, which means a write that <em>did</em> land
     * gives the player free training. That is the deliberate direction of this whole ordering:
     * failing toward "player keeps their gold" is recoverable and invisible, whereas charging for
     * a skill that was never persisted is neither.
     */
    record TransportFailure(String reason) implements GuildTrainingWriteResult {
    }
}

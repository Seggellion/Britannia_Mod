package com.seggellion.britannia_mod.service.guild;

/**
 * What Rails said about a training purchase.
 *
 * <p>Sealed rather than a boolean because the outcomes are not interchangeable to an operator
 * reading a log: a refusal means the request was wrong and retrying will not help, while a
 * transport failure means nothing is known about whether the write landed. Both mean the player is
 * not charged, but only one of them is a bug.
 */
public sealed interface GuildTrainingWriteResult {
    /**
     * Rails committed the skill, the treasury credit and the ledger row.
     *
     * <p>{@code grantedTenths} may be <em>less</em> than was asked for. The client priced against a
     * skill value that can have moved between quote and commit, so Rails clamps to the real
     * headroom rather than refusing the whole purchase — and the charge must follow what was
     * granted, never what was requested, or the player pays for training they did not receive.
     *
     * <p>This is the only outcome that permits taking coins.
     */
    record Applied(int grantedTenths, int goldCharged, double skillValue)
            implements GuildTrainingWriteResult {
    }

    /**
     * Rails understood the request and refused it — unknown NPC or skill, a skill this guild does
     * not teach, already at the cap, or a price that did not match the tenths. Retrying is
     * pointless; charge nothing.
     */
    record Rejected(String reason) implements GuildTrainingWriteResult {
    }

    /**
     * The request never produced an answer: timeout, connection failure, missing shard
     * credentials, or an unexpected server error.
     *
     * <p>Deliberately distinct from {@link Rejected}, because the write may or may not have landed.
     * The caller charges nothing, which means a write that <em>did</em> land gives free training.
     * That is the deliberate direction of this whole ordering: failing toward "the player keeps
     * their gold" is recoverable and invisible, whereas charging for a skill that was never
     * persisted is neither.
     */
    record TransportFailure(String reason) implements GuildTrainingWriteResult {
    }
}

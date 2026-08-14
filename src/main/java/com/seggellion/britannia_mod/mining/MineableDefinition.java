package com.seggellion.britannia_mod.mining;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One Mining-governed resource: the blocks it covers, the hard Mining requirement that will gate
 * them, and the identities the break flow needs to hand the resource to drops, restoration, and
 * the economy.
 *
 * <p>Mining milestone 2. Deliberately a pure-Java value type — no Minecraft classes — so the
 * catalogue can be loaded and validated by plain JUnit contract tests exactly like the
 * Blacksmithing catalogue's JSON is. Block references are registry-id strings; only the thin
 * {@link Mineables} adapter touches {@code BlockState}.
 *
 * <p>{@code requiredMining} is the access gate (inclusive, design §10.2). {@code challenge} is the
 * separate skill-gain difficulty input in the RunUO required-vs-gain-window tradition; until
 * milestone 4 calibrates real gain math it is seeded equal to the requirement and nothing reads it.
 */
public record MineableDefinition(
        String id,
        String displayName,
        Category category,
        Status status,
        float requiredMining,
        float challenge,
        List<String> blockIds,
        String dropName,
        Optional<String> economyCommodity,
        boolean restorable,
        boolean ownerReview,
        Optional<String> notes
) {

    public enum Category {
        STONE,
        ORE;

        public static Category parse(String raw) {
            for (Category value : values()) {
                if (value.name().toLowerCase(Locale.ROOT).equals(raw)) return value;
            }
            throw new IllegalStateException("Unknown mineable category '" + raw + "'");
        }
    }

    /**
     * ACTIVE definitions cover the blocks the Britannia mining flow manages today and are the only
     * ones {@link MineableCatalog#resolveBlock} returns. DEFERRED entries carry an approved
     * progression value for a block the flow does not manage yet (Dripstone, Obsidian); milestone 6
     * activates them deliberately rather than a data edit silently widening the gate's reach.
     */
    public enum Status {
        ACTIVE,
        DEFERRED;

        public static Status parse(String raw) {
            for (Status value : values()) {
                if (value.name().toLowerCase(Locale.ROOT).equals(raw)) return value;
            }
            throw new IllegalStateException("Unknown mineable status '" + raw + "'");
        }
    }

    public MineableDefinition {
        blockIds = List.copyOf(blockIds);
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }
}

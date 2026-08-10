package com.seggellion.britannia_mod.service.guild;

import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Whether a given {@code serviceNpcTypeKey} currently permits {@code guild.train}, and which
 * skills it teaches — read from the same {@link ServiceNpcRegistryCache} snapshot
 * {@link com.seggellion.britannia_mod.service.banking.BankingCapability} already reads, and
 * structured to match it method for method.
 *
 * <p>Same "do not hardcode" gate for the same reason: a Guildmaster trains only if its live,
 * Rails-published type definition says so. There is no {@code ServiceNpcKind} enum and no
 * hardcoded roster of guilds anywhere in the mod — the twelve guild types and their skill
 * groups are registry rows, so adding a thirteenth guild is Rails seed data, not a mod build.
 *
 * <p><b>Server-side only in practice.</b> {@link ServiceNpcRegistryCache} is populated by
 * {@code WorldBootstrapHandler} on the logical server; on a client the snapshot is empty, so
 * every method here fails closed to "not a Guildmaster". That is the correct answer for the
 * client, which learns a Guildmaster's identity from synchronized entity state rather than
 * from a registry it does not have.
 */
public final class GuildmasterCapability {
    static final String GUILD_TRAIN_SERVICE_KEY = "guild.train";

    private GuildmasterCapability() {
    }

    /** True exactly when this type is an active Guildmaster permitted to train. */
    public static boolean supportsGuildTrain(@Nullable String serviceNpcTypeKey) {
        return activeGuildmaster(serviceNpcTypeKey).isPresent();
    }

    /**
     * The skills this Guildmaster teaches, in the registry's published order (Rails emits them
     * sorted by slug), or empty for anything that is not an active Guildmaster.
     */
    public static List<String> taughtSkillSlugs(@Nullable String serviceNpcTypeKey) {
        return activeGuildmaster(serviceNpcTypeKey)
                .map(ServiceNpcTypeDefinition::taughtSkillSlugs)
                .orElseGet(List::of);
    }

    /**
     * Whether this Guildmaster teaches {@code skillSlug}. The authoritative membership check
     * for a training request: a client naming a skill this guild does not teach must be
     * refused rather than trained, so no caller should test the list itself.
     */
    public static boolean teaches(@Nullable String serviceNpcTypeKey, @Nullable String skillSlug) {
        return skillSlug != null && !skillSlug.isBlank()
                && taughtSkillSlugs(serviceNpcTypeKey).contains(skillSlug);
    }

    /**
     * The Guildmaster's role title for display — the type's own {@code display_name}, which is
     * already the full {@code "Warrior Guildmaster"} label in Rails. Empty for non-Guildmasters,
     * which is what keeps every existing Service NPC's nameplate byte-identical.
     */
    public static Optional<String> roleTitle(@Nullable String serviceNpcTypeKey) {
        return activeGuildmaster(serviceNpcTypeKey)
                .map(ServiceNpcTypeDefinition::displayName)
                .filter(displayName -> !displayName.isBlank());
    }

    private static Optional<ServiceNpcTypeDefinition> activeGuildmaster(@Nullable String serviceNpcTypeKey) {
        if (serviceNpcTypeKey == null || serviceNpcTypeKey.isBlank()) return Optional.empty();
        ServiceNpcTypeDefinition definition =
                ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(serviceNpcTypeKey);
        if (definition == null || !definition.active()
                || !definition.allowedServiceKeys().contains(GUILD_TRAIN_SERVICE_KEY)) {
            return Optional.empty();
        }
        // The parser already refuses to publish a guild.train type with no taught skills, but a
        // Guildmaster who teaches nothing is not a Guildmaster to any caller here either.
        return definition.taughtSkillSlugs().isEmpty() ? Optional.empty() : Optional.of(definition);
    }
}

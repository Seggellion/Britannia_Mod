package com.seggellion.britannia_mod.service;

import java.util.List;
import java.util.Map;

/**
 * One published Service NPC type from Rails' {@code service_npc_registry} bootstrap member.
 *
 * <h2>{@code taughtSkillSlugs}</h2>
 * Guildmaster milestone 1. A Guildmaster teaches a <em>group</em> of skills, not one — the
 * RunUO guild rosters this feature is modelled on give the Ranger eleven and the Fisher one,
 * and thirteen skills (MagicResist in four guilds) are taught by more than one guild. So the
 * binding is a list, and it lives on the <em>type</em> rather than on the spawn point: the
 * spawn block already selects a service type, and nothing about a taught-skill set needs a
 * per-post value.
 *
 * <p>Slugs, never display labels — {@code skills.slug} is the only stable identifier the
 * skill system has ({@code SkillManager} already keys {@code PLAYER_SKILLS} and every
 * {@code skills/*} request by it). There is deliberately no index: the Rails {@code skills}
 * table is admin-editable CMS content with no ordering column, so any positional identity
 * would silently retarget when an admin adds or unpublishes a skill.
 *
 * <p>Empty for every non-Guildmaster type, including {@code bank_teller} and any type
 * published by a Rails build that predates the {@code taught_skill_slugs} member.
 */
public record ServiceNpcTypeDefinition(
        String key,
        String displayName,
        String professionKey,
        String minecraftEntityTypeKey,
        String defaultDialogueKey,
        List<String> allowedServiceKeys,
        List<String> taughtSkillSlugs,
        Map<String, Double> minimumCitySupplies,
        boolean active,
        boolean spawnable,
        long definitionRevision
) {
    public ServiceNpcTypeDefinition {
        allowedServiceKeys = List.copyOf(allowedServiceKeys);
        taughtSkillSlugs = List.copyOf(taughtSkillSlugs);
        minimumCitySupplies = Map.copyOf(minimumCitySupplies);
    }

    /**
     * The shape before city-economy requirements existed. Milestone 2 added
     * {@link #minimumCitySupplies()}: the minimum city commodity levels Rails requires before it
     * will staff this type, keyed by plain commodity name ({@code food}, {@code silver}, …) to
     * match Rails' own {@code CityStaffing::EconomicEligibility::SUPPLY_COLUMNS}.
     *
     * <p>Empty means ungated, which is every bank teller — and is also what an older Rails build
     * that omits the member produces, so the absence can never accidentally gate anything.
     */
    public ServiceNpcTypeDefinition(
            String key,
            String displayName,
            String professionKey,
            String minecraftEntityTypeKey,
            String defaultDialogueKey,
            List<String> allowedServiceKeys,
            List<String> taughtSkillSlugs,
            boolean active,
            boolean spawnable,
            long definitionRevision
    ) {
        this(key, displayName, professionKey, minecraftEntityTypeKey, defaultDialogueKey,
                allowedServiceKeys, taughtSkillSlugs, Map.of(), active, spawnable, definitionRevision);
    }

    /**
     * The pre-Guildmaster shape, defaulting {@link #taughtSkillSlugs()} to empty.
     *
     * <p>Kept as a real overload rather than migrating the twenty-odd existing construction
     * sites (six in {@code ServiceNpcSpawnConfigurationValidatorTest} alone, plus every
     * banking GameTest's registry fixture): none of them are about taught skills, and
     * rewriting them all to pass {@code List.of()} would put this milestone's diff through
     * files it has no business touching. Arity alone disambiguates the two constructors.
     */
    public ServiceNpcTypeDefinition(
            String key,
            String displayName,
            String professionKey,
            String minecraftEntityTypeKey,
            String defaultDialogueKey,
            List<String> allowedServiceKeys,
            boolean active,
            boolean spawnable,
            long definitionRevision
    ) {
        this(key, displayName, professionKey, minecraftEntityTypeKey, defaultDialogueKey,
                allowedServiceKeys, List.of(), active, spawnable, definitionRevision);
    }
}

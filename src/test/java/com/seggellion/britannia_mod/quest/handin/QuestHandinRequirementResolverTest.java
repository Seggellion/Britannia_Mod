package com.seggellion.britannia_mod.quest.handin;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolution refuses rather than guesses.
 *
 * <p>The crop half of this needs the mod's own registries, so it lives in
 * {@code QuestItemHandinGameTests}. What is provable without a server is the more important half:
 * that a resolver this build does not implement, an item that names nothing, and a demand with one
 * bad entry among good ones all refuse the <b>whole</b> plan -- because a partially resolved plan
 * is the one that takes what it understood and leaves the player short.
 */
class QuestHandinRequirementResolverTest {

    private static final UUID HANDIN = UUID.fromString("2f8c1d40-9a3b-4c77-8f21-5b6e0d9a1c34");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aLiteralRequirementResolvesToItsOwnItemAndNeverToAResolverEntry() {
        QuestHandinResolution.Resolved resolved = resolve(
                new QuestHandinRequirement.Literal(0, "minecraft:carrot", 2));

        assertEquals(1, resolved.plan().size());
        assertEquals("minecraft:carrot", resolved.plan().get(0).itemId());
        assertEquals(2, resolved.plan().get(0).count());
        assertEquals(0, resolved.plan().get(0).requirementIndex());
        assertTrue(!resolved.plan().get(0).answersResolver());
    }

    @Test
    void aResolverThisBuildDoesNotImplementRefusesRatherThanGuessing() {
        assertEquals(QuestHandinResolution.UNKNOWN_RESOLVER, refusal(
                new QuestHandinRequirement.Resolver(0, "awarded_mount_saddle_item", "awarded_mount",
                        "horse", 1)));
    }

    @Test
    void anItemIdThatNamesNothingRegisteredRefusesTheDemand() {
        assertEquals(QuestHandinResolution.UNKNOWN_ITEM,
                refusal(new QuestHandinRequirement.Literal(0, "minecraft:not_an_item", 1)));
    }

    @Test
    void airIsRefusedEvenThoughItIsARealRegistryEntry() {
        assertEquals(QuestHandinResolution.UNKNOWN_ITEM,
                refusal(new QuestHandinRequirement.Literal(0, "minecraft:air", 1)),
                "nothing is ever carried, so an air requirement could never be handed in");
    }

    @Test
    void oneBadEntryRefusesTheWholePlanRatherThanTheEntriesAroundIt() {
        QuestItemHandinProtocol.Demand demand = new QuestItemHandinProtocol.Demand(HANDIN, "hand_over",
                List.of(new QuestHandinRequirement.Literal(0, "minecraft:carrot", 1),
                        new QuestHandinRequirement.Resolver(1, "invented_resolver", "flag", "value", 1),
                        new QuestHandinRequirement.Literal(2, "minecraft:wheat", 1)),
                "");

        assertEquals(QuestHandinResolution.UNKNOWN_RESOLVER,
                assertInstanceOf(QuestHandinResolution.Refused.class,
                        QuestHandinRequirementResolver.resolve(demand)).reason());
    }

    @Test
    void everyRequirementKeepsItsPublishedIndexSoTheProofCanBeJoinedBackToIt() {
        QuestItemHandinProtocol.Demand demand = new QuestItemHandinProtocol.Demand(HANDIN, "hand_over",
                List.of(new QuestHandinRequirement.Literal(0, "minecraft:carrot", 1),
                        new QuestHandinRequirement.Literal(1, "minecraft:wheat", 3)),
                "");

        QuestHandinResolution.Resolved resolved = assertInstanceOf(QuestHandinResolution.Resolved.class,
                QuestHandinRequirementResolver.resolve(demand));

        assertEquals(List.of(0, 1),
                resolved.plan().stream().map(QuestHandinRemoval::requirementIndex).toList());
        assertEquals(List.of("minecraft:carrot", "minecraft:wheat"),
                resolved.plan().stream().map(QuestHandinRemoval::itemId).toList());
    }

    private static QuestHandinResolution.Resolved resolve(QuestHandinRequirement requirement) {
        return assertInstanceOf(QuestHandinResolution.Resolved.class,
                QuestHandinRequirementResolver.resolve(demandOf(requirement)));
    }

    private static String refusal(QuestHandinRequirement requirement) {
        return assertInstanceOf(QuestHandinResolution.Refused.class,
                QuestHandinRequirementResolver.resolve(demandOf(requirement))).reason();
    }

    private static QuestItemHandinProtocol.Demand demandOf(QuestHandinRequirement requirement) {
        return new QuestItemHandinProtocol.Demand(HANDIN, "hand_over", List.of(requirement), "");
    }
}

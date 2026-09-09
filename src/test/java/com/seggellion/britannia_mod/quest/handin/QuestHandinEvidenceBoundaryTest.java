package com.seggellion.britannia_mod.quest.handin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundary that makes a stranded transaction impossible in ordinary play.
 *
 * <h2>What is being proved, and why it needs proving</h2>
 * Three Rails answers strand a transaction whose items are already gone: {@code evidence_rejected},
 * {@code cancelled} and {@code rejected}. A stranded row is preserved and operator-visible, but it
 * is not an ending -- no completion, no refund -- so the invariant only holds if none of the three
 * can happen during valid gameplay.
 *
 * <p>{@code cancelled} is unreachable by inspection: Rails answers it only to a report that took
 * nothing ({@code Confirm#late_confirmation}), and this server confirms only after a removal.
 * {@code rejected} means Rails holds no such transaction for this shard and player, which cannot be
 * true of one Rails itself prepared and never deletes.
 *
 * <p>{@code evidence_rejected} is the one that needs a test, because it is the only one the mod
 * could cause by itself -- by reporting a proof that does not describe the requirements. So the
 * proof this mod builds is checked against a transcription of Rails' own matcher
 * ({@link RailsEvidenceMatcher}) rather than against this repository's opinion of it.
 *
 * <p>The second half of the refusal, {@code evidence_conflict}, is a proof that contradicts one
 * already stored. That is covered structurally rather than here: the proof is built once, at
 * {@code REMOVAL_INTENT}, and every later attempt re-encodes the stored entries --
 * {@code QuestHandinLedgerStoreTest.theReloadedProofEncodesToTheSameBytesTheFirstAttemptSent} pins
 * that a reload posts the bytes the lost attempt posted.
 */
class QuestHandinEvidenceBoundaryTest {

    private static final UUID HANDIN = UUID.fromString("2f8c1d40-9a3b-4c77-8f21-5b6e0d9a1c34");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aProofBuiltFromALiteralDemandIsAcceptedByRailsOwnMatcher() {
        assertAccepted(demand("""
                [{"item": "minecraft:carrot", "count": 1}]"""));
    }

    @Test
    void everyShapeOfDemandProducesAProofRailsAccepts() {
        // One of each, then the awkward combinations: several requirements, the same item named
        // twice, the maximum count, and the maximum number of entries.
        assertAccepted(demand("""
                [{"item": "minecraft:carrot", "count": 1},
                 {"item": "minecraft:wheat", "count": 3}]"""));
        assertAccepted(demand("""
                [{"item": "minecraft:carrot", "count": 1},
                 {"item": "minecraft:carrot", "count": 2}]"""));
        assertAccepted(demand("""
                [{"item": "minecraft:carrot", "count": 1024}]"""));

        StringBuilder eight = new StringBuilder("[");
        for (int index = 0; index < 8; index++) {
            eight.append(index == 0 ? "" : ", ")
                    .append("{\"item\": \"minecraft:carrot\", \"count\": ").append(index + 1).append("}");
        }
        assertAccepted(demand(eight.append("]").toString()));
    }

    /**
     * The resolver half, built the way {@code QuestHandinRequirementResolver} builds it but without
     * the mod registries a unit test cannot bootstrap: the concrete item is whatever
     * {@code CropRegistry} answers, and the entry echoes the resolver and the pinned flag value.
     *
     * <p>The point of the assertion is that the concrete item is irrelevant to Rails. Three
     * different produce ids -- including one this mod does not register at all -- are all accepted
     * for the same requirement, which is precisely why the frozen fixture's illustrative
     * {@code britannia_mod:carrot} and the live {@code britannia_mod:carrots} do not contradict
     * each other.
     */
    @Test
    void aResolverProofIsAcceptedWhateverConcreteItemItNames() {
        List<QuestHandinRequirement> requirements = List.of(
                new QuestHandinRequirement.Resolver(0, "awarded_crop_harvest_item", "awarded_crop",
                        "carrot", 1));

        for (String produce : List.of("britannia_mod:carrots", "britannia_mod:carrot", "minecraft:carrot")) {
            RailsEvidenceMatcher.Verdict verdict = RailsEvidenceMatcher.match(requirements,
                    List.of(QuestHandinRemoval.resolved(0, produce, 1, "awarded_crop_harvest_item", "carrot")));
            assertTrue(verdict.accepted(),
                    "Rails never compares a resolver's concrete item, so " + produce
                            + " must be accepted: " + verdict.reason());
        }
    }

    /** A resolver answered with a crop the player was never awarded is refused, as it must be. */
    @Test
    void aResolverProofEchoingTheWrongPinnedValueIsRefused() {
        List<QuestHandinRequirement> requirements = List.of(
                new QuestHandinRequirement.Resolver(0, "awarded_crop_harvest_item", "awarded_crop",
                        "carrot", 1));

        assertFalse(RailsEvidenceMatcher.match(requirements,
                List.of(QuestHandinRemoval.resolved(0, "britannia_mod:wheat", 1,
                        "awarded_crop_harvest_item", "wheat"))).accepted(),
                "a flag rewritten since prepare time must not move the goalposts");
    }

    /**
     * The matcher is not vacuous.
     *
     * <p>Every way a proof can be wrong is a way this mod could have stranded a player's item, so
     * the oracle is shown refusing each of them. Without this the accepting tests above would pass
     * against a matcher that accepted everything.
     */
    @Test
    void theOracleRefusesEveryMalformedProofRailsWouldRefuse() {
        List<QuestHandinRequirement> two = List.of(
                new QuestHandinRequirement.Literal(0, "minecraft:carrot", 1),
                new QuestHandinRequirement.Literal(1, "minecraft:wheat", 2));

        assertRefused(two, List.of(QuestHandinRemoval.literal(0, "minecraft:carrot", 1)),
                "an entry omitted");
        assertRefused(two, List.of(
                QuestHandinRemoval.literal(0, "minecraft:carrot", 1),
                QuestHandinRemoval.literal(1, "minecraft:wheat", 2),
                QuestHandinRemoval.literal(2, "minecraft:wheat", 2)),
                "an entry added");
        assertRefused(two, List.of(
                QuestHandinRemoval.literal(0, "minecraft:carrot", 1),
                QuestHandinRemoval.literal(0, "minecraft:carrot", 1)),
                "an index reported twice");
        assertRefused(two, List.of(
                QuestHandinRemoval.literal(0, "minecraft:carrot", 64),
                QuestHandinRemoval.literal(1, "minecraft:wheat", 2)),
                "an inflated count");
        assertRefused(two, List.of(
                QuestHandinRemoval.literal(0, "minecraft:diamond", 1),
                QuestHandinRemoval.literal(1, "minecraft:wheat", 2)),
                "an item the requirement never named");
        assertRefused(two, List.of(
                QuestHandinRemoval.resolved(0, "minecraft:carrot", 1, "awarded_crop_harvest_item", "carrot"),
                QuestHandinRemoval.literal(1, "minecraft:wheat", 2)),
                "a literal requirement dressed up as a resolver");
    }

    /**
     * The structural invariants the acceptance rests on, asserted directly on what the resolver
     * produces: one entry per requirement, in requirement order, carrying that requirement's own
     * index and count.
     */
    @Test
    void theResolvedPlanIsOneEntryPerRequirementInOrder() {
        QuestItemHandinProtocol.Demand demand = demand("""
                [{"item": "minecraft:carrot", "count": 1},
                 {"item": "minecraft:wheat", "count": 3},
                 {"item": "minecraft:carrot", "count": 2}]""");
        List<QuestHandinRemoval> plan = resolve(demand);

        assertEquals(demand.requirements().size(), plan.size());
        for (int index = 0; index < plan.size(); index++) {
            assertEquals(index, plan.get(index).requirementIndex(), "entry " + index + " keeps its index");
            assertEquals(demand.requirements().get(index).count(), plan.get(index).count(),
                    "entry " + index + " carries its requirement's own count");
            assertFalse(plan.get(index).answersResolver(), "a literal is never dressed up as a resolver");
        }
    }

    /**
     * The fixture's illustrative produce is not a real item in this mod, and the real one is.
     *
     * <p>Pinned so the divergence stays deliberate. If a {@code britannia_mod:carrot} item is ever
     * registered, this fails and someone has to decide which one stage five means -- rather than the
     * fixture quietly becoming ambiguous.
     */
    @Test
    void theFixturesIllustrativeCarrotIsNotAnItemThisModRegisters() {
        assertTrue(QuestHandinRequirementResolver.registered("britannia_mod:carrot").isEmpty(),
                "the frozen fixture names an item this mod does not register, which is what makes it "
                        + "illustrative rather than authoritative");
        // And the live mapping is asserted where the registries exist:
        // QuestItemHandinGameTests.resolvesTheCropToItsProduceAndNeverItsSeed pins
        // CropRegistry.byId("carrot") -> britannia_mod:carrots, and that the seed is never taken.
    }

    // --- helpers --------------------------------------------------------------------------

    private static void assertAccepted(QuestItemHandinProtocol.Demand demand) {
        List<QuestHandinRemoval> plan = resolve(demand);
        RailsEvidenceMatcher.Verdict verdict = RailsEvidenceMatcher.match(demand.requirements(), plan);
        assertTrue(verdict.accepted(),
                "the mod built a proof Rails would refuse, which is a removal that could never "
                        + "complete or be refunded: " + verdict.reason());
    }

    private static void assertRefused(List<QuestHandinRequirement> requirements,
                                      List<QuestHandinRemoval> proof, String what) {
        assertFalse(RailsEvidenceMatcher.match(requirements, proof).accepted(),
                "the oracle must refuse " + what + ", or the acceptance tests prove nothing");
    }

    private static List<QuestHandinRemoval> resolve(QuestItemHandinProtocol.Demand demand) {
        return assertInstanceOf(QuestHandinResolution.Resolved.class,
                QuestHandinRequirementResolver.resolve(demand)).plan();
    }

    /** Wraps a {@code requires} array in the envelope Rails publishes it in. */
    private static QuestItemHandinProtocol.Demand demand(String requires) {
        JsonObject handin = new JsonObject();
        handin.addProperty("handin_uuid", HANDIN.toString());
        handin.addProperty("choice", "hand_over");
        handin.add("requires", JsonParser.parseString(requires).getAsJsonArray());
        JsonObject root = new JsonObject();
        root.addProperty("result", QuestItemHandinProtocol.RESULT_HANDIN_REQUIRED);
        root.add(QuestItemHandinProtocol.HANDIN_KEY, handin);
        return QuestItemHandinProtocol.parseDemand(root);
    }
}

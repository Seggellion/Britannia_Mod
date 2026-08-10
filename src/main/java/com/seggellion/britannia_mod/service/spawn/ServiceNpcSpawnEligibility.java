package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Guildmaster milestone 2: whether a city's economy currently satisfies a service type's minimum
 * supply requirement, evaluated locally from already-cached bootstrap data.
 *
 * <h2>This is a readout, not the decision</h2>
 * Rails owns the real decision. {@code CityStaffing::EconomicEligibility} is what actually decides
 * whether an NPC is assigned to a post, and no entity exists on this side without an assignment.
 * This class exists so an admin standing at a spawn block can see <em>why</em> nothing spawned,
 * instead of filing it as a bug — it mirrors the same rule against the same numbers so the two
 * agree, but nothing here gates a spawn and nothing here is authoritative.
 *
 * <p>Because it is a readout it degrades honestly rather than guessing: when the bootstrap has not
 * published supply figures for a city (an older Rails build, or a cache that has not populated
 * yet) the answer is {@link Status#UNKNOWN}, never a cheerful "eligible". Reporting "all clear"
 * from absent data is exactly how an admin ends up trusting a screen that knows nothing.
 *
 * <p>Pure and side-effect free, taking plain records, so it is fully testable in JUnit — the spawn
 * screen itself cannot be instantiated by either harness (Architecture Decision 0).
 */
public final class ServiceNpcSpawnEligibility {
    private ServiceNpcSpawnEligibility() {
    }

    public enum Status {
        /** Every configured minimum is met. Also the answer when a type configures no minimums. */
        SATISFIED,
        /** At least one configured minimum is not met; {@link Result#shortfalls()} says which. */
        BELOW_MINIMUM,
        /** The type has requirements but the city's supply figures are not available to check. */
        UNKNOWN
    }

    /**
     * One requirement and how the city currently measures against it. {@code available} is
     * {@code null} exactly when the figure is missing, which is what makes the whole result
     * {@link Status#UNKNOWN}.
     */
    public record SupplyStatus(String supply, double required, @Nullable Double available) {
        public boolean satisfied() {
            return available != null && available >= required;
        }
    }

    public record Result(Status status, List<SupplyStatus> requirements) {
        public Result {
            requirements = List.copyOf(requirements);
        }

        public boolean blocksSpawning() {
            return status == Status.BELOW_MINIMUM;
        }

        /** Only the unmet requirements, for a short "why not" line. */
        public List<SupplyStatus> shortfalls() {
            return requirements.stream().filter(requirement -> !requirement.satisfied()).toList();
        }
    }

    private static final Result NO_REQUIREMENTS = new Result(Status.SATISFIED, List.of());

    /**
     * @param type the selected service type, or {@code null} when nothing is selected yet
     * @param city the selected city, or {@code null} when nothing is selected yet
     */
    public static Result evaluate(@Nullable ServiceNpcTypeDefinition type, @Nullable BootstrapCityDefinition city) {
        if (type == null) return NO_REQUIREMENTS;
        Map<String, Double> minimums = type.minimumCitySupplies();
        if (minimums.isEmpty()) {
            // Every bank teller, and any Guildmaster an admin has deliberately left ungated.
            return NO_REQUIREMENTS;
        }

        Map<String, Double> available = city == null ? Map.of() : city.supplies();
        List<SupplyStatus> requirements = new ArrayList<>(minimums.size());
        boolean unknown = false;
        boolean below = false;

        for (Map.Entry<String, Double> entry : minimums.entrySet()) {
            Double current = available.get(entry.getKey());
            SupplyStatus status = new SupplyStatus(entry.getKey(), entry.getValue(), current);
            requirements.add(status);
            if (current == null) {
                unknown = true;
            } else if (!status.satisfied()) {
                below = true;
            }
        }

        // Deterministic for rendering: worst news first, then alphabetical so the line does not
        // reshuffle between refreshes.
        requirements.sort(Comparator
                .comparing((SupplyStatus status) -> status.available() != null && status.satisfied())
                .thenComparing(SupplyStatus::supply));

        // A confirmed shortfall outranks a missing figure: if one requirement is provably unmet the
        // spawn is blocked regardless of what the unmeasurable ones would have said.
        Status status = below ? Status.BELOW_MINIMUM : unknown ? Status.UNKNOWN : Status.SATISFIED;
        return new Result(status, requirements);
    }
}

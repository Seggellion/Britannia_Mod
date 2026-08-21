package com.seggellion.britannia_mod.resource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One managed geological resource: what it is, what may work it, what it gives, and how it comes
 * back.
 *
 * <h2>What this replaces</h2>
 * Milestone 2 of the OreVein remediation. This is {@code ManagedDeposit} grown up rather than a
 * new idea beside it — that record already had the two fields the Mining half could not express,
 * an extraction <em>tag</em> and a configured yield, and milestone 0 chose it as the seam to
 * generalise. It has gained the concerns that were previously scattered: the depleted state, the
 * regeneration duration, the generation shape, and a reference to Mining progression.
 *
 * <h2>What it deliberately does not own</h2>
 * The Mining requirement. {@code MineableCatalog} remains the single authority for
 * {@code required_mining} and {@code challenge}, and a definition {@link #mineableId() references}
 * it by id. Two files must never be able to state different Mining levels for one resource, so
 * this one states none at all — {@link ResourceCatalog} validates that the reference resolves,
 * that the categories agree, and that the two catalogues claim exactly the same blocks.
 *
 * <p>Pure Java on purpose, exactly like {@code MineableDefinition}: registry ids are strings and
 * nothing here imports Minecraft, so plain JUnit loads and validates the shipped data without
 * booting the game. {@link Resources} is the thin adapter that turns these strings into blocks,
 * items and tags.
 */
public record ResourceDefinition(
        String id,
        String displayName,
        Family family,
        List<String> blockIds,
        Optional<String> mineableId,
        String extractionToolTag,
        Yield yield,
        DepletedState depleted,
        int regenerationHours,
        Optional<Generation> generation,
        int revision
) {

    /**
     * Which extraction system governs the resource, and therefore which tool family works it.
     *
     * <p>ORE and STONE are the Mining ladder, worked with the project pickaxe and gated on the
     * Mining skill. SEDIMENT is the deposit beds — clay and silica — worked with the project
     * shovel and gated on nothing but the tool. They are parallel, not a hierarchy: a pickaxe has
     * no authority over a clay bed and a shovel has none over an ore, and the tag on each
     * definition is what says so.
     */
    public enum Family {
        ORE,
        STONE,
        SEDIMENT;

        public static Family parse(String raw) {
            for (Family value : values()) {
                if (value.name().toLowerCase(Locale.ROOT).equals(raw)) return value;
            }
            throw new IllegalStateException("Unknown resource family '" + raw + "'");
        }

        /** Whether Mining governs this family, and therefore whether a mineable id is required. */
        public boolean isMiningGoverned() {
            return this != SEDIMENT;
        }
    }

    /**
     * What one extraction hands the player.
     *
     * <p>Three modes because the mod has three, and each is already implemented: the Mining flow
     * makes a purity ore for a metal and a graded stone for a rock, and a deposit bed hands over a
     * configured item. {@code ITEM} is the only one carrying data, because the other two derive
     * everything from the block they came from.
     */
    public record Yield(Mode mode, Optional<String> itemId, int count) {

        public enum Mode {
            /** {@code PurityOreItem} carrying the ore type and a rolled purity. */
            PURITY_ORE,
            /** {@code GradeStoneItem} carrying the stone type and a rolled grade. */
            GRADED_STONE,
            /** A configured item and count, as the deposit beds use. */
            ITEM;

            public static Mode parse(String raw) {
                for (Mode value : values()) {
                    if (value.name().toLowerCase(Locale.ROOT).equals(raw)) return value;
                }
                throw new IllegalStateException("Unknown yield mode '" + raw + "'");
            }
        }

        public Yield {
            Objects.requireNonNull(mode, "yield mode is required");
            Objects.requireNonNull(itemId, "yield item optional is required");
        }
    }

    /**
     * What stands in the cell once the resource has been taken out of it.
     *
     * <p>One mode today, and it is the behaviour both extraction paths already implement: the cell
     * becomes whatever fluid occupies it, which is air on dry land. Modelling it as a named,
     * validated field rather than an inlined expression is the point of doing it now — milestone 1
     * showed why it matters, because a solid cell that becomes air under water fills with water and
     * can then never restore.
     *
     * <p>A second mode that leaves a real depleted block behind is the obvious answer to that, and
     * it is deliberately <b>not</b> added here: nothing would consume it yet, and inventing an
     * unused mode is the speculative field this milestone was told not to add. The seam is the
     * field; M4 and M6 add the mode along with the behaviour that reads it.
     */
    public enum DepletedState {
        /** Replaced by the cell's own fluid state — air on land, water in a shallow. */
        FLUID_AWARE_AIR;

        public static DepletedState parse(String raw) {
            for (DepletedState value : values()) {
                if (value.name().toLowerCase(Locale.ROOT).equals(raw)) return value;
            }
            throw new IllegalStateException("Unknown depleted state '" + raw + "'");
        }
    }

    /**
     * How and where the legacy command materialises this resource.
     *
     * <p>Present only for the nine ores {@code /populateores} can place. Everything else -- the
     * stone family, and the hand-placed deposit beds -- omits it.
     *
     * <p>{@code minRadius} and {@code maxRadius} are this resource's configured range;
     * {@link ResourceShape} owns the smallest radius its geometry means anything at, and the
     * catalogue refuses a configured minimum below it. Data may narrow a shape's range, never
     * widen it.
     *
     * <p>{@code hostTag} names the block tag whose members this resource may replace. It is the
     * resource's host policy and the only thing that decides it: milestone 3 took that decision
     * away from the shapes, which had been making it inconsistently and inside their geometry --
     * one refusing every cell that was not air, another accepting anything that was not air,
     * including bedrock, fluids and block entities.
     */
    public record Generation(
            ResourceShape shape, String blockId, int minRadius, int maxRadius, String hostTag) {
        public Generation {
            Objects.requireNonNull(shape, "generation shape is required");
            Objects.requireNonNull(blockId, "generation block is required");
            Objects.requireNonNull(hostTag, "generation host tag is required");
        }
    }

    public ResourceDefinition {
        Objects.requireNonNull(id, "resource id is required");
        Objects.requireNonNull(family, "resource family is required");
        blockIds = List.copyOf(blockIds);
        Objects.requireNonNull(mineableId, "mineable optional is required");
        Objects.requireNonNull(extractionToolTag, "extraction tool tag is required");
        Objects.requireNonNull(yield, "yield is required");
        Objects.requireNonNull(depleted, "depleted state is required");
        Objects.requireNonNull(generation, "generation optional is required");
    }

    /** The path portion of the id, which is what operator commands and Rails rows spell. */
    public String path() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    public boolean isSediment() {
        return family == Family.SEDIMENT;
    }

    /** Regeneration delay in milliseconds, the unit the restoration ledger works in. */
    public long regenerationMillis() {
        return regenerationHours * 60L * 60L * 1000L;
    }
}

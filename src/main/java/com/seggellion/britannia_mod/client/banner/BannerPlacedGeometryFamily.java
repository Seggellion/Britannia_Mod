package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Catalogue geometry resources mapped to their exact persisted footprint support and to the
 * placed cloth each family draws.
 *
 * <h2>Units</h2>
 * Every dimension here is in BLOCKS, the unit the placed renderer works in. One block is 16
 * Minecraft model pixels, so a Blockbench measurement converts as {@code pixels / 16.0}.
 *
 * <h2>Cloth size</h2>
 * A family's cloth is stated outright as {@link #clothWidth()} x {@link #clothHeight()} rather
 * than derived from one symmetric inset, because the two axes no longer scale together. The
 * baselines in {@link Baseline} are the measured means of each group's authored Blockbench
 * quads (element bounds divided by their UV fraction), which is what tied the placed cloth to
 * the artwork in the first place; the owner's 2026-08-25 review then scaled each family from
 * that baseline. Writing every constant as {@code baseline * factor} keeps both halves of that
 * history legible and lets the tests assert the same arithmetic rather than a copied literal.
 *
 * <p>The cloth is no longer square. Because the renderer maps the whole 128x128 sprite onto the
 * quad, a family whose width and height scale by different factors renders its artwork with the
 * same aspect change -- for medium and large that is the requested 1.3/1.2 = 8.3% vertical
 * stretch, applied to real geometry rather than to the UVs.
 */
public enum BannerPlacedGeometryFamily {
    LARGE(BannerAssetAvailability.id("banner/placeholder/large"), 2, 2,
            Baseline.LARGE, Baseline.WIDER, Baseline.TALLER, 0.0625, 3.0 / 16.0, Baseline.FULL_POLE),
    // Declared 2x2 and currently unreachable: every medium-wall definition ships 1x2 and so
    // resolves to MEDIUM. Kept in step with MEDIUM so it stays right if 2x2 wall art lands.
    MEDIUM_WALL(BannerAssetAvailability.id("banner/placeholder/medium_wall"), 2, 2,
            Baseline.MEDIUM_WALL, Baseline.WIDER, Baseline.TALLER, 0.125, 0.0, Baseline.FULL_POLE),
    // The only family whose footprint is not square (1x2), and the only one whose cloth cannot
    // be expressed as a positive inset: sized to the authored artwork it has to be WIDER than
    // the one block it is anchored in. Baseline 1.375 is the mean of the 14 medium definitions'
    // own authored quads (range 1.301-1.495), so each renders within ~6% of its Blockbench
    // size. The overhang is transparent margin -- the visible artwork is 62-77% of the texture
    // width -- so it still reads as a one-block banner.
    MEDIUM(BannerAssetAvailability.id("banner/placeholder/medium"), 1, 2,
            Baseline.MEDIUM, Baseline.WIDER, Baseline.TALLER, 0.03125, 0.0, Baseline.FULL_POLE),
    SMALL(BannerAssetAvailability.id("banner/placeholder/small"), 1, 1,
            Baseline.SMALL, Baseline.DOUBLE, Baseline.DOUBLE, Baseline.MOUNT_FLUSH_INSET, 0.0,
            Baseline.FULL_POLE),
    // The x-small families hang from the same mount line, to the same depth, as SMALL. They
    // read as the narrow family through their artwork rather than through a smaller quad: at
    // 31% texture fill a road-guard pennant draws 6.2px wide against the small family's 9.5px.
    // Their previous 12px cloth cleared its own block by a single pixel, against small's seven.
    X_SMALL(BannerAssetAvailability.id("banner/placeholder/x_small"), 1, 1,
            Baseline.X_SMALL,
            Baseline.X_SMALL_TO_SMALL * Baseline.THIRTY_PERCENT_MORE_CLOTH,
            Baseline.X_SMALL_TO_SMALL * Baseline.THIRTY_PERCENT_MORE_CLOTH,
            Baseline.MOUNT_FLUSH_INSET, 0.0, Baseline.PENNANT_POLE),
    ROAD_GUARD(BannerAssetAvailability.id("banner/road_guard/geometry"), 1, 1,
            Baseline.X_SMALL,
            Baseline.X_SMALL_TO_SMALL * Baseline.THIRTY_PERCENT_MORE_CLOTH,
            Baseline.X_SMALL_TO_SMALL * Baseline.THIRTY_PERCENT_MORE_CLOTH,
            Baseline.MOUNT_FLUSH_INSET, 0.0, Baseline.PENNANT_POLE),
    // Drapery rather than a pennant, so the family's "narrower than small" target does not
    // apply to it: its width is exactly doubled as asked, it takes the family's growth in height
    // only, and it keeps a full-span pole because its cloth fills what that pole carries.
    SMALL_CURTAIN(BannerAssetAvailability.id("banner/small_curtain/geometry"), 1, 1,
            Baseline.X_SMALL,
            Baseline.X_SMALL_TO_SMALL * Baseline.CURTAIN_WIDER,
            Baseline.X_SMALL_TO_SMALL * Baseline.THIRTY_PERCENT_MORE_CLOTH,
            Baseline.MOUNT_FLUSH_INSET, 0.0, Baseline.FULL_POLE);

    /**
     * What each family drew before the owner's 2026-08-25 sizing review, and the factors that
     * review applied. Held in a nested type so the enum constants above can reference them:
     * an enum constant cannot read a static field of its own enum during initialization.
     */
    public static final class Baseline {
        public static final double LARGE = 1.9375;
        public static final double MEDIUM_WALL = 1.75;
        public static final double MEDIUM = 1.375;
        public static final double SMALL = 0.625;
        public static final double X_SMALL = 0.375;

        /** Owner-requested scale factors from those baselines. */
        public static final double WIDER = 1.2;
        public static final double TALLER = 1.3;
        public static final double DOUBLE = 2.0;

        /**
         * What the x-small families render at, derived rather than chosen: exactly the factor
         * that gives them the small family's cloth, so the two hang from the same mount line to
         * the same depth. X-small stays the narrower family through its ARTWORK, which fills
         * only ~31% of its texture width against the small family's ~48%; the quad is a window
         * onto a square texture, so equal windows still render a visibly narrower banner.
         */
        public static final double X_SMALL_TO_SMALL = (SMALL * DOUBLE) / X_SMALL;

        /**
         * The canonical top-of-block mount line, measured down from the anchor block's top
         * face. It equals the bracket plate's own half-height ({@code banner/mount/bracket} is
         * 6 pixels tall, centred on the pole axis), so a bracket hung here finishes exactly
         * flush with the top of its block instead of floating below it. The small family
         * already sat here; x-small did not, because its top inset was a leftover of the old
         * symmetric-inset scheme -- it mirrored a horizontal inset rather than describing where
         * hardware belongs -- which sank the whole assembly 2 pixels into the block.
         */
        public static final double MOUNT_FLUSH_INSET = 3.0 / 16.0;

        /**
         * The owner's 2026-08-25 polish pass asked for an x-small family 30% larger. Taken per
         * axis that would have made it 27% TALLER than the small family it must only slightly
         * exceed, and would have put its cloth exactly on the render-bounds limit. Taken as 30%
         * more cloth -- the square root in each direction -- it lands inside every constraint at
         * once: 30% more banner, still a quarter narrower than small, and 11% taller.
         */
        public static final double THIRTY_PERCENT_MORE_CLOTH = Math.sqrt(1.3);

        /** Small Curtain alone was asked to double its width; it is drapery, not a pennant. */
        public static final double CURTAIN_WIDER = 2.0;

        /**
         * How much of its cloth quad a family's artwork actually paints across, and therefore
         * how far its pole needs to run. The x-small pennants fill the middle ~31% of their
         * texture, so a pole sized to the whole quad ran a third again past the banner it
         * carried; 0.7 covers that artwork and about a pixel beyond it. Every other family
         * paints close enough to its full quad to keep the whole span.
         */
        public static final double FULL_POLE = 1.0;
        public static final double PENNANT_POLE = 0.7;

        private Baseline() {
        }
    }

    private final ResourceLocation geometryId;
    private final int width;
    private final int height;
    private final double clothBaseline;
    private final double widthScale;
    private final double heightScale;
    private final double clothTopInset;
    private final double clothLift;
    private final double poleArtworkSpan;

    BannerPlacedGeometryFamily(
            ResourceLocation geometryId,
            int width,
            int height,
            double clothBaseline,
            double widthScale,
            double heightScale,
            double clothTopInset,
            double clothLift,
            double poleArtworkSpan) {
        this.geometryId = geometryId;
        this.width = width;
        this.height = height;
        this.clothBaseline = clothBaseline;
        this.widthScale = widthScale;
        this.heightScale = heightScale;
        this.clothTopInset = clothTopInset;
        this.clothLift = clothLift;
        this.poleArtworkSpan = poleArtworkSpan;
    }

    public ResourceLocation geometryId() {
        return geometryId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Placed cloth extent along the banner's span axis, in blocks. */
    public double clothWidth() {
        return clothBaseline * widthScale;
    }

    /** Placed cloth drop below its own top edge, in blocks. */
    public double clothHeight() {
        return clothBaseline * heightScale;
    }

    /**
     * The cloth width this family was tuned to before the owner's 2026-08-25 sizing review: the
     * mean full-texture quad its group's authored Blockbench geometry implies. Kept separate
     * from the scale factors so the tie between a family and its artwork stays assertable even
     * as the owner rescales the presentation.
     */
    public double clothBaseline() {
        return clothBaseline;
    }

    /** Owner-approved multiple of {@link #clothBaseline()} this family's cloth is drawn at. */
    public double widthScale() {
        return widthScale;
    }

    /** Owner-approved multiple of {@link #clothBaseline()} this family's cloth hangs at. */
    public double heightScale() {
        return heightScale;
    }

    /** How far the mount line sits below the anchor block's top face, in blocks. */
    public double clothTopInset() {
        return clothTopInset;
    }

    /**
     * How far the cloth alone is raised above the mount line, in blocks. Only LARGE uses it:
     * its artwork carries roughly 3 model pixels of transparent margin above the painted cloth
     * (tournament_curtain's alpha starts 13 rows into a 128px texture, which is 3.1 pixels of
     * a 31-pixel cloth), so a geometrically connected banner still read as hanging detached
     * below its pole. Raising the cloth without raising the assembly closes that gap;
     * {@link BannerPlacedAssembly} reads {@link BannerPlacedGeometryPlan#poleLineY()} precisely
     * so the pole and brackets stay where they are.
     */
    public double clothLift() {
        return clothLift;
    }

    /**
     * The fraction of this family's cloth width its pole runs along, so hardware is sized to the
     * artwork rather than to the transparent margin around it. See {@link Baseline#PENNANT_POLE}.
     */
    public double poleArtworkSpan() {
        return poleArtworkSpan;
    }

    /**
     * Historical accessor: the symmetric horizontal inset this family's cloth implies against
     * its own footprint. Negative when the cloth is wider than the blocks it is anchored in.
     */
    public double horizontalInset() {
        return (width - clothWidth()) / 2.0;
    }

    /** Historical accessor for {@link #clothTopInset()}, which positions the top edge only. */
    public double verticalInset() {
        return clothTopInset;
    }

    public boolean supports(int persistedWidth, int persistedHeight) {
        return width == persistedWidth && height == persistedHeight;
    }

    public static Optional<BannerPlacedGeometryFamily> from(ResourceLocation geometryId) {
        return Arrays.stream(values()).filter(value -> value.geometryId.equals(geometryId)).findFirst();
    }

    /**
     * Custom item geometry does not change the placed footprint. Real catalogue geometry lives
     * under {@code banner/<group>/...}, so the group directory selects the family whose cloth
     * was tuned for that group's authored artwork; matching by footprint alone collapsed every
     * 1x1 banner into {@link #X_SMALL} (small-family cloth at 57% of its authored size). Only
     * geometry from outside the catalogue groups falls through to the synchronized approved
     * dimensions and the most compact matching placed convention.
     */
    public static Optional<BannerPlacedGeometryFamily> from(
            ResourceLocation geometryId, BannerDimensions dimensions) {
        Optional<BannerPlacedGeometryFamily> exact = from(geometryId);
        if (exact.isPresent() || dimensions == null) {
            return exact;
        }
        Optional<BannerPlacedGeometryFamily> group = catalogueGroupFamily(geometryId)
                .filter(value -> value.supports(dimensions.widthBlocks(), dimensions.heightBlocks()));
        if (group.isPresent()) {
            return group;
        }
        return Arrays.stream(values())
                .filter(value -> value.geometryId.getPath().contains("/placeholder/"))
                .filter(value -> value.supports(dimensions.widthBlocks(), dimensions.heightBlocks()))
                .reduce((first, second) -> second);
    }

    /**
     * The catalogue group encoded in a real geometry path. Both medium groups share
     * {@link #MEDIUM}: its cloth was tuned from all 14 medium definitions' authored quads
     * (the two groups' own means differ by 0.014 blocks), while their placed presentations
     * differ by orientation, not cloth size. {@code banner/medium_wall/} must be tested before
     * {@code banner/medium/} would ever match it as a prefix.
     */
    private static Optional<BannerPlacedGeometryFamily> catalogueGroupFamily(ResourceLocation geometryId) {
        String path = geometryId.getPath();
        if (path.startsWith("banner/large/")) {
            return Optional.of(LARGE);
        }
        if (path.startsWith("banner/medium_wall/") || path.startsWith("banner/medium/")) {
            return Optional.of(MEDIUM);
        }
        if (path.startsWith("banner/small/")) {
            return Optional.of(SMALL);
        }
        if (path.startsWith("banner/x_small/")) {
            return Optional.of(X_SMALL);
        }
        return Optional.empty();
    }
}

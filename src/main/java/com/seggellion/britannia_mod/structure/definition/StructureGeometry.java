package com.seggellion.britannia_mod.structure.definition;

import java.util.ArrayList;
import java.util.List;

/** Immutable dimensions, footprint coordinates, and render metadata. */
public final class StructureGeometry {
    public static final int MAX_LOCAL_X = 2;
    public static final int MAX_LOCAL_Y = 2;
    public static final int MAX_LOCAL_Z = 1;

    private StructureGeometry() {
    }

    /** Dimensions are always expressed in width x height x depth order. */
    public record Dimensions(int width, int height, int depth) {
        public int cellCount() {
            return width * height * depth;
        }
    }

    public record LocalOffset(int x, int y, int z) {
        public static final LocalOffset ANCHOR = new LocalOffset(0, 0, 0);
    }

    public record VoxelOffset(int x, int y, int z) {
        public BlockUnitOffset toBlockUnits() {
            return new BlockUnitOffset(x / 16.0, y / 16.0, z / 16.0);
        }
    }

    public record BlockUnitOffset(double x, double y, double z) {
    }

    public enum PlacementMode {
        FLOOR_ORIENTED
    }

    public enum CollisionProfile {
        CELL_BOUNDED
    }

    public enum RenderOrigin {
        ANCHOR_LOWER_FRONT_LEFT
    }

    public enum GeometryMode {
        SHARED,
        PER_VARIANT
    }

    /**
     * Generates layers bottom-to-top; inside each layer, rows front-to-back; inside each row, X
     * left-to-right. This y/z/x order is deterministic and places the anchor first.
     */
    public static List<LocalOffset> rectangularFootprint(Dimensions dimensions) {
        List<LocalOffset> offsets = new ArrayList<>();
        for (int y = 0; y < dimensions.height(); y++) {
            for (int z = 0; z < dimensions.depth(); z++) {
                for (int x = 0; x < dimensions.width(); x++) {
                    offsets.add(new LocalOffset(x, y, z));
                }
            }
        }
        return List.copyOf(offsets);
    }
}

package com.seggellion.britannia_mod.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads what a window block actually draws, straight out of its blockstate and model JSON, so the
 * collision tests can be checked against the art instead of against a second copy of the same
 * assumptions.
 *
 * <p>Everything here works in model pixels: the block the player places occupies {@code 0..16} on
 * each axis and anything outside that belongs to a neighbouring cell. Blockstate {@code "y"}
 * rotations are applied about the block centre, which is what the game does when it bakes the
 * model, so a box that comes back at {@code x 20} really is one cell east of the block.
 */
final class WindowArt {

    static final double BLOCK = 16.0D;

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    /** One element of a model, in model pixels. */
    record Box(double x0, double y0, double z0, double x1, double y1, double z1) {

        Box rotatedY(int degrees) {
            return switch (degrees) {
                case 90 -> new Box(BLOCK - this.z1, this.y0, this.x0, BLOCK - this.z0, this.y1, this.x1);
                case 180 -> new Box(BLOCK - this.x1, this.y0, BLOCK - this.z1,
                                    BLOCK - this.x0, this.y1, BLOCK - this.z0);
                case 270 -> new Box(this.z0, this.y0, BLOCK - this.x1, this.z1, this.y1, BLOCK - this.x0);
                default -> this;
            };
        }

        /**
         * The same box pulled in by one pixel on every face, so a collision shape is not held to
         * the last fraction of a pixel of the art. Where that would leave nothing - a pane of glass
         * is drawn with no thickness at all - the axis collapses to a sliver at its middle, which
         * still has to be covered.
         */
        Box eroded() {
            double[] x = shrink(this.x0, this.x1);
            double[] y = shrink(this.y0, this.y1);
            double[] z = shrink(this.z0, this.z1);
            return new Box(x[0], y[0], z[0], x[1], y[1], z[1]);
        }

        private static double[] shrink(double lo, double hi) {
            if (hi - lo <= 2.0D) {
                double mid = (lo + hi) / 2.0D;
                return new double[] {mid - 0.005D, mid + 0.005D};
            }
            return new double[] {lo + 1.0D, hi - 1.0D};
        }

        /**
         * The part of this box inside the cell at the given whole-block offset, in that cell's own
         * coordinates, or {@code null} when the box does not really reach it. A face that lands
         * exactly on a cell boundary - the glass in {@code window_1x2} is drawn as a plane at
         * {@code z 0} - belongs to the cell it floors into rather than to both.
         */
        Box clippedToCell(int cellX, int cellY, int cellZ) {
            double[] x = overlap(this.x0, this.x1, cellX);
            double[] y = overlap(this.y0, this.y1, cellY);
            double[] z = overlap(this.z0, this.z1, cellZ);
            if (x == null || y == null || z == null) {
                return null;
            }
            return new Box(x[0] - cellX * BLOCK, y[0] - cellY * BLOCK, z[0] - cellZ * BLOCK,
                           x[1] - cellX * BLOCK, y[1] - cellY * BLOCK, z[1] - cellZ * BLOCK);
        }

        private static double[] overlap(double lo, double hi, int cell) {
            double cellLo = cell * BLOCK;
            double cellHi = cellLo + BLOCK;
            if (hi - lo <= 1.0E-6D) {
                return Math.floor(lo / BLOCK) == cell ? new double[] {lo, hi} : null;
            }
            double low = Math.max(lo, cellLo);
            double high = Math.min(hi, cellHi);
            return high - low > 1.0E-6D ? new double[] {low, high} : null;
        }

        /** The shortest side of this box, which is how a decorative overhang is told from a wall. */
        double thinnestSide() {
            return Math.min(this.x1 - this.x0, Math.min(this.y1 - this.y0, this.z1 - this.z0));
        }
    }

    /** One entry of a blockstate's {@code variants} map. */
    record Variant(String key, String model, int yRotation) {
        boolean drawsNothing() {
            return this.model.equals("minecraft:block/air");
        }
    }

    private WindowArt() {
    }

    static Path blockstates() {
        return ASSETS.resolve("blockstates");
    }

    static List<Variant> variantsOf(String blockstateName) throws IOException {
        JsonObject root = readJson(blockstates().resolve(blockstateName + ".json"));
        JsonObject variants = root.getAsJsonObject("variants");
        List<Variant> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            JsonObject value = entry.getValue().isJsonArray()
                ? entry.getValue().getAsJsonArray().get(0).getAsJsonObject()
                : entry.getValue().getAsJsonObject();
            int rotation = value.has("y") ? value.get("y").getAsInt() : 0;
            out.add(new Variant(entry.getKey(), value.get("model").getAsString(), rotation));
        }
        return out;
    }

    /**
     * The solid elements of a model, rotated into place for its variant.
     *
     * <p>Elements carrying their own {@code rotation} angle are left out: the only ones in this mod
     * are the half-pixel diagonal muntins of the {@code window_cross_*} art, which sit inside a
     * glazed opening that the frame's own collision already covers, and whose axis-aligned bounds
     * overstate where they actually are.
     */
    static List<Box> solidElements(Variant variant) throws IOException {
        if (variant.drawsNothing()) {
            return List.of();
        }
        List<Box> boxes = new ArrayList<>();
        for (Box box : rawElements(variant.model())) {
            boxes.add(box.rotatedY(variant.yRotation()));
        }
        return boxes;
    }

    /** Model elements as authored, with no blockstate rotation applied. */
    static List<Box> rawElements(String modelId) throws IOException {
        if (modelId.equals("minecraft:block/air")) {
            return List.of();
        }
        JsonObject model = readJson(modelPath(modelId));
        if (!model.has("elements")) {
            return List.of();
        }
        List<Box> boxes = new ArrayList<>();
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("rotation")
                && object.getAsJsonObject("rotation").get("angle").getAsDouble() != 0.0D) {
                continue;
            }
            var from = object.getAsJsonArray("from");
            var to = object.getAsJsonArray("to");
            boxes.add(new Box(
                from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()));
        }
        return boxes;
    }

    /** Every whole-block cell any of these boxes reaches, as {@code (x, y, z)} offsets. */
    static List<int[]> cellsTouched(List<Box> boxes) {
        Map<String, int[]> cells = new LinkedHashMap<>();
        for (Box box : boxes) {
            for (int x = floorCell(box.x0()); x <= floorCell(box.x1()); x++) {
                for (int y = floorCell(box.y0()); y <= floorCell(box.y1()); y++) {
                    for (int z = floorCell(box.z0()); z <= floorCell(box.z1()); z++) {
                        if (box.clippedToCell(x, y, z) != null) {
                            cells.put(x + "/" + y + "/" + z, new int[] {x, y, z});
                        }
                    }
                }
            }
        }
        return new ArrayList<>(cells.values());
    }

    private static int floorCell(double value) {
        return (int) Math.floor(value / BLOCK);
    }

    private static Path modelPath(String modelId) {
        String[] parts = modelId.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String path = parts.length == 2 ? parts[1] : parts[0];
        if (!namespace.equals("britannia_mod")) {
            throw new IllegalArgumentException("Only this mod's own models can be read here: " + modelId);
        }
        return ASSETS.resolve("models").resolve(path + ".json");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}

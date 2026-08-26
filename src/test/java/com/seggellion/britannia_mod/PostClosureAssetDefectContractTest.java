package com.seggellion.britannia_mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Regression contracts for the owner-reported post-closure asset defects. */
class PostClosureAssetDefectContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    @Test
    void ibisScaleAnimationWeightAndFlockingPolicyMatchTheOwnerRequest() throws Exception {
        String renderer = javaSource("client/renderer/entity/IbisRenderer.java");
        String ibis = javaSource("entity/IbisEntity.java");
        String flock = javaSource("entity/ai/IbisFlockGoal.java");
        String animationPolicy = javaSource("entity/IbisAnimationPolicy.java");

        assertTrue(renderer.contains("MODEL_SCALE = 0.64F"));
        assertEquals(1.6D, 8.0D / 5.0D, 0.00001D);
        assertTrue(animationPolicy.contains("EATING_WEIGHT = 8"));
        assertTrue(animationPolicy.contains("IDLE_WEIGHT = 5"));
        assertTrue(ibis.contains("WANDER_INTERVAL_TICKS = 40"));
        assertTrue(ibis.contains("new IbisFlockGoal"));
        assertTrue(flock.contains("candidate.getId() < ibis.getId()"));
        for (int roll = 0; roll < 13; roll++) {
            assertEquals(roll < 8,
                    com.seggellion.britannia_mod.entity.IbisAnimationPolicy.usesEatingAnimation(roll));
        }
    }

    @Test
    void cartAndWellModelsAreExactlyTwentyPercentLarger() throws Exception {
        String scaledModel = javaSource("client/model/DecorativeScaledModel.java");
        String handler = javaSource("client/ClientModelHandler.java");
        assertTrue(scaledModel.contains("px + (x - px) * scale"));
        assertTrue(handler.contains("model, 1.2F, 0.5F, 0.0F, pivotZ"));
        assertTrue(handler.contains("offsetY = SCALED_CARTS.contains(path) ? -0.4F"));
        for (String color : new String[] {"red", "purple", "blue", "green", "yellow", "white"}) {
            Bounds bounds = bounds(json(ASSETS.resolve(
                    "models/block/new_assets/merchant_cart_" + color + ".json")));
            assertEquals(43.085724D, bounds.width() * 1.2D, 0.00001D);
            assertEquals(45.347784D, bounds.height() * 1.2D, 0.00001D);
            assertEquals(56.691168D, bounds.depth() * 1.2D, 0.00001D);
            assertTrue(bounds.minX() >= -16.0D && bounds.maxX() <= 32.0D);
            assertTrue(bounds.minZ() >= -16.0D && bounds.maxZ() <= 32.0D);
        }
        Bounds well = bounds(json(ASSETS.resolve("models/block/new_assets/water_well.json")));
        assertEquals(19.2D, well.width() * 1.2D, 0.00001D);
        assertEquals(38.4D, well.height() * 1.2D, 0.00001D);
        assertEquals(38.4D, well.depth() * 1.2D, 0.00001D);
        assertTrue(well.maxY() <= 32.0D && well.maxZ() <= 32.0D);
    }

    @Test
    void permanentMoongateUsesOnlyTheLargerCameraFacingVisual() throws Exception {
        JsonObject state = json(ASSETS.resolve("blockstates/moongate_block.json"));
        assertEquals("britannia_mod:block/moongate_block",
                state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertEquals(2, json(ASSETS.resolve("models/block/moongate_summon_base.json"))
                .getAsJsonArray("elements").size());
        assertEquals(5, json(ASSETS.resolve("models/block/moongate_billboard.json"))
                .getAsJsonArray("elements").size());
        assertFalse(Files.exists(ASSETS.resolve("models/block/moongate_base.json")));
        String renderer = javaSource("client/renderer/MoongateBlockEntityRenderer.java");
        assertTrue(renderer.contains("getMainCamera().getYRot()"));
        assertTrue(renderer.contains("Axis.YP.rotationDegrees"));
        assertTrue(renderer.contains("VISUAL_SCALE = 1.35F"));
        assertTrue(renderer.contains("expandTowards(0.0D, 2.0D, 0.0D)"));
        assertTrue(javaSource("block/MoongateBlock.java").contains("return RenderShape.INVISIBLE"));
    }

    @Test
    void spinningWheelHasSynchronizedIdleAndAnimatedActivePresentations() throws Exception {
        JsonObject variants = json(ASSETS.resolve("blockstates/spinning_wheel.json"))
                .getAsJsonObject("variants");
        assertEquals(8, variants.size());
        for (String facing : new String[] {"north", "east", "south", "west"}) {
            assertTrue(variants.getAsJsonObject("active=false,facing=" + facing)
                    .get("model").getAsString().endsWith("spinning_wheel"));
            assertTrue(variants.getAsJsonObject("active=true,facing=" + facing)
                    .get("model").getAsString().endsWith("spinning_wheel_active"));
        }

        JsonObject active = json(ASSETS.resolve("models/block/new_assets/spinning_wheel_active.json"));
        assertEquals("britannia_mod:block/new_assets/spinning_wheel_animated",
                active.getAsJsonObject("textures").get("2").getAsString());
        assertTrue(active.getAsJsonArray("elements").asList().stream()
                .map(value -> value.getAsJsonObject())
                .anyMatch(element -> element.has("name")
                        && element.get("name").getAsString().equals("bobbin")));

        Path strip = ASSETS.resolve("textures/block/new_assets/spinning_wheel_animated.png");
        BufferedImage image = ImageIO.read(strip.toFile());
        assertEquals(128, image.getWidth());
        assertEquals(512, image.getHeight());
        assertEquals(4, image.getHeight() / image.getWidth());
        JsonObject animation = json(Path.of(strip + ".mcmeta")).getAsJsonObject("animation");
        assertEquals(2, animation.get("frametime").getAsInt());

        String wheel = javaSource("block/SpinningWheelBlock.java");
        assertTrue(wheel.contains("BooleanProperty.create(\"active\")"));
        assertTrue(wheel.contains("level.scheduleTick(pos, this, ACTIVE_TICKS)"));
        assertTrue(wheel.contains("state.setValue(ACTIVE, false)"));
    }

    @Test
    void scarecrowHasNoCoincidentCubesOrMixedBakedShading() throws Exception {
        JsonObject model = json(ASSETS.resolve("models/block/new_assets/scarecrow.json"));
        assertFalse(model.get("ambientocclusion").getAsBoolean());
        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(12, elements.size());
        for (var value : elements) {
            JsonObject element = value.getAsJsonObject();
            assertTrue(element.has("shade"));
            assertFalse(element.get("shade").getAsBoolean());
            assertFalse(element.has("name")
                    && element.get("name").getAsString().endsWith(" inverted"));
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(from.get(axis).getAsDouble() <= to.get(axis).getAsDouble(),
                        "negative-size scarecrow cube remained on axis " + axis);
            }
        }
    }

    @Test
    void fountainScaleAndCenteredThreeByThreeFootprintStayCoupled() throws Exception {
        Bounds fountain = bounds(json(ASSETS.resolve("models/block/new_assets/fountain.json")));
        assertEquals(41.6D, fountain.width() * 1.3D, 0.00001D);
        assertEquals(44.2D, fountain.height() * 1.3D, 0.00001D);
        assertEquals(41.6D, fountain.depth() * 1.3D, 0.00001D);

        JsonObject state = json(ASSETS.resolve("blockstates/fountain.json"));
        for (var part : state.getAsJsonArray("multipart")) {
            assertEquals("13", part.getAsJsonObject().getAsJsonObject("when")
                    .get("part").getAsString());
        }
        String registry = javaSource("registry/BlockRegistry.java");
        assertTrue(registry.contains("-1, 1, -1, 1, -1, 1"));
        assertTrue(registry.contains("fountainBaseShape(x, z)"));
        assertTrue(javaSource("client/ClientModelHandler.java")
                .contains("new DecorativeScaledModel(model, 1.3F, 0.5F, -1.0F, 0.5F)"));
    }

    @Test
    void hedgeBushHasBottomMiddleAndTopStackModelsUnderTheBreakingNewId() throws Exception {
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));
        assertEquals("Hedge Bush",
                language.get("block.britannia_mod.hedge_bush").getAsString());
        assertFalse(language.has("block.britannia_mod.moonglow_bush"));
        JsonObject variants = json(ASSETS.resolve("blockstates/hedge_bush.json"))
                .getAsJsonObject("variants");
        assertEquals(12, variants.size());
        for (String segment : new String[] {"bottom", "middle", "top"}) {
            assertTrue(Files.isRegularFile(ASSETS.resolve(
                    "models/block/new_assets/hedge_bush_" + segment + ".json")));
        }
        String block = javaSource("block/HedgeBushBlock.java");
        assertTrue(block.contains("SEGMENT"));
        assertTrue(block.contains("below.is(this)"));
    }

    @Test
    void ladderScarecrowAndDressFormRepairsArePresent() throws Exception {
        String ladder = javaSource("block/LadderMultiblockBlock.java");
        String registry = javaSource("registry/BlockRegistry.java");
        String scarecrow = javaSource("item/AdventureScarecrowItem.java");
        assertTrue(ladder.contains("living.horizontalCollision"));
        assertTrue(ladder.contains("Math.max(movement.y, 0.2D)"));
        assertTrue(registry.contains("y == 2 ? Block.box(0, 14, 8, 16, 16, 16)"));
        assertTrue(scarecrow.contains("instanceof CommunityFarmBlock"));
        assertTrue(javaSource("registry/ItemRegistry.java").contains("new AdventureScarecrowItem"));

        JsonObject dress = json(ASSETS.resolve("models/block/new_assets/dress_form.json"));
        assertFalse(dress.get("ambientocclusion").getAsBoolean());
        JsonObject head = dress.getAsJsonArray("elements").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(element -> element.getAsJsonArray("from").get(1).getAsDouble() == 24.0D)
                .findFirst().orElseThrow();
        assertFalse(head.get("shade").getAsBoolean());
        JsonArray uv = head.getAsJsonObject("faces").getAsJsonObject("up").getAsJsonArray("uv");
        assertEquals(5.0D, uv.get(0).getAsDouble());
        assertEquals(5.25D, uv.get(2).getAsDouble());
    }

    @Test
    void poolOfBloodExposesAllEightVisualVariants() throws Exception {
        String blocks = javaSource("registry/BlockRegistry.java");
        String items = javaSource("registry/ItemRegistry.java");
        String creative = javaSource("registry/CreativeTabRegistry.java");
        assertTrue(blocks.contains("\"pool_of_blood\""));
        assertTrue(items.contains("POOL_OF_BLOOD_ITEM"));
        assertTrue(creative.contains("ItemRegistry.POOL_OF_BLOOD_ITEM.get()"));
        assertTrue(javaSource("block/PoolOfBloodBlock.java").contains("VARIANT_COUNT = 8"));

        JsonObject variants = json(ASSETS.resolve("blockstates/pool_of_blood.json"))
                .getAsJsonObject("variants");
        assertEquals(32, variants.size());
        for (int variant = 0; variant < 8; variant++) {
            assertTrue(Files.isRegularFile(ASSETS.resolve(
                    "models/block/new_assets/pool_of_blood_" + variant + ".json")));
            assertTrue(Files.isRegularFile(ASSETS.resolve(
                    "textures/block/new_assets/pool_of_blood/variant_" + variant + ".png")));
        }
        json(PROJECT.resolve("src/main/resources/data/britannia_mod/loot_table/blocks/pool_of_blood.json"));
    }

    private static Bounds bounds(JsonObject model) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (var value : model.getAsJsonArray("elements")) {
            JsonObject element = value.getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            minX = Math.min(minX, Math.min(from.get(0).getAsDouble(), to.get(0).getAsDouble()));
            minY = Math.min(minY, Math.min(from.get(1).getAsDouble(), to.get(1).getAsDouble()));
            minZ = Math.min(minZ, Math.min(from.get(2).getAsDouble(), to.get(2).getAsDouble()));
            maxX = Math.max(maxX, Math.max(from.get(0).getAsDouble(), to.get(0).getAsDouble()));
            maxY = Math.max(maxY, Math.max(from.get(1).getAsDouble(), to.get(1).getAsDouble()));
            maxZ = Math.max(maxZ, Math.max(from.get(2).getAsDouble(), to.get(2).getAsDouble()));
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static String javaSource(String relative) throws Exception {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative));
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double width() { return maxX - minX; }
        double height() { return maxY - minY; }
        double depth() { return maxZ - minZ; }
    }
}

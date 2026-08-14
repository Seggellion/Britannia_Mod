package com.seggellion.britannia_mod.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.entity.IbisVariant;
import com.seggellion.britannia_mod.registry.CityRegistry;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class JhelomIbisPopulationTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void onePopulationLimitCoversAllThreeJhelomAreas() {
        List<AABB> areas = CityRegistry.getCityAreas(JhelomIbisPopulation.CITY_NAME);

        assertEquals(3, areas.size());
        assertEquals(15, JhelomIbisPopulation.MAX_POPULATION);
        for (AABB area : areas) {
            assertTrue(JhelomIbisPopulation.isInsideAny(area.getCenter(), areas));
        }
        assertFalse(JhelomIbisPopulation.isInsideAny(Vec3.ZERO, areas));
    }

    @Test
    void excessCalculationEnforcesAnExactFifteenBirdCap() {
        assertEquals(0, JhelomIbisPopulation.excessFor(0));
        assertEquals(0, JhelomIbisPopulation.excessFor(14));
        assertEquals(0, JhelomIbisPopulation.excessFor(15));
        assertEquals(1, JhelomIbisPopulation.excessFor(16));
        assertEquals(10, JhelomIbisPopulation.excessFor(25));
    }

    @Test
    void unknownVariantIdsSafelyFallBackToWhite() {
        assertEquals(IbisVariant.WHITE, IbisVariant.byId(0));
        assertEquals(IbisVariant.SCARLET, IbisVariant.byId(1));
        assertEquals(IbisVariant.WHITE, IbisVariant.byId(-1));
        assertEquals(IbisVariant.WHITE, IbisVariant.byId(99));
    }

    @Test
    void convertedResourcesPreserveTheWhiteSourceAndScarletUvAlpha() throws Exception {
        Path entityTextures = PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/textures/entity");
        Path whitePath = entityTextures.resolve("ibis_white.png");
        Path scarletPath = entityTextures.resolve("ibis_scarlet.png");
        BufferedImage white = ImageIO.read(whitePath.toFile());
        BufferedImage scarlet = ImageIO.read(scarletPath.toFile());

        assertEquals("5127c6846e013cc7021bc63c906693526685d1787906fc24ca4a43c232fa2507",
                sha256(whitePath));
        assertEquals(512, white.getWidth());
        assertEquals(512, white.getHeight());
        assertEquals(white.getWidth(), scarlet.getWidth());
        assertEquals(white.getHeight(), scarlet.getHeight());

        int changedPixels = 0;
        for (int y = 0; y < white.getHeight(); y++) {
            for (int x = 0; x < white.getWidth(); x++) {
                int whiteArgb = white.getRGB(x, y);
                int scarletArgb = scarlet.getRGB(x, y);
                assertEquals(whiteArgb >>> 24, scarletArgb >>> 24,
                        "scarlet generation changed alpha at " + x + "," + y);
                if (whiteArgb != scarletArgb) {
                    changedPixels++;
                }
            }
        }
        assertEquals(14_355, changedPixels);

        String animations = Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/animations/ibis.animation.json"));
        assertTrue(animations.contains("animation.model.walk"));
        assertTrue(animations.contains("animation.model.idle"));
        assertTrue(animations.contains("animation.model.eating"));
    }

    @Test
    void ibisIsNotRegisteredForGlobalBiomeSpawning() throws IOException {
        Path dataRoot = PROJECT.resolve("src/main/resources/data");
        try (var files = Files.walk(dataRoot)) {
            boolean globalSpawnReference = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/biome_modifier/"))
                    .anyMatch(path -> readUnchecked(path).contains("britannia_mod:ibis"));
            assertFalse(globalSpawnReference);
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect biome modifier " + path, exception);
        }
    }
}

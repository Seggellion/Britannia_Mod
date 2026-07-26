package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone13RenderFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.client.banner.*;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerPlacedAppearanceAndKeyTest {
    @BeforeAll
    static void setup() {
        Milestone13RenderFixtures.ensureLoaded();
    }

    @Test
    void itemAndPlacedFormsUseExactlyTheSameAppearanceForAllMaterialsAndMounts() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        for (String materialPath : java.util.List.of("cotton", "wool", "linen", "silk")) {
            var material = Milestone13RenderFixtures.material(materialPath);
            for (String mountPath : java.util.List.of("brass", "iron")) {
                var mount = Milestone13RenderFixtures.mount(mountPath);
                BannerInstanceState instance = Milestone13RenderFixtures.state(
                        definition, material, Milestone13RenderFixtures.natural(material), mount);
                BannerAppearanceState item = itemAppearance(instance, 7, 11);
                BannerAppearanceState placed = placedAppearance(instance, Direction.NORTH,
                        BannerOrientation.WALL_PARALLEL, 3, 2, 7, 11);
                assertEquals(item, placed, materialPath + "/" + mountPath);
                assertEquals(item.key(), placed.key());
                assertAll(
                        () -> assertEquals(item.definitionId(), placed.definitionId()),
                        () -> assertEquals(item.materialId(), placed.materialId()),
                        () -> assertEquals(item.resolvedColourId(), placed.resolvedColourId()),
                        () -> assertEquals(item.displaySrgb(), placed.displaySrgb()),
                        () -> assertEquals(item.fabricBase(), placed.fabricBase()),
                        () -> assertEquals(item.dyeMask(), placed.dyeMask()),
                        () -> assertEquals(item.staticOverlay(), placed.staticOverlay()),
                        () -> assertEquals(item.mountId(), placed.mountId()),
                        () -> assertEquals(item.mountTexture(), placed.mountTexture()),
                        () -> assertEquals(item.failure(), placed.failure()));
            }
        }
    }

    @Test
    void naturalAndDyedCottonAgreeBetweenItemAndPlacedForms() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("small");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var brass = Milestone13RenderFixtures.mount("brass");
        BannerInstanceState natural = Milestone13RenderFixtures.state(
                definition, cotton, Milestone13RenderFixtures.natural(cotton), brass);
        BannerInstanceState dyed = Milestone13RenderFixtures.state(
                definition, cotton, Milestone13RenderFixtures.dyed(cotton), brass);
        assertTrue(itemAppearance(natural, 1, 2).naturalColour());
        assertFalse(itemAppearance(dyed, 1, 2).naturalColour());
        assertEquals(itemAppearance(natural, 1, 2), placedAppearance(
                natural, Direction.SOUTH, BannerOrientation.WALL_PARALLEL, 1, 1, 1, 2));
        assertEquals(itemAppearance(dyed, 1, 2), placedAppearance(
                dyed, Direction.SOUTH, BannerOrientation.WALL_PARALLEL, 1, 1, 1, 2));
        assertNotEquals(itemAppearance(natural, 1, 2).key(), itemAppearance(dyed, 1, 2).key());
    }

    @Test
    void sourcePigmentAndExternalCustomNameDataCannotChangeAppearance() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("small");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var brass = Milestone13RenderFixtures.mount("brass");
        BannerInstanceState plain = Milestone13RenderFixtures.state(
                definition, cotton, Milestone13RenderFixtures.dyed(cotton), brass);
        BannerInstanceState pigmentOnly = Milestone13RenderFixtures.state(
                definition, cotton, Milestone13RenderFixtures.dyed(cotton),
                Optional.of(PigmentId.parse("britannia_mod:woad_blue")), brass);
        assertEquals(itemAppearance(plain, 3, 4).key(), itemAppearance(pigmentOnly, 3, 4).key());
        assertEquals(placedAppearance(plain, Direction.WEST, BannerOrientation.WALL_PARALLEL,
                        1, 1, 3, 4).key(),
                placedAppearance(pigmentOnly, Direction.WEST, BannerOrientation.WALL_PARALLEL,
                        1, 1, 3, 4).key());
    }

    @Test
    void placedContextChangesOnlyItsWrapperKey() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var cotton = Milestone13RenderFixtures.material("cotton");
        BannerInstanceState instance = Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.natural(cotton), Milestone13RenderFixtures.mount("brass"));
        BannerPlacedRenderState northParallel = placed(instance, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 3, 2, 5, 6);
        BannerPlacedRenderState northPerpendicular = placed(instance, Direction.NORTH,
                BannerOrientation.WALL_PERPENDICULAR, 3, 2, 5, 6);
        BannerPlacedRenderState eastParallel = placed(instance, Direction.EAST,
                BannerOrientation.WALL_PARALLEL, 3, 2, 5, 6);
        assertEquals(northParallel.appearance().key(), northPerpendicular.appearance().key());
        assertEquals(northParallel.appearance().key(), eastParallel.appearance().key());
        assertNotEquals(northParallel.key(), northPerpendicular.key());
        assertNotEquals(northParallel.key(), eastParallel.key());
        assertEquals(northParallel.key(), placed(instance, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 3, 2, 5, 6).key());
        assertEquals(northParallel.key().hashCode(), placed(instance, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 3, 2, 5, 6).key().hashCode());
    }

    @Test
    void materialColourMountDataAndResourceGenerationsInvalidateKeys() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("large");
        var cotton = Milestone13RenderFixtures.material("cotton");
        var wool = Milestone13RenderFixtures.material("wool");
        BannerInstanceState base = Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.natural(cotton), Milestone13RenderFixtures.mount("brass"));
        BannerPlacedRenderKey key = placed(base, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 3, 2, 9, 10).key();
        assertNotEquals(key, placed(Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.dyed(cotton), Milestone13RenderFixtures.mount("brass")),
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 3, 2, 9, 10).key());
        assertNotEquals(key, placed(Milestone13RenderFixtures.state(definition, wool,
                Milestone13RenderFixtures.natural(wool), Milestone13RenderFixtures.mount("brass")),
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 3, 2, 9, 10).key());
        assertNotEquals(key, placed(Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.natural(cotton), Milestone13RenderFixtures.mount("iron")),
                Direction.NORTH, BannerOrientation.WALL_PARALLEL, 3, 2, 9, 10).key());
        assertNotEquals(key, placed(base, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 3, 2, 10, 10).key());
        assertNotEquals(key, placed(base, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 3, 2, 9, 11).key());
    }

    @Test
    void missingAndDisabledContentUseTheSameTypedAppearanceFallback() {
        var definition = Milestone13RenderFixtures.definitionForGeometry("small");
        var cotton = Milestone13RenderFixtures.material("cotton");
        BannerInstanceState instance = Milestone13RenderFixtures.state(definition, cotton,
                Milestone13RenderFixtures.natural(cotton), Milestone13RenderFixtures.mount("brass"));
        BannerRenderDataSnapshot disabled = BannerRenderDataSnapshot.fromRegistry(
                RegistrySnapshotTestFactory.withDisabledBanner(
                        Milestone13RenderFixtures.registry(), definition));
        ClientBannerRenderPublication publication = new ClientBannerRenderPublication(disabled, 21, true);
        BannerAppearanceState item = itemAppearance(instance, publication, 12);
        var entity = Milestone13RenderFixtures.entity(BlockPos.ZERO, Direction.NORTH,
                BannerOrientation.WALL_PARALLEL, 1, 1, instance);
        BannerPlacedRenderState placed = BannerPlacedRenderStateExtractor.extract(entity, publication,
                BannerAssetAvailability.allExpected(), 12);
        assertEquals(BannerRenderFailure.MISSING_DEFINITION, item.failure());
        assertEquals(item, placed.appearance());
        assertEquals(BannerPlacedRenderFailure.APPEARANCE_FALLBACK, placed.failure());
        assertEquals(instance, entity.bannerState().orElseThrow());
    }

    private static BannerAppearanceState itemAppearance(
            BannerInstanceState instance, long dataGeneration, long resourceGeneration) {
        return itemAppearance(instance,
                Milestone13RenderFixtures.publication(dataGeneration), resourceGeneration);
    }

    private static BannerAppearanceState itemAppearance(
            BannerInstanceState instance,
            ClientBannerRenderPublication publication,
            long resourceGeneration) {
        ItemStack stack = new ItemStack(Milestone7RegisteredTestContent.banner());
        stack.set(Milestone7RegisteredTestContent.component(), instance);
        return BannerRenderStateExtractor.extract(stack, Milestone7RegisteredTestContent.banner(),
                Milestone7RegisteredTestContent.component(), publication,
                BannerAssetAvailability.allExpected(), resourceGeneration).appearance();
    }

    private static BannerAppearanceState placedAppearance(
            BannerInstanceState instance, Direction facing, BannerOrientation orientation,
            int width, int height, long dataGeneration, long resourceGeneration) {
        return placed(instance, facing, orientation, width, height,
                dataGeneration, resourceGeneration).appearance();
    }

    private static BannerPlacedRenderState placed(
            BannerInstanceState instance, Direction facing, BannerOrientation orientation,
            int width, int height, long dataGeneration, long resourceGeneration) {
        return Milestone13RenderFixtures.placed(Milestone13RenderFixtures.entity(
                new BlockPos(-17, 70, 31), facing, orientation, width, height, instance),
                dataGeneration, resourceGeneration);
    }
}

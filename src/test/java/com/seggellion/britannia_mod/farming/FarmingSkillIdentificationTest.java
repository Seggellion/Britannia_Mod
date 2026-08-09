package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.network.SkillSyncPayload;
import com.seggellion.britannia_mod.skill.ClientSkillTable;
import com.seggellion.britannia_mod.skill.SkillManager;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmingSkillIdentificationTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Component ORIGINAL = Component.literal("Exact Species Name");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allSeventyFourApprovedMappingsHaveAnExplicitIdentityPolicy() {
        List<FarmingSkillRequirementResolver.ResolvedRequirement> requirements = requirements();
        assertEquals(74, requirements.size());

        int masked = 0;
        int nativeCompatibility = 0;
        for (FarmingSkillRequirementResolver.ResolvedRequirement resolved : requirements) {
            Item item = policyItem(resolved);
            ItemStack stack = new ItemStack(item, 3);
            ItemStack before = stack.copy();
            FarmingPlantingMaterialCategory expected = expectedCategory(resolved);
            assertEquals(expected, FarmingPlantingMaterialCategory.resolve(resolved, item), resolved.speciesId());

            var below = FarmingPlantingItemPresentation.resolveResolved(
                    viewer(resolved.minimumFarmingSkill() - 0.01F, false,
                            SkillManager.SkillDataState.AVAILABLE, 1L),
                    stack, ORIGINAL, FarmingPlantingItemPresentation.Surface.ITEM_NAME, resolved);
            assertEquals(before.getCount(), stack.getCount(), resolved.speciesId());
            assertTrue(ItemStack.isSameItemSameComponents(before, stack), resolved.speciesId());
            if (expected == FarmingPlantingMaterialCategory.NATIVE_VANILLA_OUTSIDE_SCOPE) {
                nativeCompatibility++;
                assertFalse(below.applicable(), resolved.speciesId());
                assertTrue(below.identified(), resolved.speciesId());
                assertEquals(ORIGINAL, below.displayName(), resolved.speciesId());
                assertEquals(FarmingPlantingItemPresentation.IdentityDisclosurePolicy.NATIVE_VANILLA_OUTSIDE_SCOPE,
                        below.identityDisclosurePolicy(), resolved.speciesId());
                continue;
            }

            masked++;
            assertTrue(below.applicable(), resolved.speciesId());
            assertFalse(below.identified(), resolved.speciesId());
            assertEquals(Component.translatable(expected.unidentifiedNameKey()), below.displayName(), resolved.speciesId());
            assertFalse(below.minimumRequirementVisible(), resolved.speciesId());
            assertFalse(below.currentSkillVisible(), resolved.speciesId());

            for (float current : new float[]{resolved.minimumFarmingSkill(), resolved.minimumFarmingSkill() + 0.01F}) {
                var identified = FarmingPlantingItemPresentation.resolveResolved(
                        viewer(current, false, SkillManager.SkillDataState.AVAILABLE, 2L),
                        stack, ORIGINAL, FarmingPlantingItemPresentation.Surface.ITEM_NAME, resolved);
                assertTrue(identified.identified(), resolved.speciesId() + " at " + current);
                assertEquals(ORIGINAL, identified.displayName(), resolved.speciesId());
                assertEquals(before.getCount(), stack.getCount(), resolved.speciesId());
                assertTrue(ItemStack.isSameItemSameComponents(before, stack), resolved.speciesId());
            }
        }
        assertEquals(68, masked);
        assertEquals(6, nativeCompatibility);
    }

    @Test
    void grapeFlowerAndHighRequirementSentinelsUseApprovedGenericNames() {
        assertGeneric("grapes", FarmingPlantingMaterialCategory.GRAPES,
                "item.britannia_mod.unidentified_grape_seed");
        assertGeneric("britannia_mod:poppy", FarmingPlantingMaterialCategory.FLOWER_SEEDS,
                "item.britannia_mod.unidentified_flower_seeds");
        assertGeneric("britannia_mod:orfluer", FarmingPlantingMaterialCategory.FLOWER_SEEDS,
                "item.britannia_mod.unidentified_flower_seeds");
        assertGeneric("nightshade", FarmingPlantingMaterialCategory.SEEDS,
                "item.britannia_mod.unidentified_seeds");

        var poppy = requirement("britannia_mod:poppy");
        assertEquals(20.0F, poppy.minimumFarmingSkill());
        assertEquals(100.0F, FlowerInteractionService.POPPY_STAGE_SEVEN_SKILL);
    }

    @Test
    void everySupportedSurfaceUsesTheSameViewerSpecificIdentity() {
        var orfluer = requirement("britannia_mod:orfluer");
        for (FarmingPlantingItemPresentation.Surface surface : FarmingPlantingItemPresentation.Surface.values()) {
            var low = presentation(orfluer, Items.STICK, 94.99F, false,
                    SkillManager.SkillDataState.AVAILABLE, surface);
            assertEquals(Component.translatable("item.britannia_mod.unidentified_flower_seeds"),
                    low.displayName(), surface.name());
            assertEquals(low.displayName(), low.narrationText(), surface.name());

            var exact = presentation(orfluer, Items.STICK, 95.0F, false,
                    SkillManager.SkillDataState.AVAILABLE, surface);
            assertEquals(ORIGINAL, exact.displayName(), surface.name());
            assertEquals(ORIGINAL, exact.narrationText(), surface.name());
        }
    }

    @Test
    void loadingUnavailableAndUnknownSkillStateFailClosedWhileBypassesIdentify() {
        var grapes = requirement("grapes");
        for (SkillManager.SkillDataState state : List.of(
                SkillManager.SkillDataState.NOT_LOADED,
                SkillManager.SkillDataState.LOADING,
                SkillManager.SkillDataState.UNAVAILABLE
        )) {
            var hidden = presentation(grapes, Items.STICK, 100.0F, false, state,
                    FarmingPlantingItemPresentation.Surface.TOOLTIP);
            assertFalse(hidden.identified(), state.name());
            assertEquals(FarmingPlantingItemPresentation.IdentityDisclosurePolicy.FAIL_CLOSED,
                    hidden.identityDisclosurePolicy(), state.name());
        }

        for (SkillManager.SkillDataState state : SkillManager.SkillDataState.values()) {
            var bypass = presentation(grapes, Items.STICK, 0.0F, true, state,
                    FarmingPlantingItemPresentation.Surface.CREATIVE_INVENTORY);
            assertTrue(bypass.identified(), state.name());
            assertEquals(FarmingPlantingItemPresentation.IdentityDisclosurePolicy.ADMINISTRATIVE_BYPASS,
                    bypass.identityDisclosurePolicy(), state.name());
        }
    }

    @Test
    void identityIsPerViewerDynamicAndNeverMutatesStackComponents() {
        var grapes = requirement("grapes");
        ItemStack stack = new ItemStack(Items.STICK, 7);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Cabernet Seed"));
        CompoundTag metadata = new CompoundTag();
        metadata.putString("grape_variety", "cabernet_sauvignon");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(metadata));
        ItemStack before = stack.copy();

        var lowViewer = FarmingPlantingItemPresentation.resolveResolved(
                viewer(79.99F, false, SkillManager.SkillDataState.AVAILABLE, 10L), stack,
                Component.literal("Cabernet Seed"), FarmingPlantingItemPresentation.Surface.CONTAINER, grapes);
        var highViewer = FarmingPlantingItemPresentation.resolveResolved(
                viewer(80.0F, false, SkillManager.SkillDataState.AVAILABLE, 11L), stack,
                Component.literal("Cabernet Seed"), FarmingPlantingItemPresentation.Surface.CONTAINER, grapes);

        assertFalse(lowViewer.identified());
        assertEquals(Component.translatable("item.britannia_mod.unidentified_grape_seed"), lowViewer.displayName());
        assertTrue(highViewer.identified());
        assertEquals(Component.literal("Cabernet Seed"), highViewer.displayName());
        assertEquals(before.getCount(), stack.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(before, stack));

        var afterGain = FarmingPlantingItemPresentation.resolveResolved(
                viewer(80.0F, false, SkillManager.SkillDataState.AVAILABLE, 12L), stack,
                Component.literal("Cabernet Seed"), FarmingPlantingItemPresentation.Surface.CONTAINER, grapes);
        assertTrue(afterGain.identified());
    }

    @Test
    void clientProjectionRejectsStalePacketsAndResetsConnectionEpoch() {
        ClientSkillTable.beginSession();
        assertTrue(ClientSkillTable.applyAuthoritativeSync(
                SkillManager.SkillDataState.LOADING, Map.of(), 0L, false));
        assertTrue(ClientSkillTable.applyAuthoritativeSync(
                SkillManager.SkillDataState.AVAILABLE, Map.of("farming", 80.0F), 1L, true));
        assertFalse(ClientSkillTable.applyAuthoritativeSync(
                SkillManager.SkillDataState.AVAILABLE, Map.of("farming", 5.0F), 1L, false));
        assertEquals(80.0F, ClientSkillTable.get("farming"));
        assertTrue(ClientSkillTable.identificationBypass());

        ClientSkillTable.beginSession();
        assertEquals(SkillManager.SkillDataState.NOT_LOADED, ClientSkillTable.state());
        assertEquals(-1L, ClientSkillTable.revision());
        assertEquals(0.0F, ClientSkillTable.get("farming"));
        assertFalse(ClientSkillTable.identificationBypass());
        assertTrue(ClientSkillTable.applyAuthoritativeSync(
                SkillManager.SkillDataState.AVAILABLE, Map.of("farming", 10.0F), 0L, false));
    }

    @Test
    void skillPresentationPacketRoundTripsVersionStateRevisionBypassAndValues() {
        SkillSyncPayload expected = SkillSyncPayload.create(
                SkillManager.SkillDataState.AVAILABLE, 42L, true,
                Map.of("farming", 80.0F, "fishing", 12.5F)
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SkillSyncPayload.STREAM_CODEC.encode(buffer, expected);
            SkillSyncPayload decoded = SkillSyncPayload.STREAM_CODEC.decode(buffer);
            assertEquals(SkillSyncPayload.WIRE_VERSION, decoded.wireVersion());
            assertEquals(expected.state(), decoded.state());
            assertEquals(expected.revision(), decoded.revision());
            assertEquals(expected.identificationBypass(), decoded.identificationBypass());
            assertEquals(expected.skills(), decoded.skills());
        } finally {
            buffer.release();
        }
    }

    @Test
    void localizationAndClientOnlyIntegrationBoundariesAreExplicit() throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"))).getAsJsonObject();
        assertEquals("Unidentified Seeds", language.get("item.britannia_mod.unidentified_seeds").getAsString());
        assertEquals("Unidentified Flower Seeds",
                language.get("item.britannia_mod.unidentified_flower_seeds").getAsString());
        assertEquals("Unidentified Planting Material",
                language.get("item.britannia_mod.unidentified_planting_material").getAsString());
        assertEquals("a brown seed", language.get("item.britannia_mod.unidentified_grape_seed").getAsString());

        String mixins = Files.readString(PROJECT.resolve("src/main/resources/britannia_mod.mixins.json"));
        assertTrue(mixins.contains("client.ItemStackFarmingPresentationMixin"));
        assertTrue(mixins.contains("client.CreativeModeInventoryScreenAccessor"));

        String presentation = source("farming/FarmingPlantingItemPresentation.java");
        assertFalse(presentation.contains("net.minecraft.client"));
        assertFalse(presentation.contains("stack.set("));
        assertFalse(presentation.contains("stack.shrink("));

        String grapeItem = source("item/GrapeSeedsItem.java");
        assertFalse(grapeItem.contains("FarmingCultivationGate"));
        assertFalse(grapeItem.contains("FarmingPlantingItemPresentation"));
        assertTrue(grapeItem.contains("stack.shrink(1)"));

        String skillManager = source("skill/SkillManager.java");
        assertTrue(skillManager.contains("SkillDataState.LOADING"));
        assertTrue(skillManager.contains("SkillDataState.UNAVAILABLE"));
        assertTrue(skillManager.contains("PLAYER_SKILL_REVISIONS.merge"));

        String clientSetup = source("ClientModSetup.java");
        assertTrue(clientSetup.contains("addListener(ClientEventHandler::onClientLogin)"));
        assertTrue(clientSetup.contains("addListener(ClientEventHandler::onClientLogout)"));
        assertTrue(clientSetup.contains("addListener(ClientEventHandler::onRenderNameTag)"));
    }

    private static void assertGeneric(String speciesId, FarmingPlantingMaterialCategory category, String key) {
        var resolved = requirement(speciesId);
        var result = presentation(resolved, policyItem(resolved), resolved.minimumFarmingSkill() - 0.01F,
                false, SkillManager.SkillDataState.AVAILABLE,
                FarmingPlantingItemPresentation.Surface.ITEM_NAME);
        assertEquals(category, result.genericCategory());
        assertEquals(Component.translatable(key), result.displayName());
    }

    private static FarmingPlantingItemPresentation.PresentationResult presentation(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            Item item,
            float current,
            boolean bypass,
            SkillManager.SkillDataState state,
            FarmingPlantingItemPresentation.Surface surface
    ) {
        return FarmingPlantingItemPresentation.resolveResolved(
                viewer(current, bypass, state, 1L), new ItemStack(item), ORIGINAL, surface, resolved);
    }

    private static FarmingPlantingItemPresentation.ViewerState viewer(
            float current, boolean bypass, SkillManager.SkillDataState state, long revision
    ) {
        return new FarmingPlantingItemPresentation.ViewerState(state, current, bypass, revision);
    }

    private static FarmingSkillRequirementResolver.ResolvedRequirement requirement(String speciesId) {
        return requirements().stream().filter(row -> row.speciesId().equals(speciesId)).findFirst().orElseThrow();
    }

    private static List<FarmingSkillRequirementResolver.ResolvedRequirement> requirements() {
        List<FarmingSkillRequirementResolver.ResolvedRequirement> result = new ArrayList<>();
        CropRegistry.all().forEach(crop -> result.add(
                new FarmingSkillRequirementResolver.ResolvedRequirement(crop.id(), crop)));
        FlowerRegistry.initial().definitions().values().forEach(flower -> result.add(
                new FarmingSkillRequirementResolver.ResolvedRequirement(flower.id().toString(), flower)));
        return result;
    }

    private static Item policyItem(FarmingSkillRequirementResolver.ResolvedRequirement resolved) {
        return switch (resolved.speciesId()) {
            case "vanilla_potato" -> Items.POTATO;
            case "wheat" -> Items.WHEAT_SEEDS;
            case "brown_mushroom" -> Items.BROWN_MUSHROOM;
            case "red_mushroom" -> Items.RED_MUSHROOM;
            case "vanilla_pumpkin" -> Items.PUMPKIN_SEEDS;
            case "vanilla_melon" -> Items.MELON_SEEDS;
            default -> Items.STICK;
        };
    }

    private static FarmingPlantingMaterialCategory expectedCategory(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved
    ) {
        if (List.of("vanilla_potato", "wheat", "brown_mushroom", "red_mushroom",
                "vanilla_pumpkin", "vanilla_melon").contains(resolved.speciesId())) {
            return FarmingPlantingMaterialCategory.NATIVE_VANILLA_OUTSIDE_SCOPE;
        }
        if ("grapes".equals(resolved.speciesId())) {
            return FarmingPlantingMaterialCategory.GRAPES;
        }
        if (resolved.requirement() instanceof FlowerDefinition) {
            return FarmingPlantingMaterialCategory.FLOWER_SEEDS;
        }
        return FarmingPlantingMaterialCategory.SEEDS;
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/" + relative));
    }
}

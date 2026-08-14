package com.seggellion.britannia_mod.client.branding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.LoadingScreenBackgroundPolicy;
import com.seggellion.britannia_mod.client.TitleBrandingLayout;
import com.seggellion.britannia_mod.client.TitleBrandingRenderer;
import com.seggellion.britannia_mod.client.TitleWebsiteButtonBranding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.junit.jupiter.api.Test;

class ClientBrandingRuntimeContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path MINECRAFT_GUI = RESOURCES.resolve("assets/minecraft/textures/gui");
    private static final Path MINECRAFT_TEXTS = RESOURCES.resolve("assets/minecraft/texts");

    private static final List<String> REQUIRED_RUNTIME_RESOURCES = List.of(
            "assets/britannia_mod/textures/screens/chest_sequence.png",
            "assets/minecraft/texts/splashes.txt",
            "assets/minecraft/textures/gui/footer_separator.png",
            "assets/minecraft/textures/gui/header_separator.png",
            "assets/minecraft/textures/gui/menu_background.png",
            "assets/minecraft/textures/gui/menu_list_background.png",
            "assets/minecraft/textures/gui/tab_header_background.png",
            "assets/minecraft/textures/gui/title/edition.png",
            "assets/minecraft/textures/gui/title/minceraft.png",
            "assets/minecraft/textures/gui/title/minecraft.png",
            "britannia_mod.mixins.json");

    private static final Set<String> APPROVED_GUI_OVERRIDES = Set.of(
            "footer_separator.png",
            "header_separator.png",
            "menu_background.png",
            "menu_list_background.png",
            "tab_header_background.png",
            "title/edition.png",
            "title/minceraft.png",
            "title/minecraft.png");

    @Test
    void allBrandingRuntimeResourcesExistAtExactPaths() {
        for (String relativePath : REQUIRED_RUNTIME_RESOURCES) {
            Path resource = RESOURCES.resolve(relativePath);
            assertTrue(Files.isRegularFile(resource), () -> "Missing runtime branding resource: " + relativePath);
        }
    }

    @Test
    void minecraftBrandingDirectoriesContainOnlyApprovedRuntimeFiles() throws Exception {
        assertEquals(APPROVED_GUI_OVERRIDES, relativeFiles(MINECRAFT_GUI));
        assertEquals(Set.of("splashes.txt"), relativeFiles(MINECRAFT_TEXTS));
    }

    @Test
    void brandingEventSubscribersAreClientOnly() {
        assertClientOnlySubscriber(TitleBrandingRenderer.class);
        assertClientOnlySubscriber(TitleWebsiteButtonBranding.class);
    }

    @Test
    void nonMixinBrandingTypesStayInTheClientPackage() {
        for (Class<?> type : List.of(
                LoadingScreenBackgroundPolicy.class,
                TitleBrandingLayout.class,
                TitleBrandingRenderer.class,
                TitleWebsiteButtonBranding.class)) {
            assertTrue(
                    type.getPackageName().startsWith("com.seggellion.britannia_mod.client"),
                    () -> "Branding type escaped the client package: " + type.getName());
        }
    }

    @Test
    void brandingMixinsAreRegisteredOnlyInTheClientSection() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(RESOURCES.resolve("britannia_mod.mixins.json")))
                .getAsJsonObject();
        Set<String> commonMixins = strings(config.getAsJsonArray("mixins"));
        Set<String> clientMixins = strings(config.getAsJsonArray("client"));

        for (String brandingMixin : List.of("LoadingScreenPanoramaMixin", "TitleScreenBackgroundMixin")) {
            assertTrue(clientMixins.contains(brandingMixin), () -> "Missing client mixin: " + brandingMixin);
            assertFalse(commonMixins.contains(brandingMixin), () -> "Branding mixin registered as common: " + brandingMixin);
        }
    }

    private static void assertClientOnlySubscriber(Class<?> type) {
        EventBusSubscriber annotation = type.getAnnotation(EventBusSubscriber.class);
        assertNotNull(annotation, () -> "Missing EventBusSubscriber annotation: " + type.getName());
        assertArrayEquals(new Dist[] {Dist.CLIENT}, annotation.value(), () -> "Subscriber is not client-only: " + type.getName());
    }

    private static Set<String> relativeFiles(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> strings(JsonArray array) {
        return array.asList().stream().map(element -> element.getAsString()).collect(Collectors.toSet());
    }
}

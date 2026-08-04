package com.seggellion.britannia_mod.structure.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InteriorDecoratorMilestoneFiveScopeTest {
    @Test
    void existingDecoratorRegistrationIsReusedExactlyOnceAndShrineDispatchPrecedesRotation() throws IOException {
        String registry = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        assertEquals(1, occurrences(registry, "INTERIOR_DECORATOR_TOOL ="));
        assertEquals(1, occurrences(registry, "new InteriorDecoratorToolItem("));

        String item = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/item/InteriorDecoratorToolItem.java"));
        int shrine = item.indexOf("ShrineVariantCycleService.cycle(");
        int rotation = item.indexOf("Rotate horizontal blocks");
        assertTrue(shrine > 0 && shrine < rotation);
        assertTrue(item.contains("LARGE_STRUCTURE_ANCHOR"));
        assertTrue(item.contains("LARGE_STRUCTURE_PART"));
        assertTrue(item.contains("CarpetTeleporterBlock style cycling"));
        assertTrue(item.contains("ThinWall style cycling"));
        assertTrue(item.contains("BlankSignHolder style cycling"));
    }

    @Test
    void commonCycleAndAuthorizationCodeHaveNoClientOnlyImportsOrCustomPacket() throws IOException {
        String cycle = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/structure/interaction/ShrineVariantCycleService.java"));
        String authorization = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/structure/interaction/DecoratorAuthorization.java"));
        assertFalse(cycle.contains("net.minecraft.client"));
        assertFalse(authorization.contains("net.minecraft.client"));
        assertFalse(cycle.contains("Payload"));
        assertFalse(cycle.contains("ShrinePlacement"));
        assertFalse(cycle.contains("ShrineRemoval"));
        assertFalse(cycle.contains("reverse"));
    }

    @Test
    void ownerApprovedPlaceholderAssetsRetainTheirMilestoneFourHashes() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("animations/shrine.animation.json", "F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E");
        expected.put("geo/shrine.geo.json", "C31915013A7D50D1732225764D4F94FEB1AD141515BAC0F3CADC81E5C8B3BCA0");
        expected.put("geo/shrine_missing.geo.json", "460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781");
        expected.put("models/item/shrine.json", "829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985");
        expected.put("textures/block/shrine/chaos.png", "D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3");
        expected.put("textures/block/shrine/compassion.png", "79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC");
        expected.put("textures/block/shrine/honesty.png", "D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC");
        expected.put("textures/block/shrine/honor.png", "ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381");
        expected.put("textures/block/shrine/humility.png", "E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A");
        expected.put("textures/block/shrine/justice.png", "5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B");
        expected.put("textures/block/shrine/sacrifice.png", "E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4");
        expected.put("textures/block/shrine/spirituality.png", "D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1");
        expected.put("textures/block/shrine/valor.png", "A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F");
        Path root = Path.of("src/main/resources/assets/britannia_mod");
        for (var asset : expected.entrySet()) {
            assertEquals(asset.getValue(), sha256(root.resolve(asset.getKey())), asset.getKey());
        }
    }

    @Test
    void noMilestoneSixProductionClassOrRecipeWasAdded() throws IOException {
        try (var files = Files.walk(Path.of("src/main"))) {
            var paths = files.filter(Files::isRegularFile).map(Path::toString).toList();
            assertFalse(paths.stream().anyMatch(path -> path.contains("MilestoneSix")));
            assertFalse(paths.stream().anyMatch(path -> path.contains("monolith") && path.endsWith(".java")));
            assertFalse(paths.stream().anyMatch(path -> path.contains("recipes") && path.contains("shrine")));
        }
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().withUpperCase().formatHex(digest);
    }
}

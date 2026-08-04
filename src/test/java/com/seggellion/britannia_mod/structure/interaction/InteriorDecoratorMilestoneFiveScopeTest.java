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
    void currentOwnerApprovedShrineAssetsHaveExpectedHashes() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("animations/shrine.animation.json", "F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E");
        expected.put("geo/shrine.geo.json", "374E8455075B8EFFCDFE4E36432FB9254A112F777141D319F90B5545EA92AB51");
        expected.put("geo/shrine_missing.geo.json", "460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781");
        expected.put("models/item/shrine.json", "829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985");
        expected.put("textures/block/shrine/chaos.png", "9365AB11463B1442FEE89D131AA0197A38E7F5FE513102E44182D2B1595F4702");
        expected.put("textures/block/shrine/compassion.png", "F4A03F885EBF68B0C060450F72EDCFE3D2D48825E030C3A31951F826FBA627CD");
        expected.put("textures/block/shrine/honesty.png", "BF7C657E85198EB58A82E6466DFCBAFD74E45B3201B9F24000BD553B84744855");
        expected.put("textures/block/shrine/honor.png", "723898577D80867D40B4BBF9DADCDCC759446740722F4F496264E88D1826AF36");
        expected.put("textures/block/shrine/humility.png", "B8136CE1C9637D1B1A5F6E0738B9698F34854C88F796BD6FC63DB2F955C1688C");
        expected.put("textures/block/shrine/justice.png", "50531DACCE0ECB3A513714EC3036C44C96092A787F93854375EDC47904A2BFC3");
        expected.put("textures/block/shrine/sacrifice.png", "E9D3FA485A24BC1237B20CE2287F3E7BD93E8792C4BF44C8DA742C25992427DC");
        expected.put("textures/block/shrine/spirituality.png", "C4394FF25C3EF14DAE11EEDB5D2DF3BD0787608E82602712314AF32565A3CA4A");
        expected.put("textures/block/shrine/valor.png", "2482D99690D718C3D0B06E919118408BE965179C0A878F2924F396EBBA186CF9");
        expected.put("textures/block/shrine/granite.png", "A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77");
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
            assertFalse(paths.stream().anyMatch(path -> path.contains("MonolithVariantCycle")
                    || path.contains("MonolithCycleService")));
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

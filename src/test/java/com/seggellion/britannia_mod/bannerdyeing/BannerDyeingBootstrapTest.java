package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.seggellion.britannia_mod.banner.BannerFeature;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import com.seggellion.britannia_mod.dye.DyeFeature;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BannerDyeingBootstrapTest {
    private static final List<Class<?>> COMMON_CLASSES = List.of(
            StableResourceId.class,
            BannerDefinitionId.class,
            FabricMaterialId.class,
            PigmentId.class,
            ResolvedColourId.class,
            MountId.class,
            PlacementProfileId.class,
            BannerOrientation.class,
            BannerDyeingConstants.class,
            BannerFeature.class,
            DyeFeature.class,
            BannerDyeingBootstrap.class);

    @Test
    void commonBootstrapLoadsWithoutGameplayRegistration() {
        assertDoesNotThrow(BannerDyeingBootstrap::bootstrapCommon);
        assertEquals(1, BannerDyeingConstants.CURRENT_SCHEMA_VERSION);
        assertEquals("britannia_mod:cotton", BannerDyeingConstants.DEFAULT_COTTON_MATERIAL_ID.toString());
        assertEquals("britannia_mod:brass", BannerDyeingConstants.BRASS_MOUNT_ID.toString());
        assertEquals("britannia_mod:iron", BannerDyeingConstants.IRON_MOUNT_ID.toString());
        assertEquals("wall_parallel", BannerDyeingConstants.WALL_PARALLEL.serializedName());
        assertEquals("wall_perpendicular", BannerDyeingConstants.WALL_PERPENDICULAR.serializedName());
        assertEquals("britannia_mod.banner.content_validation", BannerFeature.CONTENT_VALIDATION_LOGGER.getName());
        assertEquals("britannia_mod.dye.content_validation", DyeFeature.CONTENT_VALIDATION_LOGGER.getName());
    }

    @Test
    void commonClassFilesDoNotReferenceClientPackages() throws IOException {
        for (Class<?> commonClass : COMMON_CLASSES) {
            String resourceName = "/" + commonClass.getName().replace('.', '/') + ".class";
            try (InputStream stream = commonClass.getResourceAsStream(resourceName)) {
                assertNotNull(stream, "Missing class resource for " + commonClass.getName());
                String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(constantPool.contains("net/minecraft/client/"),
                        () -> commonClass.getName() + " references a client-only Minecraft class");
            }
        }
    }
}

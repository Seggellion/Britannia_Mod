package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Client-owned models and textures for the placed-banner assembly: the wooden pole the cloth
 * hangs from, and the metal bracket that bolts that pole to the wall.
 *
 * <p>Deliberately NOT in {@code banner_client_assets.json}. That index is generated from the
 * authoritative catalogue -- one entry per banner definition and placement profile -- and
 * {@code BannerClientAssetIndexContractTest} holds it to exactly that. Poles are not authored
 * per banner: they are a fixed presentation set every banner draws from, so they belong here
 * beside the existing mount constants rather than in per-definition data.
 */
public final class BannerAssemblyAssets {
    /** Row bands in {@code banner/mount/poles}; each has one model UV-mapped to its strip. */
    public static final int POLE_VARIANTS = 6;

    public static final List<ResourceLocation> POLE_MODELS = poleModels();
    public static final ResourceLocation BRACKET_MODEL = id("banner/mount/bracket");
    public static final ResourceLocation POLE_TEXTURE = id("banner/mount/poles");
    public static final ResourceLocation BRASS_METAL_TEXTURE =
            id("banner/mount/brass_metal");
    public static final ResourceLocation IRON_METAL_TEXTURE =
            id("banner/mount/iron_metal");

    public static final Set<ResourceLocation> MODELS = models();
    public static final Set<ResourceLocation> TEXTURES =
            Set.of(POLE_TEXTURE, BRASS_METAL_TEXTURE, IRON_METAL_TEXTURE);

    private BannerAssemblyAssets() {
    }

    /**
     * Local rather than {@code BannerAssetAvailability.id}, deliberately. That class's own
     * static initialiser folds {@link #MODELS} and {@link #TEXTURES} into its expected sets, so
     * borrowing its helper here makes the two initialisers circular: whichever class a caller
     * touches first reads the other's constants while they are still null. Keeping the
     * dependency one-way -- availability depends on assembly, never the reverse -- removes the
     * cycle rather than relying on class-load order to hide it.
     */
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
    }

    /**
     * Which pole a post shows, chosen from its own position and nothing else.
     *
     * <p>Deliberately not {@code Random}: the renderer re-resolves this every frame and after
     * every resource reload, so anything with per-call or per-session state would make a
     * standing banner's pole flicker or silently change on relog. {@link Mth#getSeed} is the
     * same position hash vanilla uses to pick block model variants, so neighbouring posts get
     * visibly different poles instead of banding.
     */
    public static ResourceLocation poleModelFor(BlockPos anchor) {
        int index = (int) Math.floorMod(Mth.getSeed(anchor.getX(), anchor.getY(), anchor.getZ()),
                (long) POLE_VARIANTS);
        return POLE_MODELS.get(index);
    }

    private static List<ResourceLocation> poleModels() {
        ResourceLocation[] ids = new ResourceLocation[POLE_VARIANTS];
        for (int index = 0; index < POLE_VARIANTS; index++) {
            ids[index] = id("banner/pole/pole_" + index);
        }
        return List.of(ids);
    }

    private static Set<ResourceLocation> models() {
        LinkedHashSet<ResourceLocation> all = new LinkedHashSet<>(POLE_MODELS);
        all.add(BRACKET_MODEL);
        return Set.copyOf(all);
    }
}

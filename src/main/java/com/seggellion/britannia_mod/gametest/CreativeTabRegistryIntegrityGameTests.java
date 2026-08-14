package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression tests for the creative-tab crash: opening any creative tab threw a
 * {@link NullPointerException} from {@code CreativeModeTabs.generateSuspiciousStews} ->
 * {@link SuspiciousEffectHolder#getAllEffectHolders()}.
 *
 * <h2>The failure this guards against</h2>
 * Two {@code DeferredRegister}s registered the same item id ({@code britannia_mod:pike}: the
 * fishing item, and blacksmithing's polearm from the craftables catalogue). Vanilla does not
 * reject that -- {@code MappedRegistry.register} only calls {@code Util.pauseInIde}, which is
 * silent in a normal run -- but it appends the holder to {@code byId} twice while the key binds
 * to whichever value registered last.
 *
 * <p>On world load NeoForge re-applies a registry snapshot built from {@code keySet()}, so the
 * duplicated key contributes only its winning id; the orphaned id is never refilled and
 * {@code MappedRegistry.registerIdMapping} leaves a {@code null} hole there. Every later
 * iteration -- {@code Iterators.transform(byId.iterator(), Holder::value)} -- then throws.
 *
 * <p>Note that a duplicate id is invisible until something iterates the whole registry, which is
 * why builds, unit tests, gametests, dedicated-server startup and client joins all passed while
 * the defect was live. These tests make it fail loudly instead.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeTabRegistryIntegrityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CreativeTabRegistryIntegrityGameTests() {
    }

    /** No item id may be registered more than once -- the defect's actual root cause. */
    @GameTest(template = TEMPLATE)
    public static void noItemIdIsRegisteredTwice(GameTestHelper helper) {
        Map<ResourceLocation, List<Integer>> idsByKey = new HashMap<>();
        int size = BuiltInRegistries.ITEM.size();
        for (int id = 0; id < size; id++) {
            Holder<?> holder = BuiltInRegistries.ITEM.getHolder(id).orElse(null);
            if (holder == null || holder.getKey() == null) {
                continue;
            }
            idsByKey.computeIfAbsent(holder.getKey().location(), key -> new ArrayList<>()).add(id);
        }

        List<String> duplicates = new ArrayList<>();
        idsByKey.forEach((key, ids) -> {
            if (ids.size() > 1) {
                duplicates.add(key + " at ids " + ids);
            }
        });
        if (!duplicates.isEmpty()) {
            throw new GameTestAssertException("item ids registered more than once (this leaves a null"
                    + " hole in the registry after world load and crashes every creative tab): "
                    + duplicates);
        }
        helper.succeed();
    }

    /** The exact vanilla call that crashed the client when a creative tab was opened. */
    @GameTest(template = TEMPLATE)
    public static void suspiciousEffectHolderScanDoesNotCrash(GameTestHelper helper) {
        try {
            List<SuspiciousEffectHolder> holders = SuspiciousEffectHolder.getAllEffectHolders();
            if (holders == null) {
                throw new GameTestAssertException("getAllEffectHolders returned null");
            }
        } catch (RuntimeException failure) {
            throw new GameTestAssertException(
                    "SuspiciousEffectHolder.getAllEffectHolders() failed -- the creative-tab crash path: "
                            + failure);
        }
        helper.succeed();
    }

    /** Full item-registry iteration must be safe: no empty id slots. */
    @GameTest(template = TEMPLATE)
    public static void itemRegistryHasNoHoles(GameTestHelper helper) {
        List<Integer> holes = new ArrayList<>();
        int size = BuiltInRegistries.ITEM.size();
        for (int id = 0; id < size; id++) {
            if (BuiltInRegistries.ITEM.getHolder(id).isEmpty()) {
                holes.add(id);
            }
        }
        if (!holes.isEmpty()) {
            throw new GameTestAssertException("item registry has " + holes.size()
                    + " empty id slot(s), which makes every registry iteration throw: " + holes);
        }
        helper.succeed();
    }

    /**
     * Every active authored banner definition appears in the decor tab exactly once, in its
     * undyed default: the definition's own default material at that material's natural colour
     * (no source pigment) on the definition's default mount. Built with real
     * {@code ItemDisplayParameters} on the gametest server, whose datapack reload has already
     * published {@code BannerDataRegistries} -- the same conditions a singleplayer client's tab
     * build sees. Guards the banners-dyetub integration gap where no banner reached any tab.
     */
    @GameTest(template = TEMPLATE)
    public static void decorTabListsEveryActiveBannerDefinitionUndyed(GameTestHelper helper) {
        if (!com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries.isAvailable()) {
            throw new GameTestAssertException("banner data registries were never published on this server");
        }
        var snapshot = com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries.current();
        var bannerItem = com.seggellion.britannia_mod.registry.BannerItemRegistry.BANNER.get();

        net.minecraft.world.item.CreativeModeTab tab =
                com.seggellion.britannia_mod.registry.CreativeTabRegistry.CREATIVE_DECOR_TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));

        Map<com.seggellion.britannia_mod.banner.api.BannerDefinitionId,
                com.seggellion.britannia_mod.banner.state.BannerInstanceState> displayed = new HashMap<>();
        for (net.minecraft.world.item.ItemStack stack : tab.getDisplayItems()) {
            if (!stack.is(bannerItem)) {
                continue;
            }
            var state = bannerItem.stateAccess().read(stack).orElseThrow(
                    () -> new GameTestAssertException("decor tab holds a banner stack with unreadable state"));
            var previous = displayed.put(state.bannerDefinitionId(), state);
            if (previous != null) {
                throw new GameTestAssertException(
                        "banner definition listed twice: " + state.bannerDefinitionId());
            }
        }

        List<String> problems = new ArrayList<>();
        // Sentinel: an item accepted shortly before addBanners in the same lambda. If this is
        // absent the generator never ran to that point (or the wrong tab was built -- exactly
        // the mistake that produced this test's first failure); if present but banners are
        // missing, the fault is inside addBanners or the factory.
        boolean sentinelPresent = tab.getDisplayItems().stream().anyMatch(stack -> stack.is(
                com.seggellion.britannia_mod.registry.ItemRegistry.HANGING_LANTERN_ITEM.get()));
        if (!sentinelPresent) {
            problems.add("sentinel item (hanging lantern) absent -- decor displayItems generator did not run");
        }
        var probeFactory = new com.seggellion.britannia_mod.banner.item.BannerItemFactory(
                bannerItem, bannerItem.stateAccess());
        for (var definition : snapshot.banners().activeDefinitions()) {
            var state = displayed.remove(definition.id());
            if (state == null) {
                // Re-run the exact factory call addBanners makes, so the failure names itself.
                var probe = probeFactory.craftedMaterialBanner(definition.id(),
                        definition.defaultMaterial(), java.util.Optional.empty(), snapshot, true);
                problems.add(definition.id() + " missing from the decor tab (factory probe: "
                        + probe.failure().map(issue -> issue.kind() + "/" + issue.stableId())
                                .orElse("factory succeeds; tab pipeline dropped the stack")
                        + ")");
                continue;
            }
            var material = snapshot.fabricMaterials().find(state.materialId()).orElse(null);
            if (!definition.defaultMaterial().equals(state.materialId())) {
                problems.add(definition.id() + " uses material " + state.materialId()
                        + " instead of its default " + definition.defaultMaterial());
            } else if (material == null || !material.naturalColourId().equals(state.resolvedColourId())) {
                problems.add(definition.id() + " is not in its material's natural colour: "
                        + state.resolvedColourId());
            }
            if (state.sourcePigmentId().isPresent()) {
                problems.add(definition.id() + " carries dye pigment " + state.sourcePigmentId().get());
            }
            if (!definition.defaultMount().equals(state.mountId())) {
                problems.add(definition.id() + " uses mount " + state.mountId()
                        + " instead of its default " + definition.defaultMount());
            }
        }
        displayed.keySet().forEach(id -> problems.add(id + " displayed but not an active definition"));

        if (!problems.isEmpty()) {
            // Ground-truth context for the failure: how many stacks the build produced at all,
            // and whether vanilla's per-item feature-flag filter is what emptied it (rebuilt
            // with every registry flag enabled for comparison).
            int builtSize = tab.getDisplayItems().size();
            tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                    net.minecraft.world.flag.FeatureFlags.REGISTRY.allFlags(), false,
                    helper.getLevel().registryAccess()));
            int allFlagsSize = tab.getDisplayItems().size();
            throw new GameTestAssertException("decor tab banner listing is wrong ("
                    + problems.size() + "; built=" + builtSize
                    + " with level flags, built=" + allFlagsSize + " with all flags; level flags empty="
                    + helper.getLevel().enabledFeatures().isEmpty() + "): " + problems);
        }
        if (snapshot.banners().activeDefinitions().isEmpty()) {
            throw new GameTestAssertException("no active banner definitions loaded -- nothing was actually verified");
        }
        helper.succeed();
    }

    /** The same integrity requirement for blocks, whose items feed the same tab scan. */
    @GameTest(template = TEMPLATE)
    public static void blockRegistryIterationIsSafe(GameTestHelper helper) {
        long counted = 0L;
        for (Holder<?> holder : BuiltInRegistries.BLOCK.holders().toList()) {
            if (holder == null || !holder.isBound()) {
                throw new GameTestAssertException("unbound or null block holder encountered");
            }
            counted++;
        }
        if (counted == 0L) {
            throw new GameTestAssertException("block registry iterated to zero entries");
        }
        helper.succeed();
    }
}

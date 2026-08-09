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

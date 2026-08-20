package com.seggellion.britannia_mod.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which foundation blocks are the outside of a house and which are the floor inside it.
 *
 * <p>The housing model has three floor-and-foundation roles: the perimeter courses around the
 * outside, the decorative flooring an owner lays, and the structural interior floor the house
 * ships with -- the layer an owner cuts down through to reach a basement. Only the last two may
 * ever be broken by an owner, and nothing in the code says which block is which. The registry
 * names do not: {@code brick_foundation_spruce} sounds like a perimeter block and is not one.
 *
 * <p>The structures do say, unambiguously, and this is that evidence held still. Across every
 * shipped house the {@code brick_foundation_*} family occurs at y=0 and nowhere else -- it is
 * the ground-floor slab, brick at the exposed edges and spruce planks underfoot, which is what
 * the six small houses look like. The {@code ThinWall} foundations occur at y>=1 and never at
 * y=0 -- they are the wall bases.
 *
 * <p>This is what Milestone 5's {@code house_foundation} tag has to encode. If someone later
 * lays a brick foundation up a wall, or a wall base across a floor, this fails and the tag needs
 * rethinking before it silently protects the wrong blocks.
 */
class HouseFloorRoleTest {

    private static final Path STRUCTURES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources/data/britannia_mod/structures");

    /** Ground-floor slabs: the interior floor a house ships with. */
    private static final Set<String> GROUND_SLAB = Set.of(
            "britannia_mod:brick_foundation_oak",
            "britannia_mod:brick_foundation_spruce",
            "britannia_mod:brick_foundation_sandstone",
            "britannia_mod:brick_foundation_dark_sandstone",
            "britannia_mod:brick_foundation_flagstone",
            "britannia_mod:wooden_board_floor_foundation");

    /** Wall bases: the exterior perimeter. */
    private static final Set<String> PERIMETER_COURSE = Set.of(
            "britannia_mod:cobblestone_foundation",
            "britannia_mod:plaster_stone_foundation",
            "britannia_mod:plaster_wood_foundation",
            "britannia_mod:curtain_foundation");

    @Test
    void theGroundSlabFamilyNeverClimbsAWall() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            for (var entry : layersByBlock(style).entrySet()) {
                if (!GROUND_SLAB.contains(entry.getKey())) continue;
                assertEquals(Set.of(0), entry.getValue(),
                        style + " lays " + entry.getKey() + " at " + entry.getValue() + ". It is a "
                                + "ground-floor slab everywhere else, and the protection rules are "
                                + "about to be built on that.");
            }
        }
    }

    @Test
    void theWallBaseFamilyNeverBecomesAFloor() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            for (var entry : layersByBlock(style).entrySet()) {
                if (!PERIMETER_COURSE.contains(entry.getKey())) continue;
                assertTrue(entry.getValue().stream().allMatch(y -> y >= 1),
                        style + " lays " + entry.getKey() + " at " + entry.getValue() + ", including "
                                + "the floor. It is a wall base everywhere else, and protecting it "
                                + "would take a house's floor with it.");
            }
        }
    }

    /**
     * The six small houses floor themselves in one block, and it already plays the interior role.
     *
     * <p>Worth stating outright, because the obvious reading of the name is the wrong one and it
     * decides whether those houses can have basements. No new floor-foundation block is needed
     * for them; what is needed is for the tag to put this one on the right side of the line.
     */
    @Test
    void theSmallHousesFloorIsBrickFoundationSpruceAndNothingElse() throws IOException {
        int houses = 0;
        for (HouseStyle style : HouseStyle.values()) {
            if (style.getSize() != HouseSize.SMALL) continue;
            houses++;

            var layers = layersByBlock(style);
            assertEquals(Set.of(0), layers.get("britannia_mod:brick_foundation_spruce"),
                    style + " no longer floors itself in brick_foundation_spruce at y=0");
            assertEquals(49, countAt(style, "britannia_mod:brick_foundation_spruce", 0),
                    style + " should lay a 7x7 interior floor");
        }
        assertEquals(6, houses, "the small-house roster changed; recheck what they floor themselves in");
    }

    /* ------------------------------------------------------------------ */

    private static TreeMap<String, Set<Integer>> layersByBlock(HouseStyle style) throws IOException {
        CompoundTag structure = read(style);
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        String[] names = new String[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            names[index] = palette.getCompound(index).getString("Name");
        }

        TreeMap<String, Set<Integer>> layers = new TreeMap<>();
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            String name = names[block.getInt("state")];
            if (!name.contains("foundation")) continue;
            layers.computeIfAbsent(name, ignored -> new TreeSet<>())
                    .add(block.getList("pos", Tag.TAG_INT).getInt(1));
        }
        return layers;
    }

    private static int countAt(HouseStyle style, String blockName, int y) throws IOException {
        CompoundTag structure = read(style);
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        String[] names = new String[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            names[index] = palette.getCompound(index).getString("Name");
        }

        int count = 0;
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            if (!names[block.getInt("state")].equals(blockName)) continue;
            if (block.getList("pos", Tag.TAG_INT).getInt(1) == y) count++;
        }
        return count;
    }

    private static CompoundTag read(HouseStyle style) throws IOException {
        Path path = STRUCTURES.resolve(style.getStructureFile());
        assertTrue(Files.exists(path), "shipped structure missing: " + path);
        try (InputStream in = Files.newInputStream(path);
             DataInputStream data = new DataInputStream(in)) {
            return NbtIo.read(data);
        }
    }
}

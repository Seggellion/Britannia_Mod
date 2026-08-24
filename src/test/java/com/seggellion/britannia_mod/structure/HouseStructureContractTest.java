package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a shipped house has to contain for the housing system to work on it.
 *
 * <h2>The controller contract</h2>
 *
 * <p>A house's controller is its {@code HouseLotBlockEntity}: it holds the house UUID, the owner,
 * the privacy flag and the rotation, and every door and management action resolves through it. It
 * is not authored into the structure -- no shipped house contains one -- so
 * {@link StructurePlacer} writes it in, and {@code HouseSignBlock.useWithoutItem} reads it back
 * from {@code pos.below()}. The sign and the controller are a pair.
 *
 * <p>Placement used to compute the controller position independently, from
 * {@code width / 2 + HouseStyle.getLotOffsetX()} with y and z hard-coded to 1 and 0. Two
 * descriptions of one position drift, and these had:
 *
 * <pre>
 *   villa   sign (6,3,0)   computed lot (1,1,0)
 *   patio   sign (8,2,0)   computed lot (9,1,0)
 *   keep    sign (10,3,2)  computed lot (11,1,0)
 * </pre>
 *
 * <p>Three houses whose sign found no controller beneath it, and answered "Could not find the
 * house controller." The placer now takes the position from the sign, and this holds the
 * structures to what that requires.
 *
 * <h2>Vegetation</h2>
 *
 * <p>A house is a building. Grass, ferns and flowers swept into an export by the structure block
 * get stamped into whatever ground the house lands on, which is terrain the house did not author
 * and cannot be responsible for. {@code britannia_mod:managed_flower} and
 * {@code britannia_mod:fern} are worse than cosmetic: they are managed vegetation nodes, and a
 * house was minting them.
 *
 * <p>{@code tools/audit_house_structures.py --strip-vegetation} is the repair, and it also names
 * the plants that <em>are</em> architecture, so a house farm plot is never taken for weeds.
 */
class HouseStructureContractTest {

    private static final Path STRUCTURES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources/data/britannia_mod/structures");

    private static final String HOUSE_SIGN = "britannia_mod:house_sign";

    /**
     * Terrain vegetation, by exact id, kept in step with {@code tools/audit_house_structures.py}.
     *
     * <p>Exact ids rather than a name pattern: a pattern that matched "farm" would condemn
     * {@code house_farm_plot}, which is the one growth a house is meant to introduce.
     */
    private static final Set<String> PROHIBITED_VEGETATION = Set.of(
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
            "minecraft:large_fern", "minecraft:dead_bush", "minecraft:sweet_berry_bush",
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
            "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
            "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy",
            "minecraft:cornflower", "minecraft:lily_of_the_valley", "minecraft:sunflower",
            "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
            "minecraft:brown_mushroom", "minecraft:red_mushroom",
            "minecraft:seagrass", "minecraft:tall_seagrass",
            "britannia_mod:fern", "britannia_mod:managed_flower", "britannia_mod:blood_moss");

    @Test
    void everyHouseShipsExactlyOneSign() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            assertEquals(1, signsIn(style).size(),
                    style + " does not ship exactly one house sign. The sign is where the house's "
                            + "controller goes; without one there is nowhere to put it, and with two "
                            + "there is no rule for choosing.");
        }
    }

    @Test
    void theCellBeneathEverySignIsFreeForTheController() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            BlockPos sign = signsIn(style).get(0);
            BlockPos controller = sign.below();
            String occupant = blockAt(style, controller);
            assertEquals("minecraft:air", occupant,
                    style + " authors " + occupant + " at " + controller + ", directly beneath its "
                            + "sign. Placement writes the lot block into that cell, so whatever is "
                            + "authored there is silently destroyed when the house is placed.");
        }
    }

    /**
     * The rotated controller position is still directly beneath the rotated sign.
     *
     * <p>The placer reads the sign out of the world after the template has been rotated, so this is
     * true by construction rather than by arithmetic — which is the point of the change. Asserting
     * it anyway pins the property that made the arithmetic version fail: rotation moves x and z and
     * leaves y alone, so a controller derived with a hard-coded y can never follow a sign that is
     * not at y=1.
     */
    @Test
    void theControllerFollowsItsSignThroughEveryRotation() throws IOException {
        for (HouseStyle style : HouseStyle.values()) {
            BlockPos sign = signsIn(style).get(0);
            for (Rotation rotation : Rotation.values()) {
                BlockPos rotatedSign = net.minecraft.world.level.levelgen.structure.templatesystem
                        .StructureTemplate.calculateRelativePosition(
                                new net.minecraft.world.level.levelgen.structure.templatesystem
                                        .StructurePlaceSettings().setRotation(rotation), sign);
                BlockPos rotatedController = net.minecraft.world.level.levelgen.structure.templatesystem
                        .StructureTemplate.calculateRelativePosition(
                                new net.minecraft.world.level.levelgen.structure.templatesystem
                                        .StructurePlaceSettings().setRotation(rotation), sign.below());
                assertEquals(rotatedSign.below(), rotatedController,
                        style + " loses the sign-to-controller relationship under " + rotation);
            }
        }
    }

    @Test
    void noHouseCarriesTerrainVegetation() throws IOException {
        TreeMap<String, TreeMap<String, Integer>> offenders = new TreeMap<>();
        for (HouseStyle style : HouseStyle.values()) {
            TreeMap<String, Integer> found = new TreeMap<>();
            forEachCell(style, (name, pos) -> {
                if (PROHIBITED_VEGETATION.contains(name)) {
                    found.merge(name, 1, Integer::sum);
                }
            });
            if (!found.isEmpty()) offenders.put(style.name(), found);
        }
        assertTrue(offenders.isEmpty(),
                "house structures carry terrain vegetation that placement would stamp into the "
                        + "world: " + offenders + ". Repair with "
                        + "python tools/audit_house_structures.py --strip-vegetation");
    }

    /**
     * The audit tool and this test have to condemn the same set, or one of them is decoration.
     *
     * <p>Read out of the tool's source rather than duplicated by hand: a list maintained in two
     * places is a list that disagrees with itself.
     */
    @Test
    void theAuditToolAndThisTestCondemnTheSamePlants() throws IOException {
        String tool = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."))
                .resolve("tools/audit_house_structures.py"));
        for (String plant : PROHIBITED_VEGETATION) {
            assertTrue(tool.contains('"' + plant + '"'),
                    "tools/audit_house_structures.py does not know about " + plant
                            + ", so the tool would leave behind what this test refuses");
        }
    }

    /* ------------------------------------------------------------------ */

    private interface CellVisitor {
        void accept(String blockName, BlockPos pos);
    }

    private static List<BlockPos> signsIn(HouseStyle style) throws IOException {
        List<BlockPos> signs = new ArrayList<>();
        forEachCell(style, (name, pos) -> {
            if (HOUSE_SIGN.equals(name)) signs.add(pos);
        });
        return signs;
    }

    private static String blockAt(HouseStyle style, BlockPos target) throws IOException {
        Set<String> found = new TreeSet<>();
        forEachCell(style, (name, pos) -> {
            if (pos.equals(target)) found.add(name);
        });
        // A cell outside the authored volume is reported as such rather than as air: "nothing is
        // authored here" and "this is outside the house" are different problems.
        return found.isEmpty() ? "<no authored cell>" : found.iterator().next();
    }

    private static void forEachCell(HouseStyle style, CellVisitor visitor) throws IOException {
        CompoundTag structure = read(style);
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        String[] names = new String[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            names[index] = palette.getCompound(index).getString("Name");
        }
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            visitor.accept(names[block.getInt("state")],
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
        }
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

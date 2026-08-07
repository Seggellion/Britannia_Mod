"""
Generate the DoubleWallBlock blockstates, and the window-side mirror models.

State space (see DoubleWallBlock):
    facing (4) x shape (3) x branch_right (2) x half (2)                        = 48
    ... x mirrored (2) for MirrorableWindowBlock                                = 96

Model naming:
    <family>_straight
    <family>_corner                    branch on the counter-clockwise edge (branch_right=false)
    <family>_corner_branch_right       branch on the clockwise edge          (branch_right=true)
    <family>_t_junction
    <family>_t_junction_branch_right
    ..._window_right                   window furniture flipped to the other side (mirrored=true)

Rotation is a plain function of facing, because every canonical model is authored at y=0 with
its main run on the north edge. Nothing here compensates for a mis-authored model.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mcjson  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "britannia_mod")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

PLASTER_DIR = "britannia_mod:block/structure/plaster/"
SANDSTONE_DIR = "britannia_mod:block/structure/sandstone/"

PLASTER = [
    "plaster_ornate_wall_upper", "plaster_ornate_wall_1", "plaster_ornate_wall_2",
    "plaster_small_window", "ornate_wall_large_window", "plaster_wall_large_window",
    "plaster_wall_support_diagonal_east", "plaster_wall_support_diagonal_south",
    "plaster_wall_support_open", "plaster_wall_blank", "plaster_wall_and_support_blank",
    "plaster_archway", "plaster_and_stone_window",
]
SANDSTONE = [
    "ornate_sandstone_wall", "regular_sandstone_wall", "sandstone_block_wall",
    "ornate_sandstone_window", "sandstone_window", "sandstone_post",
    "ornate_sandstone_post", "sandstone_battlement", "sandstone_column",
]

# Only families whose straight model actually has asymmetric window furniture to move: a wide
# post at one end of the opening and a thin jamb at the other.
MIRRORABLE_WINDOWS = ["plaster_wall_large_window", "ornate_wall_large_window"]

ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}
SHAPES = {
    ("straight", "false"): "_straight",
    ("straight", "true"): "_straight",
    ("corner", "false"): "_corner",
    ("corner", "true"): "_corner_branch_right",
    ("t_junction", "false"): "_t_junction",
    ("t_junction", "true"): "_t_junction_branch_right",
}

BASE_MODELS = ["_straight", "_corner", "_corner_branch_right",
               "_t_junction", "_t_junction_branch_right"]


# ─── window-side mirror ──────────────────────────────────────────────────────

def mirror_element(el):
    """Reflect an element across the x = 8 plane."""
    x0, y0, z0 = el["from"]
    x1, y1, z1 = el["to"]
    out = {"from": [round(16.0 - x1, 4), y0, z0],
           "to":   [round(16.0 - x0, 4), y1, z1]}

    if "rotation" in el:
        r = dict(el["rotation"])
        ox, oy, oz = r["origin"]
        r["origin"] = [round(16.0 - ox, 4), oy, oz]
        # A reflection reverses orientation in every plane containing the x axis.
        if r.get("axis", "y") in ("y", "z"):
            r["angle"] = -r.get("angle", 0)
        out["rotation"] = r

    if "shade" in el:
        out["shade"] = el["shade"]

    faces = {}
    for name, face in el.get("faces", {}).items():
        new_face = dict(face)
        # Every face's u axis reverses under an x mirror. Minecraft accepts u0 > u1 and reads it
        # as a horizontal flip, which is exactly what is wanted.
        if "uv" in new_face:
            u0, v0, u1, v1 = new_face["uv"]
            new_face["uv"] = [u1, v0, u0, v1]
        if new_face.get("rotation"):
            new_face["rotation"] = (360 - new_face["rotation"]) % 360
            if new_face["rotation"] == 0:
                new_face.pop("rotation")
        faces[{"east": "west", "west": "east"}.get(name, name)] = new_face
    out["faces"] = faces
    return out


def write_window_mirrors():
    written = []
    for family in MIRRORABLE_WINDOWS:
        for suffix in BASE_MODELS:
            src = os.path.join(ASSETS, "models", "block", "structure", "plaster",
                               family + suffix + ".json")
            with open(src, encoding="utf-8") as fh:
                model = json.load(fh)
            out = {k: v for k, v in model.items() if k != "elements"}
            out["elements"] = [mirror_element(el) for el in model.get("elements", [])]
            dst = src[:-len(".json")] + "_window_right.json"
            mcjson.write(dst, out)
            written.append(os.path.basename(dst))
    return written


# ─── blockstates ─────────────────────────────────────────────────────────────

def build_blockstate(family, folder, mirrorable):
    variants = {}
    window_sides = ["false", "true"] if mirrorable else [None]

    for facing, y in ROTATION.items():
        for shape in ("straight", "corner", "t_junction"):
            for branch in ("false", "true"):
                for window in window_sides:
                    key = "facing=%s,shape=%s,branch_right=%s" % (facing, shape, branch)
                    if window is not None:
                        key += ",mirrored=" + window

                    suffix = SHAPES[(shape, branch)]
                    if window == "true":
                        suffix += "_window_right"

                    lower = {"model": folder + family + suffix}
                    if y:
                        lower["y"] = y
                    variants[key + ",half=lower"] = lower
                    # The lower half's model is 32 voxels tall and covers both blocks.
                    variants[key + ",half=upper"] = {"model": "minecraft:block/air"}

    return {"variants": variants}


def main():
    mirrors = write_window_mirrors()
    print("wrote %d window-side mirror models: %s" % (len(mirrors), ", ".join(mirrors)))

    count = 0
    for family in PLASTER:
        data = build_blockstate(family, PLASTER_DIR, family in MIRRORABLE_WINDOWS)
        with open(os.path.join(BLOCKSTATES, family + ".json"), "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2)
            fh.write("\n")
        count += 1
    for family in SANDSTONE:
        data = build_blockstate(family, SANDSTONE_DIR, False)
        with open(os.path.join(BLOCKSTATES, family + ".json"), "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2)
            fh.write("\n")
        count += 1
    print("wrote %d blockstates" % count)


if __name__ == "__main__":
    sys.exit(main())

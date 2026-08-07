"""
Compose L-corner and T-junction wall models from the approved _straight models.

Canonical orientation (see DoubleWallBlock javadoc):
  facing=north  ->  main wall run hugs the block's north edge, decorated face pointing north.
  The canonical junction model is the {north, west} pair authored at y=0. Every other
  facing/branch-side combination is this same model rotated by the blockstate, so there are
  no mirrored junction files to keep in sync.

The west run is the straight model rotated 90 degrees counter-clockwise about the block's
vertical axis, which is exactly what the blockstate does to a west-facing straight (y=270),
so the branch lines up with its neighbours voxel for voxel.

  positions   (x, z) -> (z, 16 - x)
  directions  east->north, north->west, west->south, south->east
  up/down face uv rotation += 270 / += 90
  element rotation axis  z -> x (same angle),  x -> z (negated angle),  y -> y

Corner vs junction, and why they differ:
  L-corner    the north run terminates here, so it is clipped back to the branch and the WEST
              run owns the outside corner (its west-facing art wraps the corner).
  T-junction  the north run continues into the west neighbour, so it stays full width and owns
              the corner, and the branch butts into its back face.
Both end up with the same footprint - that is inherent to an edge-aligned wall system - but
they differ in which run owns the corner, and neither carries buried geometry.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mcjson  # noqa: E402

PLASTER = os.path.join(
    "src", "main", "resources", "assets", "britannia_mod",
    "models", "block", "structure", "plaster")

# Families that own real, hand-authored straight art. The remaining families still only have
# placeholder straights; they get the same topology so nothing is ever missing-model, but they
# need real art before they will look right.
AUTHORED = [
    "plaster_ornate_wall_upper",
    "plaster_ornate_wall_1",
    "plaster_ornate_wall_2",
    "plaster_wall_blank",
    "plaster_wall_and_support_blank",
    "plaster_wall_large_window",
    "plaster_wall_support_diagonal_east",
    "plaster_wall_support_diagonal_south",
    "plaster_wall_support_open",
    "ornate_wall_large_window",
]
PLACEHOLDER = [
    "plaster_small_window",
    "plaster_and_stone_window",
    "plaster_archway",
]

SANDSTONE = os.path.join(
    "src", "main", "resources", "assets", "britannia_mod",
    "models", "block", "structure", "sandstone")

# The sandstone series shares DoubleWallBlock, so adding branch_right to the base class means its
# blockstates need the same four junction models. Its straights are still placeholders.
SANDSTONE_FAMILIES = [
    "ornate_sandstone_wall", "regular_sandstone_wall", "sandstone_block_wall",
    "ornate_sandstone_window", "sandstone_window", "sandstone_post",
    "ornate_sandstone_post", "sandstone_battlement", "sandstone_column",
]

# Counter-clockwise puts the branch on the west edge (canonical); clockwise puts it on the east.
SIDE_REMAP_CCW = {"east": "north", "north": "west", "west": "south", "south": "east"}
SIDE_REMAP_CW = {"north": "east", "east": "south", "south": "west", "west": "north"}

# For each face, which model axis its u and v run along, and whether they increase with the axis.
# (axis, u_positive) for u; (axis, v_positive) for v. Only the horizontal axes matter here.
U_AXIS = {"north": (0, False), "south": (0, True), "east": (2, False), "west": (2, True),
          "up": (0, True), "down": (0, True)}
V_AXIS = {"up": (2, True), "down": (2, False)}


def rot_point(x, z, ccw):
    return (z, 16.0 - x) if ccw else (16.0 - z, x)


def rot_element(el, ccw):
    """Rotate one element 90 degrees about the block's vertical axis."""
    x0, y0, z0 = el["from"]
    x1, y1, z1 = el["to"]
    if ccw:
        nx0, nz1 = rot_point(x0, z0, True)
        nx1, nz0 = rot_point(x1, z1, True)
    else:
        nx1, nz0 = rot_point(x0, z0, False)
        nx0, nz1 = rot_point(x1, z1, False)

    out = {"from": [round(min(nx0, nx1), 4), y0, round(min(nz0, nz1), 4)],
           "to":   [round(max(nx0, nx1), 4), y1, round(max(nz0, nz1), 4)]}

    if "rotation" in el:
        r = dict(el["rotation"])
        ox, oy, oz = r["origin"]
        nox, noz = rot_point(ox, oz, ccw)
        r["origin"] = [round(nox, 4), oy, round(noz, 4)]
        axis, angle = r.get("axis", "y"), r.get("angle", 0)
        if axis == "z":
            r["axis"] = "x"
            if not ccw:
                r["angle"] = -angle
        elif axis == "x":
            r["axis"] = "z"
            if ccw:
                r["angle"] = -angle
        out["rotation"] = r

    if "shade" in el:
        out["shade"] = el["shade"]

    remap = SIDE_REMAP_CCW if ccw else SIDE_REMAP_CW
    faces = {}
    for name, face in el.get("faces", {}).items():
        new_face = dict(face)
        if name in remap:
            faces[remap[name]] = new_face
        else:
            # up / down keep their direction but their texture turns with the model.
            turn = (270 if name == "up" else 90) if ccw else (90 if name == "up" else 270)
            new_face["rotation"] = (face.get("rotation", 0) + turn) % 360
            if new_face["rotation"] == 0:
                new_face.pop("rotation", None)
            faces[name] = new_face
    out["faces"] = faces
    return out


def _trim_uv(face, axis, frac_low, frac_high):
    """Shrink a face's uv rect by the same fraction the element was clipped, so the texture
    keeps its scale instead of stretching across a shorter face."""
    if "uv" not in face or face.get("rotation"):
        return
    u0, v0, u1, v1 = face["uv"]

    ua = U_AXIS.get(face["_name"])
    if ua and ua[0] == axis:
        du = u1 - u0
        if ua[1]:
            u0, u1 = u0 + du * frac_low, u1 - du * frac_high
        else:
            u0, u1 = u0 + du * frac_high, u1 - du * frac_low

    va = V_AXIS.get(face["_name"])
    if va and va[0] == axis:
        dv = v1 - v0
        if va[1]:
            v0, v1 = v0 + dv * frac_low, v1 - dv * frac_high
        else:
            v0, v1 = v0 + dv * frac_high, v1 - dv * frac_low

    face["uv"] = [round(u0, 5), round(v0, 5), round(u1, 5), round(v1, 5)]


def clip(el, axis, lo=None, hi=None):
    """
    Clip an element along `axis` (0 = x, 2 = z) to the range [lo, hi]. Returns None if nothing
    survives. Elements carrying a non-zero element rotation are never clipped - the clip plane
    no longer lines up with the box once it is tilted - they are kept or dropped whole.
    """
    a0, a1 = el["from"][axis], el["to"][axis]
    lo = a0 if lo is None else lo
    hi = a1 if hi is None else hi
    if a1 <= lo or a0 >= hi:
        return None
    if a0 >= lo and a1 <= hi:
        return el
    if el.get("rotation", {}).get("angle", 0) != 0:
        return el

    span = a1 - a0
    frac_low = max(0.0, (lo - a0) / span)
    frac_high = max(0.0, (a1 - hi) / span)

    out = json.loads(json.dumps(el))
    out["from"][axis] = round(max(a0, lo), 4)
    out["to"][axis] = round(min(a1, hi), 4)
    for name, face in out["faces"].items():
        face["_name"] = name
        _trim_uv(face, axis, frac_low, frac_high)
        face.pop("_name")
    return out


def body(elements):
    """
    The elements that make up the wall body. Floor beams - anything that lives entirely in the
    bottom two voxels - are excluded: they stick out further than the wall does, and using them
    to place the clip plane opened a full-height gap at the inner corner.
    """
    return [el for el in elements if el["to"][1] > 2.0]


def wall_depth(elements):
    """How far the wall body reaches back from the north face, ignoring anything outside the block."""
    return max((min(el["to"][2], 16.0) for el in body(elements)), default=16.0)


def is_edge_aligned(elements):
    """
    True when the wall body sits against the block's north face rather than running through its
    centre. The authored straights start within a couple of voxels of the face (0 to 1.5 depending
    on how much surface ornament they carry); the placeholder straights sit at z=5.5.
    """
    return min((el["from"][2] for el in body(elements)), default=0.0) <= 2.0


def build(straight, junction, mirrored):
    """
    junction is 'corner' or 't_junction'.

    mirrored=False puts the branch on the WEST edge (branch_right=false in the blockstate);
    mirrored=True puts it on the EAST edge. Both are authored at y=0 so the blockstate only ever
    has to rotate, never mirror.
    """
    source = straight.get("elements", [])
    depth = wall_depth(source)
    edge_aligned = is_edge_aligned(source)
    far = 16.0 - depth

    main, branch = [], []

    for el in source:
        piece = json.loads(json.dumps(el))
        if junction == "corner":
            # The north run stops at the branch; the branch run wraps the outside corner.
            piece = clip(piece, 0, hi=far) if mirrored else clip(piece, 0, lo=depth)
            if piece is None:
                continue
            # Flush against the branch - drop the cap rather than let the two z-fight.
            if mirrored and abs(piece["to"][0] - far) < 1e-6:
                piece["faces"].pop("east", None)
            elif not mirrored and abs(piece["from"][0] - depth) < 1e-6:
                piece["faces"].pop("west", None)
        if piece["faces"]:
            main.append(piece)

    for el in source:
        piece = rot_element(el, not mirrored)
        if junction == "t_junction" or not edge_aligned:
            # T-junction: the through-run continues past this block, so the branch butts into its
            # back face. Centre-line walls do the same for corners, because their two arms meet at
            # the block centre instead of one wrapping the outside corner.
            piece = clip(piece, 2, lo=depth)
            if piece is None:
                continue
            if abs(piece["from"][2] - depth) < 1e-6:
                piece["faces"].pop("north", None)
        # An edge-aligned corner keeps the branch whole so it wraps the outside corner; its north
        # cap is the block's own north face there, and butts against the neighbouring wall.
        if piece["faces"]:
            branch.append(piece)

    out = {}
    for key in ("format_version", "credit", "parent", "render_type", "ambientocclusion"):
        if key in straight:
            out[key] = straight[key]
    out["textures"] = straight.get("textures", {})
    out["elements"] = main + branch
    return out


# Suffixes mirror the blockstate property they serve: the plain name is branch_right=false
# (branch on the counter-clockwise / west edge), "_branch_right" is branch_right=true.
VARIANTS = [("corner", False), ("corner_branch_right", True),
            ("t_junction", False), ("t_junction_branch_right", True)]


def main():
    written = 0
    targets = [(PLASTER, AUTHORED + PLACEHOLDER), (SANDSTONE, SANDSTONE_FAMILIES)]
    for directory, families in targets:
        for family in families:
            src = os.path.join(directory, family + "_straight.json")
            if not os.path.exists(src):
                print("  !! no straight model for %s" % family)
                continue
            with open(src, encoding="utf-8") as fh:
                straight = json.load(fh)
            for suffix, mirrored in VARIANTS:
                junction = "corner" if suffix.startswith("corner") else "t_junction"
                dst = os.path.join(directory, "%s_%s.json" % (family, suffix))
                mcjson.write(dst, build(straight, junction, mirrored))
                written += 1
    print("wrote %d junction models" % written)


if __name__ == "__main__":
    sys.exit(main())

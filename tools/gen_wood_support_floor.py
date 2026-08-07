"""
Build the wood_support_floor junction models from the approved straight (plaster_wall_joist_edge).

The straight is a joist run hugging the north edge (z -2..7, full height) with a thin floor deck
(y 13..16) covering the rest of the block (z 7..16). Same canonical orientation as the walls:
facing=north puts the joist on the north edge, and the branch for the plain variants is on the
west edge.

Boundary overflow, handled deliberately rather than copied:
  T-junction  the joists' 2-voxel overhang past z=0 is removed. The run continues into the east
              and west neighbours there, so the overhang bought nothing and pushed geometry into
              an unrelated block.
  L-corner    the overhang is kept. At a corner both overhanging ends face open air outside the
              building, which is exactly where an exposed joist end should read - and the corner's
              own clipping means it no longer reaches into a neighbour that has its own joist.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mcjson  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_junctions import rot_element, clip  # noqa: E402

PLASTER = os.path.join("src", "main", "resources", "assets", "britannia_mod",
                       "models", "block", "structure", "plaster")
STRAIGHT = os.path.join(PLASTER, "plaster_wall_joist_edge.json")

JOIST_DEPTH = 7.0   # how far the joist run reaches back from the north face
DECK_INDEX = 2      # the floor deck element in the straight model


def build(junction, mirrored):
    with open(STRAIGHT, encoding="utf-8") as fh:
        straight = json.load(fh)
    source = straight["elements"]
    joists, deck = source[:DECK_INDEX], source[DECK_INDEX]
    far = 16.0 - JOIST_DEPTH

    elements = []

    # Main joist run.
    for el in joists:
        piece = json.loads(json.dumps(el))
        if junction == "corner":
            # The branch owns the corner, so the main run stops at it.
            piece = clip(piece, 0, hi=far) if mirrored else clip(piece, 0, lo=JOIST_DEPTH)
            if piece is None:
                continue
            if mirrored:
                piece["faces"].pop("east", None)
            else:
                piece["faces"].pop("west", None)
        else:
            # T-junction: the run continues through, so drop the overhang outside the block.
            piece = clip(piece, 2, lo=0.0)
            if piece is None:
                continue
        elements.append(piece)

    # Branch joist run.
    for el in joists:
        piece = rot_element(el, not mirrored)
        if junction == "t_junction":
            piece = clip(piece, 2, lo=JOIST_DEPTH)
            if piece is None:
                continue
            piece["faces"].pop("north", None)
        elements.append(piece)

    # Floor deck: only the quadrant neither joist run occupies, so no deck sits inside a joist
    # and there are no duplicated coplanar top faces.
    quadrant = clip(json.loads(json.dumps(deck)), 0, hi=far) if mirrored \
        else clip(json.loads(json.dumps(deck)), 0, lo=JOIST_DEPTH)
    if quadrant is not None:
        quadrant["faces"].pop("west" if not mirrored else "east", None)
        elements.append(quadrant)

    out = {k: v for k, v in straight.items() if k != "elements"}
    out["elements"] = elements
    return out


def main():
    variants = [("corner", False), ("corner_branch_right", True),
                ("t_junction", False), ("t_junction_branch_right", True)]
    for suffix, mirrored in variants:
        junction = "corner" if suffix.startswith("corner") else "t_junction"
        dst = os.path.join(PLASTER, "plaster_wall_joist_edge_%s.json" % suffix)
        mcjson.write(dst, build(junction, mirrored))
        print("  wrote " + os.path.basename(dst))


if __name__ == "__main__":
    sys.exit(main())

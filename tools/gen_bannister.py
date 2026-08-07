"""
Build the bannister junction models and blockstate from the straight bannister.

Same canonical orientation as the walls: facing=north puts the run on the north edge, and the
plain (branch_right=false) junctions put the perpendicular run on the west edge. The run is only
3 voxels thick, so the corner mitres inside a 3x3 corner block.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mcjson  # noqa: E402
from gen_junctions import rot_element, clip  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "britannia_mod")
STRAIGHT = os.path.join(ASSETS, "models", "block", "structure", "bannister.json")
MODEL_DIR = os.path.join(ASSETS, "models", "block", "structure")
MODEL_REF = "britannia_mod:block/structure/bannister"

THICKNESS = 3.0
ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def build(junction, mirrored):
    with open(STRAIGHT, encoding="utf-8") as fh:
        straight = json.load(fh)
    source = straight["elements"]
    far = 16.0 - THICKNESS
    elements = []

    for el in source:
        piece = json.loads(json.dumps(el))
        if junction == "corner":
            # The main run stops at the branch, which wraps the outside corner.
            piece = clip(piece, 0, hi=far) if mirrored else clip(piece, 0, lo=THICKNESS)
            if piece is None:
                continue
            piece["faces"].pop("east" if mirrored else "west", None)
        if piece["faces"]:
            elements.append(piece)

    for el in source:
        piece = rot_element(el, not mirrored)
        if junction == "t_junction":
            piece = clip(piece, 2, lo=THICKNESS)
            if piece is None:
                continue
            piece["faces"].pop("north", None)
        if piece["faces"]:
            elements.append(piece)

    out = {k: v for k, v in straight.items() if k != "elements"}
    out["elements"] = elements
    return out


def main():
    variants = [("corner", False), ("corner_branch_right", True),
                ("t_junction", False), ("t_junction_branch_right", True)]
    for suffix, mirrored in variants:
        junction = "corner" if suffix.startswith("corner") else "t_junction"
        mcjson.write(os.path.join(MODEL_DIR, "bannister_%s.json" % suffix),
                     build(junction, mirrored))
        print("  wrote bannister_%s.json" % suffix)

    suffixes = {("straight", "false"): "", ("straight", "true"): "",
                ("corner", "false"): "_corner", ("corner", "true"): "_corner_branch_right",
                ("t_junction", "false"): "_t_junction",
                ("t_junction", "true"): "_t_junction_branch_right"}
    variants_json = {}
    for facing, y in ROTATION.items():
        for shape in ("straight", "corner", "t_junction"):
            for branch in ("false", "true"):
                entry = {"model": MODEL_REF + suffixes[(shape, branch)]}
                if y:
                    entry["y"] = y
                variants_json["facing=%s,shape=%s,branch_right=%s" % (facing, shape, branch)] = entry

    path = os.path.join(ASSETS, "blockstates", "bannister.json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump({"variants": variants_json}, fh, indent=2)
        fh.write("\n")
    print("  wrote blockstates/bannister.json (%d variants)" % len(variants_json))


if __name__ == "__main__":
    sys.exit(main())

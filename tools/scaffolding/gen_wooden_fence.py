"""Generate the edge-mounted wooden_fence models and complete 64-state blockstate.

The source dimensions deliberately follow the bannister: 18 voxels tall with a full edge run.
The fence is one voxel broader (4 rather than 3), with heavier 3x4 posts and 2x2 balusters.
"""
import itertools
import json
import os


ROOT = os.path.join("src", "main", "resources", "assets", "britannia_mod")
MODEL_DIR = os.path.join(ROOT, "models", "block", "structure", "wooden_fence")
TEXTURE = "britannia_mod:block/structure/wooden_fence"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRECTIONS = ("north", "east", "south", "west")


def cuboid(start, end):
    width = max(1, min(16, end[0] - start[0]))
    height = max(1, min(16, end[1] - start[1]))
    depth = max(1, min(16, end[2] - start[2]))
    return {
        "from": start,
        "to": end,
        "faces": {
            "north": {"uv": [0, 0, width, height], "texture": "#wood"},
            "east": {"uv": [0, 0, depth, height], "texture": "#wood"},
            "south": {"uv": [0, 0, width, height], "texture": "#wood"},
            "west": {"uv": [0, 0, depth, height], "texture": "#wood"},
            "up": {"uv": [0, 0, width, depth], "texture": "#wood"},
            "down": {"uv": [0, 0, width, depth], "texture": "#wood"},
        },
    }


def horizontal(edge="north", left_post=False, right_post=False, start=None, end=None):
    z0, z1 = (0, 4) if edge == "north" else (12, 16)
    rail0, rail1 = z0 + 0.5, z1 - 0.5
    start = (3 if left_post else 0) if start is None else start
    end = (13 if right_post else 16) if end is None else end
    elements = [
        cuboid([start, 1, rail0], [end, 3.5, rail1]),
        cuboid([start, 14.5, z0], [end, 18, z1]),
    ]
    x = start + 0.75
    while x + 2 <= end - 0.5:
        elements.append(cuboid([x, 3.5, z0 + 1], [x + 2, 14.5, z1 - 1]))
        x += 3.25
    if left_post:
        elements.append(cuboid([0, 0, z0 + 0.1], [3, 18, z1 - 0.1]))
    if right_post:
        elements.append(cuboid([13, 0, z0 + 0.1], [16, 18, z1 - 0.1]))
    return elements


def vertical(edge="west", near_post=False, far_post=False, start=None, end=None):
    x0, x1 = (0, 4) if edge == "west" else (12, 16)
    rail0, rail1 = x0 + 0.5, x1 - 0.5
    start = (3 if near_post else 0) if start is None else start
    end = (13 if far_post else 16) if end is None else end
    elements = [
        cuboid([rail0, 1, start], [rail1, 3.5, end]),
        cuboid([x0, 14.5, start], [x1, 18, end]),
    ]
    z = start + 0.75
    while z + 2 <= end - 0.5:
        elements.append(cuboid([x0 + 1, 3.5, z], [x1 - 1, 14.5, z + 2]))
        z += 3.25
    if near_post:
        elements.append(cuboid([x0 + 0.1, 0, 0], [x1 - 0.1, 18, 3]))
    if far_post:
        elements.append(cuboid([x0 + 0.1, 0, 13], [x1 - 0.1, 18, 16]))
    return elements


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -0.56, 0], "scale": [0.5556, 0.5556, 0.5556]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
}


def model(elements):
    return {
        "credit": "Seggellion from Ultimacraft; wooden fence generated from bannister proportions",
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": TEXTURE, "wood": TEXTURE},
        "elements": elements,
        "display": DISPLAY,
    }


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


def topology(bits):
    count = sum(bits.values())
    if count == 0:
        return "isolated"
    if count == 1:
        return "end"
    if count == 2 and (bits["north"] and bits["south"] or bits["east"] and bits["west"]):
        return "straight"
    if count == 2:
        return "corner"
    if count == 3:
        return "t_junction"
    return "cross"


def rotation_for(bits, facing):
    shape = topology(bits)
    if shape in ("isolated", "cross"):
        return FACINGS[facing]
    if shape == "end":
        connection = next(direction for direction in DIRECTIONS if bits[direction])
        return {"east": 0, "south": 90, "west": 180, "north": 270}[connection]
    if shape == "straight":
        return FACINGS[facing]
    if shape == "corner":
        pair = frozenset(direction for direction in DIRECTIONS if bits[direction])
        return {
            frozenset(("east", "south")): 0,
            frozenset(("south", "west")): 90,
            frozenset(("west", "north")): 180,
            frozenset(("north", "east")): 270,
        }[pair]
    missing = next(direction for direction in DIRECTIONS if not bits[direction])
    return FACINGS[missing]


def entry_for(bits, facing):
    shape = topology(bits)
    model_name = shape
    if shape == "end":
        connection = next(direction for direction in DIRECTIONS if bits[direction])
        rotated_north_edge = {"east": "north", "south": "east", "west": "south", "north": "west"}[connection]
        if facing != rotated_north_edge:
            model_name = "end_mirrored"
    entry = {"model": "britannia_mod:block/structure/wooden_fence/" + model_name}
    y = rotation_for(bits, facing)
    if y:
        entry["y"] = y
    entry["uvlock"] = True
    return entry


def main():
    junction_post = [cuboid([0.1, 0, 0.1], [3.9, 18, 3.9])]
    models = {
        "isolated": horizontal("north", True, True),
        "end": horizontal("north", True, False),
        "end_mirrored": horizontal("south", True, False),
        "straight": horizontal("north", False, False),
        "corner": junction_post + horizontal("north", start=4) + vertical("west", start=4),
        "t_junction": junction_post + horizontal("north", start=4) + vertical("west", start=4),
        "cross": junction_post + horizontal("north", start=4) + vertical("west", start=4),
    }
    for name, elements in models.items():
        write_json(os.path.join(MODEL_DIR, name + ".json"), model(elements))

    variants = {}
    for facing in FACINGS:
        for values in itertools.product((False, True), repeat=4):
            bits = dict(zip(DIRECTIONS, values))
            key = "facing={},north={},east={},south={},west={}".format(
                facing, *(str(bits[d]).lower() for d in DIRECTIONS))
            variants[key] = entry_for(bits, facing)
    write_json(os.path.join(ROOT, "blockstates", "wooden_fence.json"), {"variants": variants})
    write_json(os.path.join(ROOT, "models", "item", "wooden_fence.json"), {
        "parent": "britannia_mod:block/structure/wooden_fence/isolated"
    })
    print("Generated {} models and {} blockstate variants".format(len(models), len(variants)))


if __name__ == "__main__":
    main()

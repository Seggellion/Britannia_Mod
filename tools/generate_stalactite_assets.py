#!/usr/bin/env python3
"""Generate ceiling-mounted stalactite assets from the seven stalagmite models."""

from __future__ import annotations

import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/britannia_mod"
BLOCK_MODELS = ASSETS / "models/block/decorations/cave"
ITEM_MODELS = ASSETS / "models/item"
BLOCKSTATES = ASSETS / "blockstates"
ANCHOR_Y = 16.0


def reflected(value: float) -> float:
    result = ANCHOR_Y - value
    return int(result) if result.is_integer() else result


def invert_element(element: dict) -> None:
    old_from_y = float(element["from"][1])
    old_to_y = float(element["to"][1])
    element["from"][1] = reflected(old_to_y)
    element["to"][1] = reflected(old_from_y)

    rotation = element.get("rotation")
    if rotation:
        rotation["origin"][1] = reflected(float(rotation["origin"][1]))
        if rotation["axis"] in ("x", "z"):
            rotation["angle"] = -rotation["angle"]

    faces = element.get("faces", {})
    if "up" in faces or "down" in faces:
        up = faces.pop("up", None)
        down = faces.pop("down", None)
        if down is not None:
            faces["up"] = down
        if up is not None:
            faces["down"] = up

    # Preserve the original artwork as a true vertical reflection on side faces.
    for direction in ("north", "east", "south", "west"):
        uv = faces.get(direction, {}).get("uv")
        if uv:
            faces[direction]["uv"] = [uv[0], uv[3], uv[2], uv[1]]


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8")


def generate_variant(index: int) -> None:
    source = BLOCK_MODELS / f"stalagmite_{index}.json"
    target = BLOCK_MODELS / f"stalactite_{index}.json"
    model = copy.deepcopy(json.loads(source.read_text(encoding="utf-8")))
    model["credit"] = f"{model.get('credit', 'UltimaCraft')}; ceiling inverse generated from stalagmite_{index}"
    for element in model["elements"]:
        invert_element(element)
    write_json(target, model)

    variants = {
        "variants": {
            "facing=north": {"model": f"britannia_mod:block/decorations/cave/stalactite_{index}"},
            "facing=south": {
                "model": f"britannia_mod:block/decorations/cave/stalactite_{index}",
                "y": 180,
            },
            "facing=west": {
                "model": f"britannia_mod:block/decorations/cave/stalactite_{index}",
                "y": 270,
            },
            "facing=east": {
                "model": f"britannia_mod:block/decorations/cave/stalactite_{index}",
                "y": 90,
            },
        }
    }
    write_json(BLOCKSTATES / f"stalactite_{index}.json", variants)
    write_json(
        ITEM_MODELS / f"stalactite_{index}.json",
        {"parent": f"britannia_mod:block/decorations/cave/stalactite_{index}"},
    )


def main() -> None:
    for index in range(1, 8):
        generate_variant(index)


if __name__ == "__main__":
    main()

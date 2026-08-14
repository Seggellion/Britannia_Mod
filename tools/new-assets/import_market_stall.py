#!/usr/bin/env python3
"""Deterministically import the owner-supplied market-stall Blockbench model."""

from __future__ import annotations

import argparse
import base64
import colorsys
import hashlib
import io
import json
from pathlib import Path

from PIL import Image


EXPECTED_SOURCE_SHA256 = "f0cd41734254e5de24ff5bb753f8fe1273d7b8d87de11e60a9c0dc94faf3b82e"
EXPECTED_BOUNDS = (-16.0, -12.0, -11.5, 32.0, 32.0, 16.0)
COLORS = {
    "red": None,
    "blue": (0.61, 0.90, 0.90),
    "green": (0.36, 0.78, 0.80),
    "purple": (0.78, 0.86, 0.88),
}
ROOF_UUID = "605a7735-a722-9fdf-cca9-af9d5d30d279"
BACK_CLOTH_UUID = "17e14dfe-7f8d-9c50-5a2d-a5229be798b4"
FRONT_VALANCE_UUID = "e3f64ed0-708d-a935-0041-792a4873ebee"
HIDDEN_TOP_UUIDS = {
    "814e15df-cf3a-f1e9-2910-9d8a7adf0f20",
    "edaa6848-94d5-e16f-43e8-7019c0bfb745",
    "6d64db12-5923-571e-a61f-bc2a0b1d47de",
    "de655874-99d9-a992-2dc3-465953beb2d4",
}
HIDDEN_BACK_UUIDS = {
    "814e15df-cf3a-f1e9-2910-9d8a7adf0f20",
    "de655874-99d9-a992-2dc3-465953beb2d4",
}


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def clean(value: float) -> float | int:
    rounded = round(value, 6)
    return int(rounded) if rounded == int(rounded) else rounded


def converted_element(source: dict) -> dict:
    uuid = source.get("uuid", "")
    result = {
        "name": source.get("name", "cube"),
        "from": [clean(float(value)) for value in source["from"]],
        "to": [clean(float(value)) for value in source["to"]],
    }
    rotation = source.get("rotation", [0, 0, 0])
    nonzero = [(axis, float(value)) for axis, value in zip("xyz", rotation) if float(value) != 0.0]
    if len(nonzero) > 1:
        raise ValueError(f"Unsupported multi-axis cube rotation: {rotation}")
    if nonzero:
        axis, angle = nonzero[0]
        result["rotation"] = {
            "angle": clean(angle),
            "axis": axis,
            "origin": [clean(float(value)) for value in source["origin"]],
            "rescale": bool(source.get("rescale", False)),
        }
    if source.get("shade") is False:
        result["shade"] = False
    result["faces"] = {
        face_name: {
            "uv": [clean(float(value) / 16.0) for value in face["uv"]],
            "texture": "#texture",
        }
        for face_name, face in source["faces"].items()
        if "uv" in face and face.get("texture") is not None
    }
    # Blockbench represents these visible two-sided cloth sheets as zero-thickness cubes. Give
    # their opposing faces a sub-pixel separation so Minecraft cannot z-fight them.
    if uuid == ROOF_UUID:
        result["from"][1] = 31.99
        result["faces"] = {name: face for name, face in result["faces"].items() if name in {"up", "down"}}
    elif uuid == BACK_CLOTH_UUID:
        result["from"][2] = 15.99
        result["faces"] = {name: face for name, face in result["faces"].items() if name in {"north", "south"}}
    elif uuid == FRONT_VALANCE_UUID:
        result["to"][2] = -11.49
        result["faces"] = {name: face for name, face in result["faces"].items() if name in {"north", "south"}}
    # The source also exports wood faces completely hidden where posts/bars meet those sheets.
    if uuid in HIDDEN_TOP_UUIDS:
        result["faces"].pop("up", None)
    if uuid in HIDDEN_BACK_UUIDS:
        result["faces"].pop("south", None)
    return result


def source_bounds(elements: list[dict]) -> tuple[float, ...]:
    return (
        min(float(element["from"][0]) for element in elements),
        min(float(element["from"][1]) for element in elements),
        min(float(element["from"][2]) for element in elements),
        max(float(element["to"][0]) for element in elements),
        max(float(element["to"][1]) for element in elements),
        max(float(element["to"][2]) for element in elements),
    )


def is_fabric_uv_pixel(x: int, y: int) -> bool:
    return (
        (27 <= x < 123 and 0 <= y < 27)
        or (0 <= x < 96 and 27 <= y < 70)
        or (0 <= x < 96 and 70 <= y < 76)
    )


def is_red_fabric_pixel(pixel: tuple[int, int, int, int]) -> bool:
    red, green, blue, alpha = pixel
    return alpha > 0 and red >= 48 and red > green * 1.18 and red > blue * 1.12


def recolor_fabric(source: Image.Image, hue: float, saturation_scale: float, value_scale: float) -> Image.Image:
    result = source.copy()
    pixels = result.load()
    for y in range(result.height):
        for x in range(result.width):
            original = pixels[x, y]
            if not is_fabric_uv_pixel(x, y) or not is_red_fabric_pixel(original):
                continue
            red, green, blue, alpha = original
            _, saturation, value = colorsys.rgb_to_hsv(red / 255.0, green / 255.0, blue / 255.0)
            # Scale rather than flatten S/V so every authored fold and highlight remains present.
            recolored = colorsys.hsv_to_rgb(hue, saturation * saturation_scale, value * value_scale)
            pixels[x, y] = tuple(round(channel * 255.0) for channel in recolored) + (alpha,)
    return result


def blockstate(model_name: str) -> dict:
    return {
        "multipart": [
            {
                "when": {"facing": facing, "part": "1"},
                "apply": {
                    "model": f"britannia_mod:block/new_assets/{model_name}",
                    **({} if rotation == 0 else {"y": rotation}),
                },
            }
            for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
        ]
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--project-root", required=True, type=Path)
    args = parser.parse_args()

    raw = args.source.read_bytes()
    actual_hash = hashlib.sha256(raw).hexdigest()
    if actual_hash != EXPECTED_SOURCE_SHA256:
        raise ValueError(f"Unexpected market-stall source SHA-256: {actual_hash}")
    document = json.loads(raw)
    if document.get("meta", {}).get("model_format") != "java_block":
        raise ValueError("Market-stall source is not a Java block model")
    elements = document.get("elements", [])
    if len(elements) != 12 or source_bounds(elements) != EXPECTED_BOUNDS:
        raise ValueError(f"Unexpected market-stall geometry: count={len(elements)}, bounds={source_bounds(elements)}")
    if len(document.get("outliner", [])) != 12:
        raise ValueError("Market-stall outliner no longer contains the expected 12 root cubes")

    textures = document.get("textures", [])
    if len(textures) != 1 or not textures[0].get("source", "").startswith("data:image/png;base64,"):
        raise ValueError("Market-stall source must contain exactly one embedded PNG")
    encoded = textures[0]["source"].split(",", 1)[1]
    source_png = base64.b64decode(encoded)
    image = Image.open(io.BytesIO(source_png)).convert("RGBA")
    if image.size != (256, 256):
        raise ValueError(f"Unexpected market-stall texture dimensions: {image.size}")

    asset_root = args.project_root / "src/main/resources/assets/britannia_mod"
    model_root = asset_root / "models/block/new_assets"
    texture_root = asset_root / "textures/block/new_assets"
    state_root = asset_root / "blockstates"
    item_root = asset_root / "models/item"
    loot_root = args.project_root / "src/main/resources/data/britannia_mod/loot_table/blocks"

    canonical = {
        "credit": "Owner-supplied medieval_market_marketstall_red.bbmodel",
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "texture_size": [256, 256],
        "textures": {"texture": "britannia_mod:block/new_assets/market_stall_red", "particle": "#texture"},
        "elements": [converted_element(element) for element in elements],
        "display": document.get("display", {}),
    }
    write_json(model_root / "market_stall.json", canonical)

    texture_root.mkdir(parents=True, exist_ok=True)
    (texture_root / "market_stall_red.png").write_bytes(source_png)
    for color, transform in COLORS.items():
        if transform is not None:
            recolor_fabric(image, *transform).save(texture_root / f"market_stall_{color}.png", format="PNG")
        name = f"market_stall_{color}"
        write_json(model_root / f"{name}.json", {
            "parent": "britannia_mod:block/new_assets/market_stall",
            "textures": {"texture": f"britannia_mod:block/new_assets/{name}"},
        })
        write_json(item_root / f"{name}.json", {"parent": f"britannia_mod:block/new_assets/{name}"})
        write_json(state_root / f"{name}.json", blockstate(name))
        write_json(loot_root / f"{name}.json", {"type": "minecraft:block", "pools": []})

    print("Imported market stall: 12 authoritative cubes, baked-quad normalized to 48x16x44.815764, four fabric colours.")


if __name__ == "__main__":
    main()

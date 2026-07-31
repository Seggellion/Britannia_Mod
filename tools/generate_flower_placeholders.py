#!/usr/bin/env python3
"""Generate deterministic technical flower placeholders with no third-party packages.

The default mode is create-only and refuses to overwrite any target. The explicit
--overwrite-placeholders mode first verifies every existing target against the
previous generated hash ledger, so owner replacement artwork is never silently
replaced. --check performs a byte-for-byte reproducibility audit without writing.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "britannia_mod"
DATA = RESOURCES / "data" / "britannia_mod"
HASH_LEDGER = ROOT / "tools" / "flower_placeholder_hashes.json"
MANIFEST = ROOT / "FLOWER_ASSET_PLACEHOLDER_MANIFEST.md"
SHARED_PARENT_ID = "britannia_mod:block/flowers/shared/multi_plane"
SHARED_PARENT_PATH = ASSETS / "models" / "block" / "flowers" / "shared" / "multi_plane.json"

SPECIES = (
    ("poppy", "Poppy", (190, 71, 80)),
    ("snowdrop", "Snowdrop", (220, 220, 205)),
    ("lily", "Lily", (224, 157, 83)),
    ("foxglove", "Foxglove", (157, 104, 170)),
    ("campion", "Campion", (194, 99, 140)),
    ("hyacinth", "Hyacinth", (92, 118, 176)),
    ("orfluer", "Orfluer", (114, 164, 154)),
)
STAGES = tuple(range(1, 8))
EMPTY_MASK_STAGES = frozenset((1, 2))


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def png_chunk(chunk_type: bytes, payload: bytes) -> bytes:
    body = chunk_type + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def encode_png(width: int, height: int, pixels: list[list[tuple[int, int, int, int]]]) -> bytes:
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel)
    signature = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return signature + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + png_chunk(b"IEND", b"")


def canvas(size: int) -> list[list[tuple[int, int, int, int]]]:
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]


def put(pixels: list[list[tuple[int, int, int, int]]], x: int, y: int, color: tuple[int, int, int, int]) -> None:
    if 0 <= y < len(pixels) and 0 <= x < len(pixels[0]):
        pixels[y][x] = color


def line(
    pixels: list[list[tuple[int, int, int, int]]],
    x0: int,
    y0: int,
    x1: int,
    y1: int,
    color: tuple[int, int, int, int],
    width: int = 1,
) -> None:
    dx = abs(x1 - x0)
    sx = 1 if x0 < x1 else -1
    dy = -abs(y1 - y0)
    sy = 1 if y0 < y1 else -1
    error = dx + dy
    while True:
        radius = width // 2
        for py in range(y0 - radius, y0 + radius + 1):
            for px in range(x0 - radius, x0 + radius + 1):
                put(pixels, px, py, color)
        if x0 == x1 and y0 == y1:
            return
        doubled = 2 * error
        if doubled >= dy:
            error += dy
            x0 += sx
        if doubled <= dx:
            error += dx
            y0 += sy


def ellipse(
    pixels: list[list[tuple[int, int, int, int]]],
    cx: int,
    cy: int,
    rx: int,
    ry: int,
    color: tuple[int, int, int, int],
) -> None:
    if rx <= 0 or ry <= 0:
        return
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if ((x - cx) * (x - cx)) / (rx * rx) + ((y - cy) * (y - cy)) / (ry * ry) <= 1.0:
                put(pixels, x, y, color)


def stage_art(species_index: int, species: str, stage: int) -> tuple[bytes, bytes]:
    size = 128
    base = canvas(size)
    mask = canvas(size)
    bottom = 119
    tops = (0, 101, 88, 74, 59, 43, 28, 17)
    top = tops[stage]
    center = 64 + ((species_index % 3) - 1) * 2
    stem = (52 + species_index * 2, 120 + species_index * 3, 66 + species_index, 255)
    leaf = (43 + species_index * 2, 105 + species_index * 3, 57 + species_index, 255)
    dark = (30, 76, 41, 255)

    line(base, center, bottom, center + (species_index % 2), top + 3, dark, 5)
    line(base, center, bottom, center + (species_index % 2), top + 3, stem, 3)
    leaf_count = min(4, stage)
    for leaf_index in range(leaf_count):
        y = bottom - 15 - leaf_index * max(10, (bottom - top) // 5)
        direction = -1 if (leaf_index + species_index) % 2 == 0 else 1
        ellipse(base, center + direction * (8 + leaf_index), y, 9 + stage // 3, 4 + stage // 4, leaf)
        line(base, center, y + 2, center + direction * (14 + leaf_index), y - 1, dark, 2)

    if stage == 3:
        ellipse(mask, center, top + 2, 5, 7, (150, 150, 150, 255))
        ellipse(mask, center - 1, top, 3, 3, (225, 225, 225, 255))
    elif stage == 4:
        ellipse(mask, center, top + 3, 7, 9, (145, 145, 145, 255))
        ellipse(mask, center - 2, top, 4, 4, (230, 230, 230, 255))
    elif stage >= 5:
        radius = {5: 6, 6: 9, 7: 11}[stage]
        offsets = ((0, -radius), (radius, 0), (0, radius), (-radius, 0))
        if species in ("foxglove", "hyacinth"):
            offsets = ((-5, -radius), (5, -radius // 2), (-4, 2), (5, radius // 2), (0, radius))
        for index, (ox, oy) in enumerate(offsets):
            gray = (155, 195, 230, 175)[index % 4]
            ellipse(mask, center + ox, top + oy, radius, max(4, radius - 2), (gray, gray, gray, 255))
        ellipse(mask, center, top, max(3, radius // 2), max(3, radius // 2), (110, 110, 110, 255))
        ellipse(mask, center - 2, top - 2, max(1, radius // 4), max(1, radius // 4), (240, 240, 240, 255))
        if stage == 7 and species == "poppy":
            ellipse(mask, center, top, 5, 8, (105, 105, 105, 255))
            line(mask, center - 5, top, center + 5, top, (220, 220, 220, 255), 2)

    # Tintable pixels are deliberately absent from the fixed base pass.
    for y in range(size):
        for x in range(size):
            if mask[y][x][3] != 0:
                base[y][x] = (0, 0, 0, 0)

    return encode_png(size, size, base), encode_png(size, size, mask)


def item_art(species_index: int, accent: tuple[int, int, int], seeds: bool) -> bytes:
    size = 32
    pixels = canvas(size)
    if seeds:
        ellipse(pixels, 16, 17, 8, 11, (94, 60, 32, 255))
        ellipse(pixels, 14, 14, 5, 7, (164, 116, 62, 255))
        line(pixels, 11, 20, 21, 13, (*accent, 255), 2)
    else:
        line(pixels, 16, 28, 16, 10, (45, 105, 55, 255), 3)
        ellipse(pixels, 11, 21, 5, 3, (56, 124, 64, 255))
        ellipse(pixels, 21, 18, 5, 3, (56, 124, 64, 255))
        for ox, oy in ((0, -5), (5, 0), (0, 5), (-5, 0)):
            ellipse(pixels, 16 + ox, 9 + oy, 5, 4, (*accent, 255))
        ellipse(pixels, 16, 9, 3, 3, (225, 205, 110, 255))
    for marker in range(species_index + 1):
        put(pixels, 2 + marker * 2, 29, (235, 235, 235, 255))
    return encode_png(size, size, pixels)


def shared_parent() -> dict[str, object]:
    def faces(a: str, b: str) -> dict[str, object]:
        return {
            a: {"texture": "#flower", "uv": [0, 0, 16, 16]},
            b: {"texture": "#flower", "uv": [0, 0, 16, 16]},
        }

    return {
        "ambientocclusion": False,
        "gui_light": "front",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "#flower"},
        "elements": [
            {"from": [1, 0, 8], "to": [15, 16, 8], "faces": faces("north", "south")},
            {"from": [8, 0, 1], "to": [8, 16, 15], "faces": faces("east", "west")},
            {
                "from": [1, 0, 8], "to": [15, 16, 8],
                "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8], "rescale": False},
                "faces": faces("north", "south"),
            },
            {
                "from": [1, 0, 8], "to": [15, 16, 8],
                "rotation": {"angle": -45, "axis": "y", "origin": [8, 8, 8], "rescale": False},
                "faces": faces("north", "south"),
            },
        ],
    }


def resource_path(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def build_manifest() -> bytes:
    lines = [
        "# Flower Asset Placeholder Manifest",
        "",
        "**All current flower assets are technical placeholders and are not final owner-approved artwork.**",
        "",
        "**Do not add a third in-world texture.**",
        "",
        "**Do not bake a species colour into dye_mask.**",
        "",
        "**Do not change base and dye-mask dimensions independently.**",
        "",
        "**Do not change the UV layout in only one pass.**",
        "",
        "**Do not overwrite approved replacement artwork with the placeholder generator.**",
        "",
        "Generated deterministically by `tools/generate_flower_placeholders.py` using only the Python standard library. "
        "Default generation is create-only. `--overwrite-placeholders` requires every existing file to match "
        "`tools/flower_placeholder_hashes.json`; `--check` performs a non-writing reproducibility audit.",
        "",
        "## In-world stage placeholders",
        "",
        "Every pass inherits the same four-plane cutout parent. UV `[0,0,16,16]` covers the full 128x128 canvas; "
        "base and mask use identical canvas bounds, padding assumptions, model transforms, and pixel grid. "
        "Stages 1-2 intentionally have no visible bloom, so their dye masks are valid fully transparent PNGs. "
        "Poppy stage 7 is a distinct reserved manual-stage asset only; this milestone adds no advancement behavior.",
        "",
        "| Species registry ID | Stage | Base model path | Dye-mask model path | Shared geometry parent | Base texture path | Dye-mask texture path | Dimensions | UV assumptions | Dye mask intentionally empty | Status | Replacement status | Particle texture source | Final-art approval |",
        "|---|---:|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for species, _, _ in SPECIES:
        for stage in STAGES:
            prefix = f"src/main/resources/assets/britannia_mod"
            base_model = f"{prefix}/models/block/flowers/{species}/stage_{stage}_base.json"
            mask_model = f"{prefix}/models/block/flowers/{species}/stage_{stage}_dye_mask.json"
            base_texture = f"{prefix}/textures/block/flowers/{species}/stage_{stage}_base_texture.png"
            mask_texture = f"{prefix}/textures/block/flowers/{species}/stage_{stage}_dye_mask.png"
            empty = "Yes - no bloom at this stage" if stage in EMPTY_MASK_STAGES else "No"
            lines.append(
                f"| `britannia_mod:{species}` | {stage} | `{base_model}` | `{mask_model}` | "
                f"`{SHARED_PARENT_ID}` | `{base_texture}` | `{mask_texture}` | 128x128 | Full-canvas 0..16; identical pass geometry and pixel grid | "
                f"{empty} | Generated placeholder | Pending owner replacement | Paired base texture | Not approved |"
            )

    lines.extend([
        "",
        "## Item placeholders",
        "",
        "Harvested flowers and seeds are ordinary `net.minecraft.world.item.Item` registrations in Milestone 3. "
        "`FlowerRegistry` remains the sole seed-to-species mapping. No food, healing, colour-bearing item state, "
        "planting, harvesting, or crop-to-seed activation behavior is implemented here.",
        "",
        "| Registry ID | Item model path | Item texture path | Item class / registration approach | Creative-tab placement | Status | Final-art approval |",
        "|---|---|---|---|---|---|---|",
    ])
    for species, _, _ in SPECIES:
        for suffix, placement in (("", "Britannia World - Farming Produce"), ("_seeds", "Britannia World - Farming Seeds")):
            registry_id = f"britannia_mod:{species}{suffix}"
            model = f"src/main/resources/assets/britannia_mod/models/item/{species}{suffix}.json"
            texture = f"src/main/resources/assets/britannia_mod/textures/item/flowers/{species}{suffix}.png"
            lines.append(
                f"| `{registry_id}` | `{model}` | `{texture}` | Ordinary `Item`; authoritative mapping in `FlowerRegistry` | "
                f"{placement} | Generated placeholder | Not approved |"
            )

    lines.extend([
        "",
        "## Shared and supporting resources",
        "",
        "- Milestone 4 temporary generic-block presentation: `assets/britannia_mod/blockstates/flower_block.json` selects six thin wrapper models, `models/block/flower_block_0.json` through `flower_block_5.json`, which inherit the existing hydration-specific farming-soil models. These files deliberately render soil only; they do not select a species/stage model, apply tint, or compete with the Milestone 7 block-entity renderer. Milestone 7 may retain or replace this soil pass when the flower renderer is registered.",
        f"- Shared geometry parent: `{resource_path(SHARED_PARENT_PATH)}` (generated, four intersecting planes, cutout, no PNG dependency of its own).",
        "- Harvested-item tag: `src/main/resources/data/britannia_mod/tags/items/flowers.json`.",
        "- Seed-item tag: `src/main/resources/data/britannia_mod/tags/items/flower_seeds.json`.",
        "- Placeholder hash ledger: `tools/flower_placeholder_hashes.json` (generated safety metadata; it does not include itself).",
        "- Placeholder generator: `tools/generate_flower_placeholders.py` (handwritten development tooling; not a runtime dependency).",
        "",
        "## Counts and replacement contract",
        "",
        "- 7 species",
        "- 14 logical flower/seed items and 14 item textures",
        "- 49 logical in-world stage models",
        "- 49 base pass models and 49 dye-mask pass models",
        "- 49 base textures and 49 dye-mask textures (98 in-world PNGs total)",
        "- 1 shared geometry parent",
        "",
        "Replacement art must keep the paired 128x128 canvas, full-canvas UV layout, transparent padding, and "
        "base/mask pixel alignment unless the two pass models are intentionally revised together. Base pixels "
        "must remain transparent wherever the mask selects tint. Visible mask RGB must remain neutral grayscale. "
        "Final owner artwork approval is a separate review and is not implied by technical validation.",
        "",
    ])
    return "\n".join(lines).encode("utf-8")


def expected_outputs() -> dict[Path, bytes]:
    outputs: dict[Path, bytes] = {SHARED_PARENT_PATH: json_bytes(shared_parent())}
    flowers: list[str] = []
    seeds: list[str] = []
    for species_index, (species, _, accent) in enumerate(SPECIES):
        flowers.append(f"britannia_mod:{species}")
        seeds.append(f"britannia_mod:{species}_seeds")
        for suffix, is_seed in (("", False), ("_seeds", True)):
            item_id = f"{species}{suffix}"
            outputs[ASSETS / "models" / "item" / f"{item_id}.json"] = json_bytes({
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"britannia_mod:item/flowers/{item_id}"},
            })
            outputs[ASSETS / "textures" / "item" / "flowers" / f"{item_id}.png"] = item_art(species_index, accent, is_seed)

        for stage in STAGES:
            base_ref = f"britannia_mod:block/flowers/{species}/stage_{stage}_base_texture"
            mask_ref = f"britannia_mod:block/flowers/{species}/stage_{stage}_dye_mask"
            model_root = ASSETS / "models" / "block" / "flowers" / species
            outputs[model_root / f"stage_{stage}_base.json"] = json_bytes({
                "parent": SHARED_PARENT_ID,
                "textures": {"flower": base_ref, "particle": base_ref},
            })
            outputs[model_root / f"stage_{stage}_dye_mask.json"] = json_bytes({
                "parent": SHARED_PARENT_ID,
                "textures": {"flower": mask_ref, "particle": base_ref},
            })
            base_png, mask_png = stage_art(species_index, species, stage)
            texture_root = ASSETS / "textures" / "block" / "flowers" / species
            outputs[texture_root / f"stage_{stage}_base_texture.png"] = base_png
            outputs[texture_root / f"stage_{stage}_dye_mask.png"] = mask_png

    outputs[DATA / "tags" / "items" / "flowers.json"] = json_bytes({"replace": False, "values": flowers})
    outputs[DATA / "tags" / "items" / "flower_seeds.json"] = json_bytes({"replace": False, "values": seeds})
    outputs[MANIFEST] = build_manifest()
    return outputs


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def ledger_bytes(outputs: dict[Path, bytes]) -> bytes:
    return json_bytes({
        "generator": "tools/generate_flower_placeholders.py",
        "schema": 1,
        "files": {resource_path(path): digest(data) for path, data in sorted(outputs.items(), key=lambda entry: resource_path(entry[0]))},
    })


def check(outputs: dict[Path, bytes]) -> None:
    failures: list[str] = []
    for path, data in outputs.items():
        if not path.is_file():
            failures.append(f"missing: {resource_path(path)}")
        elif path.read_bytes() != data:
            failures.append(f"not reproducible or replaced: {resource_path(path)}")
    expected_ledger = ledger_bytes(outputs)
    if not HASH_LEDGER.is_file():
        failures.append(f"missing: {resource_path(HASH_LEDGER)}")
    elif HASH_LEDGER.read_bytes() != expected_ledger:
        failures.append(f"hash ledger mismatch: {resource_path(HASH_LEDGER)}")
    if failures:
        raise RuntimeError("Placeholder check failed:\n" + "\n".join(failures))


def verify_safe_overwrite(outputs: dict[Path, bytes]) -> None:
    if not HASH_LEDGER.is_file():
        raise RuntimeError("Cannot overwrite without the generated hash ledger")
    ledger = json.loads(HASH_LEDGER.read_text(encoding="utf-8"))
    recorded = ledger.get("files", {})
    expected_paths = {resource_path(path) for path in outputs}
    if set(recorded) != expected_paths:
        raise RuntimeError("Refusing overwrite because the managed placeholder path set changed")
    changed = []
    for path in outputs:
        if path.exists() and digest(path.read_bytes()) != recorded[resource_path(path)]:
            changed.append(resource_path(path))
    if changed:
        raise RuntimeError("Refusing to overwrite replaced or edited artwork:\n" + "\n".join(changed))


def write(outputs: dict[Path, bytes], overwrite: bool) -> None:
    if overwrite:
        verify_safe_overwrite(outputs)
    else:
        existing = [resource_path(path) for path in (*outputs.keys(), HASH_LEDGER) if path.exists()]
        if existing:
            raise RuntimeError("Create-only generation refused existing targets:\n" + "\n".join(existing))
    for path, data in sorted(outputs.items(), key=lambda entry: resource_path(entry[0])):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    HASH_LEDGER.write_bytes(ledger_bytes(outputs))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Verify all generated placeholders byte-for-byte without writing")
    mode.add_argument(
        "--overwrite-placeholders",
        action="store_true",
        help="Overwrite only files that still match the prior generated hash ledger",
    )
    args = parser.parse_args()
    outputs = expected_outputs()
    try:
        if args.check:
            check(outputs)
            print(f"Verified {len(outputs)} generated placeholder files plus hash ledger")
        else:
            write(outputs, args.overwrite_placeholders)
            print(f"Generated {len(outputs)} placeholder files plus hash ledger")
    except RuntimeError as exception:
        print(str(exception), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

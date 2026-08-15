#!/usr/bin/env python3
"""Generate the custom metal ingot textures and their item models, no third-party packages.

Every custom ingot previously pointed its model at ``minecraft:item/iron_ingot``, so Silver,
Tin, Shadow Iron, Agapite, Verite, Valorite and Copper were visually indistinguishable in an
inventory, and Bronze had no art at all. Referencing Mojang's texture is fine; copying or
recolouring it into this All Rights Reserved mod is not. These sprites are therefore drawn from
an original template defined here, in palettes chosen for this project.

Create-only by default: it refuses to overwrite anything, so owner replacement artwork is never
clobbered. ``--overwrite-generated`` replaces only files whose current bytes still match the hash
ledger. ``--check`` audits reproducibility without writing.

    python tools/generate_metal_ingot_textures.py
    python tools/generate_metal_ingot_textures.py --check
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
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "britannia_mod"
HASH_LEDGER = ROOT / "tools" / "metal_ingot_texture_hashes.json"

SIZE = 16

# One original ingot silhouette shared by every metal, so the set reads as a family and only the
# palette distinguishes them. '.' transparent, 'o' outline, 's' shadow, 'b' body, 'h' highlight.
TEMPLATE = (
    "................",
    "................",
    "................",
    "................",
    "....oooooooo....",
    "..oohhhhhhhhoo..",
    ".ohhbbbbbbbbhho.",
    ".obbbbbbbbbbbbo.",
    ".obbbbbbbbbbbbo.",
    ".osbbbbbbbbbbso.",
    ".osssssssssssso.",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
    "................",
)

# outline, shadow, body, highlight. Chosen so the ladder reads at a glance: the humble metals are
# grey/brown, the fantasy metals carry their Ultima Online colours (agapite rose-purple, verite
# green, valorite blue), and bronze sits visibly between copper and gold.
PALETTES = {
    "silver_ingot": ("#535a63", "#8b939d", "#c4ccd5", "#eff3f7"),
    "tin_ingot": ("#5f666b", "#909a9f", "#bcc6cb", "#e2eaee"),
    "copper_ingot": ("#68371e", "#a5572c", "#d07b45", "#eaa26a"),
    "bronze_ingot": ("#573813", "#8a5820", "#b8802f", "#dcab55"),
    "shadow_iron_ingot": ("#25262d", "#414350", "#5d606f", "#858a9c"),
    "agapite_ingot": ("#4b2a50", "#7d4a87", "#a96cb4", "#cd9ad4"),
    "verite_ingot": ("#123f27", "#1f6b3f", "#2f9457", "#57c281"),
    "valorite_ingot": ("#1c3750", "#2f5c80", "#4586b4", "#77b6df",),
}


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


def rgba(hex_colour: str) -> tuple[int, int, int, int]:
    value = hex_colour.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


def ingot_pixels(palette: tuple[str, str, str, str]) -> list[list[tuple[int, int, int, int]]]:
    outline, shadow, body, highlight = (rgba(colour) for colour in palette)
    lookup = {"o": outline, "s": shadow, "b": body, "h": highlight}
    pixels = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    for y, row in enumerate(TEMPLATE):
        for x, glyph in enumerate(row):
            if glyph != ".":
                pixels[y][x] = lookup[glyph]
    return pixels


def model_bytes(name: str) -> bytes:
    model = {
        "parent": "item/generated",
        "textures": {"layer0": f"britannia_mod:item/{name}"},
    }
    return (json.dumps(model, indent=2) + "\n").encode("utf-8")


def targets() -> dict[Path, bytes]:
    planned: dict[Path, bytes] = {}
    for name, palette in PALETTES.items():
        planned[ASSETS / "textures" / "item" / f"{name}.png"] = encode_png(
            SIZE, SIZE, ingot_pixels(palette)
        )
        planned[ASSETS / "models" / "item" / f"{name}.json"] = model_bytes(name)
    return planned


def digest(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="audit only, write nothing")
    parser.add_argument(
        "--overwrite-generated",
        action="store_true",
        help="replace targets whose bytes still match the ledger",
    )
    arguments = parser.parse_args()

    planned = targets()
    ledger = json.loads(HASH_LEDGER.read_text()) if HASH_LEDGER.exists() else {"files": {}}
    recorded = ledger.get("files", {})

    if arguments.check:
        problems = []
        for path, payload in planned.items():
            key = relative(path)
            if not path.exists():
                problems.append(f"missing: {key}")
            elif digest(path.read_bytes()) != digest(payload):
                problems.append(f"not reproducible: {key}")
        for problem in problems:
            print(problem)
        print("check failed" if problems else f"check ok: {len(planned)} files reproducible")
        return 1 if problems else 0

    written = 0
    skipped = 0
    for path, payload in sorted(planned.items()):
        key = relative(path)
        if path.exists():
            current = digest(path.read_bytes())
            if current == digest(payload):
                skipped += 1
                continue
            if not (arguments.overwrite_generated and recorded.get(key) == current):
                print(f"refusing to overwrite (not generated by this tool): {key}")
                skipped += 1
                continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        recorded[key] = digest(payload)
        written += 1

    HASH_LEDGER.write_text(json.dumps({"files": dict(sorted(recorded.items()))}, indent=2) + "\n")
    print(f"wrote {written}, left alone {skipped}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

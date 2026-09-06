#!/usr/bin/env python3
"""Extract and normalize ordered roof textures from the PDF-compatible Illustrator master."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from io import BytesIO
from pathlib import Path

import PIL
import pypdf
from PIL import Image, ImageCms, ImageDraw
from pypdf import PdfReader


EXPECTED_SOURCE_SHA256 = (
    "9ae5a6a8b520d0710da961f0f637e246dcabefa949097df14eaeebb0354caf8a"
)
EXPECTED_SIZE = (1254, 1254)
OUTPUT_SIZE = (64, 64)
EXPECTED_LAYERS = {
    "sandstone": [f"/Im{index}" for index in range(6)],
    "limestone": [f"/Im{index}" for index in range(6, 12)],
}
CONTENT_TOKEN = re.compile(
    rb"/OC\s+(/MC\d+)\s+BDC|(/Im\d+)\s+Do|\bEMC\b"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_order(page, reader: PdfReader) -> dict[str, list[str]]:
    properties = page["/Resources"]["/Properties"]
    marker_layers = {
        str(marker): str(reference.get_object()["/Name"])
        for marker, reference in properties.items()
    }
    content = page.get_contents().get_data()
    ordered: dict[str, list[str]] = {name: [] for name in EXPECTED_LAYERS}
    active_layer: str | None = None

    for match in CONTENT_TOKEN.finditer(content):
        marker, image_name = match.group(1), match.group(2)
        if marker is not None:
            marker_name = marker.decode("ascii")
            active_layer = marker_layers.get(marker_name)
            if active_layer not in EXPECTED_LAYERS:
                raise ValueError(
                    f"unexpected optional-content layer for {marker_name}: {active_layer}"
                )
        elif image_name is not None:
            if active_layer is None:
                raise ValueError(f"image {image_name!r} appears outside an expected layer")
            ordered[active_layer].append(image_name.decode("ascii"))
        else:
            active_layer = None

    if ordered != EXPECTED_LAYERS:
        raise ValueError(
            f"Illustrator layer/object order changed: expected {EXPECTED_LAYERS}, got {ordered}"
        )
    return ordered


def icc_description(image_object) -> tuple[bytes, str]:
    color_space = image_object["/ColorSpace"]
    if str(color_space[0]) != "/ICCBased":
        raise ValueError(f"expected ICCBased RGB, got {color_space}")
    profile_object = color_space[1].get_object()
    if int(profile_object["/N"]) != 3:
        raise ValueError(f"expected three-component ICC profile, got {profile_object['/N']}")
    profile_bytes = profile_object.get_data()
    profile = ImageCms.ImageCmsProfile(BytesIO(profile_bytes))
    return profile_bytes, ImageCms.getProfileDescription(profile).strip()


def make_contact_sheet(entries: list[dict], qa_dir: Path) -> Path:
    tile_size = 192
    label_height = 28
    margin = 8
    sheet = Image.new(
        "RGB",
        (6 * tile_size + 7 * margin, 2 * (tile_size + label_height) + 3 * margin),
        "#202020",
    )
    draw = ImageDraw.Draw(sheet)
    for index, entry in enumerate(entries):
        row = 0 if entry["material"] == "sandstone" else 1
        column = entry["source_position"] - 1
        x = margin + column * (tile_size + margin)
        y = margin + row * (tile_size + label_height + margin)
        with Image.open(entry["output_path"]) as texture:
            preview = texture.resize((tile_size, tile_size), Image.Resampling.NEAREST)
            sheet.paste(preview, (x, y))
        draw.text(
            (x, y + tile_size + 6),
            f"{entry['material']} {entry['source_position']}  {entry['xobject']}",
            fill="white",
        )
    qa_dir.mkdir(parents=True, exist_ok=True)
    path = qa_dir / "roof-textures-64px-contact-sheet.png"
    sheet.save(path, format="PNG", optimize=True, compress_level=9)
    return path


def extract(args: argparse.Namespace) -> dict:
    source = args.source.resolve()
    output_dir = args.output_dir.resolve()
    source_bytes = source.read_bytes()
    source_hash = sha256(source_bytes)
    if source_hash != EXPECTED_SOURCE_SHA256:
        raise ValueError(
            "authoritative Illustrator master hash changed: "
            f"expected {EXPECTED_SOURCE_SHA256}, got {source_hash}"
        )

    reader = PdfReader(source)
    if len(reader.pages) != 1:
        raise ValueError(f"expected one PDF-compatible Illustrator page, got {len(reader.pages)}")
    page = reader.pages[0]
    order = source_order(page, reader)
    xobjects = page["/Resources"]["/XObject"]
    output_dir.mkdir(parents=True, exist_ok=True)

    expected_outputs = {
        f"{material}_roof_{position}.png"
        for material in EXPECTED_LAYERS
        for position in range(1, 7)
    }
    actual_outputs = {
        path.name
        for material in EXPECTED_LAYERS
        for path in output_dir.glob(f"{material}_roof_*.png")
    }
    unexpected_outputs = actual_outputs - expected_outputs
    if unexpected_outputs:
        raise ValueError(
            f"unexpected numbered roof textures already exist: {sorted(unexpected_outputs)}"
        )

    entries: list[dict] = []
    for material, image_names in order.items():
        for position, image_name in enumerate(image_names, start=1):
            image_object = xobjects[image_name].get_object()
            width = int(image_object["/Width"])
            height = int(image_object["/Height"])
            bits = int(image_object["/BitsPerComponent"])
            if (width, height) != EXPECTED_SIZE or bits != 8:
                raise ValueError(
                    f"{material} source {position} changed geometry: "
                    f"{width}x{height}, {bits} bits"
                )
            profile_bytes, profile_description = icc_description(image_object)
            if "sRGB" not in profile_description:
                raise ValueError(
                    f"{material} source {position} is not tagged sRGB: {profile_description}"
                )

            decoded = image_object.get_data()
            expected_length = width * height * 3
            if len(decoded) != expected_length:
                raise ValueError(
                    f"{material} source {position} decoded to {len(decoded)} bytes; "
                    f"expected {expected_length} RGB bytes"
                )
            original = Image.frombytes("RGB", (width, height), decoded)
            normalized = original.resize(
                OUTPUT_SIZE,
                Image.Resampling.LANCZOS,
                reducing_gap=3.0,
            )
            output_path = output_dir / f"{material}_roof_{position}.png"
            normalized.save(
                output_path,
                format="PNG",
                optimize=True,
                compress_level=9,
            )
            output_bytes = output_path.read_bytes()
            entry = {
                "material": material,
                "source_position": position,
                "layer": material,
                "xobject": image_name,
                "pdf_object": image_object.indirect_reference.idnum,
                "source_width": width,
                "source_height": height,
                "source_bits_per_component": bits,
                "source_mode": "RGB",
                "source_icc_description": profile_description,
                "source_icc_sha256": sha256(profile_bytes),
                "source_decoded_rgb_sha256": sha256(decoded),
                "output_file": output_path.name,
                "output_width": normalized.width,
                "output_height": normalized.height,
                "output_mode": normalized.mode,
                "output_png_sha256": sha256(output_bytes),
                "output_rgb_sha256": sha256(normalized.tobytes()),
                "output_path": str(output_path),
            }
            entries.append(entry)

            if args.qa_dir is not None:
                qa_originals = args.qa_dir.resolve() / "originals"
                qa_originals.mkdir(parents=True, exist_ok=True)
                original.save(
                    qa_originals / f"{material}_source_{position}_{image_name[1:]}.png",
                    format="PNG",
                    optimize=True,
                    compress_level=9,
                    icc_profile=profile_bytes,
                )

    sandstone = [
        entry for entry in entries if entry["material"] == "sandstone"
    ]
    limestone = [
        entry for entry in entries if entry["material"] == "limestone"
    ]
    sandstone_groups: dict[str, list[int]] = {}
    for entry in sandstone:
        sandstone_groups.setdefault(entry["source_decoded_rgb_sha256"], []).append(
            entry["source_position"]
        )
    duplicate_groups = sorted(
        positions for positions in sandstone_groups.values() if len(positions) > 1
    )
    if duplicate_groups != [[2, 4]] or len(sandstone_groups) != 5:
        raise ValueError(
            "sandstone duplicate contract changed: "
            f"expected only positions 2 and 4 to match, got {duplicate_groups}"
        )
    if len({entry["source_decoded_rgb_sha256"] for entry in limestone}) != 6:
        raise ValueError("limestone source images are no longer six unique designs")
    if len({entry["output_rgb_sha256"] for entry in sandstone}) != 5:
        raise ValueError("normalized sandstone outputs do not retain exactly five unique designs")
    if len({entry["output_rgb_sha256"] for entry in limestone}) != 6:
        raise ValueError("normalized limestone outputs are not six unique designs")

    for entry in entries:
        entry.pop("output_path")
    manifest = {
        "schema": 1,
        "generated_by": "tools/roof_textures/extract_roof_textures.py",
        "source": {
            "file": str(source),
            "sha256": source_hash,
            "pdf_version": reader.pdf_header,
            "page_count": len(reader.pages),
        },
        "normalization": {
            "algorithm": "Pillow LANCZOS with reducing_gap=3.0",
            "width": OUTPUT_SIZE[0],
            "height": OUTPUT_SIZE[1],
            "mode": "RGB",
            "png_compress_level": 9,
            "pypdf_version": pypdf.__version__,
            "pillow_version": PIL.__version__,
        },
        "ordering": {
            "sandstone": "PDF optional-content layer sandstone, /Im0 through /Im5",
            "limestone": "PDF optional-content layer limestone, /Im6 through /Im11",
        },
        "duplicate_gate": {
            "sandstone_unique_designs": 5,
            "sandstone_duplicate_source_positions": [2, 4],
            "limestone_unique_designs": 6,
            "final_sandstone_visual_acceptance_blocked": True,
        },
        "textures": entries,
    }

    args.manifest.resolve().parent.mkdir(parents=True, exist_ok=True)
    args.manifest.resolve().write_text(
        json.dumps(manifest, indent=2) + "\n",
        encoding="utf-8",
    )
    if args.qa_dir is not None:
        contact_sheet = make_contact_sheet(
            [
                {
                    **entry,
                    "output_path": str(output_dir / entry["output_file"]),
                }
                for entry in entries
            ],
            args.qa_dir.resolve(),
        )
        print(f"QA contact sheet: {contact_sheet}")
    return manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--qa-dir", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    manifest = extract(args)
    duplicate = manifest["duplicate_gate"]
    print(
        "Extracted 12 ordered roof textures: "
        f"sandstone unique={duplicate['sandstone_unique_designs']}/6 "
        f"(duplicate positions {duplicate['sandstone_duplicate_source_positions']}), "
        f"limestone unique={duplicate['limestone_unique_designs']}/6"
    )


if __name__ == "__main__":
    main()

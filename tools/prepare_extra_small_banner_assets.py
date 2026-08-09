#!/usr/bin/env python3
"""Normalize Illustrator mask exports, adopt runtime assets, and create review artifacts."""

from __future__ import annotations

import hashlib
import json
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


BANNERS = (
    "road_guard",
    "pale_road_guard",
    "red_crosslets",
    "captains_red_crosslets",
    "scarlet_court",
    "verdant_court",
    "small_curtain",
    "prosperity_standard",
    "guardian_standard",
)
CANVAS = (128, 128)
BLUE = (42, 99, 210)
DISPLAY_NAMES = {
    "road_guard": "Road Guard",
    "pale_road_guard": "Pale Road Guard",
    "red_crosslets": "Red Crosslets",
    "captains_red_crosslets": "Captain's Red Crosslets",
    "scarlet_court": "Scarlet Court",
    "verdant_court": "Verdant Court",
    "small_curtain": "Small Curtain",
    "prosperity_standard": "Prosperity Standard",
    "guardian_standard": "Guardian Standard",
}
CATALOGUE_INDICES = {banner_id: 27 + index for index, banner_id in enumerate(BANNERS)}
ROAD_GUARD_GEOMETRY = "britannia_mod:banner/road_guard/geometry"
ROAD_GUARD_GEOMETRY_SHA256 = "9cbe83118813091c2314b44ea90bdbc86f3e896ebc4cca3a5b21f7d989593de2"
SMALL_CURTAIN_GEOMETRY = "britannia_mod:banner/small_curtain/geometry"
SMALL_CURTAIN_GEOMETRY_SHA256 = "ae35f37d3f9baa25fcc2eb5aa55cf1625c938059f8d134a100e209151b17ad0f"
SMALL_CURTAIN_SOURCE_SHA256 = "0e84f8c210042081b968cf257e65a2c4560bb7588627d749a2cd61df3a933cb3"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def checkerboard(size: tuple[int, int], cell: int = 8) -> Image.Image:
    image = Image.new("RGBA", size)
    pixels = image.load()
    colours = ((210, 210, 210, 255), (245, 245, 245, 255))
    for y in range(size[1]):
        for x in range(size[0]):
            pixels[x, y] = colours[((x // cell) + (y // cell)) & 1]
    return image


def composite_on_checker(source: Image.Image) -> Image.Image:
    return Image.alpha_composite(checkerboard(source.size), source)


def normalize_mask(base: Image.Image, authored: Image.Image) -> Image.Image:
    """Preserve authored selection while whitening it and constraining alpha to the base."""
    base_rgba = base.convert("RGBA")
    mask_rgba = authored.convert("RGBA")
    base_alpha = base_rgba.getchannel("A")
    mask_alpha = mask_rgba.getchannel("A")
    safe_alpha = ImageChops.darker(mask_alpha, base_alpha)
    white = Image.new("RGBA", mask_rgba.size, (255, 255, 255, 0))
    white.putalpha(safe_alpha)
    return white


def make_alignment(base: Image.Image, mask: Image.Image) -> Image.Image:
    base_alpha = base.getchannel("A")
    mask_alpha = mask.getchannel("A")
    image = checkerboard(base.size)
    cyan = Image.new("RGBA", base.size, (0, 180, 210, 0))
    cyan.putalpha(base_alpha.point(lambda value: min(150, value)))
    image = Image.alpha_composite(image, cyan)
    magenta = Image.new("RGBA", base.size, (235, 0, 150, 0))
    magenta.putalpha(mask_alpha.point(lambda value: min(210, value)))
    return Image.alpha_composite(image, magenta)


def make_blue_recolour(base: Image.Image, mask: Image.Image) -> Image.Image:
    tint = Image.new("RGBA", base.size, BLUE + (0,))
    tint.putalpha(mask.getchannel("A"))
    return composite_on_checker(Image.alpha_composite(base, tint))


def review_sheet(
    banner_id: str,
    natural: Image.Image,
    mask: Image.Image,
    blue: Image.Image,
    alignment: Image.Image,
) -> Image.Image:
    scale = 2
    panel_size = (CANVAS[0] * scale, CANVAS[1] * scale)
    sheet = Image.new("RGBA", (panel_size[0] * 2, 48 + panel_size[1] * 2), (32, 35, 42, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((12, 10), f"{banner_id} — 128×128 authoritative export", fill=(255, 255, 255, 255))
    panels = (
        ("Natural base", natural),
        ("Authored mask", mask),
        ("Blue recolour", blue),
        ("Alignment: base cyan / mask magenta", alignment),
    )
    for index, (label, panel) in enumerate(panels):
        column = index % 2
        row = index // 2
        x = column * panel_size[0]
        y = 48 + row * panel_size[1]
        sheet.alpha_composite(panel.resize(panel_size, Image.Resampling.NEAREST), (x, y))
        draw.rectangle((x + 4, y + 4, x + 4 + 7 * len(label), y + 20), fill=(0, 0, 0, 190))
        draw.text((x + 8, y + 7), label, fill=(255, 255, 255, 255))
    return sheet


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    intake_root = root / "content" / "banner-final-intake" / "submissions"
    runtime_root = root / "src" / "main" / "resources" / "assets" / "britannia_mod" / "textures" / "banner"
    results: list[dict[str, object]] = []
    family_sheets: list[Image.Image] = []

    for banner_id in BANNERS:
        submission = intake_root / banner_id
        base_path = submission / "base_texture.png"
        mask_path = submission / "dye_mask.png"
        if not base_path.is_file() or not mask_path.is_file():
            raise FileNotFoundError(f"Missing Illustrator export for {banner_id}")

        base = Image.open(base_path).convert("RGBA")
        authored_mask = Image.open(mask_path).convert("RGBA")
        if base.size != CANVAS or authored_mask.size != CANVAS:
            raise ValueError(
                f"{banner_id}: expected 128x128 exports, got base={base.size}, mask={authored_mask.size}"
            )
        mask = normalize_mask(base, authored_mask)
        base.save(base_path, "PNG", compress_level=9)
        mask.save(mask_path, "PNG", compress_level=9)

        runtime = runtime_root / banner_id
        runtime.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(base_path, runtime / "base_texture.png")
        shutil.copyfile(mask_path, runtime / "dye_mask.png")

        review = submission / "review"
        review.mkdir(parents=True, exist_ok=True)
        natural = composite_on_checker(base)
        mask_checker = composite_on_checker(mask)
        blue = make_blue_recolour(base, mask)
        alignment = make_alignment(base, mask)
        natural.save(review / "natural_checkerboard.png", "PNG", compress_level=9)
        mask_checker.save(review / "dye_mask_checkerboard.png", "PNG", compress_level=9)
        blue.save(review / "blue_recolour.png", "PNG", compress_level=9)
        alignment.save(review / "alignment.png", "PNG", compress_level=9)
        sheet = review_sheet(banner_id, natural, mask_checker, blue, alignment)
        sheet.save(review / "review_sheet.png", "PNG", compress_level=9)
        family_sheets.append(sheet)

        base_pixels = list(base.getdata())
        mask_pixels = list(mask.getdata())
        active = sum(1 for pixel in mask_pixels if pixel[3] > 0)
        transparent = sum(1 for pixel in mask_pixels if pixel[3] == 0)
        fixed = sum(1 for base_pixel, mask_pixel in zip(base_pixels, mask_pixels)
                    if base_pixel[3] > 0 and mask_pixel[3] == 0)
        violations = sum(1 for base_pixel, mask_pixel in zip(base_pixels, mask_pixels)
                         if mask_pixel[3] > base_pixel[3])
        nonwhite = sum(1 for pixel in mask_pixels if pixel[3] > 0 and pixel[:3] != (255, 255, 255))
        bbox = base.getchannel("A").getbbox()
        if active == 0 or transparent == 0 or fixed == 0 or violations or nonwhite:
            raise ValueError(
                f"{banner_id}: invalid mask active={active}, transparent={transparent}, "
                f"fixed={fixed}, alpha_violations={violations}, nonwhite={nonwhite}"
            )

        results.append(
            {
                "stable_id": f"britannia_mod:{banner_id}",
                "base_path": base_path.relative_to(root).as_posix(),
                "base_sha256": sha256(base_path),
                "mask_path": mask_path.relative_to(root).as_posix(),
                "mask_sha256": sha256(mask_path),
                "runtime_base_sha256": sha256(runtime / "base_texture.png"),
                "runtime_mask_sha256": sha256(runtime / "dye_mask.png"),
                "dimensions": list(CANVAS),
                "mode": "RGBA",
                "base_alpha_bounds": list(bbox) if bbox else None,
                "mask_active_pixels": active,
                "mask_transparent_pixels": transparent,
                "fixed_base_pixels": fixed,
                "mask_alpha_exceeds_base": violations,
                "active_mask_nonwhite_pixels": nonwhite,
                "review_sheet": (review / "review_sheet.png").relative_to(root).as_posix(),
            }
        )

        small_curtain = banner_id == "small_curtain"
        geometry_id = SMALL_CURTAIN_GEOMETRY if small_curtain else ROAD_GUARD_GEOMETRY
        intake = {
            "schema_version": 1,
            "approval": {
                "status": "APPROVED",
                "approved_by": "Product Owner",
                "approved_date": "2026-07-28",
                "notes": (
                    "Approved for integration under the authoritative nine-definition extra-small "
                    "family decision. Automated asset checks passed; live review remains pending."
                ),
            },
            "banner": {
                "stable_id": f"britannia_mod:{banner_id}",
                "final_display_name": DISPLAY_NAMES[banner_id],
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "family": "x-small",
                "width_blocks": 1,
                "height_blocks": 1,
                "supported_orientations": ["wall_parallel", "wall_perpendicular"],
                "supported_mounts": ["britannia_mod:brass", "britannia_mod:iron"],
                "default_mount": "britannia_mod:brass",
                "placement_profile_id": "britannia_mod:extra_small",
                "geometry_id": geometry_id,
                "geometry_convention": (
                    "Definition-specific 128 x 128 planar curtain geometry; the authored alpha "
                    "preserves the wider flat-bottomed silhouette."
                    if small_curtain
                    else "Shared Road Guard-style 128 x 128 planar geometry; mount geometry remains "
                    "separate and orientation-selected by the extra-small placement profile."
                ),
            },
            "localization": {"language": "en_us", "value": DISPLAY_NAMES[banner_id]},
            "assets": {
                "base_texture": {
                    "resource_id": f"britannia_mod:banner/{banner_id}/base_texture",
                    "source_file": base_path.relative_to(root).as_posix(),
                    "sha256": sha256(base_path),
                },
                "dye_mask": {
                    "resource_id": f"britannia_mod:banner/{banner_id}/dye_mask",
                    "source_file": mask_path.relative_to(root).as_posix(),
                    "sha256": sha256(mask_path),
                },
                "geometry": {
                    "shared_geometry_approved": not small_curtain,
                    "source_file": (
                        "content/banner-final-intake/submissions/small_curtain/source/geometry.json"
                        if small_curtain
                        else None
                    ),
                    "sha256": SMALL_CURTAIN_SOURCE_SHA256 if small_curtain else None,
                    "runtime_sha256": (
                        SMALL_CURTAIN_GEOMETRY_SHA256 if small_curtain
                        else ROAD_GUARD_GEOMETRY_SHA256
                    ),
                },
            },
            "references": {
                "authoring_source_file": None,
                "preview_image_file": (review / "review_sheet.png").relative_to(root).as_posix(),
            },
            "provenance": {
                "original_art": True,
                "creator": "Britannia Mod project artist",
                "creation_method": (
                    "Named base_texture and dye_mask objects exported from the authoritative "
                    "Adobe Illustrator document to one aligned 128 x 128 RGBA canvas; active mask "
                    "RGB normalized to white and alpha constrained to base alpha."
                ),
                "source_project_file": "C:/projects/britannia/raw fiels/tabbard/banner.ai",
                "distribution_permission_confirmed": True,
                "copied_from_reference_art": False,
            },
            "manual_verification": {
                "performed": False,
                "tester": "",
                "date": "",
                "notes": "Not performed; renewed live review is required for this asset hash.",
            },
            "requested_content_status": "in_progress",
        }
        (submission / f"{banner_id}.yml").write_text(
            json.dumps(intake, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )

    contact_panel = (256, 280)
    contact = Image.new("RGBA", (contact_panel[0] * 3, contact_panel[1] * 3), (20, 22, 27, 255))
    for index, sheet in enumerate(family_sheets):
        contact.alpha_composite(
            sheet.resize(contact_panel, Image.Resampling.LANCZOS),
            ((index % 3) * contact_panel[0], (index // 3) * contact_panel[1]),
        )
    review_root = root / "content" / "banner-final-intake" / "review"
    review_root.mkdir(parents=True, exist_ok=True)
    contact.save(review_root / "extra_small_family_review.png", "PNG", compress_level=9)

    output = root / "content" / "banner-final-intake" / "extra_small_asset_report.json"
    output.write_text(json.dumps({"schema_version": 1, "assets": results}, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared and verified {len(results)} extra-small banner asset pairs")
    return 0


if __name__ == "__main__":
    sys.exit(main())

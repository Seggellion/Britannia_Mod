#!/usr/bin/env python3
"""Prepare parallel Medium banner assets without inventing owner approval."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


BANNERS = (
    "verdant_grape_pennon",
    "silver_rosette_pennon",
    "four_seals_pennon",
    "twin_spades_pennon",
    "ankh_pennon",
    "joined_wards",
)
DISPLAY_NAMES = {
    "verdant_grape_pennon": "Verdant Grape Pennon",
    "silver_rosette_pennon": "Silver Rosette Pennon",
    "four_seals_pennon": "Four Seals Pennon",
    "twin_spades_pennon": "Twin Spades Pennon",
    "ankh_pennon": "Ankh Pennon",
    "joined_wards": "Joined Wards",
}
PREVIOUS_IDS = {
    "verdant_grape_pennon": "medium_wall_01",
    "silver_rosette_pennon": "medium_wall_02",
    "four_seals_pennon": "medium_wall_03",
    "twin_spades_pennon": "medium_wall_04",
    "ankh_pennon": "medium_wall_05",
    "joined_wards": None,
}
CATALOGUE_INDICES = {banner_id: 7 + index for index, banner_id in enumerate(BANNERS)}
CANVAS = (128, 128)
BLUE = (42, 99, 210)
SOURCE = "C:/projects/britannia/raw fiels/tabbard/banner_medium_wall.ai"
SOURCE_SHA256 = "a6a75becd1793dac7a5b36361c0a33d615846ac4e97502deff794ef5ac337fac"


@dataclass(frozen=True)
class GeometryGroup:
    key: str
    resource_id: str
    representative: str
    members: tuple[str, ...]
    evidence: str


GEOMETRY_GROUPS = (
    GeometryGroup(
        "grape_rosette_pair",
        "britannia_mod:banner/medium_wall/grape_rosette_pair/geometry",
        "verdant_grape_pennon",
        ("verdant_grape_pennon", "silver_rosette_pennon"),
        "Matching crenellated attachment edge, straight sides, centred lower point, "
        "width-to-height relationship, and full-canvas layout; colour and heraldry differ.",
    ),
    GeometryGroup(
        "four_seals_pennon",
        "britannia_mod:banner/medium_wall/four_seals_pennon/geometry",
        "four_seals_pennon",
        ("four_seals_pennon",),
        "Distinct wide rounded lower edge, top suspension opening, and split field.",
    ),
    GeometryGroup(
        "twin_spades_pennon",
        "britannia_mod:banner/medium_wall/twin_spades_pennon/geometry",
        "twin_spades_pennon",
        ("twin_spades_pennon",),
        "Distinct offset top attachment pixels, diagonal checked field, straight sides, "
        "and shallow pointed lower edge.",
    ),
    GeometryGroup(
        "ankh_pennon",
        "britannia_mod:banner/medium_wall/ankh_pennon/geometry",
        "ankh_pennon",
        ("ankh_pennon",),
        "Distinct narrow U-ended silhouette, curved upper opening, and attachment row.",
    ),
    GeometryGroup(
        "joined_wards",
        "britannia_mod:banner/medium_wall/joined_wards/geometry",
        "joined_wards",
        ("joined_wards",),
        "Distinct two-point swallowtail silhouette and quartered field.",
    ),
)
GROUP_BY_BANNER = {
    member: group for group in GEOMETRY_GROUPS for member in group.members
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


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
    """Whiten only the authored selection and constrain its alpha to the base."""
    safe_alpha = ImageChops.darker(
        authored.getchannel("A"), base.getchannel("A")
    )
    normalized = Image.new("RGBA", authored.size, (255, 255, 255, 0))
    normalized.putalpha(safe_alpha)
    return normalized


def make_alignment(base: Image.Image, mask: Image.Image) -> Image.Image:
    image = checkerboard(base.size)
    cyan = Image.new("RGBA", base.size, (0, 180, 210, 0))
    cyan.putalpha(base.getchannel("A").point(lambda value: min(150, value)))
    image = Image.alpha_composite(image, cyan)
    magenta = Image.new("RGBA", base.size, (235, 0, 150, 0))
    magenta.putalpha(mask.getchannel("A").point(lambda value: min(210, value)))
    return Image.alpha_composite(image, magenta)


def make_blue_recolour(base: Image.Image, mask: Image.Image) -> Image.Image:
    tint = Image.new("RGBA", base.size, BLUE + (0,))
    tint.putalpha(mask.getchannel("A"))
    return composite_on_checker(Image.alpha_composite(base, tint))


def make_parallel_geometry_preview(base: Image.Image) -> Image.Image:
    preview = Image.new("RGBA", (512, 384), (30, 33, 40, 255))
    draw = ImageDraw.Draw(preview)
    draw.text(
        (16, 12),
        "wall_parallel | proposed 1 x 2 footprint | shared parallel mount",
        fill="white",
    )

    wall = (24, 54, 264, 362)
    draw.rectangle(wall, fill=(82, 85, 92, 255), outline=(150, 155, 165, 255), width=2)
    for y in range(54, 363, 32):
        draw.line((24, y, 264, y), fill=(105, 109, 117, 255))
    for x in range(24, 265, 48):
        draw.line((x, 54, x, 362), fill=(105, 109, 117, 255))
    draw.rectangle((72, 74, 216, 346), outline=(110, 175, 255, 255), width=3)
    cloth = base.resize((128, 256), Image.Resampling.NEAREST)
    preview.alpha_composite(cloth, (80, 82))
    draw.line((74, 78, 218, 78), fill=(210, 170, 70, 255), width=5)
    draw.rectangle((68, 72, 76, 86), fill=(210, 170, 70, 255))
    draw.rectangle((216, 72, 224, 86), fill=(210, 170, 70, 255))
    draw.text((74, 350), "fabric and authored fixed pixels", fill="white")

    origin = (388, 206)
    draw.ellipse((origin[0] - 5, origin[1] - 5, origin[0] + 5, origin[1] + 5), fill="white")
    directions = (
        ("N", 0, -104),
        ("S", 0, 104),
        ("E", 104, 0),
        ("W", -104, 0),
    )
    for label, dx, dy in directions:
        end = (origin[0] + dx, origin[1] + dy)
        draw.line((origin[0], origin[1], end[0], end[1]), fill=(110, 175, 255), width=3)
        draw.text((end[0] - 5, end[1] - 8), label, fill="white")
    draw.text((290, 52), "Four-facing transform", fill="white")
    draw.text((286, 330), "Mount material: brass or iron", fill=(210, 170, 70))
    draw.text((286, 348), "Mount pass remains untinted", fill=(210, 170, 70))
    return preview


def review_sheet(
    banner_id: str,
    natural: Image.Image,
    mask: Image.Image,
    blue: Image.Image,
    alignment: Image.Image,
) -> Image.Image:
    panel_size = (256, 256)
    sheet = Image.new("RGBA", (512, 560), (32, 35, 42, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text(
        (12, 10),
        f"{banner_id} - authoritative parallel Medium 128 x 128 export",
        fill="white",
    )
    panels = (
        ("Natural base", natural),
        ("Authored mask", mask),
        ("Blue recolour", blue),
        ("Alignment: base cyan / mask magenta", alignment),
    )
    for index, (label, panel) in enumerate(panels):
        x = (index % 2) * panel_size[0]
        y = 48 + (index // 2) * panel_size[1]
        sheet.alpha_composite(panel.resize(panel_size, Image.Resampling.NEAREST), (x, y))
        draw.rectangle(
            (x + 4, y + 4, x + 14 + 7 * len(label), y + 22),
            fill=(0, 0, 0, 190),
        )
        draw.text((x + 8, y + 7), label, fill="white")
    return sheet


def planar_geometry(
    representative: str,
    resource_id: str,
    bbox: tuple[int, int, int, int],
) -> dict[str, object]:
    x0, y0, x1, y1 = bbox
    pixel_width = x1 - x0
    pixel_height = y1 - y0
    physical_height = 20.0
    physical_width = physical_height * pixel_width / pixel_height
    left = 8.0 - physical_width / 2.0
    right = 8.0 + physical_width / 2.0
    uv = [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]
    texture_root = f"britannia_mod:banner/{representative}"
    return {
        "credit": (
            "Proposed parallel Medium-family planar geometry derived from the "
            f"authoritative 128 x 128 alpha bounds for {resource_id}"
        ),
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {
            "base_texture": f"{texture_root}/base_texture",
            "dye_mask": f"{texture_root}/dye_mask",
            "particle": f"{texture_root}/base_texture",
        },
        "elements": [
            {
                "from": [round(left, 5), -4.0, 7.49],
                "to": [round(right, 5), 16.0, 7.51],
                "faces": {
                    face: {"uv": uv, "texture": "#base_texture"}
                    for face in ("north", "south")
                },
            },
            {
                "from": [round(left, 5), -4.0, 7.47],
                "to": [round(right, 5), 16.0, 7.53],
                "faces": {
                    face: {
                        "uv": uv,
                        "texture": "#dye_mask",
                        "tintindex": 1,
                    }
                    for face in ("north", "south")
                },
            },
        ],
    }


def normalized_silhouette(path: Path) -> set[int]:
    with Image.open(path) as opened:
        alpha = opened.convert("RGBA").getchannel("A")
    cropped = alpha.crop(alpha.getbbox()).resize(CANVAS, Image.Resampling.NEAREST)
    return {
        index
        for index, value in enumerate(cropped.get_flattened_data())
        if value > 16
    }


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    intake_root = root / "content" / "banner-final-intake" / "submissions"
    image_data: dict[str, dict[str, object]] = {}
    family_sheets: list[Image.Image] = []

    for banner_id in BANNERS:
        submission = intake_root / banner_id
        base_path = submission / "base_texture.png"
        mask_path = submission / "dye_mask.png"
        if not base_path.is_file() or not mask_path.is_file():
            raise FileNotFoundError(f"Missing Illustrator export for {banner_id}")

        with Image.open(base_path) as opened:
            if opened.mode != "RGBA" or opened.size != CANVAS:
                raise ValueError(
                    f"{banner_id}: base must be 128 x 128 RGBA, got "
                    f"{opened.size} {opened.mode}"
                )
            base = opened.copy()
        with Image.open(mask_path) as opened:
            if opened.size != CANVAS:
                raise ValueError(f"{banner_id}: mask must be 128 x 128")
            authored_mask = opened.convert("RGBA")

        mask = normalize_mask(base, authored_mask)
        mask.save(mask_path, "PNG", compress_level=9)

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
        geometry_preview = make_parallel_geometry_preview(base)
        geometry_preview.save(
            review / "parallel_geometry_mount.png", "PNG", compress_level=9
        )
        sheet = review_sheet(banner_id, natural, mask_checker, blue, alignment)
        sheet.save(review / "review_sheet.png", "PNG", compress_level=9)
        family_sheets.append(sheet)

        base_pixels = list(base.get_flattened_data())
        mask_pixels = list(mask.get_flattened_data())
        active = sum(pixel[3] > 0 for pixel in mask_pixels)
        transparent = sum(pixel[3] == 0 for pixel in mask_pixels)
        fixed = sum(
            base_pixel[3] > 0 and mask_pixel[3] == 0
            for base_pixel, mask_pixel in zip(base_pixels, mask_pixels)
        )
        violations = sum(
            mask_pixel[3] > base_pixel[3]
            for base_pixel, mask_pixel in zip(base_pixels, mask_pixels)
        )
        nonwhite = sum(
            pixel[3] > 0 and pixel[:3] != (255, 255, 255)
            for pixel in mask_pixels
        )
        upper_band_fixed = sum(
            base.getpixel((x, y))[3] > 0 and mask.getpixel((x, y))[3] == 0
            for y in range(48)
            for x in range(CANVAS[0])
        )
        bbox = base.getchannel("A").getbbox()
        if (
            bbox is None
            or active == 0
            or transparent == 0
            or fixed == 0
            or upper_band_fixed == 0
            or violations
            or nonwhite
        ):
            raise ValueError(
                f"{banner_id}: invalid mask active={active}, transparent={transparent}, "
                f"fixed={fixed}, upper_band_fixed={upper_band_fixed}, "
                f"alpha_violations={violations}, "
                f"nonwhite={nonwhite}"
            )

        image_data[banner_id] = {
            "bbox": bbox,
            "base_path": base_path,
            "mask_path": mask_path,
            "review_path": review / "review_sheet.png",
            "geometry_preview_path": review / "parallel_geometry_mount.png",
            "base_sha256": sha256(base_path),
            "mask_sha256": sha256(mask_path),
            "active": active,
            "transparent": transparent,
            "fixed": fixed,
            "upper_band_fixed": upper_band_fixed,
            "violations": violations,
            "nonwhite": nonwhite,
        }

    pair = GEOMETRY_GROUPS[0].members
    silhouettes = [
        normalized_silhouette(image_data[member]["base_path"]) for member in pair
    ]
    intersection = len(set.intersection(*silhouettes))
    union = len(set.union(*silhouettes))
    if union == 0 or intersection / union < 0.95:
        raise ValueError(
            f"Shared grape/rosette geometry differs: IoU={intersection / union:.4f}"
        )

    geometry_records: list[dict[str, object]] = []
    for group in GEOMETRY_GROUPS:
        representative_data = image_data[group.representative]
        geometry_path = (
            intake_root / group.representative / "source" / "geometry.json"
        )
        write_json(
            geometry_path,
            planar_geometry(
                group.representative,
                group.resource_id,
                representative_data["bbox"],
            ),
        )
        geometry_records.append(
            {
                "key": group.key,
                "resource_id": group.resource_id,
                "members": list(group.members),
                "classification": (
                    "REUSE_SHARED_MEDIUM_WALL_GEOMETRY"
                    if len(group.members) > 1
                    else "CUSTOM_MEDIUM_WALL_GEOMETRY_REQUIRED"
                ),
                "existing_medium_geometry_reuse": False,
                "existing_medium_geometry_reuse_reason": (
                    "No completed perpendicular Medium model has the same silhouette, "
                    "proportions, attachment edge, and UV bounds."
                ),
                "shape_evidence": group.evidence,
                "source_file": geometry_path.relative_to(root).as_posix(),
                "sha256": sha256(geometry_path),
                "alpha_bounds": list(representative_data["bbox"]),
            }
        )

    asset_records: list[dict[str, object]] = []
    for banner_id in BANNERS:
        submission = intake_root / banner_id
        data = image_data[banner_id]
        group = GROUP_BY_BANNER[banner_id]
        group_record = next(
            record for record in geometry_records if record["key"] == group.key
        )
        final_name = DISPLAY_NAMES[banner_id]
        intake = {
            "schema_version": 1,
            "approval": {
                "status": "NOT_APPROVED",
                "approved_by": "",
                "approved_date": "",
                "notes": (
                    "Draft prepared from the authoritative Illustrator source. "
                    "The request supplies authoritative artwork, family membership, and "
                    "final names, but no product-owner identity/date, creator attestation, "
                    "distribution permission, or manual approval."
                ),
            },
            "banner": {
                "stable_id": f"britannia_mod:{banner_id}",
                "final_display_name": final_name,
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "family": "medium_wall",
                "width_blocks": 1,
                "height_blocks": 2,
                "supported_orientations": ["wall_parallel"],
                "supported_mounts": [
                    "britannia_mod:brass",
                    "britannia_mod:iron",
                ],
                "default_mount": "britannia_mod:brass",
                "placement_profile_id": "britannia_mod:medium_parallel",
                "geometry_id": group.resource_id,
                "geometry_convention": (
                    "Proposed 20-unit-tall parallel Medium-family planar geometry "
                    "derived from the complete authored alpha bounds. The 128-pixel UV "
                    "basis and established two-pass thickness are preserved. Parallel "
                    "physical placement reuses the shared wall_parallel mount geometry; "
                    "brass/iron remain separate untinted material choices. Authored "
                    "attachment and crossbar pixels stay fixed in base_texture."
                ),
            },
            "localization": {"language": "en_us", "value": final_name},
            "assets": {
                "base_texture": {
                    "resource_id": f"britannia_mod:banner/{banner_id}/base_texture",
                    "source_file": data["base_path"].relative_to(root).as_posix(),
                    "sha256": data["base_sha256"],
                },
                "dye_mask": {
                    "resource_id": f"britannia_mod:banner/{banner_id}/dye_mask",
                    "source_file": data["mask_path"].relative_to(root).as_posix(),
                    "sha256": data["mask_sha256"],
                },
                "geometry": {
                    "shared_geometry_approved": False,
                    "source_file": group_record["source_file"],
                    "sha256": group_record["sha256"],
                },
            },
            "references": {
                "authoring_source_file": None,
                "preview_image_file": data["review_path"].relative_to(root).as_posix(),
            },
            "provenance": {
                "original_art": False,
                "creator": "",
                "creation_method": (
                    "Named base_texture and authored dye_mask artwork exported from "
                    "banner_medium_wall.ai using one shared 128 x 128 artboard transform. "
                    "Active mask RGB was normalized to white and mask alpha was constrained "
                    "to base alpha without expanding or reinterpreting the selection."
                ),
                "source_project_file": SOURCE,
                "distribution_permission_confirmed": False,
                "copied_from_reference_art": None,
            },
            "manual_verification": {
                "performed": False,
                "tester": "",
                "date": "",
                "notes": (
                    "Not performed. Review artifacts are prepared, but Gate E must wait "
                    "until the intake is approved and integrated."
                ),
            },
            "requested_content_status": "in_progress",
        }
        intake_path = submission / f"{banner_id}.yml"
        write_json(intake_path, intake)

        asset_records.append(
            {
                "stable_id": f"britannia_mod:{banner_id}",
                "previous_id": (
                    f"britannia_mod:{PREVIOUS_IDS[banner_id]}"
                    if PREVIOUS_IDS[banner_id]
                    else None
                ),
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "display_name": final_name,
                "export_classification": "READY_FOR_EXPORT",
                "validator_expected": "NOT_READY",
                "integration_readiness": "NOT_READY",
                "base_path": data["base_path"].relative_to(root).as_posix(),
                "base_sha256": data["base_sha256"],
                "mask_path": data["mask_path"].relative_to(root).as_posix(),
                "mask_sha256": data["mask_sha256"],
                "dimensions": list(CANVAS),
                "mode": "RGBA",
                "base_alpha_bounds": list(data["bbox"]),
                "mask_active_pixels": data["active"],
                "mask_transparent_pixels": data["transparent"],
                "fixed_base_pixels": data["fixed"],
                "fixed_upper_band_pixels": data["upper_band_fixed"],
                "mask_alpha_exceeds_base": data["violations"],
                "active_mask_nonwhite_pixels": data["nonwhite"],
                "review_sheet": data["review_path"].relative_to(root).as_posix(),
                "geometry_preview": data["geometry_preview_path"]
                .relative_to(root)
                .as_posix(),
                "geometry_id": group.resource_id,
                "geometry_classification": group_record["classification"],
                "intake_path": intake_path.relative_to(root).as_posix(),
                "missing_fields": [
                    "approval.approved_by",
                    "approval.approved_date",
                    "provenance.original_art confirmation",
                    "provenance.creator",
                    "provenance.distribution_permission_confirmed",
                    "provenance.copied_from_reference_art",
                    "manual product-owner review",
                    "logical-dimension approval",
                    "geometry approval",
                ],
            }
        )

    family_review = root / "content" / "banner-final-intake" / "review"
    family_review.mkdir(parents=True, exist_ok=True)
    contact = Image.new(
        "RGBA",
        (family_sheets[0].width * 2, family_sheets[0].height * 3),
        (24, 26, 31, 255),
    )
    for index, sheet in enumerate(family_sheets):
        contact.alpha_composite(
            sheet,
            ((index % 2) * sheet.width, (index // 2) * sheet.height),
        )
    family_sheet = family_review / "parallel_medium_family_review.png"
    contact.save(family_sheet, "PNG", compress_level=9)

    report = {
        "schema_version": 1,
        "source_file": SOURCE,
        "source_sha256": SOURCE_SHA256,
        "canvas": list(CANVAS),
        "logical_dimensions_proposal": {
            "width_blocks": 1,
            "height_blocks": 2,
            "evidence": (
                "Every authoritative silhouette is tall and narrow, with base-alpha "
                "bounds 68-84 pixels wide and 115-121 pixels high. This matches the "
                "approved 1 x 2 Medium footprint; the old 2 x 2 medium-wall default is "
                "explicitly provisional."
            ),
            "approval": "PENDING",
        },
        "placement_profile_proposal": {
            "id": "britannia_mod:medium_parallel",
            "dimensions": {"width_blocks": 1, "height_blocks": 2},
            "orientations": ["wall_parallel"],
            "orientation_mount_geometry": {
                "wall_parallel": "britannia_mod:banner/mount/wall_parallel",
            },
            "mount_materials": [
                "britannia_mod:brass",
                "britannia_mod:iron",
            ],
            "default_mount": "britannia_mod:brass",
            "runtime_integration": "BLOCKED_PENDING_READY_INTAKE",
            "architecture_note": (
                "The current placement-profile codec accepts an orientation-specific "
                "subset and registry cross-validation requires it to match the consuming "
                "definition. A parallel-only Medium profile is architecture-compatible "
                "without changing smaller-family or perpendicular Medium profiles."
            ),
        },
        "geometry_groups": geometry_records,
        "assets": asset_records,
        "family_review_sheet": family_sheet.relative_to(root).as_posix(),
    }
    write_json(
        root / "content" / "banner-final-intake" / "parallel_medium_asset_report.json",
        report,
    )
    print(
        "Prepared six parallel Medium asset packages; all remain NOT_READY pending "
        "product-owner approval and provenance."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

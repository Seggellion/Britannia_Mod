#!/usr/bin/env python3
"""Prepare parallel Large banner assets without inventing owner approval."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

from prepare_parallel_medium_banner_assets import (
    BLUE,
    CANVAS,
    checkerboard,
    composite_on_checker,
    make_alignment,
    normalize_mask,
    normalized_silhouette,
    sha256,
    write_json,
)


BANNERS = (
    "tournament_curtain",
    "threefold_chain_standard",
    "iron_serpent_standard",
    "silver_fleur_curtain",
    "gilded_trellis_curtain",
    "gilded_chevron_curtain",
)
DISPLAY_NAMES = {
    "tournament_curtain": "Tournament Curtain",
    "threefold_chain_standard": "Threefold Chain Standard",
    "iron_serpent_standard": "Iron Serpent Standard",
    "silver_fleur_curtain": "Silver Fleur Curtain",
    "gilded_trellis_curtain": "Gilded Trellis Curtain",
    "gilded_chevron_curtain": "Gilded Chevron Curtain",
}
PREVIOUS_IDS = {
    banner_id: f"large_{index:02d}"
    for index, banner_id in enumerate(BANNERS, start=1)
}
CATALOGUE_INDICES = {
    banner_id: index for index, banner_id in enumerate(BANNERS, start=1)
}
SOURCE = "C:/projects/britannia/raw fiels/tabbard/banner_large.ai"
SOURCE_SHA256 = "64fd720476243a937d155b9ea547de60003ccc83f1c78093098e45517d25379e"


def make_blue_recolour(base: Image.Image, mask: Image.Image) -> Image.Image:
    tint = Image.new("RGBA", base.size, BLUE + (0,))
    tint.putalpha(mask.getchannel("A"))
    return composite_on_checker(Image.alpha_composite(base, tint))


def make_parallel_geometry_preview(base: Image.Image) -> Image.Image:
    preview = Image.new("RGBA", (640, 448), (30, 33, 40, 255))
    draw = ImageDraw.Draw(preview)
    draw.text(
        (16, 12),
        "wall_parallel | proposed 2 x 2 footprint | shared parallel mount",
        fill="white",
    )

    wall = (24, 54, 408, 438)
    draw.rectangle(wall, fill=(82, 85, 92, 255), outline=(150, 155, 165, 255), width=2)
    for y in range(54, 439, 96):
        draw.line((24, y, 408, y), fill=(105, 109, 117, 255))
    for x in range(24, 409, 96):
        draw.line((x, 54, x, 438), fill=(105, 109, 117, 255))
    draw.rectangle((60, 72, 380, 404), outline=(110, 175, 255, 255), width=3)
    cloth = base.resize((320, 320), Image.Resampling.NEAREST)
    preview.alpha_composite(cloth, (60, 76))
    draw.line((52, 72, 388, 72), fill=(210, 170, 70, 255), width=5)
    draw.rectangle((48, 66, 56, 82), fill=(210, 170, 70, 255))
    draw.rectangle((384, 66, 392, 82), fill=(210, 170, 70, 255))
    draw.text((60, 410), "2 x 2 anchor footprint; authored fixed pixels stay in base", fill="white")

    origin = (526, 244)
    draw.ellipse((origin[0] - 5, origin[1] - 5, origin[0] + 5, origin[1] + 5), fill="white")
    directions = (
        ("N", 0, -126),
        ("S", 0, 126),
        ("E", 96, 0),
        ("W", -96, 0),
    )
    for label, dx, dy in directions:
        end = (origin[0] + dx, origin[1] + dy)
        draw.line((origin[0], origin[1], end[0], end[1]), fill=(110, 175, 255), width=3)
        draw.text((end[0] - 5, end[1] - 8), label, fill="white")
    draw.text((454, 76), "Four-facing transform", fill="white")
    draw.text((438, 398), "Mount: brass or iron", fill=(210, 170, 70))
    draw.text((438, 416), "Mount pass untinted", fill=(210, 170, 70))
    return preview


def make_review_sheet(
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
        f"{banner_id} - authoritative parallel Large 128 x 128 export",
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


def large_planar_geometry(
    representative: str,
    resource_id: str,
    bbox: tuple[int, int, int, int],
) -> dict[str, object]:
    x0, y0, x1, y1 = bbox
    pixel_width = x1 - x0
    pixel_height = y1 - y0
    physical_height = 28.0
    physical_width = physical_height * pixel_width / pixel_height
    left = 8.0 - physical_width / 2.0
    right = 8.0 + physical_width / 2.0
    uv = [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]
    texture_root = f"britannia_mod:banner/{representative}"
    return {
        "credit": (
            "Proposed parallel Large-family planar geometry derived from the "
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
                "from": [round(left, 5), -6.0, 7.49],
                "to": [round(right, 5), 22.0, 7.51],
                "faces": {
                    face: {"uv": uv, "texture": "#base_texture"}
                    for face in ("north", "south")
                },
            },
            {
                "from": [round(left, 5), -6.0, 7.47],
                "to": [round(right, 5), 22.0, 7.53],
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
            review / "parallel_geometry_footprint.png", "PNG", compress_level=9
        )
        sheet = make_review_sheet(banner_id, natural, mask_checker, blue, alignment)
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
            for y in range(40)
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
                f"alpha_violations={violations}, nonwhite={nonwhite}"
            )

        image_data[banner_id] = {
            "bbox": bbox,
            "base_path": base_path,
            "mask_path": mask_path,
            "review_path": review / "review_sheet.png",
            "geometry_preview_path": review / "parallel_geometry_footprint.png",
            "base_sha256": sha256(base_path),
            "mask_sha256": sha256(mask_path),
            "active": active,
            "transparent": transparent,
            "fixed": fixed,
            "upper_band_fixed": upper_band_fixed,
            "violations": violations,
            "nonwhite": nonwhite,
        }

    silhouettes = {
        banner_id: normalized_silhouette(image_data[banner_id]["base_path"])
        for banner_id in BANNERS
    }
    comparisons: list[dict[str, object]] = []
    for left_index, left in enumerate(BANNERS):
        for right in BANNERS[left_index + 1 :]:
            intersection = len(silhouettes[left] & silhouettes[right])
            union = len(silhouettes[left] | silhouettes[right])
            comparisons.append(
                {"left": left, "right": right, "iou": intersection / union}
            )
    if any(record["iou"] >= 0.95 for record in comparisons):
        raise ValueError("Large silhouettes at or above the sharing threshold require review")

    geometry_records: list[dict[str, object]] = []
    for banner_id in BANNERS:
        resource_id = f"britannia_mod:banner/large/{banner_id}/geometry"
        geometry_path = intake_root / banner_id / "source" / "geometry.json"
        write_json(
            geometry_path,
            large_planar_geometry(
                banner_id,
                resource_id,
                image_data[banner_id]["bbox"],
            ),
        )
        geometry_records.append(
            {
                "key": banner_id,
                "resource_id": resource_id,
                "members": [banner_id],
                "classification": "CUSTOM_LARGE_GEOMETRY_REQUIRED",
                "existing_geometry_reuse": False,
                "existing_geometry_reuse_reason": (
                    "No completed smaller-family model has the same square Large "
                    "proportions, outer silhouette, attachment edge, and UV bounds."
                ),
                "shape_evidence": (
                    "The normalized authored alpha silhouette is distinct from every "
                    "other Large candidate at the 0.95 IoU sharing threshold."
                ),
                "source_file": geometry_path.relative_to(root).as_posix(),
                "sha256": sha256(geometry_path),
                "alpha_bounds": list(image_data[banner_id]["bbox"]),
            }
        )

    asset_records: list[dict[str, object]] = []
    for banner_id in BANNERS:
        submission = intake_root / banner_id
        data = image_data[banner_id]
        geometry = next(
            record for record in geometry_records if record["key"] == banner_id
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
                    "The request supplies authoritative artwork, Large membership, and "
                    "final names, but no product-owner identity/date, creator attestation, "
                    "distribution permission, or manual approval."
                ),
            },
            "banner": {
                "stable_id": f"britannia_mod:{banner_id}",
                "final_display_name": final_name,
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "family": "large",
                "width_blocks": 2,
                "height_blocks": 2,
                "supported_orientations": ["wall_parallel"],
                "supported_mounts": [
                    "britannia_mod:brass",
                    "britannia_mod:iron",
                ],
                "default_mount": "britannia_mod:brass",
                "placement_profile_id": "britannia_mod:large_parallel",
                "geometry_id": geometry["resource_id"],
                "geometry_convention": (
                    "Proposed 28-unit-tall parallel Large-family planar geometry "
                    "derived from the complete authored alpha bounds. The 128-pixel UV "
                    "basis and established two-pass thickness are preserved. Physical "
                    "placement uses a 2 x 2 wall_parallel footprint and the shared "
                    "wall_parallel mount geometry; brass/iron remain separate untinted "
                    "material choices. Authored attachment and crossbar pixels stay "
                    "fixed in base_texture."
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
                    "source_file": geometry["source_file"],
                    "sha256": geometry["sha256"],
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
                    "banner_large.ai using one shared 128 x 128 artboard transform. "
                    "Active mask RGB was normalized to white and mask alpha was "
                    "constrained to base alpha without expanding or reinterpreting "
                    "the authored selection."
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
                    "Not performed. Review artifacts are prepared, but Gate E must "
                    "wait until the intake is approved and integrated."
                ),
            },
            "requested_content_status": "in_progress",
        }
        intake_path = submission / f"{banner_id}.yml"
        write_json(intake_path, intake)

        asset_records.append(
            {
                "stable_id": f"britannia_mod:{banner_id}",
                "previous_id": f"britannia_mod:{PREVIOUS_IDS[banner_id]}",
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "display_name": final_name,
                "export_classification": "READY_FOR_EXPORT",
                "validator_expected": "INVALID",
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
                "geometry_id": geometry["resource_id"],
                "geometry_classification": geometry["classification"],
                "intake_path": intake_path.relative_to(root).as_posix(),
                "missing_fields": [
                    "approval.approved_by",
                    "approval.approved_date",
                    "provenance.original_art confirmation",
                    "provenance.creator",
                    "provenance.distribution_permission_confirmed",
                    "provenance.copied_from_reference_art",
                    "logical-dimension approval",
                    "geometry approval",
                ],
                "validator_blocker": (
                    "The canonical source-named ID is intentionally not migrated into "
                    "the live catalogue until the package is approved."
                ),
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
    family_sheet = family_review / "parallel_large_family_review.png"
    contact.save(family_sheet, "PNG", compress_level=9)

    report = {
        "schema_version": 1,
        "source_file": SOURCE,
        "source_sha256": SOURCE_SHA256,
        "canvas": list(CANVAS),
        "logical_dimensions_proposal": {
            "width_blocks": 2,
            "height_blocks": 2,
            "evidence": (
                "All authoritative Large base-alpha bounds are approximately square "
                "(98-116 pixels wide by 88-120 pixels high) on the common 128 x 128 "
                "canvas. A 2 x 2 logical footprint preserves those proportions; the "
                "old 3 x 2 placeholder default is explicitly provisional."
            ),
            "approval": "PENDING",
        },
        "placement_profile_proposal": {
            "id": "britannia_mod:large_parallel",
            "dimensions": {"width_blocks": 2, "height_blocks": 2},
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
                "The placement-profile codec accepts an orientation-specific subset "
                "and registry cross-validation requires it to match the consuming "
                "definition. A wall_parallel-only 2 x 2 profile is architecture-compatible "
                "without changing completed smaller-family profiles."
            ),
        },
        "silhouette_comparisons": comparisons,
        "geometry_groups": geometry_records,
        "assets": asset_records,
        "family_review_sheet": family_sheet.relative_to(root).as_posix(),
    }
    write_json(
        root / "content" / "banner-final-intake" / "large_asset_report.json",
        report,
    )
    print(
        "Prepared six parallel Large asset packages; all remain NOT_READY pending "
        "product-owner approval, provenance, dimensions, and geometry approval."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

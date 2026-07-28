# Road Guard Gate E Product-Owner Review

## Review environment

- Reviewer: Product Owner (self-identified; personal name not supplied)
- Review date: 2026-07-27
- Minecraft: 1.21.1
- NeoForge: 21.1.72
- Commit tested: `d03fe2985baf6886cbb109765e68aac9f0a36ac7`
- Resource packs: Not specified in the submitted review
- Shaders: Not specified in the submitted review
- Screenshots: None supplied for tracking
- General observations: None supplied

## Appearance

- Natural Road Guard base appearance: PASS
- Fixed charcoal and other non-dyeable regions remain unchanged: PASS
- Only the intended yellow regions receive dye: PASS
- Highlights, shadows, texture, and local contrast remain visible after dyeing: PASS
- No purple, black, missing-texture, or diagnostic fallback appears: PASS

## Materials

- Natural cotton: PASS
- Natural wool: PASS
- Natural linen: PASS
- Natural silk: PASS
- The four materials retain distinct metadata and palettes while sharing the approved native Road Guard artwork: PASS

## Mounts

- Brass mount renders correctly and remains untinted: PASS
- Iron mount renders correctly and remains untinted: PASS

## Item and preview

- Inventory icon: PASS
- First-person and third-person hand rendering: PASS
- Dropped-item rendering: PASS
- Item-frame rendering: PASS
- Dye preview current state: PASS
- Dye preview proposed state: PASS
- Cancel leaves the banner unchanged: PASS
- Apply changes the intended regions: PASS
- Re-dyeing replaces the previous colour rather than combining colours: PASS
- Unlimited dye tubs remain unlimited: PASS

## Pigment coverage

- Madder Red: PASS
- Woad Blue: PASS
- Verdigris: PASS
- Weld Gold: PASS
- Soot Black: PASS
- Chalk White: PASS
- Ice Blue: PASS

## Placement

- Wall-parallel placement: PASS
- Wall-perpendicular placement: PASS
- North-facing placement: PASS
- South-facing placement: PASS
- East-facing placement: PASS
- West-facing placement: PASS
- Artwork is not mirrored, backwards, or incorrectly rotated: PASS
- Mount and banner geometry align correctly: PASS
- Item, preview, and placed appearances agree: PASS

## Persistence and lifecycle

- Save and reload preserve the configured state and appearance: PASS
- Relog/client tracking restores the correct appearance: PASS
- F3+T resource reload restores the correct appearance: PASS
- `/reload` preserves state and appearance: PASS
- Breaking the banner drops exactly one configured item: PASS
- Support loss cleans up the banner and produces one configured item: PASS
- Pick block preserves definition, material, colour, pigment provenance, and mount: PASS
- Re-placement preserves the configured appearance: PASS

## Overall decision

- Overall Road Guard visual approval: APPROVED
- Gate E result: PASS
- Approved status transition: `in_progress` to `complete`
- Crafting: not applicable; product-disabled

Road Guard was reviewed after the generic banner-atlas correction commit
`d03fe2985baf6886cbb109765e68aac9f0a36ac7`. No failed or unresolved required review item was reported.

## Historical applicability

This Gate E record is preserved as historical evidence for the superseded runtime assets only:

- Base SHA-256: `a75880a969570e47fdff0b15eb812eb9e94564f345c424c00af92fe3d4cb1240`
- Dye-mask SHA-256: `89495bc3e8b9b8a4e98424d539536b57853f08bf20771cf3456151014120b669`

The current authoritative `banner.ai` export has different hashes. This record does not approve those
replacement bytes; Road Guard returned to `in_progress` pending renewed live review.

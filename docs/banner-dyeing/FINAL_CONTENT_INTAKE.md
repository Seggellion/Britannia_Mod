# Final Banner Content Intake

## 1. Purpose

Final banner content requires explicit product-owner approval and original project artwork. Codex may validate,
report on, and integrate approved inputs. Codex must not invent heraldry, names, dimensions, orientations, mount
support, provenance, or approval.

The files under `content/banner-final-intake/` are owner-facing templates only. They are not runtime data, are not
approval records while marked `NOT_APPROVED`, and do not change the live catalogue.

## 2. Stable identity

- Stable banner IDs do not change during final-content intake.
- A final display name may change without changing the stable ID.
- Saved items and placed banners refer to the stable ID, not the display name.
- `britannia_mod:x_small_unnamed_01` retains that stable ID after it receives a final name.
- Asset filenames and display names must not be used to infer or replace stable identity.

## 3. Required owner decisions

One intake record must explicitly approve all of the following for one banner:

- Final display name
- Width in blocks
- Height in blocks
- Supported orientations
- Supported mounts
- Default mount
- Placement profile
- Geometry convention
- Localization
- Requested content-status target

Current runtime capability is width `1–3`, height `1–2`, orientations `wall_parallel` and
`wall_perpendicular`, and mounts `britannia_mod:brass` and `britannia_mod:iron`. These are supported technical
values, not automatic approval for any definition. Image proportions, catalogue groups, and provisional defaults
must not be used to infer decisions.

An intake ready to begin integration requests `in_progress`. `complete` is not an intake target: it is reached only
after integration, automated validation, manual verification, and product-owner review.

## 4. Required visual assets

Each final design requires:

- A neutral fabric-base PNG
- A dye-mask PNG
- An untinted static-overlay PNG
- A geometry/model asset unless a named shared geometry is explicitly approved
- An optional reference to the authoring/source project file
- An optional preview image for human review

The three PNG layers must map unambiguously to one stable ID, use distinct namespaced resource IDs, share exact pixel
dimensions, and align pixel-for-pixel. A preview or authoring file is supporting evidence, not runtime art.

## 5. Fabric-base rules

The fabric base must:

- Be neutral or grayscale
- Contain cloth texture and shading
- Contain no heraldry
- Contain no brass or iron
- Contain no pre-baked dye colour
- Support every material palette colour
- Preserve transparency where needed

## 6. Dye-mask rules

The dye mask must:

- Include only fabric pixels intended to receive dye
- Exclude heraldry
- Exclude mount hardware
- Preserve cutouts
- Align exactly with the fabric base and static overlay
- Use the established renderer mask convention: only the mask layer receives the fabric tint

## 7. Static-overlay rules

The static overlay must:

- Contain heraldry and details that remain unchanged when dyed
- Remain untinted
- Exclude generic fabric background
- Exclude mount hardware
- Be original project artwork
- Align exactly with the fabric base and dye mask

## 8. Mount rules

Brass and iron mounts are separate shared renderer layers. Banner-specific fabric or overlay assets must not contain
mount hardware. Mount pixels remain untinted regardless of material or dye colour.

## 9. File requirements

- Runtime texture input must be a readable PNG.
- Final layer PNGs must be 8-bit true-colour RGBA with an alpha channel. Indexed-colour conversion is not accepted
  when it changes or damages alpha.
- Repository resource paths are case-sensitive even when a developer filesystem is not.
- Resource IDs use the `namespace:path` convention, normally under `britannia_mod`.
- Source-file paths in intake YAML are repository-relative, must not escape the repository, and must exist before
  an intake is ready.
- Supply a SHA-256 digest for every required source asset.
- Pixel-art assets must remain compatible with nearest-neighbour filtering; do not introduce resampling blur.
- Do not include external scene lighting, a mockup background, or an environmental background.
- A source-sheet crop is a reference, not acceptable final runtime art.
- Current diagnostic placeholder textures happen to be 16×16. That is not a final-size policy.
- Final pixel dimensions require explicit approval or a verified existing renderer convention. Do not infer block
  dimensions from PNG proportions.

The repository uses YAML 1.2 in JSON-compatible syntax for intake files, matching `content/banner_catalogue.yml`.
This keeps validation deterministic without adding another parser.

## 10. Original-art provenance

Each intake must record a concise provenance statement containing:

- Artist or owner
- Creation method
- Source-file location or project reference
- Confirmation that the work is original project artwork
- Confirmation that the project may distribute it
- Confirmation that it was not copied from source-reference art

No additional legal boilerplate is required.

## 11. Approval states

- `MISSING`: no value or evidence was supplied.
- `AMBIGUOUS`: a value or asset exists but its meaning, mapping, or approval cannot be established.
- `PROVISIONAL`: usable for development, but not approved as final content.
- `APPROVED`: explicitly approved by the product owner with attribution and date.

Only `APPROVED` values may replace live catalogue values. The intake field `approval.status` remains
`NOT_APPROVED` until every required owner decision and provenance field is complete.

## 12. Content statuses

- `placeholder`: shared diagnostic content; not final.
- `in_progress`: at least one approved input is being integrated, but verification is incomplete.
- `complete`: every applicable automated and manual requirement passed and the final appearance was reviewed.
- `disabled`: excluded by production validation.

Manual verification is mandatory before `complete`. Automated tests cannot substitute for observing item, dye,
mount, placed, reload, and persistence behavior.

## 13. Existing-world consequences

- Stable IDs preserve saved identity.
- Existing placed structures preserve their persisted footprint.
- A changed definition dimension affects new placement only.
- Existing structures are not automatically resized or migrated.
- Removing an orientation or mount from supported lists affects new placement and validation only.
- Existing items and structures retain their stored state, including their saved mount and placed orientation.
- Administrators may need to break and replace an old structure to adopt newly approved dimensions.

## 14. Integration workflow

1. The owner copies and fills one intake YAML.
2. The owner supplies the referenced original assets.
3. Run `.\tools\scaffold_banners.bat --check-final-intake <path>`.
4. Codex audits approval, provenance, hashes, and stable-ID mappings.
5. Codex updates the editable manifest, never a generated definition as the source of truth.
6. The definition becomes `in_progress`.
7. Declared generated outputs are refreshed without overwriting owner content.
8. Focused and regression tests run.
9. Item, dye, mount, placed, reload, and persistence behavior are reviewed live.
10. The definition becomes `complete` only after every check passes and the product owner approves the result.

Validator results are:

- `NOT_READY`: structurally understandable, but approval or required information is incomplete.
- `READY_FOR_INTEGRATION`: explicit approval, decisions, asset mappings, provenance, permission, and source files
  are complete and valid.
- `INVALID`: malformed, contradictory, unsafe, unknown, or unreadable input was supplied.

`READY_FOR_INTEGRATION` does not claim that manual in-game verification has occurred. That verification happens after
integration and remains mandatory before `complete`.

## 15. Crafting boundary

```text
Banner crafting: not approved
Crafting requirements: not applicable
Admin acquisition: implemented
Survival acquisition: unresolved
```

Recipes, patterns, crafting inputs, prices, NPC data, and shop data are not part of final-content intake.

# Banner Final-Content Intake Templates

These files are templates and examples only. They are outside `src/main/resources`, are never scanned by runtime
registries, and do not update `content/banner_catalogue.yml`.

- `intake.example.yml` is a generic one-banner template using the non-catalogue ID
  `britannia_mod:example_banner`.
- `extra_small_batch.example.yml` provides one visibly unapproved record for each current extra-small stable ID.

Both files use YAML 1.2 expressed in JSON-compatible syntax, matching the repository catalogue convention.

## Owner workflow

1. Copy one record to a new owner-supplied intake file.
2. Keep the real stable ID unchanged.
3. Fill every blank or `null` decision without inferring it from placeholders or image proportions.
4. Supply exactly `base_texture.png` and `dye_mask.png` at repository-relative paths.
5. Calculate and record SHA-256 for required assets.
6. Record original-art provenance and distribution permission.
7. Change `approval.status` to `APPROVED`, then provide `approved_by` and `approved_date`, only after the owner has
   approved every decision and supplied asset.
8. Set `requested_content_status` to `in_progress`.
9. Run:

   ```text
   .\tools\scaffold_banners.bat --check-final-intake <path>
   ```

`NOT_READY` means the document is still a draft. `INVALID` means it contains malformed, contradictory, unsafe,
unknown, or unreadable input. `READY_FOR_INTEGRATION` authorizes Codex to begin integration; it does not authorize
`content_status: complete`.

The base texture is the complete original full-colour default banner, including fixed artwork, native colour,
shading, cloth texture, silhouette, and transparency. The grayscale RGBA dye mask is transparent wherever the base
must remain unchanged; its RGB preserves brightness and texture while its alpha controls recolour strength. Both
images must have identical dimensions and alignment. An active mask pixel must align with a fully opaque base pixel;
transparent or partially transparent base pixels must have transparent mask pixels so normal two-pass blending
preserves base alpha. Active mask RGB channels may differ by at most 1.

There is no separate overlay and no alternate rendering strategy. A fixed-colour foreground detail cannot
independently overlap a recoloured underlayer at the exact same pixel. For current banners, each pixel must be
classified as fixed or recolourable; any future exception requires a separately approved architecture milestone.

The validator is read-only. It does not copy art, change the catalogue, generate definitions, update statuses, stage
files, or load intake data into runtime registries.

Do not add recipes, patterns, crafting inputs, prices, NPC/shop data, arbitrary NBT, runtime item components, source
pigments, or item-state placement orientation to an intake.

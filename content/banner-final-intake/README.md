# Banner Final-Content Intake Templates

These files are templates and examples only. They are outside `src/main/resources`, are never scanned by runtime
registries, and do not update `content/banner_catalogue.yml`.

- `intake.example.yml` is a generic one-banner template using the non-catalogue ID
  `britannia_mod:example_banner`.
- `extra_small_batch.example.yml` provides one visibly unapproved record for each current extra-small stable ID.
- `small_batch.example.yml` provides one visibly unapproved record for each current Small stable ID.
- `large_batch.example.yml` provides one visibly unapproved record for each current Large stable ID and records the
  `wall_parallel`-only product constraint without approving unresolved content decisions.

All templates use YAML 1.2 expressed in JSON-compatible syntax, matching the repository catalogue convention.

## Owner workflow

1. Copy one record to a new owner-supplied intake file.
2. Keep the real stable ID unchanged.
3. Fill every blank or `null` decision without inferring it from placeholders or image proportions.
4. Supply exactly `base_texture.png` and `dye_mask.png` at repository-relative paths; approved final-content files
   must each be exactly 128 x 128 pixels in 8-bit RGBA.
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
images must each be exactly 128 x 128 pixels and have identical alignment. An active mask pixel must align with a
visible base pixel, and mask alpha must never exceed the aligned base alpha. Partial mask alpha is valid and controls
recolour strength. Active mask RGB channels may differ by at most 1. Existing 16 x 16 diagnostic placeholders remain
a non-final exception and are not upscaled; runtime rendering remains independent of texture resolution.

There is no separate overlay and no alternate rendering strategy. A fixed-colour foreground detail cannot
independently overlap a recoloured underlayer at the exact same pixel. For current banners, each pixel must be
classified as fixed or recolourable; any future exception requires a separately approved architecture milestone.

The validator is read-only. It does not copy art, change the catalogue, generate definitions, update statuses, stage
files, or load intake data into runtime registries.

Do not add recipes, patterns, crafting inputs, prices, NPC/shop data, arbitrary NBT, runtime item components, source
pigments, or item-state placement orientation to an intake.

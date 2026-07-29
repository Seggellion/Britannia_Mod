# Final Banner Content Review Checklist

Use one copy per stable banner definition.

## Identity and decisions

- [ ] Stable ID verified
- [ ] Final display name approved
- [ ] Localization approved
- [ ] Width approved
- [ ] Height approved
- [ ] Supported orientations approved
- [ ] Supported mounts approved
- [ ] Default mount approved
- [ ] Placement profile approved
- [ ] Geometry convention approved
- [ ] Final 128 x 128 pixel dimensions approved

## Asset intake

- [ ] Complete full-colour `base_texture.png` supplied
- [ ] Grayscale RGBA `dye_mask.png` supplied
- [ ] Geometry supplied or shared geometry explicitly approved
- [ ] Base and mask are each exactly 128 x 128 RGBA
- [ ] Base and mask dimensions match
- [ ] Base and mask align pixel-for-pixel
- [ ] Fixed artwork remains in the base texture
- [ ] Mask is transparent over fixed pixels
- [ ] Mask is transparent wherever aligned base alpha is zero
- [ ] Mask alpha never exceeds aligned base alpha; partial-alpha recolouring is reviewed deliberately
- [ ] Mask contains transparent and active pixels
- [ ] Mask active RGB is grayscale within channel tolerance 1
- [ ] Asset-to-ID mapping is unambiguous
- [ ] Provenance recorded
- [ ] Distribution permission recorded
- [ ] No placeholder markings remain
- [ ] Exactly two runtime banner images are declared

## Automated validation

- [ ] Manifest validates
- [ ] Definition JSON validates
- [ ] Localization exists
- [ ] Every referenced asset exists
- [ ] SHA-256 hashes match
- [ ] PNG metadata validates
- [ ] RGBA and alpha validate
- [ ] Base remains untinted
- [ ] Natural/default state omits the mask pass
- [ ] Recoloured state tints only the mask
- [ ] Transparent mask pixels preserve base pixels
- [ ] Partial mask alpha blends deterministically
- [ ] Mask brightness ordering survives tinting
- [ ] Brass remains untinted
- [ ] Iron remains untinted
- [ ] Item renderer extracts state
- [ ] Placed renderer extracts state
- [ ] Missing-content fallback remains functional
- [ ] Scaffold `--check` passes
- [ ] Full tests pass
- [ ] Build passes
- [ ] JAR contains intended resources
- [ ] Removed image fields and files are absent
- [ ] Crafting remains absent

## Manual item matrix

- [ ] Inventory
- [ ] First-person hand
- [ ] Third-person hand
- [ ] Dropped item
- [ ] Item frame
- [ ] Dye-preview current
- [ ] Dye-preview proposed

## Manual material matrix

For cotton, wool, linen, and silk:

- [ ] Natural colour uses the authored base unchanged
- [ ] Representative light recolour
- [ ] Representative dark recolour
- [ ] Material metadata remains distinct

## Manual mount matrix

- [ ] Brass
- [ ] Iron

## Manual placement matrix

For each approved orientation:

- [ ] North
- [ ] South
- [ ] East
- [ ] West

## Persistence matrix

- [ ] Save/reload
- [ ] Relog
- [ ] Late client tracking
- [ ] Resource reload
- [ ] Data reload
- [ ] Anchor break
- [ ] Child break, if applicable
- [ ] Pick block
- [ ] Re-place recovered item

## Completion

- [ ] Product owner reviewed final natural and recoloured appearance
- [ ] Manual verification is recorded
- [ ] `content_status` may be changed to `complete`

Crafting requirements are not applicable. Banner crafting is not approved.

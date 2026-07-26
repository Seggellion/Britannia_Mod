# Final Banner Content Review Checklist

Copy this checklist for each definition. Record evidence and actual results; do not check a box based only on intent.

Stable ID: `________________________`

## Identity and decisions

- [ ] Stable ID verified
- [ ] Final display name approved
- [ ] Width approved
- [ ] Height approved
- [ ] Supported orientations approved
- [ ] Supported mounts approved
- [ ] Default mount approved
- [ ] Placement profile approved

## Asset intake

- [ ] Fabric base supplied
- [ ] Dye mask supplied
- [ ] Static overlay supplied
- [ ] Geometry supplied or shared geometry explicitly approved
- [ ] Asset-to-ID mapping unambiguous
- [ ] Provenance recorded
- [ ] Distribution permission recorded
- [ ] No placeholder markings remain

## Automated validation

- [ ] Manifest validates
- [ ] Definition JSON validates
- [ ] Localization exists
- [ ] Every referenced asset exists
- [ ] PNG metadata validates
- [ ] Alpha validates
- [ ] Fabric tint test passes
- [ ] Overlay remains untinted
- [ ] Brass remains untinted
- [ ] Iron remains untinted
- [ ] Item renderer extracts state
- [ ] Placed renderer extracts state
- [ ] Missing-content fallback remains functional
- [ ] Scaffold `--check` passes
- [ ] Full tests pass
- [ ] Build passes
- [ ] JAR contains intended resources
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

### Cotton

- [ ] Natural colour
- [ ] Representative light colour
- [ ] Representative dark colour

### Wool

- [ ] Natural colour
- [ ] Representative light colour
- [ ] Representative dark colour

### Linen

- [ ] Natural colour
- [ ] Representative light colour
- [ ] Representative dark colour

### Silk

- [ ] Natural colour
- [ ] Representative light colour
- [ ] Representative dark colour

## Manual mount matrix

- [ ] Brass
- [ ] Iron

## Manual placement matrix

Test only approved orientations.

### Wall parallel

- [ ] North
- [ ] South
- [ ] East
- [ ] West

### Wall perpendicular

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

- [ ] Product owner reviewed final appearance
- [ ] `content_status` may be changed to `complete`

Crafting: not applicable — product-disabled.

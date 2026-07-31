# Compatibility

The server owns definitions, dye resolution, preview authority, application, persistence, placement, and
synchronized display metadata. Clients own only presentation and bounded caches. Confirm/cancel packets contain
an opaque UUID; clients cannot submit a colour, pigment, material, mount, or result.

## Data packs

Data packs may add valid definitions, materials, pigments, palettes, mounts, and placement profiles within codec
and network count limits. Invalid or incomplete reload candidates are rejected before atomic publication. Existing
saved IDs remain stable across reload. Removing referenced content produces typed missing-reference state rather
than data loss.

## Resource packs

Resource packs may replace declared geometry and textures. Every released banner expects a 128 x 128 RGBA base and
mask in the block atlas plus declared geometry. Missing geometry/base/mask/mount assets produce deterministic
fallback presentation and bounded diagnostics. Resource reload clears client appearance caches by generation.

## Network and performance

The release render snapshot is 11,619 bytes against a 64 KiB regression ceiling. Preview is 297 bytes against
4 KiB. Confirm/cancel are 16 bytes; the largest tested result packet is at most 18 bytes. The largest tested banner
block-entity update tag is 578 bytes against 4 KiB.

Render snapshot counts are bounded before allocation. Translation keys are limited to 256 characters. Item,
appearance, and placed-visual caches are 256-entry LRU caches; missing diagnostics are bounded to 256, block-entity
load diagnostics to 1,024, alias diagnostics to 64, and terminal preview replay records to 4,096.

## Unsupported combinations

Crafting/pattern content is disabled. Definitions expose only their declared orientations and mounts. Unknown
future schema versions, invalid IDs, unsupported mounts, corrupt rectangles, cross-player/replayed sessions, and
unprovable colour recovery fail closed. No compatibility claim is made for mismatched Minecraft/NeoForge versions,
client-only mods on a dedicated server, or third-party packs that exceed protocol limits.

# Adding a Mount

Mounts are physical, untinted rendering choices and are separate from fabric material.

1. Reserve a stable `MountId` and add schema-1 mount data, geometry, texture, and translation.
2. Add the ID only to definitions and placement profiles whose artwork and support rules have been reviewed.
3. Provide every required orientation-specific geometry mapping. Parallel and perpendicular geometry must be
   distinct when both are supported.
4. Test every supported facing, footprint, wall support boundary, item/preview/placed rendering, save/reload,
   break/drop, pick/re-place, F3+T, and `/reload`.

An unsupported or missing saved mount is a typed invalid state. It is retained for recovery and is never silently
defaulted to brass or iron.

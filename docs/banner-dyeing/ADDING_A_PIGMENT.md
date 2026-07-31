# Adding a Pigment

1. Reserve a stable `PigmentId` and add its schema-1 definition under `data/britannia_mod/pigments/`.
2. Supply reference sRGB and relevant colour-family tags. Reference RGB is resolver input, not saved banner RGB.
3. Add translation and operator command coverage.
4. Resolve it against every active material. Add an explicit palette override only when product intent requires a
   fixed target; otherwise deterministic nearest-colour and tie-break rules apply.
5. Test bounded/unlimited tubs, preview/cancel/apply, re-dye, depleted tubs, hand changes, stale/replayed sessions,
   two-player isolation, reload, and save/reload.

Existing saved banners keep their `resolved_colour_id` if mappings later change. A fresh dye operation uses the
current mapping. If pigment provenance becomes unavailable, the system keeps raw saved state and does not guess.

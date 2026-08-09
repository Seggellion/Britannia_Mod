# House Farm Plot Farming Integration

`HouseFarmPlotBlock` is a persistent-assignment adapter over UltimaCraft's existing farming and
flower systems. It does not define a separate plant registry, growth clock, harvest table, or set of
plant models.

## Lifecycle

- A plot with no assignment data is `UNINITIALIZED` and initializes once with
  `FlowerRegistry.POPPY` at the ordinary newly-planted flower stage.
- A planted plot is `ASSIGNED` to a canonical namespaced plant ID plus its crop/flower kind.
- Crop and flower harvests use their existing reward, tool, quality, skill, and reset paths. The
  assignment remains unchanged, and annual crops use the existing regrowth reset on this surface.
- `FarmingHoeItem` removes the active crop/flower (including owned tall or tree structures), leaves
  the house plot in place, and persists `CLEARED`.
- Only a valid plant accepted by the ordinary crop or flower registries can transition a `CLEARED`
  plot back to `ASSIGNED`.

The assignment is saved by `HouseFarmPlotBlockEntity`. Crop growth remains in
`FarmingBlockEntity`; flower species, color, growth, soil, quality, and provenance remain in the
existing `FlowerBlockEntity` lifecycle. Client state uses the standard block-entity update packet.

## Rendering and geometry

The multipart house model's soil top is `14/16` block units. The house renderer calls the shared
crop and flower renderer entry points with surface-specific offsets:

- flowers: `14/16`;
- non-tall crop models whose geometry begins at `-1/16`: `15/16`;
- segmented tall-crop roots retain the ordinary zero-offset alignment with their real upper blocks.

No house-specific crop or flower stage models are created.

## Protection and cleanup

Mutating interactions use the owning `StructureRecord` when the plot lies inside a registered house
region, with Creative/operator bypass matching existing administrative behavior. Linked tall-crop
and fruit-tree interactions resolve back to the house plot, preventing an alternate harvest or break
path from bypassing ownership or the hoe-only reset. Tall cleanup checks crop kind, and tree cleanup
checks the root's exact soil position and tree type before removing generated structure.

## Automated coverage

`HouseFarmPlotAssignmentTest` covers serialization of `UNINITIALIZED`, crop/flower `ASSIGNED`, and
`CLEARED`, including corrupt-ID fallback. `HouseFarmPlotIntegrationTest` locks the shared pipeline,
renderer anchor, model-reuse, hoe-reset, linked-plant protection, and registration contracts. The
existing farming and flower suites remain the regression authority for ordinary plots.

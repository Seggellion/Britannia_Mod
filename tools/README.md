# tools

Run everything from the repository root.

## Regenerating the wall junction models

The `_corner`, `_corner_branch_right`, `_t_junction` and `_t_junction_branch_right` models for
every `DoubleWallBlock` family are **generated** from that family's `_straight` model. Edit the
straight in Blockbench, then regenerate — do not hand-edit a junction file, it will be overwritten.

```bash
python tools/gen_junctions.py && python tools/gen_wood_support_floor.py && python tools/gen_blockstates.py
```

`gen_junctions.py` also carries the list of which families have real authored art versus which are
still placeholders. Move a family from `PLACEHOLDER` to `AUTHORED` once its straight is real.

## Validating resources

```bash
python tools/validate_resources.py --scope structure/plaster structure/sandstone
```

Checks that blockstates and models resolve case-sensitively, that JSON parses, that no element
declares coplanar duplicate faces or a zero-thickness box with faces on both sides, and that
elements stay inside Minecraft's legal `-16..32` range.

To also confirm every reachable block state has a variant — which is what stops a missing-model
fallback reaching the game — generate the expected state space and pass it in:

```bash
python tools/gen_expected_states.py > build/expected_states.json
python tools/validate_resources.py --states build/expected_states.json --scope structure/plaster
```

`gen_expected_states.py` mirrors what `BlockRegistry` declares; keep its lists in step when you
change a block's class. Moving a block to `MirrorableWindowBlock` adds the `mirrored` property and
doubles its state count, and the validator will tell you if you forget.

The mod has a large backlog of pre-existing breakage elsewhere in the tree, so always pass
`--scope` rather than trying to get the whole tree to zero at once.

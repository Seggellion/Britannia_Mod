# tools

Run everything from the repository root.

**Every model in this project is hand-authored.** Nothing here rewrites your models. Edit them in
Blockbench and the tools will check them and get them to the client — that is all.

## After editing a model in Blockbench

```bash
python tools/prepare_client.py
```

Then restart the client. This validates, runs `gradlew processResources`, and confirms every asset
reached `build/resources/main`.

That copy step is the one that catches people out: the dev client reads `build/resources/main`, not
the source tree, so editing a model and reloading in game does nothing until it has run. The script
ends with `all assets are in sync` so you can tell at a glance whether a missing change is a
pipeline problem or something else.

## Blockbench drops three things when it re-saves a model

`prepare_client.py` reports these as warnings; fix them in the file, they are one line each.

| what | why it matters |
|---|---|
| `"render_type": "minecraft:cutout"` | a model using the transparent `ornateness` sheet needs it, or Minecraft ignores alpha and the clear texels render as opaque black panels |
| `"ambientocclusion": false` | a model with geometry outside the 0..16 cube needs it, or smooth lighting extrapolates past the block bounds and the part above the block goes black |
| zero-thickness panes | an element flat on one axis with a face on **both** sides renders two quads in the same plane and always z-fights — give it a little thickness |

`python tools/scaffolding/normalize_sources.py` applies all three if you would rather not do them
by hand. Nothing runs it for you.

## Which model does a block actually use?

```bash
python tools/which_model.py plaster_ornate_wall_upper
python tools/which_model.py plaster_wall_blank --state shape=corner
```

Which state you are looking at decides which file renders: a wall standing in a corner renders
`_corner`, not `_straight`; a joist floor with blocks on all four sides renders `_enclosed`; a
window whose side has been toggled renders `_window_right`.

## Validating on its own

```bash
python tools/gen_expected_states.py > build/expected_states.json
python tools/validate_resources.py --states build/expected_states.json --scope structure/plaster
```

`--states` confirms every reachable block state has a variant, which is what stops a missing-model
fallback reaching the game. Keep `gen_expected_states.py` in step with `BlockRegistry` when a
block's class changes. Always pass `--scope`: the mod has a large backlog of pre-existing breakage
elsewhere in the tree.

## tools/scaffolding/

One-shot generators used to bootstrap a **new** block family from a `_straight` — corners,
junctions, mirrored windows, blockstates. They overwrite whatever they target, so they are kept out
of the normal workflow deliberately. Run one only when starting a family from scratch, and
hand-refine the output afterwards.

Nothing here touches `display` / inventory transforms. Those are managed in Blockbench.

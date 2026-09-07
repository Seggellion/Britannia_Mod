# M8 fence state, collision and paths

Connected fence facing now comes from block topology rather than a neighbour's saved facing. A loaded linear run searches its negative-axis junction first, then its positive-axis junction, and uses that junction model's occupied edge. With no junction it uses north for east/west runs and west for north/south runs. Corners and T junctions retain their existing model topology; four-way pieces use a fixed north tie-breaker. Isolated pieces retain their intentional facing.

Selection bounds remain one block tall. Collision extends each existing occupied strip to 1.5 blocks without filling the open interior. The authored end, mirrored end, straight, corner, T and cross assets are unchanged.

The dirt-path Mixin allows only `WoodenFenceBlock` above an existing path, and cancels an already queued conversion only while that custom fence remains above it. Vanilla shovel use and other blocks retain the vanilla path rules. The Mixin follows this NeoForge repository's mapped-runtime `remap=false` convention.

On chunk load, a bounded queue inspects loaded chunks and their loaded cardinal neighbours after the server tick boundary. Sections without this fence are skipped. Matching blocks receive one reconciliation tick; unchanged state creates no further updates. No chunk generation or whole-world scan is added.

Six GameTests cover:

- All 16 neighbourhoods, four initial facings and every permutation of the occupied placement cells: 1,044 layouts, with connected fixed-point equality and isolated-facing preservation.
- Direct L versus L→T→L in four rotations and three mirrors, including the south-arm outside edge.
- Every collision strip in all 64 states: outline 1.0, collision 1.5, complete vertical coverage and no full-cube interior.
- A path created through the real vanilla shovel item, custom-fence survival, a queued path tick, and unchanged oak-fence conversion control.
- Repair of deliberately stale saved facing in a 12-piece loaded run and stability after further ticks.
- Real Survival and Adventure player collision movement at ground height and the normal jump envelope beside a vanilla oak-fence control; movement above 1.5 remains possible.

The movement test exercises the server collision routine. It is not an observed keyboard-driven client walk/run/jump session. The load test exercises the actual loaded-chunk inspection and scheduled-block-tick path; a separate interactive client restart and visual model inspection remain part of the named M11 runtime gate. Full test totals and local commit are recorded in the scratchpad.

# Wooden edge fence decisions

Patch 18 baseline: c8044e4565281514253e290167c555413c7a5293. Minecraft 1.21.1,
NeoForge 21.1.72, NeoGradle 7.0.165, Gradle 8.9, Java 21, GeckoLib 4.6.6.

The saved compatibility unit is the old **rendered layout and collision layout
separately**, including noncanonical end states and the mismatched old T.
`oracle/legacy-blockstate.json` freezes all 64 old mappings; the authored hash
manifest prevents the retired generator from replacing checked-in artwork.
The enforced hash normalizes CRLF to LF because Git's Windows checkout
conversion changes byte hashes. Discovery's original raw hashes remain in
`oracle/DISCOVERY_RAW_MODEL_SHA256.txt`; Git shows no authored mesh diff.

Use the reference `layout_code` 0–31 plus legacy sentinel 32. Bits N=1 E=2
S=4 W=8 are old selectors, not current connections. Bit 16 is reflection
parity. Registered default 32 makes missing-property decoding read the old
flags. Querying a sentinel never writes it; an update or intentional transform
materializes the old mask **first**, then recomputes contact flags. There are
2,112 states and 192 mapping keys. Share outline/collision geometry by the
16 edge masks (eight nonempty masks are reachable), rather than rebuilding
it for every connection combination. There are seven authored and seven
derived reflected models; mutable connections do not select new meshes.

For parity one, decode Q(G(Q(facing), Q(mask))), Q reflecting local Z about 8.
Quarter turns permute facing, selector bits and flags. Mirrors also toggle
parity. Derive reflected assets from the current meshes, retaining vertex UV
correspondence after reversing winding. Identity operations preserve physical
geometry, although a sentinel may become concrete.

Connections use cardinal, loaded, wooden owners only. Compare individual
terminal strip cross sections in world coordinates, with positive shared
width and height. At least one strip must terminate at the shared boundary;
side-by-side parallel surfaces alone are not an endpoint join. Use only
strips occupied in both artwork and collision. The old T's center branch
and arbitrary end states that disagree with collision are conservative:
they advertise no imaginary edge arms. No diagonal, gate, vanilla, iron or
corral adapters, chunk loading or run scans.

New candidates are four singles and four old L footprints. Horizontal support
clicks constrain candidates to include player-look-opposite. Fence endpoint
clicks constrain candidates to contact the clicked owner's terminal, then
prefer the fewest panels, nearest hit, intended facing and N/E/S/W. Ground or
replaceable clicks maximize compatible contacts before those tie breaks.
No eligible hints means an isolated player-look-opposite strip. Existing
pieces never change their physical layout to improve the new placement.

A corner cell already occupied by a single must be removed and placed again
after both arms exist. Place ordinary straight arms first, then fill the
corner gap; the new L bridges compatible arms. Pinned corners retain both
panels when either neighbor is removed. Deliberate decorator rotation or
replacement is the repair workflow for already displaced builds.

Connection counts may exceed panel counts: a perpendicular neighbor can
terminate on a straight panel's outer face. Such flags describe real contact
and never select a T or cross mesh for a newly placed single.

Deferred: duplicate post polish, true T/cross artwork, waterlogging, mixed
adapters, diagonal ownership, other fence families. The medallion visual
release hold remains independent; no deployment or production-world work.

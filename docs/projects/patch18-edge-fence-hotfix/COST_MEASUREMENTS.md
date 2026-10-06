# Wooden fence client cost

Measured 2026-10-06 using normally compiled development clients at the same
main-menu scene. Baseline c8044e45 and hotfix 07359f98 used Minecraft 1.21.1,
NeoForge 21.1.72, Temurin 21.0.9+10, G1, compressed object pointers and the
same automatic maximum heap (8,514,437,120 bytes). Three `jcmd
GC.class_histogram` samples per client force collection before counting.
These are actual loaded client objects, not estimates from JSON entries.

| Loaded object | Baseline count | Hotfix count | Additional shallow bytes |
| --- | ---: | ---: | ---: |
| BlockState | 42,485 | 44,533 | 196,608 |
| BlockBehaviour.BlockStateBase.Cache | 40,576 | 42,624 | 65,536 |
| SimpleBakedModel | 15,918 | 15,946 | 1,344 |
| BakedQuad | 616,822 | 618,070 | 39,936 |

Counts were identical across each client's three samples. The 2,048 added
states match the change from 64 to 2,112 registered fence states. The model
increase is 28 loaded SimpleBakedModels and 1,248 quads; selector variants
do not create 2,112 independent model meshes.

Total post-collection Java heap samples (bytes):

- Baseline: 498,674,376 / 498,633,416 / 498,629,752; median 498,633,416;
  range 44,624.
- Hotfix: 507,376,928 / 507,348,304 / 507,352,592; median 507,352,592;
  range 28,624.
- Median increase: 8,719,176 bytes (8.32 MiB, 1.75%). This is total client
  heap difference, not an exclusive retained-size attribution to fences.

The third hotfix sample followed window maximization; the first two used
the baseline window size. Java object counts remained stable. Native/GPU
memory and frame-time performance were not profiled and are not inferred
from the Java histogram.

One startup per source revision was timed from existing log timestamps:

| Interval | Baseline | Hotfix |
| --- | ---: | ---: |
| ModLauncher to animation-loader completion | 34.999 s | 34.596 s |
| Resource reload start to animation-loader completion | 9.740 s | 8.520 s |

Baseline endpoints: 13:28:10.472 / 13:28:35.731 / 13:28:45.471.
Hotfix endpoints: 13:40:40.111 / 13:41:06.187 / 13:41:14.707.
The reload interval includes textures and other resources; it is not an
isolated model-bake timer. A single startup pair establishes no timing
variance or performance improvement. Repeated startup/model-bake and
frame-time profiling remain unmeasured. The three heap samples establish
the reported heap/count variability only.

Raw histograms, JVM flags and client logs are preserved with task evidence.
Complete blockstate coverage and authored/reflected mesh checks are in the
resource verifier and acceptance ledger.

Normalized model/blockstate/texture warning messages are identical between
clients: 846 each, no baseline-only or hotfix-only message and no wooden
fence warning. Existing cotton, grape, corral and other resource warnings
remain outside this fix; the normalized comparison is preserved in JSON.

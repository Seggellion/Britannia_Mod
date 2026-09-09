# UltimaCraft — Britannia Mod

[![Build](https://github.com/Seggellion/Britannia_Mod/actions/workflows/build.yml/badge.svg)](https://github.com/Seggellion/Britannia_Mod/actions/workflows/build.yml)

UltimaCraft brings the world of **Ultima Online** to Minecraft. This NeoForge mod recreates
Britannia's systems — skills, a player-driven economy, banking, guilds, quests, and moongate
travel — on top of Minecraft 1.21.1, backed by a shared UltimaCraft web service that ties
independent servers ("shards") into one persistent world.

**Current release: Patch 18 (August 2026) — mod version 0.1.8b, the newest version of UltimaCraft.**

## Features

- **Skills & progression** — UO-style skill gain (Mining, Farming, and more), with access
  requirements and gain windows modeled on the classic RunUO formulas.
- **Player economy** — vendors and traders reconstructed from the RunUO rosters: buy from
  profession vendors, sell to commodity traders, with copper/silver/gold denominations.
- **Banking** — a full bank interface with deposits, withdrawals, balances, and bank cheques,
  settled against the shared UltimaCraft ledger.
- **Guilds & NPCs** — guildmaster service NPCs offering training in their guild's skills.
- **World content** — quests and quest journal, moongate travel, shrines and monoliths,
  wild reagents and resource gathering, harvestable and managed vegetation.
- **Building & decoration** — custom architecture families (sandstone walls, windows, posts,
  battlements), scaffolding, stalactites, dyeable banner sets, display cases, and hundreds of
  custom models, textures, and sounds with GeckoLib animation.
- **Shard networking** — dedicated servers authenticate to the UltimaCraft service with a
  per-shard credential; players link accounts in game with `/verify <code>`.

## Getting started locally (TL;DR)

Prerequisite: **JDK 21**. Everything else comes through the Gradle wrapper.

```bash
git clone https://github.com/Seggellion/Britannia_Mod.git
cd Britannia_Mod
./gradlew runClient -Pdev --no-configuration-cache
```

That launches a development Minecraft client with the mod loaded (first run takes a while
while NeoForge sets up). Other useful tasks:

```bash
./gradlew build                                          # build jars into build/libs/
./gradlew runServer                                      # local dedicated dev server
./gradlew test                                           # unit test suite
./gradlew runGameTestServer --no-configuration-cache     # in-game GameTest suite
./gradlew artifactIdentity                               # size, SHA-256 and commit of each jar
```

## Deploying — which jar

`./gradlew build` writes **two** jars, and only one of them can be deployed.

| File | Deploy? | What it is |
| --- | --- | --- |
| `build/libs/britannia_mod-<version>-all.jar` | **Yes** | Bundles GeckoLib 4.6.6 and nanohttpd under `META-INF/jarjar/`. |
| `build/libs/britannia_mod-<version>-thin.jar` | No | No bundled dependencies. Kept for the Maven publication and for consumers that supply their own GeckoLib. |

Drop the **`-all`** jar into the `mods/` folder of a NeoForge 21.1.72 installation for
Minecraft 1.21.1, then restart the server.

**Do not tell them apart by file size.** They are within a few percent of each other, and the
thin jar does not announce its own problem: `neoforge.mods.toml` does not declare GeckoLib, so a
server given the thin jar starts clean, runs, and then throws `NoClassDefFoundError` at the first
animated render — minutes after the deploy, with nothing in the stack trace about packaging. Go by
the classifier in the filename, or read the contents:

```bash
unzip -l build/libs/britannia_mod-0.1.8b-all.jar | grep jarjar
#   META-INF/jarjar/nanohttpd-2.2.0.jar
#   META-INF/jarjar/geckolib-neoforge-1.21.1-4.6.6.jar
#   META-INF/jarjar/metadata.json
```

`./gradlew check` — and so `./gradlew build`, and CI — runs `verifyDeployableJar`, which fails the
build if the `-all` jar has lost either bundled dependency or cannot say which commit produced it.
`-Pdev` deliberately drops GeckoLib from the bundle, so it belongs on `runClient` and nothing else:
`./gradlew build -Pdev` now fails rather than quietly producing an `-all` jar that is not one.

Every jar also carries `britannia_mod_build.properties` — mod version, git commit, branch, dirty
flag and build timestamp — which a running server reports through `/grabby env`. That is how a
deployed file is matched back to a commit; `./gradlew artifactIdentity` prints the same identity,
plus each jar's size and SHA-256, at build time.

By default the mod points at a local UltimaCraft backend on `127.0.0.1:3000`; single-player
and development work fine without one, and production shards configure their real endpoint
and credentials via `config/britannia_mod-server.properties` or the `ULTIMACRAFT_SHARD_NAME`
/ `ULTIMACRAFT_SHARD_SECRET` environment pair.

## Run your own shard — server operators wanted

**We are always looking for more server operators.** UltimaCraft grows one shard at a time:
you run the Minecraft server, and the shared UltimaCraft service provides accounts, banking,
economy, and world state behind it.

**[Contact us on Discord](https://discord.gg/ESERudf2UX) to add your Ultima Online server
using this public repository** — we'll provision your shard name and credentials and help
you get connected. More at [ultimacraft.com](https://www.ultimacraft.com).

## License

Source is published for reference and shard operation. All rights reserved; see
`TEMPLATE_LICENSE.txt` for the MIT-licensed NeoForged MDK template files this project
started from.

UltimaCraft is a fan project. Ultima Online is a trademark of Electronic Arts Inc.;
this project is not affiliated with or endorsed by EA. Not an official Minecraft product;
not approved by or associated with Mojang or Microsoft.

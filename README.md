# UltimaCraft — Britannia Mod

[![Build](https://github.com/Seggellion/Britannia_Mod/actions/workflows/build.yml/badge.svg)](https://github.com/Seggellion/Britannia_Mod/actions/workflows/build.yml)

UltimaCraft brings the world of **Ultima Online** to Minecraft. This NeoForge mod recreates
Britannia's systems — skills, a player-driven economy, banking, guilds, quests, and moongate
travel — on top of Minecraft 1.21.1, backed by a shared UltimaCraft web service that ties
independent servers ("shards") into one persistent world.

**Current release: Patch 18 (August 2026) — mod version 0.1.8, the newest version of UltimaCraft.**

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
```

To use the mod outside the dev environment, drop `build/libs/Britannia_Mod-<version>-all.jar`
into the `mods/` folder of a NeoForge 21.1.72 installation for Minecraft 1.21.1 (the `-all`
jar bundles GeckoLib and other runtime dependencies).

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

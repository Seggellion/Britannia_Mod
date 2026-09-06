# UltimaCraft Patch 18

Patch 18 is a broad expansion of UltimaCraft's living-world systems. Farming, mining, blacksmithing, banking, housing, vendors, city life and decoration now connect much more closely to the server's skills and economy. The update also brings a substantial collection of new world-building content, creatures, visual improvements and reliability fixes.

## Highlights

- A deep Farming system now covers 67 crop definitions, skill-based planting, soil care, climate preferences, specialized harvesting and persistent farm plots.
- Mining has become a server-curated resource profession, with 29 managed resource definitions, skill and tool requirements, shaped deposits, restoration and economy-ready output.
- Blacksmiths receive a catalogue of 204 craftable entries across weapons, armor, shields, tools, components and artillery.
- Bankers now provide a complete banking experience: item storage, account balances, coin deposits and withdrawals, cheques and cheque cashing.
- The Architect can sell 10 implemented house-deed styles, including the new two-story villa, large patio and stone keep.
- Grabby Hands turns furniture, containers and loose goods into physical possessions that can be lifted, moved and arranged.
- Cities gain economy-driven vendors, skill-training Guildmasters, population-managed townspeople and new ambient wildlife.
- Floriculture, banner dyeing, textile processing, training dummies, managed vegetation and wild resources add new professions and reasons to interact with the world.

## Farming & Floriculture

Farming is now a full skill-driven profession rather than a small collection of plant blocks.

- The farming catalogue contains 67 crop definitions. It covers vegetables, grains, berries, fiber plants, herbs, magical reagents, orchard crops, trellis plants and compatibility paths for several familiar Minecraft crops.
- Crops care about more than time. Hydration, soil nutrients, climate, altitude and the plant's own tolerances all influence cultivation.
- Farming skill now determines what can be planted. Unfamiliar seeds are presented generically until the viewer has enough skill to identify and use them.
- Harvest methods matter. Grain blades, scissors, shovels and hand harvesting are used by different crop families, with server-checked yields and skill gains.
- Tall and perennial plants have richer life cycles. Corn and bananas grow vertically, while tomatoes, hops, peppers, cucumbers and melons can use trellis-style growth and regrow after harvest.
- Grapes now use one unified, perennial arbor system. A grape plant grows into a tall, variety-aware arbor, carries the variety supplied by the server and produces grapes without falling back to the retired standalone-vine system.
- Seven flower species—poppy, snowdrop, lily, foxglove, campion, hyacinth and orfluer—join the farming simulation. Their palettes draw from 23 persistent colors, including uncommon and very rare outcomes.
- House farm plots remember their assigned crop or flower after harvest. Owners can clear a plot deliberately with a hoe when they want to change it.

The result is a profession built around learning the land: recognizing seeds, matching plants to a location, caring for the soil and choosing the correct tool at harvest.

## Mining, Geology & Resources

Patch 18 replaces random vanilla-style mineral supply with an UltimaCraft-managed geology system.

- The resource catalogue covers 29 ores, minerals, stones and sediments. Twenty-seven participate directly in Mining progression; clay and silica are worked as managed sediment beds with shovels.
- Metal progression includes iron, silver, tin, shadow iron, copper, gold, agapite, verite and valorite. Coal and a broad rock-and-stone ladder are integrated as well.
- Deposits can take recognizable forms such as vertical veins, layers, clusters, snakes, geodes and sedimentary lenses.
- Mining skill and the correct tool are checked by the server before extraction. Higher-tier resources require greater Mining skill.
- Managed deposits keep a persistent identity. Extracted cells enter restoration rather than becoming an endlessly disposable source, allowing the world economy to replenish without reverting to uncontrolled vanilla ore generation.
- Ore carries purity and quarried stone carries grade information, giving later crafting and trading systems meaningful material data.
- Player-placed building blocks remain removable as building material; the managed-resource rules distinguish them from economic deposits.
- Automation, Silk Touch and Fortune do not bypass the managed extraction rules.
- Creative mode is for building and testing, not earning. A creative player breaks catalogued stone and sited deposits like any other block, with no tool, skill or depletion rules applied, unless they attack with the Britannia pickaxe, which runs the full managed mining flow so mining can be tested without leaving creative.

The UltimaCraft server decides where managed deposits exist. Patch 18 supplies their shapes, progression, safe extraction and restoration; it does not scatter a second hidden set of natural deposits behind the world's economy.

### New material loops

- Copper and tin can be combined in a forge to produce bronze.
- New ingot presentation supports copper, silver, tin, bronze, shadow iron, agapite, verite and valorite.
- Silica sand can be fired into raw glass.
- Quarried limestone can be fired into plaster.
- Quarried cobblestone can be fired back into common stone while retaining the graded-stone workflow.

## Blacksmithing

Blacksmithing now has the breadth expected of a true UltimaCraft craft profession.

- The final catalogue contains 204 craftable entries: 121 weapons plus armor, helmets, shields, miscellaneous components and cannon equipment.
- Major weapon groups include bladed weapons, axes, bashing weapons, polearms and throwing weapons.
- The supplied viking sword, katana, rapier, halberd and decorative shield artwork now has usable equipment and blacksmith crafting integration, with dagger combat and durability repaired. See [the asset inventory and verification guide](docs/new-assets/PATCH18_WEAPON_INTEGRATION.md).
- Crafting is performed with a blacksmith's hammer near an anvil, using the selected supported metal and the recipe's other ingredients.
- Skill requirements and success chances are enforced by the server. Difficult work can fail, while sufficiently skilled smiths can produce exceptional results.
- Crafted equipment records material, quality, maker and city provenance where the recipe supports them.
- Learned recipes, race restrictions and gender restrictions are honored where the catalogue calls for them.
- The same interface now supports crafting, repairs and smelting down eligible work. Repairing consumes matching material; smelting recovers part of the original material.

Together with purity ore, bronze alloying and the expanded resource ladder, this makes gathering and smithing parts of one economic journey.

## Banking, Guilds & Skill Training

Bankers now open a complete, connected banking interface.

- Store eligible items in a paged Bank Box measured by total weight, including the contents of nested containers.
- Drag an item from the Bank Box to your pack to withdraw it, or drag an eligible inventory item to the vault to deposit it.
- View account balances for gold, silver and copper.
- Deposit all carried coins in one action or withdraw currency in the denomination you need.
- Create cheques, fund them from account value and cash bank cheques from the vault.
- Clear feedback explains full packs, ineligible items, capacity limits and failed transactions.

Bank transfers are designed to survive disconnects, retries and server restarts without duplicating or silently losing the item being moved.

Guildmasters are now part of the same service-NPC framework. A Guildmaster teaches only the skills assigned to that guild by the live server catalogue. Players can review a lesson quote and buy skill training with gold up to the Ultima Online-style Guildmaster ceiling of 40.0, or the skill's own lower cap.

Patch 18 also introduces an animated training dummy. Striking it with fists or a supported weapon trains the matching combat skill—Wrestling, Fencing, Swordsmanship or Mace Fighting—up to 25.0, with occasional Tactics practice and a short cooldown between valid training strikes.

## Housing & Building

Housing has been rebuilt around larger designs, survival ownership and persistent regions.

- The Architect can now offer 10 implemented deed styles through the ordinary city economy: six small-house styles, the two-story villa, large patio, stone keep and castle.
- The two-story villa, large patio and stone keep are newly integrated large-house designs with structure-aware entrances and footprints.
- The six small house structures were rebuilt alongside the larger exports so their foundations and floor behavior follow the same rules.
- House owners can build and remodel inside their own region while staying in their normal game mode. They do not receive creative inventory, flight or permission to edit another player's home.
- Permanent perimeter foundations and house infrastructure remain protected. Interior floor foundations can be removed where owners need access for a basement.
- Newly recorded house regions are restored after a server restart, preserving ownership checks, door-lock identity, farm plots and decoration permissions.
- Changing a house to private locks its doors and issues a key; returning it to public unlocks the doors and removes the key.
- A locked house door now stays locked even when redstone is powered. Once unlocked, normal redstone behavior resumes.

Architect inventory, price and availability come from the live city economy, so a deed can be implemented without being in stock in every city at every moment.

## Cities, NPCs & Economy

Patch 18 moves more of city life onto the shared world economy.

- Economic vendors use live city prices and stock. A purchase is rechecked at the moment of sale, paid in the correct coin value and stamped with material, quality and city provenance when applicable.
- Traders use buyback quotes and purchase only commodities the server currently says they accept. Missing policy fails safely instead of guessing.
- Trader sales and economic purchases use durable transaction boundaries to prevent a crash or retry from duplicating goods or payment.
- Architects, ordinary vendors and service NPCs share persistent, server-directed posts rather than competing local spawn systems.
- Town populations can grow, shrink and shift toward the targets assigned to their regions, with bounded background maintenance.
- Service NPC identities and assignments survive restarts and reconcile with the authoritative server state.

Two new animated bird families make the cities feel more alive:

- White and scarlet ibis flock within Jhelom under their own city-wide population limit.
- Pink, rose and white flamingos join the ambient city wildlife pool.

Parrots are now fully protected from damage and cannot be killed.

## Decoration, Furniture & Craft Stations

### Grabby Hands

Thirty-five furniture and container block types can now be treated as movable possessions. The supported set includes chairs, stools, benches, thrones, tables, counters, wine bottles, lights, chests, armoires, drawers and all three crate sizes.

- Sneak-right-click with both hands empty to lift an eligible player-placed object.
- Both hands must be empty. If only your off hand is holding something, Grabby Hands now says so instead of doing nothing.
- Place it again from the item in your hand, including stacking compatible furniture.
- Container state and contents travel with supported chests, armoires, drawers and crates.
- Locked, protected, open or unsafe containers are refused instead of losing their state.
- If your inventory is full, the object remains in the world.
- Using an axe on an eligible object opens a destruction confirmation rather than destroying it immediately.
- Thirty loose goods—including foods, harvests, reagents, leather and musical instruments—can also be placed physically in the world.

House ownership and city protection still apply. Grabby Hands does not grant general building or breaking permission.

### Crate stacking

Storage crates can now be stacked into compact columns rather than sitting side by side.

- Small and medium crates stack into a compact column that keeps each crate separately openable.
- A single crate can be broken out of, or carried out of, a column without disturbing the ones above it.
- Large crates stack on other large crates and seat themselves on the lid below.
- Compact columns can stand on a large crate, and a stack whose foundation is removed settles instead of floating.
- Grabby Hands names and carries a stacked crate correctly, and a swing is judged by the crate you were actually aiming at.

### Textile processing

- Spinning wheels turn wool into yarn and cotton or flax into thread.
- Looms turn five yarn or thread items into folded cloth.
- Successful use animates the spinning wheel, and full inventories safely drop the completed output rather than losing it.

### Banners and dyeing

Patch 18 includes a data-driven banner system with 35 completed designs.

- Designs range from one-block standards to one-by-two pennons and two-by-two curtains.
- Banners support brass and iron mounts, wall-parallel or wall-perpendicular presentation where authored, layered item rendering and persistent placed rendering.
- Multi-block placement validates the entire footprint and previews the result before committing it.
- A dye tub can hold a pigment, preview that pigment against the banner's fabric palette and apply the selected result through a server-confirmed transaction.
- Four fabric palettes and six common pigment families support different resolved colors. An additional ice-blue entry remains a special development pigment rather than a normal common dye.

Patch 18 intentionally does not add survival crafting recipes for banners. Initial banner distribution is controlled through server content and world-building tools.

### New world-building content

Patch 18 adds or completes a wide collection of registered decorative and utility pieces, including:

- animated fountains, globes, kettles, mugs, table settings, dress forms, cloth bolts and folded cloth;
- scarecrows, looms, spinning wheels and animated training dummies;
- small, medium and large storage crates;
- connected display cases and a fully connecting wooden fence family;
- water wells that can fill supported pitchers and large adventure ladders that are genuinely climbable;
- merchant carts in seven colors and multiblock market stalls in four colors;
- refreshed one-block city moongate presentation;
- eight Virtue shrine variants plus Chaos for world builders, with protected multi-block placement and decorator-tool cycling.

Large decorative structures place as complete footprints: if the required space is not valid, the placement is refused instead of leaving half a model behind.

## World & Visual Improvements

- A complete sandstone architecture family adds regular and ornate walls, posts and windows alongside block walls, columns and battlements.
- The villa construction set gains half plaster walls, adaptive corners and junctions, bannisters, floors, ceilings, slabs, roofs and improved windows.
- Ordinary stone now draws from 32 deterministic texture variants, reducing obvious repetition without changing the identity of the block.
- Vanilla sand receives similar terrain variation.
- A standalone flagstone block selects from multiple textures and can be cycled with the interior decorator tool.
- A unified stone roof family adds slate, sandstone and limestone roofs that share one adaptive shape system and can be cycled with the interior decorator tool.
- Light and dark sandstone pavers add randomized paving variants for streets, courtyards and plazas.
- Foundation bricks gain more varied side faces.
- Ceiling stalactites expand cave-building options and complement the existing floor formations.
- Managed grass, ferns and flowers can gradually grow above grass blocks, mature and regrow after being cut with an appropriate blade.
- Swamps receive a subtle green fog tint and can grow Blood Moss as part of their managed vegetation palette.
- Sulphurous Ash patches can appear near lava, while black-lipped oysters can appear on limestone near water and yield black pearls when harvested with a dagger.
- The title screen now carries UltimaCraft branding, a Version 18 subtitle, a direct website button, curated splash text, dark loading/menu backgrounds and an UltimaCraft menu theme.

## Keepsakes & Blessed Items

Some items are meant to survive the things that normally destroy an item.

- Blessed items are rescued from despawning, lava, fire and the void instead of being lost.
- The trash barrel no longer silently deletes a blessed item.
- Whether a blessed item has been delivered is decided by the server's record rather than by whether it happens to be in your inventory, and deliveries carry a durable receipt so a failure can be reconciled.
- The Starfarer's Medallion is a commemorative keepsake with no recipe and no gameplay effect, bestowed on travelers who answered a call from beyond the skies of Britannia.

## Quality of Life & Fixes

- Quest escorts now survive a cold or delayed journal load.
- Refused quest actions explain why they were refused.
- Quest turn-ins have a bounded retry path, and a failed login bootstrap no longer disables the quest system for the session.
- Objective completion is decided by the server, and quest givers are tracked by stable identity rather than display name.
- The Skills screen is scrollable, and routine skill-gain messages are heavily reduced to keep chat readable.
- Banking, NPC synchronization, town population work and legacy spawn checks no longer wait on external services from the server tick.
- Repeated timeout logging was contained so an external-service failure cannot flood the console until players are kicked.
- City population rules no longer remove service NPCs or ambient creatures that another city system is responsible for.
- Failed portrait downloads now report the real failure instead of a misleading generic result.
- Fountain water animation and rendering have been restored.
- Window collisions now align more closely with their visible geometry.
- Wooden fences, roofs, plaster pieces, bannisters and sandstone shapes received connection, collision and texture corrections.

## Behind the World

Many of Patch 18's largest improvements are about making the Minecraft world and the UltimaCraft server agree.

Bank balances, vendor stock, Guildmaster rosters, city staffing, house records and managed deposit locations are server-owned facts. The mod now treats them that way while performing visible world changes safely on the Minecraft server. Requests are authenticated, network work is kept away from the game tick, and important item or currency movements are recorded so they can be reconciled after a failure.

This foundation is what allows the new systems to feel persistent: a bank transfer, a house region, an NPC assignment or a resource deposit is no longer just a temporary local event.

Patch 18 also completes host configuration parity. A server operator now sets the API base URL and the shard identity through the documented environment variables or the server properties file, and the mod uses exactly what was configured. Previously an environment-configured host could fall back to a compiled-in default, which meant it talked to the wrong place and no operator setting could correct it. Shard identity now travels with the credentials that authenticate the request, so a record cannot be written under one shard name while being authenticated as another.

WorldEditCUI joins the client mods the server recognizes, alongside the existing WorldEdit, Sodium, Iris and Freecam entries.

### Verification status at time of writing

Patch 18's automated coverage is green: the full unit suite and the dedicated-server GameTest suite both pass on the release candidate. Some systems are proven by automated coverage but still awaiting confirmation on a live server with real clients - crate stacking and destruction, the fertile-dirt survival loop, blessed-item handling and the Starfarer's Medallion, and market-stall visuals. The unified stone roof family has already been confirmed on a live dedicated server with shaders enabled.

# Marketing Content Index

This index is intended as source material for announcements, feature pages, trailers and social campaigns. Availability claims remain bounded by the final Patch 18 implementation.

### Farming as a profession

**Feature:** 67 crop definitions, skill-gated seed knowledge, soil care, climates, specialized harvest tools, tall crops, trellises, orchards and perennial grape arbors.

**Player fantasy / benefit:** Become a knowledgeable Britannian farmer whose skill and choice of land matter as much as the seed.

**What is visually demonstrable:** Fields at different growth stages, tall corn and bananas, grape arbors, trellis crops, orchards, seed tooltips and tool-specific harvesting.

**Potential screenshot or video subject:** A farm tour moving from beginner vegetables through grains and rare reagents to a mature vineyard.

**Potential marketing angle:** “UltimaCraft turns a Minecraft farm into an RPG profession.”

### Floriculture and rare colors

**Feature:** Seven flower species with 23 persistent colors, weighted rarities and environmental preferences.

**Player fantasy / benefit:** Cultivate rare, recognizable flowers instead of collecting interchangeable decoration blocks.

**What is visually demonstrable:** Color-varied flower beds, rare palette outcomes, flowers maturing in different climates and house plots retaining their assignments.

**Potential screenshot or video subject:** A formal garden arranged by species and rarity, ending on a rare orfluer color reveal.

**Potential marketing angle:** “Grow a garden whose rarest colors have to be earned.”

### Managed mining deposits

**Feature:** A 29-resource managed catalogue with Mining progression, server-curated deposit locations, distinct vein shapes, purity or grade and restoration.

**Player fantasy / benefit:** Prospect for meaningful deposits and master a resource profession instead of strip-mining disposable vanilla ore.

**What is visually demonstrable:** Layered coal, vertical silver, clustered copper, a gold snake, an agapite geode, silica and clay beds, purity ore and restored cells.

**Potential screenshot or video subject:** A miner following a shaped vein, returning later to a restored working and bringing purity ore to a forge.

**Potential marketing angle:** “Minecraft geology rebuilt for a persistent MMO economy.”

### Blacksmithing and bronze

**Feature:** 204 craftable catalogue entries, 121 weapons, skill-based success, exceptional results, repair, smelting and bronze alloying.

**Player fantasy / benefit:** Build a reputation as a smith whose material, skill, maker identity and city follow the equipment they create.

**What is visually demonstrable:** Forge and anvil work, material selection, the category interface, finished weapon families, exceptional quality and bronze production.

**Potential screenshot or video subject:** Ore-to-weapon sequence: mine copper and tin, alloy bronze, choose a recipe, craft and inspect the finished maker-marked item.

**Potential marketing angle:** “From the vein to the blade, one connected crafting economy.”

### A real Britannian bank

**Feature:** Weight-based item storage, balances, coin deposits and withdrawals, cheques, cheque cashing and drag-driven vault transfers.

**Player fantasy / benefit:** Use a persistent RPG bank rather than hiding every valuable in local chests.

**What is visually demonstrable:** Banker interaction, the strongbox interface, item icons, dragging between pack and vault, denomination buttons and a written cheque.

**Potential screenshot or video subject:** Deposit a valuable container, deposit all coins, write a cheque and cash it from the Bank Box.

**Potential marketing angle:** “Ultima Online-style banking, built directly into Minecraft.”

### Guildmasters and combat training

**Feature:** Live guild-specific skill lessons up to 40.0 and animated training dummies for early combat practice up to 25.0.

**Player fantasy / benefit:** Seek out a guild teacher or train in the yard before risking a real fight.

**What is visually demonstrable:** Named Guildmaster NPCs, skill quotes, gold payment, dummy hit animation and different weapon disciplines.

**Potential screenshot or video subject:** A new character trains first on a dummy, then visits the appropriate Guildmaster for a paid lesson.

**Potential marketing angle:** “Your character learns from Britannia—not from a generic XP menu.”

### Expanded housing

**Feature:** Ten implemented Architect deeds, three newly integrated large homes, survival-mode owner remodeling, persistent regions, privacy and dependable locks.

**Player fantasy / benefit:** Purchase a real home in the city economy and make its interior your own without stepping outside the RPG rules.

**What is visually demonstrable:** The villa, patio and keep exteriors; Architect inventory; basement remodeling; public/private transition; locked doors resisting redstone.

**Potential screenshot or video subject:** A three-home showcase followed by an owner furnishing a room and opening a basement.

**Potential marketing angle:** “Britannian property ownership grows from cottage to keep.”

### Grabby Hands and physical interiors

**Feature:** Thirty-five movable object types, content-preserving containers and 30 loose goods that can be displayed in the world.

**Player fantasy / benefit:** Treat the contents of a home, shop or tavern as possessions to arrange rather than permanent map geometry.

**What is visually demonstrable:** Lifting furniture, carrying a filled crate, stacking chairs, placing food and instruments, and axe-destruction confirmation.

**Potential screenshot or video subject:** Time-lapse an empty house into a lived-in tavern using moved furniture and physically placed goods.

**Potential marketing angle:** “Build less like a block grid; decorate more like a world.”

### Living cities and economy

**Feature:** Live-priced vendors, accepted-commodity buyback, server-directed NPC posts, population-managed townspeople, Guildmasters, bankers, ibis and flamingos.

**Player fantasy / benefit:** Visit cities whose people, markets and wildlife are parts of a persistent world rather than static props.

**What is visually demonstrable:** Busy vendor streets, changing stock, traders buying resources, service NPC nameplates, Jhelom ibis flocks and colored flamingos.

**Potential screenshot or video subject:** A day-in-the-city sequence from market sale to bank visit, Guildmaster lesson and Jhelom waterfront wildlife.

**Potential marketing angle:** “UltimaCraft's cities are becoming systems, not scenery.”

### Banners, textiles and world building

**Feature:** 35 completed banner designs, material-aware dyeing, multi-block placement, spinning, weaving, connected display cases, carts, stalls and utility structures.

**Player fantasy / benefit:** Give a guild hall, market, castle or home a recognizable visual identity.

**What is visually demonstrable:** Dye preview, pennons and large curtains, spinning-wheel animation, a market street, connected cases and colored carts or stalls.

**Potential screenshot or video subject:** Spin and weave cloth, preview a banner color, then reveal a decorated guild hall and market square.

**Potential marketing angle:** “Patch 18 gives Britannia the tools to look inhabited.” Banner acquisition should be described as server/content-controlled, not survival crafting.

### A more atmospheric Britannia

**Feature:** Managed vegetation, wild ash and oysters, Jhelom ibis, flamingos, stone and sand variation, sandstone architecture, cave formations and full client branding.

**Player fantasy / benefit:** Explore a world with regional resources, wildlife and visual texture that belongs specifically to UltimaCraft.

**What is visually demonstrable:** Regrowing roadside plants, lava-side ash, a limestone oyster bed, bird variants, non-repeating terrain, sandstone streets and the Version 18 menu.

**Potential screenshot or video subject:** A cinematic journey from the branded menu through varied countryside, a cave, a swamp and a populated city.

**Potential marketing angle:** “Patch 18 makes Britannia recognizable before a player opens a menu.”

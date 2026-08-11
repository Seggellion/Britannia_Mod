# Vendor/Trader Economy — OPEN_QUESTIONS

Unresolved items that cannot be answered confidently from source. Nothing here is silently treated
as decided.

1. **Economic NPC types: extend `ServiceNpcType` or a sibling table?** `ServiceNpcType` requires a
   `default_service_dialogue_set` and validates guildmaster/skill coherence; vendors/traders need
   catalogs and valuation strategies instead. Options: (a) add a `kind` (service|vendor|trader) and
   relax dialogue requirements per kind; (b) a new `economic_npc_types` table sharing
   `ServiceNpcSpawnPoint`/`NpcSpawnAssignment`/`WorldNpc` (spawn point's `service_npc_type` FK would
   need generalizing). Milestone 3 design decision; both avoid a second identity system.

2. **Shard scoping of requirements.** `service_npc_types.minimum_city_supplies` is per type,
   global across shards. The playbook requires per-shard configurability. Does the owner want a
   shard-override table for *service* NPCs too, or only for the new economic types?

3. **Treasury coupling direction.** Trader payouts must debit a treasury; today's
   `currency_breakdown` grants coins without any debit. Which balance field should fund NPC
   purchases — `coins_outstanding` (symmetric with `GuildTraining::Purchase#credit_treasury` and
   with what `City#gold_amount` reads) or `reserve`? The Guildmaster owner note ("training gold
   lands in coins_outstanding") suggests coins_outstanding, but a *debit* below zero must be
   impossible — needs an owner-approved failure semantics (reject sale vs partial payout).

4. **Recipe authority for valuation.** Minecraft is the only recipe authority
   (`economy/MerchantRecipes` for merchants, `skill/crafting/CraftableRegistry` + data recipes for
   crafting). For Rails to price recipe-derived products without trusting the client, recipe
   compositions must be exported/seeded to Rails (initial recommendation), or quotes must carry
   client-claimed compositions (rejected by playbook's no-client-trust rules?). Decide the export
   format and the sync/versioning story in Milestones 2–3.

5. **Player payout representation.** Trader sales currently pay players twice-over in
   representation: Rails credits `shard_user.currency` (JSON ledger) AND Minecraft grants physical
   coin items from the response. Banking treats physical coins + bank accounts as the real economy.
   Which representation is authoritative for the new flows, and should the shard_user ledger credit
   be retired?

6. **"One tier below maximum" quality.** Three coexisting scales: blacksmithing writes 1|2
   (Normal|Exceptional, `skill/BlacksmithCrafting.java:111`); `QualitySwordItem.getQualityName`
   names 1–4 (Crude/Basic/Fine/Exceptional); crops use 0–100. On the metal-goods scale actually in
   use (1|2), "one below max" = Normal, which defeats the playbook's "high quality" intent. Owner
   must pick the canonical ladder for NPC-produced goods (e.g. adopt the 1–4 ladder and use 3
   "Fine", or extend blacksmithing's scale).

7. **"Highest eligible material" rule.** `UOMetalToolMaterial` has no explicit tier ranking
   (enum order is not sorted by strength; gold sits between iron and shadow_iron). What defines
   "highest": the RunUO ore ladder (iron→…→valorite), durability, or a new explicit rank column in
   Rails commodity/material data?

8. **Commodity saleability flag.** `city_commodities` has no saleable/eligibility column; the
   playbook requires "commodity sale eligibility". New column vs policy table vs derived from
   category — Rails-side design, Milestone 3.

9. **Raw vs processed classification.** Currently subcategory conventions (`raw`, `logs`,
   `whole`/`milled`, `ingot`). Is a convention list acceptable as the Rails-configurable policy
   input, or should commodities gain an explicit `form` (raw|processed) column so shard multipliers
   can bind to data rather than string conventions?

10. **Reconciliation trigger for economic population.** `CityStaffing::Reconcile` is admin-triggered
    only. Economic NPCs reacting to commodity/treasury changes need a trigger (scheduled job,
    after-commit hook on threshold crossings, or explicit admin action only?). Also whether the
    no-hysteresis oscillation behavior (documented in `EconomicEligibility`) is acceptable for
    vendors, which are more visible than Guildmasters.

11. **Alcohol/wine/salvage legacy branch.** These roles bypass `SaleTransactionProcessor`
    (wine/salvage-specific persistence in the controller). The new Trader architecture must either
    absorb wine quality valuation (`CityCommodity#calculate_wine_value`) and salvage
    material/quality valuation into the strategy registry, or keep the legacy branch alive during
    migration. Which wins, and when?

12. **Townsperson side-spawning.** Legacy Trader/Merchant blocks also maintain `townPersonAmount`
    ambient townspeople under the same sourceId. Where does that responsibility live after
    migration (separate ambient-population post? Rails-staffed? dropped)?

13. **Rails test environment repair.** All `ultimacraft_test*` databases are owned by role
    `ultimacraft`, so the canonical `bin/codex_test` flow is broken for every checkout (see
    PROJECT_FACTS §2). Requires the user/local admin to drop or re-own them; until then no Rails
    baseline can be recorded and Milestone 3+ Rails work is blocked by AGENTS.md's own rule.

14. **RunUO catalog completeness.** `runuo_vendor_catalog.json` is safety-filtered (11 vendors /
    12 catalogs; restricted rows omitted). Milestone 2 must audit the pinned RunUO tree
    (`runuo/runuo` @ `71b2794f`) directly — confirm the pinned source is available locally or must
    be fetched, and where it lives.

15. **Merchant price acceptance.** Rails accepts client-submitted merchant line prices
    (`merchant_purchase_processor.rb` `submitted_line_price` first). The target says Rails-priced
    only. Is a compatibility window needed for old clients during migration, or is a hard cutover
    acceptable on this private shard?

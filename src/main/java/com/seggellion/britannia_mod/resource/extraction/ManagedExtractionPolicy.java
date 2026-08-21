package com.seggellion.britannia_mod.resource.extraction;

import com.seggellion.britannia_mod.resource.ResourceDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Who is allowed to turn a managed geological resource into economy, and what a success costs.
 *
 * <h2>Why this is one class and not twenty-eight data fields</h2>
 * Every managed economic resource in the catalogue answers these questions identically: Fortune
 * buys nothing, Silk Touch buys nothing, a fake player is refused, a non-player is refused, and a
 * creative player is not extracting at all. Writing that out per resource would produce
 * twenty-eight copies of one sentence and twenty-eight chances to get one of them wrong — and the
 * first resource that genuinely differed would still need a field. So the policy is central, the
 * resource definitions stay as they are, and a field gets added when a resource actually varies.
 *
 * <p>What stays in the resource definition is what genuinely varies today: which item tag may work
 * it, what it yields, what it leaves behind, how long it takes to come back. Those are per
 * resource because they really are per resource. Who may swing the tool is not.
 *
 * <h2>Enchantments</h2>
 * There is deliberately no enchantment code here, and that is the policy rather than an omission.
 * Managed extraction never consults a loot table: the yield is whatever the resource definition
 * says, produced by the extraction service directly, and the vanilla break is cancelled before it
 * can drop anything. Fortune has nothing to multiply and Silk Touch has nothing to substitute.
 * Adding an explicit "ignore Fortune" branch would imply there was a path where it applied.
 *
 * <p>The invariant that matters — a managed deposit block can never become a portable item — holds
 * for a stronger reason than policy: the blocks have no loot table and no block item at all. See
 * {@code ManagedDepositBlock}.
 *
 * <h2>Relationship to the Mining gate</h2>
 * {@code MiningBreakGate} asks a larger question — skill, tools, provenance, feedback — and its
 * actor typing is this class's. That keeps one definition of "fake player" and one definition of
 * "creative" for both resource families, so the sediment beds and the ore ladder cannot drift into
 * disagreeing about who a fake player is.
 */
public final class ManagedExtractionPolicy {

    private ManagedExtractionPolicy() {
    }

    /** What kind of thing is holding the tool. */
    public enum Actor {
        /** A real person's server-side player. */
        PLAYER,
        /** NeoForge's stand-in for a machine, a mod, or anything automating a player action. */
        FAKE_PLAYER,
        /** No player at all: a dispenser, an explosion, a command block, worldgen. */
        NON_PLAYER
    }

    /** Whether this actor may perform an economic extraction, and if not, why. */
    public enum Verdict {
        ALLOWED,
        /** Automation does not earn. No resource definition opts into this today. */
        DENIED_FAKE_PLAYER,
        /** Nothing that is not a player reaches the economy through a generic break path. */
        DENIED_NON_PLAYER,
        /**
         * Creative is administration, not mining. The break is still allowed to happen — that is
         * the established bypass — but no economy is produced by it.
         */
        DENIED_CREATIVE;

        public boolean allowed() {
            return this == ALLOWED;
        }
    }

    /**
     * Type the actor.
     *
     * <p>{@link FakePlayer} is NeoForge's own marker and the only reliable one: fake players are
     * real {@code ServerPlayer} instances, so every check that stops at "is this a ServerPlayer"
     * lets automation straight through. That is precisely how the sediment beds were reachable by
     * a machine before this milestone.
     */
    public static Actor actorOf(@Nullable Player actor) {
        if (!(actor instanceof ServerPlayer serverPlayer)) {
            return Actor.NON_PLAYER;
        }
        return serverPlayer instanceof FakePlayer ? Actor.FAKE_PLAYER : Actor.PLAYER;
    }

    /**
     * The server's own game mode, never {@link Player#isCreative()}.
     *
     * <p>{@code isCreative()} is a derived view that can be overridden, and GameTest's mock players
     * hard-code it to true whatever mode they are actually in; {@code gameMode.getGameModeForPlayer()}
     * is the same state the vanilla break pipeline consults. {@code MiningBreakGate} and
     * {@code StructureProtectionHandler} both already take this position.
     */
    public static boolean isCreativeGameMode(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    /**
     * May this actor commit an economic extraction?
     *
     * <p>Order matters only in one place: a fake player is typed before its game mode is read, so
     * nothing here depends on how NeoForge happens to initialise a fake player's mode.
     */
    public static Verdict evaluate(@Nullable Player actor) {
        Actor type = actorOf(actor);
        if (type != Actor.PLAYER) {
            return decide(type, false);
        }
        return decide(type, isCreativeGameMode((ServerPlayer) actor));
    }

    /**
     * The decision itself, over nothing but the two facts it depends on.
     *
     * <p>Separated from {@link #evaluate} for the same reason {@code MiningBreakGate} separates its
     * core: the rule can then be driven by a plain unit test, with no server, no level and no
     * player, and the part that needs Minecraft is reduced to reading two facts off an actor.
     */
    public static Verdict decide(Actor type, boolean creative) {
        return switch (type) {
            case NON_PLAYER -> Verdict.DENIED_NON_PLAYER;
            case FAKE_PLAYER -> Verdict.DENIED_FAKE_PLAYER;
            case PLAYER -> creative ? Verdict.DENIED_CREATIVE : Verdict.ALLOWED;
        };
    }

    /**
     * Whether this managed block is a <em>deposit cell</em> — something that exists because the
     * economy put it there — rather than ambient terrain that happens to be mineable.
     *
     * <p>The distinction only matters for one rule, and it matters a lot for it: a creative break
     * of a deposit cell is refused, so an operator cannot delete a vein by clicking it. Applying
     * that to ambient rock instead would mean a creative builder could not break
     * {@code minecraft:stone}, {@code granite} or {@code deepslate} anywhere in the world, which
     * would make terraforming and the housing systems unusable. Stone is not a deposit; it is the
     * crust.
     *
     * <p>The line is drawn from the data rather than from a list of names:
     * <ul>
     *   <li>ORE and SEDIMENT are deposits by definition — an ore block or a bed is placed, never
     *       ambient, and every one of them is economy somebody sited deliberately.</li>
     *   <li>STONE is ambient <em>when the block is vanilla's</em>. A mod-owned stone block —
     *       {@code britannia_mod:sandstone_deposit}, the four bespoke rocks — only exists where
     *       this mod put it, so it is a deposit cell too.</li>
     * </ul>
     *
     * <p>Player-placed blocks are not this method's business: provenance answers that separately,
     * and a builder may always remove their own construction.
     */
    public static boolean isDepositCell(ResourceDefinition definition, BlockState state) {
        if (definition.family() != ResourceDefinition.Family.STONE) {
            return true;
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .getNamespace()
                .equals(com.seggellion.britannia_mod.BritanniaMod.MODID);
    }

    /** Convenience for the break handlers: exactly the actors that may produce economy. */
    public static boolean mayExtract(@Nullable Player actor) {
        return evaluate(actor).allowed();
    }

    /**
     * Charge one successful extraction to the tool, once.
     *
     * <p>"Once" means one call to {@link ItemStack#hurtAndBreak}, which is Minecraft's ordinary
     * durability path — so Unbreaking applies exactly as it does everywhere else, an item with no
     * durability is untouched, and a tool that runs out breaks with its usual effects. It is
     * deliberately not a forced decrement past the enchantment: the project already charges managed
     * harvests this way in {@code ManagedVegetationService}, and having geology disagree with
     * vegetation about what Unbreaking means would be a worse outcome than either rule alone.
     *
     * <p>Call this only after the extraction has committed. Every refusal — wrong tool, too little
     * skill, protected ground, denied actor, a duplicate event that did not commit — costs nothing,
     * because the tool never did any work.
     */
    public static void chargeExtractionTool(ServerPlayer player) {
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }
        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }
}

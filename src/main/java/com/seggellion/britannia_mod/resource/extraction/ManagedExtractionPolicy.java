package com.seggellion.britannia_mod.resource.extraction;

import com.seggellion.britannia_mod.registry.ToolRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Who is allowed to turn a managed geological resource into economy, and what a success costs.
 *
 * <h2>Why this is one class and not twenty-eight data fields</h2>
 * Every managed economic resource in the catalogue answers these questions identically: Fortune
 * buys nothing, Silk Touch buys nothing, a fake player is refused, a non-player is refused, and a
 * creative player is not extracting at all unless they are deliberately testing. Writing that out
 * per resource would produce twenty-eight copies of one sentence and twenty-eight chances to get
 * one of them wrong — and the first resource that genuinely differed would still need a field. So
 * the policy is central, the resource definitions stay as they are, and a field gets added when a
 * resource actually varies.
 *
 * <p>What stays in the resource definition is what genuinely varies today: which item tag may work
 * it, what it yields, what it leaves behind, how long it takes to come back. Those are per
 * resource because they really are per resource. Who may swing the tool is not.
 *
 * <h2>Creative</h2>
 * A creative player is an administrator, and administration is not mining. Every managed
 * destructive path — the Mining gate, the deposit handler, the yield handler — <em>stands
 * aside</em> for a creative player: no tool question, no skill question, no denial, no yield, no
 * depletion, no restoration debt, no Mining award. The block then breaks exactly as any other
 * block breaks in creative, which is what a builder cutting a cellar through catalogued stone, or
 * clearing a misplaced vein, expects.
 *
 * <p>The one exception is the Britannia pickaxe — {@code britannia_mod:pickaxe}, by registered
 * identity — in the attacking hand. A creative player holding it is a tester: every rule then
 * applies to them exactly as it applies to a survival miner, so the whole managed flow (tool,
 * skill, roll, yield, depletion, restoration, award) can be exercised without leaving creative.
 * The exception is deliberately narrow. It is the registered item, not a tag, not a class and not
 * a name, so a vanilla pickaxe, a future pickaxe added to {@code #britannia_mod:mining_pickaxes},
 * or a Britannia pickaxe in the offhand or the inventory keeps the ordinary creative answer.
 * {@link #bypassesManagedExtraction} is the one place that decision is made; the handlers only
 * ask it.
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
 * actor typing and its creative rule are this class's. That keeps one definition of "fake player"
 * and one definition of "creative bypass" for both resource families, so the sediment beds and
 * the ore ladder cannot drift into disagreeing about who a fake player or a tester is.
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
         * Creative without the Britannia pickaxe is administration, not mining. The break is still
         * allowed to happen — every managed path stands aside for it — but no economy is produced
         * by it. A creative player attacking with the Britannia pickaxe is a tester and is
         * {@link #ALLOWED} like any survival miner.
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

    /* ------------------------------------------------------------------ */
    /*  The creative bypass                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Whether this stack is the Britannia pickaxe: the registered {@code britannia_mod:pickaxe}
     * item itself, by identity.
     *
     * <p>Not the {@code #britannia_mod:mining_pickaxes} tag, which is the data-driven answer to a
     * different question ("may this stack work that resource") and may legitimately grow; not a
     * class check, which would admit anything that extends it; and not a name. The testing
     * exception is meant to be exactly one item wide.
     */
    public static boolean isBritanniaPickaxe(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ToolRegistry.PICKAXE.get());
    }

    /**
     * Whether the player is attacking with the Britannia pickaxe.
     *
     * <p>Block breaking is always a main-hand action — the vanilla break pipeline reads
     * {@code getMainHandItem()} and nothing else — so the attacking hand is the main hand. A
     * Britannia pickaxe in the offhand or anywhere in the inventory does not count.
     */
    public static boolean attacksWithBritanniaPickaxe(ServerPlayer player) {
        return isBritanniaPickaxe(player.getMainHandItem());
    }

    /**
     * The creative rule itself, over nothing but the two facts it depends on: in creative and
     * not attacking with the Britannia pickaxe means every managed extraction path stands aside.
     *
     * <p>Pure, so the truth table is unit-testable without a player, and shared by
     * {@link #bypassesManagedExtraction} and {@code MiningBreakGate}'s decision core so that the
     * handlers and the gate can never hold two opinions of who is bypassing.
     */
    public static boolean creativeBypasses(boolean creative, boolean attacksWithBritanniaPickaxe) {
        return creative && !attacksWithBritanniaPickaxe;
    }

    /**
     * Whether every managed extraction path must stand aside for this player right now, judged
     * from live server state: the server's game mode and the server's copy of the main hand.
     *
     * <p>Read fresh at every point it is asked — the first swing, the completed break, the yield
     * — rather than remembered, so a game-mode or held-item change between the two moments is
     * seen. A creative break completes inside its first swing anyway, but a survival dig that
     * finishes after its digger switched to creative is judged by what they are at completion.
     *
     * <p>"Stand aside" means exactly that: the handler returns without cancelling, without
     * feedback and without touching the world, and the ordinary vanilla creative break proceeds.
     * It is not a refusal. A stack the caller was explicitly asked about can be judged instead
     * through {@link #bypassesManagedExtraction(ServerPlayer, ItemStack)}.
     */
    public static boolean bypassesManagedExtraction(ServerPlayer player) {
        return bypassesManagedExtraction(player, player.getMainHandItem());
    }

    /** The same question about the stack the caller named, for services that take the tool. */
    public static boolean bypassesManagedExtraction(ServerPlayer player, @Nullable ItemStack attackingWith) {
        return creativeBypasses(isCreativeGameMode(player), isBritanniaPickaxe(attackingWith));
    }

    /* ------------------------------------------------------------------ */
    /*  Who earns                                                          */
    /* ------------------------------------------------------------------ */

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
        return decide(type, bypassesManagedExtraction((ServerPlayer) actor));
    }

    /**
     * The decision itself, over nothing but the two facts it depends on: what the actor is, and
     * whether the creative bypass is in effect for it ({@link #creativeBypasses}).
     *
     * <p>Separated from {@link #evaluate} for the same reason {@code MiningBreakGate} separates its
     * core: the rule can then be driven by a plain unit test, with no server, no level and no
     * player, and the part that needs Minecraft is reduced to reading the facts off an actor.
     */
    public static Verdict decide(Actor type, boolean creativeBypass) {
        return switch (type) {
            case NON_PLAYER -> Verdict.DENIED_NON_PLAYER;
            case FAKE_PLAYER -> Verdict.DENIED_FAKE_PLAYER;
            case PLAYER -> creativeBypass ? Verdict.DENIED_CREATIVE : Verdict.ALLOWED;
        };
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
     * vegetation about what Unbreaking means would be a worse outcome than either rule alone. The
     * same ordinary path is why a creative tester's pickaxe never wears: vanilla charges nothing
     * to a player with infinite materials, and nothing here overrides that.
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

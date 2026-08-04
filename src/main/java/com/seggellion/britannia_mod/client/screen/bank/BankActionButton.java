package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.DialoguePresentation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Milestone 3: a right-hand action button that can also be <i>pending</i>.
 *
 * <p>Vanilla {@link Button} already draws normal, hover, keyboard focus, pressed and disabled --
 * five of the six states the playbook requires. The sixth is pending, and it is genuinely
 * different from disabled: disabled means "you cannot do this", pending means "you already did
 * this and the server has not answered". Rendering them identically is how a player ends up
 * clicking twice.
 *
 * <p>They are distinguished two ways, neither of them colour (design §16): the label swaps to a
 * caller-supplied pending label, and a small marker is drawn at the leading edge. {@code
 * BankScreen} did the label half of this ("Depositing..."); this keeps that and adds the
 * marker, which survives translation into a language where the two labels happen to look alike.
 */
public final class BankActionButton extends Button {

    private final Component idleLabel;
    private final Component pendingLabel;
    private boolean pending;

    private BankActionButton(
            int x, int y, int width, int height,
            Component idleLabel, Component pendingLabel,
            OnPress onPress
    ) {
        super(x, y, width, height, DialoguePresentation.text(idleLabel), onPress, DEFAULT_NARRATION);
        this.idleLabel = Objects.requireNonNull(idleLabel, "idleLabel");
        this.pendingLabel = Objects.requireNonNull(pendingLabel, "pendingLabel");
    }

    /**
     * @param idleLabel    what it reads normally
     * @param pendingLabel what it reads while a request is in flight; pass {@code idleLabel} for
     *                     buttons that never initiate a mutation, such as Back
     */
    public static BankActionButton create(
            int x, int y, int width, int height,
            Component idleLabel, Component pendingLabel,
            OnPress onPress
    ) {
        return new BankActionButton(x, y, width, height, idleLabel, pendingLabel, onPress);
    }

    /**
     * Puts the button into or out of the pending state. A pending button is also inactive, so it
     * cannot be pressed again -- the visible half of duplicate protection, backed by {@code
     * ClientBankingSession}'s lock and by the server's own guards.
     */
    public void setPending(boolean pending) {
        if (this.pending == pending) return;
        this.pending = pending;
        setMessage(DialoguePresentation.text(pending ? pendingLabel : idleLabel));
        if (pending) this.active = false;
    }

    public boolean isPending() {
        return pending;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (!pending) return;
        // A short bar at the leading edge: present only while pending, so it reads differently
        // from a merely disabled button without relying on the label or on colour alone.
        int markerX = getX() + 3;
        int markerY = getY() + (height / 2) - 1;
        graphics.fill(markerX, markerY, markerX + 4, markerY + 2, 0xFFFFFFFF);
    }
}

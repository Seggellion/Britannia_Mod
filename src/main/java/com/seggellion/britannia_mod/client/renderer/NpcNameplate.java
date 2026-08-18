package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.joml.Matrix4f;

/**
 * Draws an NPC nameplate without the translucent black plate vanilla puts behind it.
 *
 * <h2>Why this is a re-implementation rather than a tweak</h2>
 * The background is not a separate draw that could be skipped. {@code EntityRenderer.renderNameTag}
 * computes it inline as {@code (int)(options.getBackgroundOpacity(0.25F) * 255.0F) << 24} and hands
 * it to {@code Font#drawInBatch} as the {@code backgroundColor} argument, so the only ways to reach
 * it are a Mixin into that local, or replacing the call. A Mixin here would have to pin the ordinal
 * of an {@code int} local in a vanilla method — brittle across versions, and global to every
 * nameplate in the game including players. {@link RenderNameTagEvent} already offers a supported
 * seam that is naturally scoped to the entities we care about, so this takes it.
 *
 * <p>The transform below is therefore a deliberate line-for-line mirror of vanilla's, with one
 * value changed: the background argument is {@code 0}. Two details are worth stating because they
 * look like omissions and are not. The {@code "deadmau5"} name special-case is dropped, since it
 * offsets the plate for one specific player name and no NPC is named that. And the two-pass draw is
 * kept exactly as vanilla has it — a dim see-through pass followed by an opaque one — because
 * dropping the second pass is what makes names render washed out through walls.
 *
 * <h2>Scope</h2>
 * Applied only to this mod's own NPCs (see the caller), not to players or vanilla mobs, whose
 * nameplates keep their backgrounds. Widening it is a one-line change at the call site.
 */
public final class NpcNameplate {

    /** Vanilla's dim colour for the see-through pass: {@code 0x20FFFFFF}. */
    private static final int SEE_THROUGH_COLOR = 553648127;

    /** Vanilla lifts the plate half a block above the entity's name-tag attachment point. */
    private static final double HEIGHT_ABOVE_ATTACHMENT = 0.5D;

    private static final float SCALE = 0.025F;

    private NpcNameplate() {
    }

    /**
     * Renders {@code event}'s content with no background plate.
     *
     * <p>The caller must have suppressed vanilla rendering first, or this draws a second copy over
     * the top of vanilla's. Note the distance guard below: {@link RenderNameTagEvent} is fired
     * <em>before</em> {@code renderNameTag} runs, so suppressing that call also skips the
     * render-distance test inside it, which this has to perform itself or NPC names would become
     * visible from arbitrarily far away.
     */
    public static void renderWithoutBackground(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        if (!ClientHooks.isNameplateInRenderDistance(entity, dispatcher.distanceToSqr(entity))) return;

        Vec3 attachment = entity.getAttachments()
                .getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(event.getPartialTick()));
        if (attachment == null) return;

        Component content = event.getContent();
        // Vanilla's flag: a crouching entity's name does not show through walls.
        boolean seeThrough = !entity.isDiscrete();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + HEIGHT_ABOVE_ATTACHMENT, attachment.z);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(SCALE, -SCALE, SCALE);
        Matrix4f pose = poseStack.last().pose();

        Font font = minecraft.font;
        float left = -font.width(content) / 2.0F;
        font.drawInBatch(content, left, 0.0F, SEE_THROUGH_COLOR, false, pose, event.getMultiBufferSource(),
                seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        if (seeThrough) {
            font.drawInBatch(content, left, 0.0F, -1, false, pose, event.getMultiBufferSource(),
                    Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        }
        poseStack.popPose();
    }
}

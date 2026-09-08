package com.seggellion.britannia_mod.client.quest;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.quest.achievement.QuestAchievementAward;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Rowan farming questline M10: the one place a quest {@code client_actions} entry becomes something
 * the player sees.
 *
 * <p>It was in {@code ClientNetworkHandler}, reachable only from a trigger result. The claim that
 * ends a questline is a turn-in, which comes back through {@link
 * com.seggellion.britannia_mod.quest.network.QuestClient} instead -- so the achievement toast and
 * its challenge sound, authored on quest 5's claim since M7, had no path to the screen at all. Both
 * callers now come here, and there is exactly one rendering of each action type.
 *
 * <p>The decision of <em>whether</em> to announce an achievement is not made here: the server has
 * already made it at the authoritative completion boundary and removed the announcement from the
 * response when this player had earned the advancement before (see {@link QuestAchievementAward}).
 * This class renders what it is given.
 */
@OnlyIn(Dist.CLIENT)
public final class QuestClientActions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation FONT_UO_CLASSIC =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);
    private static final TextColor ACHIEVEMENT_GOLD = TextColor.fromRgb(0xFFAA00);

    private QuestClientActions() {
    }

    /**
     * Renders every client action an authoritative response carried.
     *
     * @param questId    for the log line only
     * @param triggerKey the objective this came from, or empty for a turn-in; log line only
     */
    public static void present(@Nullable List<QuestModels.ClientAction> actions, long questId, String triggerKey) {
        if (actions == null || actions.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();

        for (QuestModels.ClientAction action : actions) {
            if (action == null) continue;
            String actionType = action.type != null && !action.type.isBlank() ? action.type : action.action;
            if (QuestAchievementAward.ACHIEVEMENT_ACTION.equals(actionType)) {
                achievement(mc, action);
            } else if ("stat_gain".equals(actionType)) {
                statGain(mc, action);
            } else if ("spawn_escort".equals(actionType)) {
                // Server-triggered environmental results do not spawn client-side escorts.
            } else {
                LOGGER.warn("Quest client action unknown quest_id={} trigger_key={} type={} action={} name={}",
                        questId, triggerKey, action.type, action.action, action.name);
            }
        }
    }

    /** The challenge-style toast and its sound: the shape the questline has promised since M7. */
    private static void achievement(Minecraft mc, QuestModels.ClientAction action) {
        mc.getToasts().addToast(SystemToast.multiline(
                mc,
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable(QuestScreenText.ACHIEVEMENT_TOAST_TITLE)
                        .withStyle(UO_STYLE.withColor(ACHIEVEMENT_GOLD)),
                achievementName(action).withStyle(UO_STYLE)));
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
    }

    /**
     * What to call the achievement: the advancement's own title when this pack has one, so the
     * questline reads the same in the toast and in the advancements screen, and what Rails called
     * it otherwise.
     */
    private static net.minecraft.network.chat.MutableComponent achievementName(QuestModels.ClientAction action) {
        QuestAchievementAward.Display display = QuestAchievementAward.display(action.key, action.name);
        if (!display.translationKey().isEmpty()) {
            return Component.translatableWithFallback(display.translationKey(), display.fallback());
        }
        return display.fallback().isEmpty()
                ? Component.translatable(QuestScreenText.ACHIEVEMENT_UNNAMED)
                : Component.literal(display.fallback());
    }

    private static void statGain(Minecraft mc, QuestModels.ClientAction action) {
        if (mc.player == null) return;
        if (action.karma > 0 && action.fame > 0) {
            mc.player.sendSystemMessage(Component.translatable(
                    QuestScreenText.STAT_KARMA_AND_FAME, action.karma, action.fame).withStyle(UO_STYLE));
        } else if (action.karma > 0) {
            mc.player.sendSystemMessage(
                    Component.translatable(QuestScreenText.STAT_KARMA, action.karma).withStyle(UO_STYLE));
        } else if (action.fame > 0) {
            mc.player.sendSystemMessage(
                    Component.translatable(QuestScreenText.STAT_FAME, action.fame).withStyle(UO_STYLE));
        }
    }
}

package faygolover.rpperks.events;

import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.network.PerkActionPacket;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.PerkRules;
import faygolover.rpperks.skill.SkillDef;
import faygolover.rpperks.skill.SkillService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверная обработка кнопок окна перков.
 * Клиенту не доверяем: права и правила проверяются здесь заново.
 */
public final class PerkActions {
    private PerkActions() {}

    public static void handle(ServerPlayer sender, PerkActionPacket msg) {
        ServerPlayer target = sender.server.getPlayerList().getPlayer(msg.target);
        if (target == null) return;
        PlayerPerks data = target.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
        if (data == null) return;

        if (msg.action.isAdmin()) {
            if (!PerkEventHandler.canSeeHidden(sender)) return; // не оператор — молча игнорируем
            handleAdmin(sender, target, data, msg);
        } else {
            if (target != sender) return; // игрок управляет только своими перками
            handlePlayer(sender, data, msg);
        }
    }

    // =====================================================================
    // Игрок
    // =====================================================================

    private static void handlePlayer(ServerPlayer player, PlayerPerks data, PerkActionPacket msg) {
        Perk perk = msg.perk;
        switch (msg.action) {
            case TAKE -> {
                if (perk == null) return;
                if (deny(player, data, PerkRules.whyCannotTake(data, perk, PerkEventHandler.canSeeHidden(player)))) return;
                data.grantPerk(perk, null);
                PerkEventHandler.refreshPlayer(player, data);
                player.sendSystemMessage(Component.translatable("message.rpperks.taken", Component.translatable(perk.getNameKey())));
            }
            case REFUSE -> {
                if (perk == null) return;
                if (deny(player, data, PerkRules.whyCannotRefuse(data, perk))) return;
                data.revokePerk(perk);
                data.setRefuseTokens(data.getRefuseTokens() - 1);
                PerkEventHandler.refreshPlayer(player, data);
                player.sendSystemMessage(Component.translatable("message.rpperks.refused",
                        Component.translatable(perk.getNameKey()), data.getRefuseTokens()));
            }
            case RESET -> {
                if (deny(player, data, PerkRules.whyCannotReset(data))) return;
                data.resetResettable();
                data.setResetTokens(data.getResetTokens() - 1);
                PerkEventHandler.refreshPlayer(player, data);
                player.sendSystemMessage(Component.translatable("message.rpperks.reset", data.getResetTokens()));
            }
            default -> { }
        }
    }

    /** Если есть причина отказа — сообщает её игроку и пересылает актуальные данные (на случай рассинхрона). */
    private static boolean deny(ServerPlayer player, PlayerPerks data, @Nullable Component reason) {
        if (reason == null) return false;
        player.displayClientMessage(reason.copy().withStyle(s -> s.withColor(0xE06C6C)), true);
        PerkEventHandler.syncToClient(player, data);
        return true;
    }

    // =====================================================================
    // Администратор
    // =====================================================================

    private static void handleAdmin(ServerPlayer admin, ServerPlayer target, PlayerPerks data, PerkActionPacket msg) {
        Perk perk = msg.perk;
        String log;
        switch (msg.action) {
            case ADMIN_GRANT -> {
                if (perk == null) return;
                List<Perk> removed = new ArrayList<>();
                if (!data.grantPerk(perk, removed)) return;
                log = "выдал перк " + perk.getId() + (removed.isEmpty() ? "" : " (сняты: " + removed + ")");
            }
            case ADMIN_REVOKE -> {
                if (perk == null || !data.revokePerk(perk)) return;
                log = "снял перк " + perk.getId();
            }
            case ADMIN_POINTS -> {
                data.addPoints(msg.amount);
                log = "изменил баллы на " + msg.amount + " (теперь " + data.getPoints() + ")";
            }
            case ADMIN_REFUSE_TOKENS -> {
                data.setRefuseTokens(data.getRefuseTokens() + msg.amount);
                log = "изменил жетоны отказа на " + msg.amount + " (теперь " + data.getRefuseTokens() + ")";
                notifyTokens(target, data);
            }
            case ADMIN_RESET_TOKENS -> {
                data.setResetTokens(data.getResetTokens() + msg.amount);
                log = "изменил жетоны сброса на " + msg.amount + " (теперь " + data.getResetTokens() + ")";
                notifyTokens(target, data);
            }
            case ADMIN_TOGGLE_BLOCK -> {
                if (perk == null) return;
                boolean blocked = data.toggleBlocked(perk);
                log = (blocked ? "запретил брать перк " : "разрешил брать перк ") + perk.getId();
            }
            case ADMIN_TOGGLE_SELECTION -> {
                data.setSelectionLocked(!data.isSelectionLocked());
                log = data.isSelectionLocked() ? "закрыл выбор перков" : "открыл выбор перков";
            }
            case ADMIN_WIPE -> {
                data.reset();
                log = "очистил карточку";
            }
            case ADMIN_SKILL_UP, ADMIN_SKILL_DOWN, ADMIN_SKILL_UP_TENTH, ADMIN_SKILL_DOWN_TENTH -> {
                if (msg.amount < 0 || msg.amount >= SkillDef.VALUES.length) return;
                SkillDef skill = SkillDef.VALUES[msg.amount];
                double step = switch (msg.action) {
                    case ADMIN_SKILL_UP -> 1;
                    case ADMIN_SKILL_DOWN -> -1;
                    case ADMIN_SKILL_UP_TENTH -> 0.1;
                    default -> -0.1;
                };
                SkillService.shift(data, skill, step);
                log = String.format(java.util.Locale.ROOT, "изменил навык %s на %+.1f (теперь %.4f)",
                        skill.key(), step, SkillService.progress(data, skill));
            }
            case ADMIN_PROFILE -> {
                // Анкету после сохранения правит только ГМ (как /rpcore profile): пол, возраст, рост.
                var sexes = faygolover.rpcore.api.BodyProfile.Sex.values();
                int sex = msg.amount & 3, age = (msg.amount >> 2) & 127, height = msg.amount >> 9;
                if (sex >= sexes.length) return;
                var prof = new faygolover.rpcore.api.BodyProfile(sexes[sex], age, height, true);
                faygolover.rpcore.api.RpCoreAPI.setProfile(target, prof);
                log = "изменил анкету: " + prof.sex().id() + ", " + prof.age() + " лет, " + (int) prof.heightCm() + " см";
            }
            default -> {
                return;
            }
        }
        // refreshPlayer сам разошлёт обновление игроку, в HUD наблюдателей и в открытые админские окна
        PerkEventHandler.refreshPlayer(target, data);
        Rpperks.LOGGER.info("[RP Perks] {} {} у игрока {}", admin.getScoreboardName(), log, target.getScoreboardName());
    }

    private static void notifyTokens(ServerPlayer target, PlayerPerks data) {
        target.sendSystemMessage(Component.translatable("message.rpperks.tokens_changed",
                data.getRefuseTokens(), data.getResetTokens()));
    }
}

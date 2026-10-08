package faygolover.rpperks.skill;

import faygolover.rpperks.Config;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.events.PerkEventHandler;
import faygolover.rpperks.network.NetworkHandler;
import faygolover.rpperks.network.SyncSkillsPacket;
import faygolover.rpperks.perk.Perk;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Навыки 0–10 (решения 08.10.2026). Итоговый уровень = уровень ГМа, иначе наработанный, но не ниже уровня от
 * перков (от нескольких — наибольший); Плохая координация −1 к огнестрелу, Пацифист — боевые не выше 1.
 * Рост — от практики (отключается в конфиге): каждый уровень дороже. Сила и Форма — физические: от перков не
 * зависят, а сами двигают динамические перки (Слабак ↔ Силач, Задохлик ↔ Выносливый ↔ Атлет) без баллов;
 * без нагрузки медленно падают.
 */
public final class SkillService {
    private SkillService() {}

    /** Физический перк — отметка на шкале навыка: от min до max уровня; при покупке задаёт уровень start. */
    public record Band(Perk perk, SkillDef skill, int min, int max, int start) {}

    public static final List<Band> PHYSIQUE = List.of(
            new Band(Perk.WIMP, SkillDef.STRENGTH, 0, 2, 1),
            new Band(Perk.STRONGMAN, SkillDef.STRENGTH, 7, 10, 7),
            new Band(Perk.WEAKLING, SkillDef.FITNESS, 0, 2, 1),
            new Band(Perk.ENDURING, SkillDef.FITNESS, 7, 8, 7),
            new Band(Perk.ATHLETE, SkillDef.FITNESS, 9, 10, 9));

    /** Откуда итоговый уровень (для вкладки навыков). */
    public enum Source { TRAINED, PERK, GM }

    @Nullable
    public static PlayerPerks data(Player p) {
        return p.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }

    /** Уровень от перков (наибольший). */
    public static int perkFloor(PlayerPerks d, SkillDef s) {
        int best = 0;
        for (Perk perk : d.getEffectivePerks()) best = Math.max(best, Config.getPerkSkills(perk).getOrDefault(s, 0));
        return best;
    }

    /** Наработанный уровень с учётом перков: от него идёт рост. */
    public static int base(PlayerPerks d, SkillDef s) {
        int t = d.getTrained(s.key());
        if (s.kind == SkillDef.Kind.PHYSICAL) return t >= 0 ? t : Config.get(Config.PHYSICAL_START);
        return Math.max(t, perkFloor(d, s));
    }

    public static Source source(PlayerPerks d, SkillDef s) {
        if (d.getSkillLevel(s.id.toString()) >= 0) return Source.GM;
        if (s.kind != SkillDef.Kind.PHYSICAL && perkFloor(d, s) > d.getTrained(s.key())) return Source.PERK;
        return Source.TRAINED;
    }

    /** Итоговый уровень навыка 0–10 (сервер; на клиенте — из пакета). */
    public static int level(PlayerPerks d, SkillDef s) {
        int gm = d.getSkillLevel(s.id.toString());
        if (gm >= 0) return gm;
        int l = base(d, s);
        if (s.isCombat() && s != SkillDef.MELEE && s != SkillDef.EXPLOSIVES && d.hasPerk(Perk.CLUMSY))
            l -= Config.get(Config.CLUMSY_WEAPON_PENALTY);
        if (s.isCombat() && d.hasPerk(Perk.PACIFIST)) l = Math.min(l, Config.get(Config.PACIFIST_COMBAT_CAP));
        return Math.max(0, Math.min(10, l));
    }

    public static int level(Player p, SkillDef s) {
        PlayerPerks d = data(p);
        if (d == null) return 0;
        if (p.level().isClientSide) {
            float[] v = d.getClientSkills().get(s.key());
            return v != null ? (int) v[0] : 0;
        }
        return level(d, s);
    }

    /** Опыт на переход с уровня {@code level} на следующий. */
    public static double need(int level) {
        return Config.get(Config.XP_BASE) * Math.pow(Config.get(Config.XP_GROWTH), level);
    }

    // ------------------------------------------------------------------ рост

    public static void practice(ServerPlayer p, SkillDef s, double amount) {
        if (amount <= 0 || !Config.get(Config.SKILL_GROWTH)) return;
        PlayerPerks d = data(p);
        if (d == null || d.isAdmin() || d.getSkillLevel(s.id.toString()) >= 0) return;
        double mult = Config.get(Config.XP_MULTIPLIER);
        if (s == SkillDef.MEDICINE) mult *= Config.get(Config.XP_MEDICINE);
        // Сила и Форма: RP Stamina сообщает сотни потраченной выносливости.
        if (s.kind == SkillDef.Kind.PHYSICAL) mult *= Config.get(Config.XP_PER_100_STAMINA);
        if (d.hasPerk(Perk.PACIFIST)) mult *= s.isCombat() ? Config.get(Config.PACIFIST_COMBAT_XP)
                : s.kind == SkillDef.Kind.KNOWLEDGE ? Config.get(Config.PACIFIST_PEACE_XP) : 1.0;
        if (s.kind == SkillDef.Kind.PHYSICAL) d.setIdleMinutes(0);
        int lvl = base(d, s);
        if (lvl >= 10) return;
        double xp = d.getSkillXp(s.key()) + amount * mult;
        boolean up = false;
        while (lvl < 10 && xp >= need(lvl)) {
            xp -= need(lvl);
            lvl++;
            up = true;
        }
        d.setTrained(s.key(), lvl);
        d.setSkillXp(s.key(), lvl >= 10 ? 0 : xp);
        dirty(p);
        if (up) {
            p.displayClientMessage(Component.translatable("message.rpperks.skill_up", Component.translatable(s.nameKey()), lvl)
                    .withStyle(ChatFormatting.GREEN), false);
            levelChanged(p, d, s);
        }
    }

    /** Раз в минуту в сети: Сила и Форма без нагрузки медленно падают. */
    public static void minuteTick(ServerPlayer p, PlayerPerks d) {
        if (!Config.get(Config.SKILL_GROWTH) || d.isAdmin()) return;
        d.setIdleMinutes(d.getIdleMinutes() + 1);
        if (d.getIdleMinutes() < Config.get(Config.PHYSICAL_IDLE_MINUTES)) return;
        double decay = Config.get(Config.PHYSICAL_DECAY_PER_MINUTE);
        if (decay <= 0) return;
        for (SkillDef s : new SkillDef[]{SkillDef.STRENGTH, SkillDef.FITNESS}) {
            if (d.getSkillLevel(s.id.toString()) >= 0) continue;
            int lvl = base(d, s);
            double xp = d.getSkillXp(s.key()) - decay;
            if (xp >= 0) {
                d.setSkillXp(s.key(), xp);
                continue;
            }
            if (lvl <= 0) {
                d.setSkillXp(s.key(), 0);
                continue;
            }
            lvl--;
            d.setTrained(s.key(), lvl);
            d.setSkillXp(s.key(), need(lvl) * 0.9);
            p.displayClientMessage(Component.translatable("message.rpperks.skill_down", Component.translatable(s.nameKey()), lvl)
                    .withStyle(ChatFormatting.GRAY), false);
            levelChanged(p, d, s);
        }
        dirty(p);
    }

    private static void levelChanged(ServerPlayer p, PlayerPerks d, SkillDef s) {
        if (s.kind == SkillDef.Kind.PHYSICAL) updatePhysique(d);
        PerkEventHandler.refreshPlayer(p, d);
    }

    // ------------------------------------------------------------------ динамические перки

    /**
     * Перед пересчётом характеристик: купленный физический перк задаёт стартовый уровень (Силач → Сила 7);
     * потом перки следуют за уровнем. Без роста навыков перки статичны (как раньше).
     */
    public static void syncPhysique(PlayerPerks d) {
        for (Band b : PHYSIQUE) {
            boolean owns = d.owns(b.perk());
            if (owns && !d.isPhysiqueApplied(b.perk())) {
                d.setTrained(b.skill().key(), b.start());
                d.setSkillXp(b.skill().key(), 0);
                d.setPhysiqueApplied(b.perk(), true);
            } else if (!owns && d.isPhysiqueApplied(b.perk())) {
                d.setPhysiqueApplied(b.perk(), false);
            }
        }
        if (Config.get(Config.SKILL_GROWTH)) updatePhysique(d);
    }

    /** Динамические перки по текущим уровням Силы и Формы (без баллов). */
    public static void updatePhysique(PlayerPerks d) {
        if (!Config.get(Config.SKILL_GROWTH)) return;
        for (Band b : PHYSIQUE) {
            int lvl = level(d, b.skill());
            boolean want = lvl >= b.min() && lvl <= b.max();
            // Черепаха (купленный негативный перк) не даёт стать Атлетом: Форма упирается в «Выносливого».
            if (b.perk() == Perk.ATHLETE && d.owns(Perk.TURTLE)) want = false;
            if (b.perk() == Perk.ENDURING && d.owns(Perk.TURTLE) && lvl >= 9) want = true;
            if (d.owns(b.perk()) != want) {
                d.setDynamicPerk(b.perk(), want);
                d.setPhysiqueApplied(b.perk(), want);
            }
        }
    }

    // ------------------------------------------------------------------ синхронизация

    private static final Map<java.util.UUID, Long> DIRTY = new HashMap<>();

    /** Отметить, что навыки изменились: пакет уйдёт не чаще раза в 2 секунды. */
    public static void dirty(ServerPlayer p) {
        DIRTY.putIfAbsent(p.getUUID(), p.level().getGameTime());
    }

    public static void tick(ServerPlayer p) {
        Long since = DIRTY.get(p.getUUID());
        if (since != null && p.level().getGameTime() - since >= 40) {
            DIRTY.remove(p.getUUID());
            sync(p);
        }
    }

    public static void forget(ServerPlayer p) {
        DIRTY.remove(p.getUUID());
    }

    public static void sync(ServerPlayer p) {
        PlayerPerks d = data(p);
        if (d != null) NetworkHandler.sendToPlayer(p, SyncSkillsPacket.of(d));
    }
}

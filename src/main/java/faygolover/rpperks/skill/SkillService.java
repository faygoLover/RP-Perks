package faygolover.rpperks.skill;

import faygolover.rpcore.api.BodyNorms;
import faygolover.rpcore.api.NutritionProvider;
import faygolover.rpcore.api.RpCoreAPI;
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
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Навыки 0–10. Итоговый уровень = наработанный; ГМ прибавляет и отнимает уровни поверх наработанного, не замораживая
 * навык (2.7.0). Умение — стартовый капитал (2.9.0, слои персонажа 06.11): при взятии один раз поднимает наработанный
 * уровень до своего, дальше навык живёт сам; отказ от умения (жетон, ГМ) забирает выданные уровни. Плохая координация
 * −1 к огнестрелу, Пацифист — боевые не выше 1.
 * <p>
 * 2.0.0 (решения брата 13–14.10.2026, RP Stamina Sports docs/02, 06, 07):
 * <ul>
 *   <li>Сила и Телосложение сами дают баффы каждым уровнем ({@link PhysicalEffects}); обычный человек — 3
 *       ({@code physical_start_level}). Физические особенности только сдвигают старт: Хиляк/Слабак −3 … Атлетичный/Силач +3.</li>
 *   <li>Талант: скорость роста зависит от СТАРТОВОГО уровня навыка (старт — от особенностей и умений).</li>
 *   <li>Множители опыта перемножаются: талант × Быстро/Плохообучаемый (кроме физических) × возраст и белок (физические)
 *       × Пацифист × боль ({@link RpCoreAPI#learningFactor}).</li>
 *   <li>Сила и Телосложение падают не от простоя, а от плохого питания и долгой болезни; вес вне нормы ставит потолок.</li>
 * </ul>
 */
public final class SkillService {
    private SkillService() {}

    /** Физическая особенность: какой навык и на сколько сдвигает старт. */
    public record Offset(SkillDef skill, int amount) {}

    public static final Map<Perk, Offset> PHYSICAL_OFFSETS;

    static {
        Map<Perk, Offset> m = new EnumMap<>(Perk.class);
        m.put(Perk.PUNY, new Offset(SkillDef.FITNESS, -3));
        m.put(Perk.OUT_OF_SHAPE, new Offset(SkillDef.FITNESS, -2));
        m.put(Perk.FIT, new Offset(SkillDef.FITNESS, 2));
        m.put(Perk.ATHLETIC, new Offset(SkillDef.FITNESS, 3));
        m.put(Perk.WIMP, new Offset(SkillDef.STRENGTH, -3));
        m.put(Perk.WEAK, new Offset(SkillDef.STRENGTH, -2));
        m.put(Perk.STRONG, new Offset(SkillDef.STRENGTH, 2));
        m.put(Perk.STRONGMAN, new Offset(SkillDef.STRENGTH, 3));
        PHYSICAL_OFFSETS = Map.copyOf(m);
    }

    @Nullable
    public static PlayerPerks data(Player p) {
        return p.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }

    /** Уровень от перков-умений (наибольший): старт для таланта, капитал при взятии. */
    public static int perkFloor(PlayerPerks d, SkillDef s) {
        int best = 0;
        for (Perk perk : d.getEffectivePerks()) best = Math.max(best, Config.getPerkSkills(perk).getOrDefault(s, 0));
        return best;
    }

    /** Обычный уровень Силы и Телосложения — от него считаются баффы («×1»). */
    public static int pivot() {
        return Config.get(Config.PHYSICAL_START);
    }

    /** Стартовый уровень: физические — обычный + сдвиги особенностей; остальные — от умений. */
    public static int startLevel(PlayerPerks d, SkillDef s) {
        if (s.kind != SkillDef.Kind.PHYSICAL) return perkFloor(d, s);
        int l = pivot();
        for (Map.Entry<Perk, Offset> e : PHYSICAL_OFFSETS.entrySet()) {
            if (e.getValue().skill() == s && d.owns(e.getKey())) l += e.getValue().amount();
        }
        return Math.max(0, Math.min(10, l));
    }

    /** Наработанный уровень с учётом перков: от него идёт рост. */
    public static int base(PlayerPerks d, SkillDef s) {
        int t = d.getTrained(s.key());
        if (s.kind == SkillDef.Kind.PHYSICAL) return t >= 0 ? t : startLevel(d, s);
        return Math.max(0, t);
    }

    /** Прогресс в уровнях: наработанный уровень + доля опыта до следующего (3,4567 — третий и 45,67 %). */
    public static double progress(PlayerPerks d, SkillDef s) {
        int b = base(d, s);
        if (b >= 10) return 10;
        return b + Math.min(0.9999, d.getSkillXp(s.key()) / need(s, b));
    }

    /**
     * ГМ: поставить прогресс (уровень + доля) как наработанный. Навык не замораживается — дальше растёт и падает сам.
     */
    public static void setProgress(PlayerPerks d, SkillDef s, double progress) {
        double p = Math.round(Math.max(0, Math.min(10, progress)) * 10000) / 10000.0; // шаги по 0,1 без хвостов 3,9999…
        int lvl = (int) Math.floor(p);
        d.setTrained(s.key(), lvl);
        d.setSkillXp(s.key(), lvl >= 10 ? 0 : (p - lvl) * need(s, lvl));
    }

    /** ГМ: прибавить или отнять уровни поверх наработанного; 0,1 — 10 % уровня. */
    public static void shift(PlayerPerks d, SkillDef s, double levels) {
        setProgress(d, s, progress(d, s) + levels);
    }

    /** ГМ (/rpcore skill): уровень с сохранением доли до следующего; {@code level < 0} — вернуть к стартовому. */
    public static void setLevel(PlayerPerks d, SkillDef s, int level) {
        if (level < 0) {
            d.clearTrained(s.key());
            return;
        }
        double p = progress(d, s);
        setProgress(d, s, Math.min(10, level) + (p - Math.floor(p)));
    }

    /**
     * Старое поле уровня ГМа (до 2.7.0 замораживало навык) → наработанный уровень по правилу Алекса 31.10:
     * уровень = больший из наработанного и уровня ГМа; если ГМ был выше — опыт 10 % до следующего,
     * иначе опыт наработанного сохраняется и к нему +10 %. На 10-м уровне опыт 0. Возвращает, сколько навыков переведено.
     */
    public static int migrateGmLevels(PlayerPerks d) {
        int n = 0;
        for (SkillDef s : SkillDef.VALUES) {
            int gm = d.getSkillLevel(s.id.toString());
            if (gm < 0) continue;
            int trained = base(d, s);
            int lvl = Math.max(trained, Math.min(10, gm));
            double xp = lvl >= 10 ? 0 : (lvl > trained ? 0 : d.getSkillXp(s.key())) + need(s, lvl) * 0.1;
            if (lvl < 10) xp = Math.min(xp, need(s, lvl) * 0.999);
            d.setTrained(s.key(), lvl);
            d.setSkillXp(s.key(), xp);
            d.setSkillLevel(s.id.toString(), -1);
            n++;
        }
        return n;
    }

    /** Итоговый уровень навыка 0–10 (сервер; на клиенте — из пакета). */
    public static int level(PlayerPerks d, SkillDef s) {
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

    /** Опыт на следующий уровень навыка: Сила и Телосложение дороже в {@code physical_xp_cost} раз. */
    public static double need(SkillDef s, int level) {
        return need(level) * (s.kind == SkillDef.Kind.PHYSICAL ? Config.get(Config.PHYSICAL_XP_COST) : 1.0);
    }

    // ------------------------------------------------------------------ скорость роста

    /** Талант: скорость роста по стартовому уровню (умственные и боевые — одна таблица, физические — другая). */
    public static double talent(PlayerPerks d, SkillDef s) {
        int start = startLevel(d, s);
        var table = s.kind == SkillDef.Kind.PHYSICAL ? Config.TALENT_PHYSICAL : Config.TALENT_MENTAL;
        return tableClamped(table, start);
    }

    /** Значение по индексу; индекс за концом таблицы — последнее значение. */
    @SuppressWarnings("unchecked")
    private static double tableClamped(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> t, int i) {
        java.util.List<? extends Number> l = (java.util.List<? extends Number>) (java.util.List<?>) Config.get(t);
        if (l.isEmpty()) return 1.0;
        return l.get(Math.max(0, Math.min(l.size() - 1, i))).doubleValue();
    }

    /** Мало белка (RP Culinary): запас ниже 20. */
    private static boolean lowProtein(Player p) {
        NutritionProvider n = RpCoreAPI.nutrition();
        if (n == null) return false;
        double v = n.nutrient(p, NutritionProvider.PROTEIN);
        return Double.isFinite(v) && v < 20;
    }

    /** Множитель опыта для навыка без талант-независимых частей «практики» (за 100 выносливости и т. п.). */
    public static double xpMultiplier(Player p, PlayerPerks d, SkillDef s) {
        double mult = Config.get(Config.XP_MULTIPLIER) * talent(d, s);
        if (s == SkillDef.MEDICINE) mult *= Config.get(Config.XP_MEDICINE);
        if (d.hasPerk(Perk.PACIFIST)) mult *= s.isCombat() ? Config.get(Config.PACIFIST_COMBAT_XP)
                : s.kind == SkillDef.Kind.KNOWLEDGE ? Config.get(Config.PACIFIST_PEACE_XP) : 1.0;
        if (s.kind == SkillDef.Kind.PHYSICAL) {
            if (Config.get(Config.PHYSICAL_AGE_LEARNING)) mult *= BodyNorms.agePhysicalLearning(RpCoreAPI.profile(p).age());
            if (lowProtein(p)) mult *= Config.get(Config.LOW_PROTEIN_PHYSICAL_XP);
        } else {
            if (d.hasPerk(Perk.FAST_LEARNER)) mult *= Config.get(Config.FAST_LEARNER_XP);
            if (d.hasPerk(Perk.SLOW_LEARNER)) mult *= Config.get(Config.SLOW_LEARNER_XP);
        }
        return mult * RpCoreAPI.learningFactor(p);
    }

    /** Множитель для окна навыков: талант и особенности, без боли и белка (они меняются). */
    public static double shownMultiplier(PlayerPerks d, SkillDef s) {
        double mult = talent(d, s);
        if (s.kind != SkillDef.Kind.PHYSICAL) {
            if (d.hasPerk(Perk.FAST_LEARNER)) mult *= Config.get(Config.FAST_LEARNER_XP);
            if (d.hasPerk(Perk.SLOW_LEARNER)) mult *= Config.get(Config.SLOW_LEARNER_XP);
        }
        return mult;
    }

    private static boolean sportsLoaded() {
        return ModList.get().isLoaded("rpsports");
    }

    // ------------------------------------------------------------------ рост

    /** Практика (сколько сделано): физические — сотни потраченной выносливости (RP Stamina). */
    public static void practice(ServerPlayer p, SkillDef s, double amount) {
        if (amount <= 0) return;
        if (s.kind == SkillDef.Kind.PHYSICAL) {
            amount *= Config.get(Config.XP_PER_100_STAMINA);
            PlayerPerks d = data(p);
            if (d != null && d.hasPerk(Perk.GYM_LOVER) && !sportsLoaded()) amount *= Config.get(Config.GYM_LOVER_XP_MULT);
        }
        addXp(p, s, amount);
    }

    /** Прямой опыт (RP Core grantXp): спорт за каждый повтор. */
    public static void grantXp(ServerPlayer p, SkillDef s, double xp) {
        if (xp > 0) addXp(p, s, xp);
    }

    private static void addXp(ServerPlayer p, SkillDef s, double raw) {
        if (!Config.get(Config.SKILL_GROWTH)) return;
        PlayerPerks d = data(p);
        if (d == null || d.isAdmin()) return;
        double amount = raw * xpMultiplier(p, d, s);
        if (amount <= 0) return;
        int lvl = base(d, s);
        int cap = s.kind == SkillDef.Kind.PHYSICAL ? cap(p, d, s) : 10;
        if (lvl >= 10) return;
        double xp = d.getSkillXp(s.key()) + amount;
        boolean up = false;
        while (lvl < 10 && lvl < cap && xp >= need(s, lvl)) {
            xp -= need(s, lvl);
            lvl++;
            up = true;
        }
        // Упёрся в потолок (вес вне нормы): опыт копится, но уровень не растёт.
        if (lvl >= cap && lvl < 10) xp = Math.min(xp, need(s, lvl) * 0.999);
        d.setTrained(s.key(), lvl);
        d.setSkillXp(s.key(), lvl >= 10 ? 0 : xp);
        dirty(p);
        if (up) {
            p.displayClientMessage(Component.translatable("message.rpperks.skill_up", Component.translatable(s.nameKey()), lvl)
                    .withStyle(ChatFormatting.GREEN), false);
            levelChanged(p, d, s);
        }
    }

    // ------------------------------------------------------------------ потеря и потолок

    /** Тело относительно нормы: по весу (Culinary/Medicine), а если вес никто не ведёт — по особенностям веса. */
    public static BodyNorms.BodyState bodyState(Player p, PlayerPerks d) {
        if (Double.isFinite(RpCoreAPI.weightKg(p)) && Double.isFinite(RpCoreAPI.bodyFatPercent(p))) return RpCoreAPI.bodyState(p);
        if (d.hasPerk(Perk.EMACIATED)) return BodyNorms.BodyState.UNDER;
        if (d.hasPerk(Perk.OBESE)) return BodyNorms.BodyState.OBESE;
        if (d.hasPerk(Perk.OVERWEIGHT)) return BodyNorms.BodyState.OVER;
        return BodyNorms.BodyState.NORMAL;
    }

    /** Потолок Силы или Телосложения при весе вне нормы (10 — потолка нет). */
    public static int cap(Player p, PlayerPerks d, SkillDef s) {
        if (!Config.get(Config.PHYSICAL_DECAY_ENABLED)) return 10;
        return switch (bodyState(p, d)) {
            case EXHAUSTED -> Config.get(Config.CAP_EXHAUSTED);
            case UNDER -> Config.get(Config.CAP_UNDER);
            case OVER -> s == SkillDef.FITNESS ? Config.get(Config.CAP_OVER) : 10;
            case OBESE -> s == SkillDef.FITNESS ? Config.get(Config.CAP_OBESE) : 10;
            case NORMAL -> 10;
        };
    }

    /** Накопленное время тела по игрокам, в «минутах по нынешним числам». */
    private static final Map<java.util.UUID, Double> BODY_MINUTES = new HashMap<>();

    /**
     * Раз в реальную минуту в сети: время тела (RP Core {@code bodyScale}; 1:1 при 3-часовых сутках, на паузе 0)
     * копится, и за каждую накопленную минуту — шаг потери Силы и Телосложения.
     */
    public static void minuteTick(ServerPlayer p, PlayerPerks d) {
        double acc = BODY_MINUTES.getOrDefault(p.getUUID(), 0.0) + bodyMinute();
        int steps = (int) Math.min(10, Math.floor(acc));
        BODY_MINUTES.put(p.getUUID(), Math.min(1, acc - steps));
        for (int i = 0; i < steps; i++) bodyMinuteTick(p, d);
    }

    /** Одна реальная минута в минутах времени тела. Без Core 0.13.0 — как раньше: 1, без паузы. */
    private static double bodyMinute() {
        try {
            return RpCoreAPI.bodyScale() / 8.0;
        } catch (NoSuchMethodError old) {
            return 1;
        }
    }

    /** Минута времени тела: плохое питание и болезнь отнимают Силу и Телосложение, вес вне нормы — тянет к потолку. */
    private static void bodyMinuteTick(ServerPlayer p, PlayerPerks d) {
        if (!Config.get(Config.SKILL_GROWTH) || !Config.get(Config.PHYSICAL_DECAY_ENABLED) || d.isAdmin()) return;
        NutritionProvider n = RpCoreAPI.nutrition();
        var body = RpCoreAPI.body();
        boolean protein = false, starving = false, carbs = false, dry = false, ill = false;
        if (n != null) {
            double pr = n.nutrient(p, NutritionProvider.PROTEIN), cb = n.nutrient(p, NutritionProvider.CARBS);
            protein = count(d, "protein", Double.isFinite(pr) && pr < 20, Config.get(Config.DECAY_LOW_PROTEIN_MINUTES));
            starving = count(d, "starving", n.hungerStage(p) >= 3, Config.get(Config.DECAY_STARVING_MINUTES));
            carbs = count(d, "carbs", Double.isFinite(cb) && cb < 20, Config.get(Config.DECAY_LOW_CARBS_MINUTES));
            dry = count(d, "dehydrated", n.thirstStage(p) >= 3, Config.get(Config.DECAY_DEHYDRATED_MINUTES));
        }
        if (body != null) {
            ill = count(d, "ill", body.condition(p) < Config.get(Config.DECAY_ILL_CONDITION), Config.get(Config.DECAY_ILL_MINUTES));
        }
        double per = Config.get(Config.DECAY_PER_CAUSE), max = Config.get(Config.DECAY_MAX);
        int causesS = (protein ? 1 : 0) + (starving ? 1 : 0) + (ill ? 1 : 0);
        int causesF = (starving ? 1 : 0) + (carbs ? 1 : 0) + (dry ? 1 : 0) + (ill ? 1 : 0);
        boolean changed = false;
        changed |= lose(p, d, SkillDef.STRENGTH, Math.min(max, causesS * per), 0, "message.rpperks.skill_down");
        changed |= lose(p, d, SkillDef.FITNESS, Math.min(max, causesF * per), 0, "message.rpperks.skill_down");
        double drift = Config.get(Config.CAP_DRIFT);
        for (SkillDef s : new SkillDef[]{SkillDef.STRENGTH, SkillDef.FITNESS}) {
            int cap = cap(p, d, s);
            if (base(d, s) > cap) changed |= lose(p, d, s, drift, cap, "message.rpperks.skill_down_weight");
        }
        if (changed) dirty(p);
    }

    /** Счётчик минут подряд для причины; true — причина действует (дольше порога). */
    private static boolean count(PlayerPerks d, String key, boolean now, int threshold) {
        int m = now ? d.getDecayMinutes(key) + 1 : 0;
        d.setDecayMinutes(key, m);
        return m >= threshold;
    }

    /** Отнять опыт; при нехватке — уровень вниз (не ниже {@code floor}). true — что-то изменилось. */
    private static boolean lose(ServerPlayer p, PlayerPerks d, SkillDef s, double amount, int floor, String messageKey) {
        if (amount <= 0) return false;
        int lvl = base(d, s);
        if (lvl <= floor && d.getSkillXp(s.key()) <= 0) return false;
        double xp = d.getSkillXp(s.key()) - amount;
        if (xp >= 0) {
            d.setTrained(s.key(), lvl);
            d.setSkillXp(s.key(), xp);
            return true;
        }
        if (lvl <= floor) {
            d.setTrained(s.key(), lvl);
            d.setSkillXp(s.key(), 0);
            return true;
        }
        lvl--;
        d.setTrained(s.key(), lvl);
        d.setSkillXp(s.key(), need(s, lvl) * 0.9);
        p.displayClientMessage(Component.translatable(messageKey, Component.translatable(s.nameKey()), lvl)
                .withStyle(ChatFormatting.GRAY), false);
        levelChanged(p, d, s);
        return true;
    }

    private static void levelChanged(ServerPlayer p, PlayerPerks d, SkillDef s) {
        PerkEventHandler.refreshPlayer(p, d);
    }

    // ------------------------------------------------------------------ физические особенности

    /**
     * Перед пересчётом характеристик: взятая (или снятая) физическая особенность сдвигает наработанный уровень.
     * Пока навык ещё не наработан, старт и так считается с особенностями ({@link #startLevel}).
     */
    public static void syncPhysique(PlayerPerks d) {
        for (Map.Entry<Perk, Offset> e : PHYSICAL_OFFSETS.entrySet()) {
            Perk perk = e.getKey();
            SkillDef s = e.getValue().skill();
            boolean owns = d.owns(perk);
            boolean applied = d.isPhysiqueApplied(perk);
            if (owns == applied) continue;
            int t = d.getTrained(s.key());
            if (t >= 0) d.setTrained(s.key(), t + (owns ? e.getValue().amount() : -e.getValue().amount()));
            d.setPhysiqueApplied(perk, owns);
        }
    }

    /**
     * Умения — стартовый капитал (2.9.0). Взятое умение один раз поднимает наработанный уровень до своего (ниже не
     * опускает), запоминая прежний. Снятое (жетон отказа, ГМ) забирает выданное: уровень опускается на столько, сколько
     * умение дало, но не ниже прежнего — наработанное сверху остаётся. Старые сохранения при первом входе получают
     * отметку без изменений: раньше умение и так держало минимум.
     */
    public static void syncSkillPerks(PlayerPerks d) {
        var grants = d.skillGrants();
        for (Perk perk : Perk.values()) {
            // owns, а не «действует»: режим Админа не должен забирать капитал.
            boolean owns = d.owns(perk);
            for (Map.Entry<SkillDef, Integer> e : Config.getPerkSkills(perk).entrySet()) {
                SkillDef s = e.getKey();
                if (s.kind == SkillDef.Kind.PHYSICAL) continue;
                String key = perk.getId() + "/" + s.key();
                Integer before = grants.get(key);
                if (owns && before == null) {
                    int t = Math.max(0, d.getTrained(s.key()));
                    grants.put(key, t);
                    if (t < e.getValue()) {
                        d.setTrained(s.key(), e.getValue());
                        d.setSkillXp(s.key(), 0);
                    }
                } else if (!owns && before != null) {
                    grants.remove(key);
                    int t = Math.max(0, d.getTrained(s.key()));
                    int given = Math.max(0, e.getValue() - before);
                    int lvl = Math.max(before, t - given);
                    if (lvl != t) {
                        d.setTrained(s.key(), lvl);
                        d.setSkillXp(s.key(), 0);
                    }
                }
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
        BODY_MINUTES.remove(p.getUUID());
    }

    public static void sync(ServerPlayer p) {
        PlayerPerks d = data(p);
        if (d == null) return;
        NetworkHandler.sendToPlayer(p, SyncSkillsPacket.of(d));
        faygolover.rpperks.events.PerkEventHandler.updateAdminScreens(p, d);
    }
}

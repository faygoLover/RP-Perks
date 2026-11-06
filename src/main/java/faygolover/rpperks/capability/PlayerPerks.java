package faygolover.rpperks.capability;

import faygolover.rpperks.Config;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.Stat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Данные перков игрока. Существует и на сервере, и на клиенте:
 * клиентская копия заполняется пакетом синхронизации (нужна окну перков, HUD, Анорексичке и Сухопутному).
 */
public class PlayerPerks {
    private int points = 0;
    /** Перк -> на сколько изменился баланс при его выдаче. Возврат идёт ровно на эту сумму, даже если цену потом поменяли в конфиге. */
    private final EnumMap<Perk, Integer> perks = new EnumMap<>(Perk.class);
    private int refuseTokens = 0;
    private int resetTokens = 0;
    /** Перки, которые админ запретил этому игроку брать. */
    private final EnumSet<Perk> blockedPerks = EnumSet.noneOf(Perk.class);
    /** Админ закрыл игроку самостоятельный выбор перков целиком. */
    private boolean selectionLocked = false;
    private int caffeineTimer = 0;
    private int anorexiaTimer = 0;
    private boolean refuseNextMeal = false;
    private int unluckyCooldown = 0;
    /**
     * Уровни навыков (0–10), выставленные ГМом: id навыка RP Core -> уровень. Нет записи — уровень по перкам
     * (см. {@link faygolover.rpperks.compat.CoreTraits}). Навыки семейства RP ведёт RP Perks (решение автора 07.10).
     */
    private final Map<String, Integer> skillLevels = new java.util.HashMap<>();
    /** Наработанный уровень навыка (от практики; не ниже уровня от перков, когда до него дошёл рост). */
    private final Map<String, Integer> trained = new java.util.HashMap<>();
    /** Опыт к следующему уровню. */
    private final Map<String, Double> skillXp = new java.util.HashMap<>();
    /** Физические перки, стартовый уровень которых уже применён (Силач → Сила 7 и т. п.). */
    private final Set<String> physiqueApplied = new java.util.HashSet<>();
    /** Умения — стартовый капитал (2.9.0): «перк/навык» → наработанный уровень до того, как умение его подняло. */
    private final Map<String, Integer> skillGrants = new java.util.HashMap<>();
    /** Минут подряд, пока действует причина потери Силы и Телосложения (мало белка, голод, болезнь…). */
    private final Map<String, Integer> decayMinutes = new java.util.HashMap<>();
    /** Версия данных: 2 — особенности 2.0.0. */
    private static final int DATA_VERSION = 2;
    /** 2.0.0: сколько старых особенностей снято с возвратом очков (-1 — сообщать нечего). Не синхронизируется. */
    private int migrationRefunded = -1;
    /** Клиент: итоговые уровни, опыт (доля) и источник по навыкам — из пакета навыков. */
    private Map<String, float[]> clientSkills = Map.of();

    /**
     * Кэш последнего пересчёта характеристик (см. PerkEventHandler.computeStats).
     * Только сервер: не сохраняется в NBT и не участвует в синхронизации с клиентом.
     * Обновляется в PerkEventHandler.refreshPlayer при любом изменении перков, чтобы горячие пути
     * (например, PerkMechanicsHandler.onKnockback на каждый удар в ближнем бою) не пересчитывали
     * все статы заново на каждое обращение.
     */
    @Nullable
    private EnumMap<Stat, Double> cachedStats = null;

    // ---------- Баллы ----------
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public void addPoints(int amount) { this.points += amount; }

    // ---------- Жетоны ----------
    public int getRefuseTokens() { return refuseTokens; }
    public void setRefuseTokens(int tokens) { this.refuseTokens = Math.max(0, tokens); }
    public int getResetTokens() { return resetTokens; }
    public void setResetTokens(int tokens) { this.resetTokens = Math.max(0, tokens); }

    // ---------- Перки ----------

    /** Все перки игрока, включая приостановленные режимом Админа. Порядок стабильный (как в enum). */
    public Set<Perk> getActivePerks() { return Collections.unmodifiableSet(perks.keySet()); }

    /** Перк -> изменение баланса при его выдаче. */
    public Map<Perk, Integer> getPerkDeltas() { return Collections.unmodifiableMap(perks); }

    /** Сколько баллов изменилось при выдаче перка (0, если перка нет). */
    public int getDelta(Perk perk) { return perks.getOrDefault(perk, 0); }

    public boolean owns(Perk perk) { return perks.containsKey(perk); }

    public boolean isAdmin() { return perks.containsKey(Perk.ADMIN); }

    /** Действует ли перк прямо сейчас. Пока активен Админ, все остальные перки «выключены». */
    public boolean hasPerk(Perk perk) {
        if (perk == Perk.ADMIN) return isAdmin();
        return !isAdmin() && perks.containsKey(perk);
    }

    /** Перки, которые реально действуют. */
    public Set<Perk> getEffectivePerks() {
        return isAdmin() ? EnumSet.of(Perk.ADMIN) : getActivePerks();
    }

    /**
     * Выдаёт перк, снимая несовместимые с ним (с возвратом баллов).
     * @param removedConflicts сюда складываются снятые перки (можно null)
     * @return false, если перк уже был
     */
    public boolean grantPerk(Perk perk, @Nullable List<Perk> removedConflicts) {
        if (perks.containsKey(perk)) return false;
        for (Perk conflict : Config.getConflicts(perk)) {
            if (revokePerk(conflict) && removedConflicts != null) removedConflicts.add(conflict);
        }
        int delta = perk.getPointsDelta();
        perks.put(perk, delta);
        points += delta;
        return true;
    }

    /** Снимает перк с возвратом баллов. */
    public boolean revokePerk(Perk perk) {
        Integer delta = perks.remove(perk);
        if (delta == null) return false;
        points -= delta;
        return true;
    }

    /** Перки, которые игрок может сбросить сам (всё, кроме служебных). */
    public List<Perk> getResettablePerks() {
        return perks.keySet().stream().filter(p -> p.getCategory() != Perk.Category.SERVICE).toList();
    }

    /** Сброс игроком: снимает все не служебные перки с возвратом баллов. Возвращает число снятых. */
    public int resetResettable() {
        List<Perk> list = getResettablePerks();
        list.forEach(this::revokePerk);
        return list.size();
    }

    /** Полный сброс карточки администратором. Жетоны и запреты сохраняются. */
    public void reset() {
        perks.clear();
        points = 0;
        caffeineTimer = 0;
        anorexiaTimer = 0;
        refuseNextMeal = false;
        unluckyCooldown = 0;
        cachedStats = null;
    }

    // ---------- Ограничения выбора (ставит админ) ----------
    public Set<Perk> getBlockedPerks() { return Collections.unmodifiableSet(blockedPerks); }
    public boolean isBlocked(Perk perk) { return blockedPerks.contains(perk); }
    /** Переключает запрет на перк. Возвращает новое состояние (true — запрещён). */
    public boolean toggleBlocked(Perk perk) {
        if (blockedPerks.remove(perk)) return false;
        blockedPerks.add(perk);
        return true;
    }
    public boolean isSelectionLocked() { return selectionLocked; }
    public void setSelectionLocked(boolean locked) { this.selectionLocked = locked; }

    // ---------- Навыки (RP Core) ----------
    /**
     * Уровень ГМа из старых версий (до 2.7.0 он замораживал навык) или -1. Новых не пишется: при входе игрока
     * переводится в наработанный ({@link faygolover.rpperks.skill.SkillService#migrateGmLevels}).
     */
    public int getSkillLevel(String skill) { return skillLevels.getOrDefault(skill, -1); }
    /** {@code level < 0} — убрать. */
    public void setSkillLevel(String skill, int level) {
        if (level < 0) skillLevels.remove(skill);
        else skillLevels.put(skill, Math.min(10, level));
    }
    public Map<String, Integer> getSkillLevels() { return Collections.unmodifiableMap(skillLevels); }

    /** Наработанный уровень или -1. */
    public int getTrained(String skill) { return trained.getOrDefault(skill, -1); }
    public void setTrained(String skill, int level) { trained.put(skill, Math.max(0, Math.min(10, level))); }
    /** Забыть наработанное: уровень снова стартовый, опыт 0. */
    public void clearTrained(String skill) {
        trained.remove(skill);
        skillXp.remove(skill);
    }
    public double getSkillXp(String skill) { return skillXp.getOrDefault(skill, 0.0); }
    public void setSkillXp(String skill, double xp) { skillXp.put(skill, Math.max(0, xp)); }
    public boolean isPhysiqueApplied(Perk perk) { return physiqueApplied.contains(perk.getId()); }
    public void setPhysiqueApplied(Perk perk, boolean applied) {
        if (applied) physiqueApplied.add(perk.getId());
        else physiqueApplied.remove(perk.getId());
    }
    /** Выданные умениями уровни: ключ «перк/навык» → уровень до выдачи. */
    public Map<String, Integer> skillGrants() { return skillGrants; }
    public int getDecayMinutes(String cause) { return decayMinutes.getOrDefault(cause, 0); }
    public void setDecayMinutes(String cause, int minutes) {
        if (minutes <= 0) decayMinutes.remove(cause);
        else decayMinutes.put(cause, minutes);
    }
    public int getMigrationRefunded() { return migrationRefunded; }
    public void setMigrationRefunded(int n) { migrationRefunded = n; }
    public Map<String, float[]> getClientSkills() { return clientSkills; }
    public void setClientSkills(Map<String, float[]> skills) { clientSkills = Map.copyOf(skills); }

    // ---------- Таймеры механик ----------
    public int getCaffeineTimer() { return caffeineTimer; }
    public void setCaffeineTimer(int ticks) { this.caffeineTimer = ticks; }

    public int getAnorexiaTimer() { return anorexiaTimer; }
    public void setAnorexiaTimer(int ticks) { this.anorexiaTimer = ticks; }

    public boolean isRefuseNextMeal() { return refuseNextMeal; }
    public void setRefuseNextMeal(boolean refuse) { this.refuseNextMeal = refuse; }

    public int getUnluckyCooldown() { return unluckyCooldown; }
    public void setUnluckyCooldown(int ticks) { this.unluckyCooldown = ticks; }

    // ---------- Счётчики механик без сохранения (сервер) ----------
    private int sprintSeconds;
    private int runnerCooldown;
    public int getSprintSeconds() { return sprintSeconds; }
    public void setSprintSeconds(int s) { sprintSeconds = Math.max(0, s); }
    public int getRunnerCooldown() { return runnerCooldown; }
    public void setRunnerCooldown(int ticks) { runnerCooldown = Math.max(0, ticks); }

    // ---------- Кэш характеристик (только сервер, не сохраняется и не синхронизируется) ----------
    @Nullable
    public EnumMap<Stat, Double> getCachedStats() { return cachedStats; }
    public void setCachedStats(@Nullable EnumMap<Stat, Double> stats) { this.cachedStats = stats; }

    // ---------- Синхронизация на клиент ----------
    public void applyClientSync(int points, Map<Perk, Integer> perkDeltas, int refuseTokens, int resetTokens,
                                Collection<Perk> blocked, boolean selectionLocked, int caffeineTimer, int anorexiaTimer, boolean refuseNextMeal,
                                Map<String, Integer> skills) {
        this.points = points;
        this.perks.clear();
        this.perks.putAll(perkDeltas);
        this.refuseTokens = refuseTokens;
        this.resetTokens = resetTokens;
        this.blockedPerks.clear();
        this.blockedPerks.addAll(blocked);
        this.selectionLocked = selectionLocked;
        this.caffeineTimer = caffeineTimer;
        this.anorexiaTimer = anorexiaTimer;
        this.refuseNextMeal = refuseNextMeal;
        this.skillLevels.clear();
        this.skillLevels.putAll(skills);
    }

    // ---------- Копирование при смерти / возвращении из Энда ----------
    public void copyFrom(PlayerPerks source) {
        this.points = source.points;
        this.perks.clear();
        this.perks.putAll(source.perks);
        this.refuseTokens = source.refuseTokens;
        this.resetTokens = source.resetTokens;
        this.blockedPerks.clear();
        this.blockedPerks.addAll(source.blockedPerks);
        this.selectionLocked = source.selectionLocked;
        this.caffeineTimer = source.caffeineTimer;
        this.anorexiaTimer = source.anorexiaTimer;
        this.refuseNextMeal = source.refuseNextMeal;
        this.unluckyCooldown = source.unluckyCooldown;
        this.skillLevels.clear();
        this.skillLevels.putAll(source.skillLevels);
        this.trained.clear();
        this.trained.putAll(source.trained);
        this.skillXp.clear();
        this.skillXp.putAll(source.skillXp);
        this.physiqueApplied.clear();
        this.physiqueApplied.addAll(source.physiqueApplied);
        this.skillGrants.clear();
        this.skillGrants.putAll(source.skillGrants);
        this.decayMinutes.clear();
        this.decayMinutes.putAll(source.decayMinutes);
        this.migrationRefunded = source.migrationRefunded;
    }

    // ---------- NBT ----------
    public void saveNBT(CompoundTag nbt) {
        nbt.putInt("points", points);
        nbt.putInt("refuseTokens", refuseTokens);
        nbt.putInt("resetTokens", resetTokens);
        nbt.putBoolean("selectionLocked", selectionLocked);
        ListTag blocked = new ListTag();
        for (Perk perk : blockedPerks) blocked.add(StringTag.valueOf(perk.getId()));
        nbt.put("blocked", blocked);
        nbt.putInt("caffeineTimer", caffeineTimer);
        nbt.putInt("anorexiaTimer", anorexiaTimer);
        nbt.putBoolean("refuseNextMeal", refuseNextMeal);
        nbt.putInt("unluckyCooldown", unluckyCooldown);

        ListTag list = new ListTag();
        perks.forEach((perk, delta) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", perk.getId());
            entry.putInt("delta", delta);
            list.add(entry);
        });
        nbt.put("perk_entries", list);
        CompoundTag skills = new CompoundTag();
        skillLevels.forEach(skills::putInt);
        nbt.put("skills", skills);
        CompoundTag tr = new CompoundTag();
        trained.forEach(tr::putInt);
        nbt.put("skillsTrained", tr);
        CompoundTag xp = new CompoundTag();
        skillXp.forEach(xp::putDouble);
        nbt.put("skillsXp", xp);
        ListTag applied = new ListTag();
        for (String id : physiqueApplied) applied.add(StringTag.valueOf(id));
        nbt.put("physiqueApplied", applied);
        CompoundTag grants = new CompoundTag();
        skillGrants.forEach(grants::putInt);
        nbt.put("skillGrants", grants);
        CompoundTag decay = new CompoundTag();
        decayMinutes.forEach(decay::putInt);
        nbt.put("decayMinutes", decay);
        nbt.putInt("perksVersion", DATA_VERSION);
        nbt.putInt("migrationRefunded", migrationRefunded);
    }

    public void loadNBT(CompoundTag nbt) {
        this.points = nbt.getInt("points");
        this.refuseTokens = nbt.getInt("refuseTokens");
        this.resetTokens = nbt.getInt("resetTokens");
        this.selectionLocked = nbt.getBoolean("selectionLocked");
        this.blockedPerks.clear();
        ListTag blocked = nbt.getList("blocked", Tag.TAG_STRING);
        for (int i = 0; i < blocked.size(); i++) Perk.fromId(blocked.getString(i)).ifPresent(blockedPerks::add);
        this.caffeineTimer = nbt.getInt("caffeineTimer");
        this.anorexiaTimer = nbt.getInt("anorexiaTimer");
        this.refuseNextMeal = nbt.getBoolean("refuseNextMeal");
        this.unluckyCooldown = nbt.getInt("unluckyCooldown");
        this.skillLevels.clear();
        CompoundTag skills = nbt.getCompound("skills");
        for (String k : skills.getAllKeys()) skillLevels.put(k, skills.getInt(k));
        this.trained.clear();
        CompoundTag tr = nbt.getCompound("skillsTrained");
        for (String k : tr.getAllKeys()) trained.put(k, tr.getInt(k));
        this.skillXp.clear();
        CompoundTag xp = nbt.getCompound("skillsXp");
        for (String k : xp.getAllKeys()) skillXp.put(k, xp.getDouble(k));
        this.physiqueApplied.clear();
        ListTag applied = nbt.getList("physiqueApplied", Tag.TAG_STRING);
        for (int i = 0; i < applied.size(); i++) physiqueApplied.add(applied.getString(i));
        this.skillGrants.clear();
        CompoundTag grants = nbt.getCompound("skillGrants");
        for (String k : grants.getAllKeys()) skillGrants.put(k, grants.getInt(k));
        this.decayMinutes.clear();
        CompoundTag decay = nbt.getCompound("decayMinutes");
        for (String k : decay.getAllKeys()) decayMinutes.put(k, decay.getInt(k));
        this.migrationRefunded = nbt.contains("migrationRefunded") ? nbt.getInt("migrationRefunded") : -1;
        this.perks.clear();
        int version = nbt.getInt("perksVersion");

        if (nbt.contains("perk_entries", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("perk_entries", Tag.TAG_COMPOUND);
            Map<String, Integer> raw = new java.util.LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                raw.put(entry.getString("id"), entry.getInt("delta"));
            }
            if (version < 2) migrateTo2(raw);
            else raw.forEach((id, delta) -> Perk.fromId(id).ifPresentOrElse(perk -> perks.put(perk, delta), () -> points -= delta));
        } else {
            // Сохранения старой версии: список ID без цен. Баллы тогда списывались по старым ценам,
            // поэтому и возвращать их нужно по старым — иначе баланс «уплывёт».
            ListTag legacy = nbt.getList("perks", Tag.TAG_STRING);
            for (int i = 0; i < legacy.size(); i++) {
                Perk.fromId(legacy.getString(i)).ifPresent(perk -> perks.put(perk, legacyDelta(perk)));
            }
        }
    }

    /**
     * Переход на особенности 2.0.0 (решение брата 13.10.2026): у кого старые версии особенностей — снять их и вернуть
     * очки, пусть выберут заново. Наработанные уровни навыков сохраняются.
     * <ul>
     *   <li>особенности, чья суть поменялась, и исчезнувшие (Атлет, Чистюля) — сняты, очки возвращены;</li>
     *   <li>«динамические» физические перки (без очков) — просто сняты;</li>
     *   <li>купленный старый физический перк когда-то сдвинул Силу или Телосложение — этот сдвиг убираем (новый
     *       возьмут заново);</li>
     *   <li>ненаработанные Сила и Телосложение были 4 — оставляем 4, хотя новый обычный уровень 3.</li>
     * </ul>
     */
    private void migrateTo2(Map<String, Integer> raw) {
        Set<String> revoke = Set.of("survivor", "tough", "fragile", "enduring", "weakling", "strongman", "wimp", "smoker", "clean");
        // Сдвиг старта от купленного старого перка (старый обычный уровень — 4).
        Map<String, int[]> oldStart = Map.of("wimp", new int[]{0, -3}, "strongman", new int[]{0, 3},
                "weakling", new int[]{1, -3}, "enduring", new int[]{1, 3}, "athlete", new int[]{1, 5});
        int refunded = 0;
        for (var e : raw.entrySet()) {
            String id = e.getKey();
            int delta = e.getValue();
            int[] shift = oldStart.get(id);
            if (shift != null && delta != 0 && physiqueApplied.contains(id)) {
                String skill = shift[0] == 0 ? "strength" : "fitness";
                int t = getTrained(skill);
                if (t >= 0) setTrained(skill, t - shift[1]);
            }
            var perk = Perk.fromId(id);
            if (delta == 0 && perk.isPresent() && perk.get().getCategory() != Perk.Category.SERVICE && shift != null) continue;
            if (perk.isEmpty() || revoke.contains(id)) {
                points -= delta;
                if (delta != 0) refunded++;
                continue;
            }
            perks.put(perk.get(), delta);
        }
        for (String skill : new String[]{"strength", "fitness"}) if (getTrained(skill) < 0) setTrained(skill, 4);
        physiqueApplied.clear();
        migrationRefunded = refunded;
    }

    /** Цены из версии мода до переноса в конфиг. */
    private static int legacyDelta(Perk perk) {
        int cost = switch (perk) {
            case SURVIVOR -> 10;
            case TOUGH, STRONGMAN, TOURIST, ORGANIZED, LUCKY -> 8;
            case ENDURING, BRAVE, NERD_RAGE, SAILOR, TAILOR, FARMER, COOK -> 6;
            case SOLDIER, BUILDER, MELEE_FIGHTER, FRAGILE -> 7;
            case DRIVER, PILOT, GUNNER, HOMEBODY, UNLUCKY, WEAKLING -> 5;
            case AGILE, ALCOHOLIC, SOLAR_POWERED, LIGHT_TRAVEL, TURTLE, WIMP, SUGGESTIBLE,
                 CAFFEINE_ADDICTION, CLUMSY, TECHNOPHOBE -> 4;
            case DISORGANIZED, DIABETIC, ANOREXIC -> 6;
            case LANDLUBBER, COWARD -> 3;
            case SMOKER -> 2;
            case TEETOTALER -> 1;
            default -> perk.getCost();
        };
        return perk.isPositive() ? -cost : cost;
    }
}

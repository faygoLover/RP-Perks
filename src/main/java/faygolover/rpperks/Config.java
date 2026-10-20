package faygolover.rpperks;

import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.Stat;
import faygolover.rpperks.events.PerkEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.*;

/**
 * Серверный конфиг мода: world/serverconfig/rpperks-server.toml
 * (для новых миров можно положить готовый файл в defaultconfigs/).
 * Сервер сам отправляет его клиентам при входе.
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private Config() {}

    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    private static final Map<Stat, ForgeConfigSpec.DoubleValue> BASELINE = new EnumMap<>(Stat.class);
    private static final Map<Perk, ForgeConfigSpec.IntValue> COSTS = new EnumMap<>(Perk.class);
    private static final Map<Perk, Map<Stat, ForgeConfigSpec.DoubleValue>> BONUSES = new EnumMap<>(Perk.class);

    // ---------- [general] ----------
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HIDDEN_PERKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SERVICE_PERKS;
    public static final ForgeConfigSpec.IntValue DEFAULTS_VERSION;

    // ---------- [selection] ----------
    public static final ForgeConfigSpec.BooleanValue PLAYERS_CAN_SELECT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NEGATIVE_BALANCE;

    // ---------- [baseline] ----------
    public static final ForgeConfigSpec.DoubleValue SPRINT_BOOST_PERCENT;

    // ---------- Механики конкретных перков ----------
    public static final ForgeConfigSpec.DoubleValue NERD_RAGE_HEALTH_PERCENT;
    public static final ForgeConfigSpec.IntValue NERD_RAGE_EFFECT_LEVEL;
    public static final ForgeConfigSpec.IntValue SOLAR_SPEED_LEVEL;
    public static final ForgeConfigSpec.DoubleValue SOLAR_STAMINA_REGEN_BONUS;
    public static final ForgeConfigSpec.IntValue LIGHT_TRAVEL_MAX_ARMOR;
    public static final ForgeConfigSpec.DoubleValue LIGHT_TRAVEL_SPEED_PERCENT;

    public static final ForgeConfigSpec.IntValue ANOREXIA_MAX_FOOD;
    public static final ForgeConfigSpec.DoubleValue ANOREXIA_REFUSE_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue ANOREXIA_REFUSE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue UNLUCKY_RETARGET_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue UNLUCKY_RETARGET_RADIUS;
    public static final ForgeConfigSpec.IntValue UNLUCKY_MISHAP_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue UNLUCKY_FALL_MIN_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue UNLUCKY_FALL_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue UNLUCKY_SPRAIN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue UNLUCKY_TOOL_CHANCE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue UNLUCKY_TOOL_DAMAGE_PERCENT;
    public static final ForgeConfigSpec.IntValue CAFFEINE_WITHDRAWAL_MINUTES;
    public static final ForgeConfigSpec.DoubleValue CAFFEINE_SPEED_PENALTY_PERCENT;
    public static final ForgeConfigSpec.DoubleValue CAFFEINE_ATTACK_SPEED_PENALTY_PERCENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CAFFEINE_ITEMS;
    public static final ForgeConfigSpec.DoubleValue CLUMSY_DROP_CHANCE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue LANDLUBBER_SINK_SPEED;
    public static final ForgeConfigSpec.DoubleValue STRONGMAN_MELEE_PERCENT, WIMP_MELEE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue SURVIVOR_HEAL_MULT, DELICATE_HEAL_MULT, TOUGH_DAMAGE_MULT, FRAGILE_DAMAGE_MULT, FRAGILE_KNOCKBACK_PERCENT;
    public static final ForgeConfigSpec.DoubleValue IRON_STOMACH_POISON_MULT, WEAK_STOMACH_CHANCE_PERCENT, BAD_TEETH_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue ASTHMA_SPRINT_SECONDS, ASTHMA_SLOW_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CLUMSY_RUNNER_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue CLUMSY_RUNNER_COOLDOWN_MINUTES;


    // ---------- [skill_system] ----------
    public static final ForgeConfigSpec.BooleanValue SKILL_GROWTH;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue XP_BASE;
    public static final ForgeConfigSpec.DoubleValue XP_GROWTH;
    public static final ForgeConfigSpec.DoubleValue XP_GUN_SHOT;
    public static final ForgeConfigSpec.DoubleValue XP_GUN_HIT;
    public static final ForgeConfigSpec.DoubleValue XP_EXPLOSIVE_SHOT;
    public static final ForgeConfigSpec.DoubleValue XP_MELEE_HIT;
    public static final ForgeConfigSpec.DoubleValue XP_PER_100_STAMINA;
    public static final ForgeConfigSpec.DoubleValue XP_MEDICINE;
    public static final ForgeConfigSpec.IntValue PHYSICAL_START;
    public static final ForgeConfigSpec.DoubleValue PHYSICAL_XP_COST;
    public static final ForgeConfigSpec.BooleanValue PHYSICAL_DECAY_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> TALENT_MENTAL, TALENT_PHYSICAL;
    public static final ForgeConfigSpec.BooleanValue PHYSICAL_AGE_LEARNING;
    public static final ForgeConfigSpec.DoubleValue LOW_PROTEIN_PHYSICAL_XP;
    public static final ForgeConfigSpec.DoubleValue FAST_LEARNER_XP, SLOW_LEARNER_XP, GYM_LOVER_XP_MULT;
    public static final ForgeConfigSpec.DoubleValue STRENGTH_ATTACK_SPEED_PER_LEVEL, STRENGTH_KNOCKBACK_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue FITNESS_STAMINA_PER_LEVEL, FITNESS_MOVE_COST_PER_LEVEL, FITNESS_SPEED_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue FITNESS_STEP_LEVEL;
    public static final ForgeConfigSpec.DoubleValue DECAY_PER_CAUSE, DECAY_MAX, DECAY_ILL_CONDITION, CAP_DRIFT, OBESE_SPEED_PERCENT;
    public static final ForgeConfigSpec.IntValue DECAY_LOW_PROTEIN_MINUTES, DECAY_STARVING_MINUTES, DECAY_LOW_CARBS_MINUTES,
            DECAY_DEHYDRATED_MINUTES, DECAY_ILL_MINUTES;
    public static final ForgeConfigSpec.IntValue CAP_EXHAUSTED, CAP_UNDER, CAP_OVER, CAP_OBESE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PERK_SKILLS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> T_SPREAD, T_RECOIL, T_RELOAD, T_AIM, T_MELEE_DAMAGE, T_MELEE_SPEED, T_EXPLOSIVE;

    // Таблицы стрельбы. V1 — до 09.10.2026: на уровне 0 оружие слушалось слишком хорошо. Если в конфиге сервера
    // осталась V1 без правок — при загрузке заменяется на V2 (своё, изменённое вручную, не трогаем).
    private static final double[] SPREAD_V1 = {3.0, 2.2, 1.6, 1.35, 1.15, 1.0, 0.9, 0.8, 0.7, 0.62, 0.55};
    private static final double[] RECOIL_V1 = {2.5, 1.9, 1.45, 1.25, 1.1, 1.0, 0.92, 0.84, 0.76, 0.68, 0.6};
    private static final double[] RELOAD_V1 = {2.0, 1.6, 1.3, 1.18, 1.08, 1.0, 0.94, 0.87, 0.8, 0.72, 0.65};
    private static final double[] AIM_V1 = RELOAD_V1;
    // 0 и 1 — по решению 09.10, 2–4 — плавно (геометрически) от 1 к 5, 5–10 — как было.
    private static final double[] SPREAD_V2 = {10.0, 7.0, 4.3, 2.65, 1.63, 1.0, 0.9, 0.8, 0.7, 0.62, 0.55};
    private static final double[] RECOIL_V2 = {10.0, 5.0, 3.34, 2.24, 1.5, 1.0, 0.92, 0.84, 0.76, 0.68, 0.6};
    private static final double[] RELOAD_V2 = {4.0, 3.0, 2.28, 1.73, 1.32, 1.0, 0.94, 0.87, 0.8, 0.72, 0.65};
    private static final double[] AIM_V2 = {4.0, 4.0, 2.83, 2.0, 1.41, 1.0, 0.94, 0.87, 0.8, 0.72, 0.65};
    public static final ForgeConfigSpec.DoubleValue STRENGTH_MELEE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue STRENGTH_HEAVY_RECOIL_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue CLUMSY_WEAPON_PENALTY;
    public static final ForgeConfigSpec.IntValue PACIFIST_COMBAT_CAP;
    public static final ForgeConfigSpec.DoubleValue PACIFIST_HUMAN_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue PACIFIST_COMBAT_XP;
    public static final ForgeConfigSpec.DoubleValue PACIFIST_PEACE_XP;

    // ---------- [conflicts] ----------
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFLICTS;

    public static final ForgeConfigSpec SPEC;

    // ---------- Разобранные значения (обновляются при загрузке/перезагрузке файла) ----------
    private static volatile Set<ResourceLocation> caffeineItems = Set.of();
    private static volatile Set<Perk> hiddenPerks = EnumSet.of(Perk.ADMIN);
    private static volatile Set<Perk> servicePerks;
    private static volatile Map<Perk, Set<Perk>> conflicts = Map.of();
    private static volatile Map<Perk, Map<faygolover.rpperks.skill.SkillDef, Integer>> perkSkills = Map.of();

    static {
        // =====================================================================
        B.comment(
                " ==================================================================",
                "  RP Perks — настройки сервера",
                " ------------------------------------------------------------------",
                "  Как читать этот файл:",
                "   * [baseline] — характеристики КАЖДОГО игрока без перков.",
                "   * В секциях перков указаны ИЗМЕНЕНИЯ относительно базы:",
                "       max_health = 5.0               -> +5 ХП (+2.5 сердца)",
                "       max_health = -5.0              -> -5 ХП",
                "       movement_speed_percent = 25.0  -> скорость +25% от базы",
                "       stamina_cost_multiplier = -0.15 -> расход выносливости 1.0 - 0.15 = x0.85",
                "   * cost — цена перка в баллах. Положительные перки и умения",
                "     её списывают, негативные — начисляют игроку.",
                "   * Все шансы указаны в процентах (5.0 = 5%).",
                "   * (РП) — перк без игровой механики, только для отыгрыша.",
                "   * stamina_* — характеристики мода RP Stamina (rpstamina). Если его нет, они пропускаются.",
                "     База максимальной выносливости задаётся здесь ([baseline] stamina_max).",
                "",
                "  Файл перечитывается на лету: после сохранения характеристики всех",
                "  игроков онлайн пересчитываются сами.",
                " ==================================================================",
                "",
                " Общие настройки")
                .push("general");

        HIDDEN_PERKS = B.comment(
                        " Перки, которые обычный игрок не видит в окне /rpperks и не может взять.",
                        " Операторы (уровень 2+) видят их и выдают через /rpperks admin <ник>.",
                        " Указываются ID перков, например: [\"admin\", \"nerd_rage\"]")
                .defineListAllowEmpty(List.of("hidden_perks"), List.of("admin"), Config::isString);

        SERVICE_PERKS = B.comment(
                        " Перки, которые считаются служебными: вкладка «Служебные» у ГМа, взять самому нельзя,",
                        " выдаёт только администрация (как «Админ»). Указываются ID, например: [\"solar_powered\"]")
                .defineListAllowEmpty(List.of("service_perks"), List.of(), Config::isString);

        DEFAULTS_VERSION = B.comment(
                        " Служебное: до какой версии уже обновлены значения по умолчанию. Не меняйте.")
                .defineInRange("defaults_version", 2, 0, 1000);
        B.pop();

        // =====================================================================
        B.comment(
                        " Самостоятельный выбор перков через окно (/rpperks).",
                        " Взятый перк остаётся навсегда: отказаться от одного перка можно только за жетон отказа,",
                        " сбросить все — за жетон сброса. Жетоны выдаёт администрация:",
                        " в окне /rpperks admin <ник>. Там же можно запретить игроку конкретные перки или закрыть ему выбор целиком.")
                .push("selection");
        PLAYERS_CAN_SELECT = B.comment(" Могут ли игроки сами брать перки в окне. false — только выдача администрацией")
                .define("players_can_select", true);
        ALLOW_NEGATIVE_BALANCE = B.comment(" Можно ли уйти в минус по баллам, беря перки или отказываясь от негативных")
                .define("allow_negative_balance", false);
        B.pop();

        // =====================================================================
        B.comment(
                        " Базовые характеристики всех игроков (без перков).",
                        " «Ваниль» — значение в обычном Minecraft, для ориентира.")
                .push("baseline");
        for (Stat stat : Stat.values()) {
            BASELINE.put(stat, B.comment(" " + stat.getTitle() + ". Ваниль: " + stat.getVanilla())
                    .defineInRange(stat.getKey(), stat.getDefaultBase(), stat.getMin(), stat.getMax()));
        }
        SPRINT_BOOST_PERCENT = B.comment(
                        " Дополнительное ускорение при беге, % (поверх ванильных +30%).",
                        " 20.0 -> бег = ходьба x 1.3 x 1.2. Не действует в режиме Админа.")
                .defineInRange("sprint_boost_percent", 20.0, 0.0, 500.0);
        B.pop();

        // =====================================================================
        B.comment(" ОСНОВНЫЕ ПЕРКИ (положительные)").push("main_perks");

        beginPerk(Perk.ATHLETIC, "Атлетичный: Телосложение +3 к старту (6), +10 % запаса выносливости. Растёт на 20 % быстрее (талант от старта)");
        bonus(Perk.ATHLETIC, Stat.STAMINA_MAX, 100.0);
        B.pop();
        beginPerk(Perk.FIT, "В форме: Телосложение +2 к старту (5)");
        B.pop();
        beginPerk(Perk.STRONGMAN, "Силач: Сила +3 к старту (6), +10 % урона в ближнем бою");
        STRONGMAN_MELEE_PERCENT = B.comment(" Прибавка к урону в ближнем бою, % (навсегда, поверх уровня Силы)")
                .defineInRange("melee_damage_percent", 10.0, 0.0, 500.0);
        B.pop();
        beginPerk(Perk.STRONG, "Сильный: Сила +2 к старту (5)");
        B.pop();

        beginPerk(Perk.ENDURING, "Выносливый: быстрее восстанавливает выносливость, бег и плавание дешевле (RP Stamina)");
        bonus(Perk.ENDURING, Stat.STAMINA_REGEN, 0.2);
        bonus(Perk.ENDURING, Stat.STAMINA_MOVE_COST, -0.1);
        bonus(Perk.ENDURING, Stat.STAMINA_SWIM_COST, -0.1);
        B.pop();
        rpPerk(Perk.SECOND_WIND, "Второе дыхание: раз в час, когда выносливость упала до нуля, сразу +50 % (механика — RP Stamina)");
        rpPerk(Perk.QUICK_REST, "Быстрый отдых: сидя восстанавливается вдвое быстрее (механика — RP Stamina)");
        beginPerk(Perk.GYM_LOVER, "Любитель спорта: с RP Stamina Sports тренировки полезнее; без него Сила и Телосложение от обычной нагрузки растут быстрее");
        GYM_LOVER_XP_MULT = B.comment(" Без Sports-аддона: множитель опыта Силы и Телосложения от обычной нагрузки")
                .defineInRange("xp_multiplier_without_sports", 1.25, 0.0, 10.0);
        B.pop();

        beginPerk(Perk.SURVIVOR, "Живучий. С RP Medicine: заживление +20 %, кровопотеря −20 %, боль −20 % (числа — в конфиге Medicine).",
                "Без Medicine: +2,5 сердца и быстрее восстанавливает здоровье");
        bonus(Perk.SURVIVOR, Stat.MAX_HEALTH, 5.0);
        SURVIVOR_HEAL_MULT = B.comment(" Без Medicine: множитель восстановления здоровья").defineInRange("heal_multiplier", 1.2, 0.0, 10.0);
        B.pop();
        beginPerk(Perk.TOUGH, "Крепкий: −20 % урона по телу (с Medicine — по частям тела), +1 твёрдость брони, меньше отбрасывает");
        bonus(Perk.TOUGH, Stat.ARMOR_TOUGHNESS, 1.0);
        bonus(Perk.TOUGH, Stat.KNOCKBACK_RESISTANCE, 0.2);
        TOUGH_DAMAGE_MULT = B.comment(" Без Medicine: множитель получаемого урона").defineInRange("damage_multiplier", 0.8, 0.0, 10.0);
        B.pop();

        beginPerk(Perk.IRON_STOMACH, "Железный желудок: отравиться едой вдвое реже. Без RP Culinary — ванильное отравление (сырая курица, гнилая плоть) реже");
        IRON_STOMACH_POISON_MULT = B.comment(" Без Culinary: шанс, что ванильное отравление от еды подействует").defineInRange("vanilla_poison_chance", 0.5, 0.0, 1.0);
        B.pop();
        rpPerk(Perk.LIGHT_EATER, "Малоежка: голод медленнее, желудок меньше (механика — RP Culinary)");
        rpPerk(Perk.LOW_THIRST, "Мало пьёт: вода тратится медленнее (механика — RP Culinary)");
        rpPerk(Perk.FAST_METABOLISM, "Быстрый обмен веществ: тратит на 15 % больше, легко держит вес (нужен RP Culinary)");
        rpPerk(Perk.GOURMET, "Гурман: однообразная еда надоедает быстрее, хорошая и вкусная радует сильнее (нужен RP Culinary)");

        beginPerk(Perk.FLEET, "Быстроногий: +10 % скорости");
        bonus(Perk.FLEET, Stat.MOVEMENT_SPEED, 10.0);
        B.pop();
        beginPerk(Perk.LIGHT_TRAVEL, "Путешествие налегке: быстрее в лёгкой броне");
        LIGHT_TRAVEL_MAX_ARMOR = B.comment(" Бонус действует, пока очков брони не больше N")
                .defineInRange("max_armor", 6, 0, 30);
        LIGHT_TRAVEL_SPEED_PERCENT = B.comment(" Прибавка к скорости, % от базы")
                .defineInRange("movement_speed_percent", 10.0, 0.0, 500.0);
        B.pop();
        beginPerk(Perk.AGILE, "Ловкий: дальность взаимодействия +1; с RP Medicine и Sports — зоны мини-игр шире. Пара к Плохой координации");
        bonus(Perk.AGILE, Stat.BLOCK_REACH, 1.0);
        bonus(Perk.AGILE, Stat.ENTITY_REACH, 1.0);
        B.pop();

        beginPerk(Perk.FAST_LEARNER, "Быстрообучаемый: больше опыта всех навыков, кроме Силы и Телосложения");
        FAST_LEARNER_XP = B.comment(" Множитель опыта").defineInRange("xp_multiplier", 1.2, 0.0, 10.0);
        B.pop();
        rpPerk(Perk.QUICK_WORKER, "Расторопный: медицина и готовка на 20 % быстрее (механика — RP Medicine, RP Culinary)");

        rpPerk(Perk.BRAVE, "Храбрый: медленнее поддаётся панике (РП; позже — психика)");
        rpPerk(Perk.STRONG_WILLED, "Волевой: устойчив к когнитивному воздействию (РП; позже — психика)");
        beginPerk(Perk.NERD_RAGE, "При низком здоровье получает Силу и Сопротивление");
        NERD_RAGE_HEALTH_PERCENT = B.comment(" Порог срабатывания: здоровье ниже N% от максимума")
                .defineInRange("health_threshold_percent", 20.0, 1.0, 100.0);
        NERD_RAGE_EFFECT_LEVEL = B.comment(" Уровень эффектов (1 = I, 2 = II ...)")
                .defineInRange("effect_level", 2, 1, 10);
        B.pop();
        rpPerk(Perk.ALCOHOLIC, "Закалённый алкоголик: легко переносит алкоголь (РП)");

        beginPerk(Perk.TOURIST, "Турист: устойчивость к температуре среды (LSO). С RP Medicine — иммунитет крепче");
        bonus(Perk.TOURIST, Stat.THERMAL_RESISTANCE, 2.0);
        B.pop();
        beginPerk(Perk.SOLAR_POWERED, "Днём под открытым небом (без дождя): Скорость и быстрое восстановление выносливости");
        SOLAR_SPEED_LEVEL = B.comment(" Уровень эффекта Скорости (0 = не давать)")
                .defineInRange("speed_effect_level", 1, 0, 10);
        SOLAR_STAMINA_REGEN_BONUS = B.comment(
                        " Прибавка к множителю восстановления выносливости на солнце (0.5 = на 50% быстрее)")
                .defineInRange("stamina_regen_bonus", 0.5, 0.0, 10.0);
        B.pop();
        beginPerk(Perk.LUCKY, "Повышенная удача. Пара к Неудачливому");
        bonus(Perk.LUCKY, Stat.LUCK, 2.0);
        B.pop();
        beginPerk(Perk.ORGANIZED, "+3 слота инвентаря");
        bonus(Perk.ORGANIZED, Stat.INVENTORY_SLOTS, 3.0);
        B.pop();
        B.pop();

        // =====================================================================
        B.comment(" УМЕНИЯ").push("skills");
        beginPerk(Perk.SOLDIER, "Подготовленный солдат: оружие на уровне хорошего бойца, базовая первая помощь (уровни — skill_system.perk_skills)");
        B.pop();
        beginPerk(Perk.MEDIC, "Медицина 8: сложные манипуляции, препараты и оборудование (RP Medicine)");
        B.pop();
        beginPerk(Perk.MELEE_FIGHTER, "Ближний бой 5 (любое оружие ближнего боя и голые руки), меньше устаёт в бою");
        bonus(Perk.MELEE_FIGHTER, Stat.STAMINA_MELEE_COST, -0.2);
        B.pop();
        beginPerk(Perk.BUILDER, "Непрофессиональные инженерно-строительные работы (РП). Добыча блоков меньше утомляет");
        bonus(Perk.BUILDER, Stat.STAMINA_MINE_COST, -0.2);
        B.pop();
        beginPerk(Perk.GUNNER, "Короткоствольное оружие 4, длинноствольное 1");
        B.pop();
        beginPerk(Perk.EXPLOSIVES, "Взрывчатка 5, тяжёлое оружие 1; создание и обезвреживание взрывных устройств (РП)");
        B.pop();
        rpPerk(Perk.TECHNICIAN, "Работа со сложным оборудованием и техникой, ограниченный ремонт (РП)");
        rpPerk(Perk.HACKER, "Взлом замков, электронных систем, дверей и терминалов (РП)");
        rpPerk(Perk.COOK, "Кулинария 5: хорошие блюда, сложные рецепты (уровень — skill_system.perk_skills; с RP Culinary — качество блюд)");
        beginPerk(Perk.BASIC_TRAINING, "Курс молодого бойца: короткоствольное и длинноствольное 2, ближний бой 1, медицина 1");
        B.pop();
        beginPerk(Perk.SAILOR, "Рыбалка, повышенная скорость плавания, плавание меньше утомляет");
        bonus(Perk.SAILOR, Stat.SWIM_SPEED, 0.4);
        bonus(Perk.SAILOR, Stat.STAMINA_SWIM_COST, -0.4);
        B.pop();
        rpPerk(Perk.TAILOR, "Шитьё и ремонт одежды (РП)");
        rpPerk(Perk.FARMER, "Фермерство, сбор и различение диких растений (РП)");
        rpPerk(Perk.DRIVER, "Минимальное управление наземным транспортом (РП)");
        rpPerk(Perk.PILOT, "Минимальное управление воздушным транспортом (РП)");
        beginPerk(Perk.FIRST_AID, "Медицина 3: полная первая помощь, базовое обращение с медицинскими вещами");
        B.pop();
        rpPerk(Perk.DIETITIAN, "Диетолог: точный состав еды и советы «что съесть» (нужен RP Culinary)");
        rpPerk(Perk.FIELD_COOK, "Полевой повар: на костре и в коптильне готовит как на 2 уровня Кулинарии выше (нужен RP Culinary)");
        B.pop();

        // =====================================================================
        B.comment(" НАВЫКИ: уровни 0–10, рост от практики, влияние на оружие, ближний бой, Силу и Телосложение").push("skill_system");
        SKILL_GROWTH = B.comment(" Навыки растут от практики (оружие, ближний бой, медицина, нагрузка). false — только перки и ГМ.")
                .define("growth_enabled", true);
        XP_MULTIPLIER = B.comment(" Общий множитель опыта (2.0 — вдвое быстрее)").defineInRange("xp_multiplier", 1.0, 0.0, 100.0);
        XP_BASE = B.comment(" Опыт на уровень 0 -> 1").defineInRange("xp_base", 150.0, 1.0, 1.0e7);
        XP_GROWTH = B.comment(" Каждый следующий уровень дороже во столько раз").defineInRange("xp_growth", 1.6, 1.0, 10.0);
        XP_GUN_SHOT = B.comment(" Опыт оружейного навыка за выстрел").defineInRange("xp_gun_shot", 0.15, 0.0, 1000.0);
        XP_GUN_HIT = B.comment(" Опыт оружейного навыка за попадание по живой цели").defineInRange("xp_gun_hit", 1.0, 0.0, 1000.0);
        XP_EXPLOSIVE_SHOT = B.comment(" Опыт «Взрывчатки» за выстрел из гранатомёта").defineInRange("xp_explosive_shot", 1.0, 0.0, 1000.0);
        XP_MELEE_HIT = B.comment(" Опыт ближнего боя за удар по живой цели").defineInRange("xp_melee_hit", 1.0, 0.0, 1000.0);
        XP_PER_100_STAMINA = B.comment(" Опыт Силы/Телосложения за каждые 100 потраченной выносливости (нужен RP Stamina)")
                .defineInRange("xp_per_100_stamina", 1.0, 0.0, 1000.0);
        XP_MEDICINE = B.comment(" Множитель опыта Медицины (сколько даёт лечение — решает RP Medicine)").defineInRange("xp_medicine", 1.0, 0.0, 100.0);
        PHYSICAL_START = B.comment(" Сила и Телосложение у игрока без физических особенностей (обычный человек).",
                        " Особенности сдвигают старт: Хиляк/Слабак −3, Не в форме/Слабый −2, В форме/Сильный +2, Атлетичный/Силач +3")
                .defineInRange("physical_start_level", 3, 0, 10);
        PHYSICAL_XP_COST = B.comment(" Сила и Телосложение: во сколько раз больше опыта нужно на уровень, чем другим навыкам")
                .defineInRange("physical_xp_cost", 10.0, 0.1, 1000.0);
        TALENT_MENTAL = table("talent_mental", " Талант: скорость роста умственных и боевых навыков по СТАРТОВОМУ уровню 0, 1, 2, 3+ (старт — от умений)",
                0.7, 0.8, 1.0, 1.2);
        TALENT_PHYSICAL = table("talent_physical", " Талант: скорость роста Силы и Телосложения по стартовому уровню 0..6+ (старт — от особенностей)",
                0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2);
        PHYSICAL_AGE_LEARNING = B.comment(" Возраст из анкеты тела (RP Core) влияет на рост Силы и Телосложения: 16–17 ×1,1 … 61+ ×0,6")
                .define("age_affects_physical", true);
        LOW_PROTEIN_PHYSICAL_XP = B.comment(" С RP Culinary: мало белка (запас ниже 20) — множитель опыта Силы и Телосложения")
                .defineInRange("low_protein_physical_xp", 0.5, 0.0, 1.0);
        PERK_SKILLS = B.comment(" Уровни навыков от перков: \"перк = навык:уровень, ...\". Перк даёт МИНИМУМ уровня; от нескольких перков берётся наибольший.",
                        " Навыки: medicine, cooking, short_arms, long_arms, heavy_arms, explosives, melee.",
                        " Сила и Телосложение задают физические особенности (physical_start_level и сдвиги), не эта таблица.")
                .defineListAllowEmpty(List.of("perk_skills"), List.of(
                        "first_aid = medicine:3",
                        "medic = medicine:8",
                        "cook = cooking:5",
                        "basic_training = short_arms:2, long_arms:2, melee:1, medicine:1",
                        "gunner = short_arms:4, long_arms:1",
                        "explosives = explosives:5, heavy_arms:1",
                        "melee_fighter = melee:5",
                        "soldier = short_arms:5, long_arms:5, heavy_arms:3, explosives:2, melee:3, medicine:2"
                ), Config::isString);
        T_SPREAD = table("weapon_spread", " Разброс оружия по уровню 0..10 (5 = как в TaCZ)", SPREAD_V2);
        T_RECOIL = table("weapon_recoil", " Отдача по уровню 0..10", RECOIL_V2);
        T_RELOAD = table("weapon_reload", " Время перезарядки по уровню 0..10 (во сколько раз дольше)", RELOAD_V2);
        T_AIM = table("weapon_aim", " Время прицеливания по уровню 0..10 (во сколько раз дольше)", AIM_V2);
        T_MELEE_DAMAGE = table("melee_damage", " Урон в ближнем бою (любое оружие и кулаки) по уровню 0..10", 0.7, 0.76, 0.82, 0.88, 0.94, 1.0, 1.08, 1.16, 1.24, 1.32, 1.4);
        T_MELEE_SPEED = table("melee_speed", " Скорость ударов по уровню 0..10", 0.8, 0.84, 0.88, 0.92, 0.96, 1.0, 1.05, 1.1, 1.15, 1.2, 1.25);
        T_EXPLOSIVE = table("explosive_power", " Сила и радиус взрывов своего оружия по уровню «Взрывчатки» 0..10", 0.6, 0.68, 0.76, 0.84, 0.92, 1.0, 1.06, 1.12, 1.18, 1.24, 1.3);
        STRENGTH_MELEE_PER_LEVEL = B.comment(" Сила: прибавка к урону в ближнем бою за уровень выше обычного (physical_start_level) и убавка ниже")
                .defineInRange("strength_melee_per_level", 0.05, 0.0, 1.0);
        STRENGTH_ATTACK_SPEED_PER_LEVEL = B.comment(" Сила: прибавка к скорости ударов за уровень выше обычного")
                .defineInRange("strength_attack_speed_per_level", 0.03, 0.0, 1.0);
        STRENGTH_KNOCKBACK_PER_LEVEL = B.comment(" Сила: прибавка к отбрасыванию от ударов за уровень выше обычного")
                .defineInRange("strength_knockback_per_level", 0.08, 0.0, 1.0);
        STRENGTH_HEAVY_RECOIL_PER_LEVEL = B.comment(" Сила: насколько меньше отдача тяжёлого оружия за уровень выше обычного")
                .defineInRange("strength_heavy_recoil_per_level", 0.03, 0.0, 1.0);
        FITNESS_STAMINA_PER_LEVEL = B.comment(" Телосложение: прибавка к запасу И скорости восстановления выносливости за уровень выше обычного (RP Stamina)")
                .defineInRange("fitness_stamina_per_level", 0.06, 0.0, 1.0);
        FITNESS_MOVE_COST_PER_LEVEL = B.comment(" Телосложение: насколько меньше трата выносливости на бег, прыжки, лестницы за уровень выше обычного")
                .defineInRange("fitness_move_cost_per_level", 0.025, 0.0, 1.0);
        FITNESS_SPEED_PER_LEVEL = B.comment(" Телосложение: прибавка к скорости передвижения за уровень выше обычного")
                .defineInRange("fitness_speed_per_level", 0.015, 0.0, 1.0);
        FITNESS_STEP_LEVEL = B.comment(" Телосложение: с этого уровня шагает на блок без прыжка (0 — никогда)")
                .defineInRange("fitness_step_level", 9, 0, 10);
        CLUMSY_WEAPON_PENALTY = B.comment(" Плохая координация: минус к уровням огнестрельных навыков").defineInRange("clumsy_weapon_penalty", 1, 0, 10);
        PACIFIST_COMBAT_CAP = B.comment(" Пацифист: боевые навыки не выше").defineInRange("pacifist_combat_cap", 1, 0, 10);
        PACIFIST_HUMAN_DAMAGE = B.comment(" Пацифист: множитель урона по людям (игроки и сущности из тега rpperks:humans)")
                .defineInRange("pacifist_human_damage", 0.25, 0.0, 1.0);
        PACIFIST_COMBAT_XP = B.comment(" Пацифист: множитель опыта боевых навыков").defineInRange("pacifist_combat_xp", 0.1, 0.0, 10.0);
        PACIFIST_PEACE_XP = B.comment(" Пацифист: множитель опыта мирных навыков (медицина, кулинария)").defineInRange("pacifist_peace_xp", 1.25, 0.0, 10.0);
        B.pop();

        // =====================================================================
        B.comment(" ПОТЕРЯ СИЛЫ И ТЕЛОСЛОЖЕНИЯ: не от простоя, а от плохого питания (RP Culinary), долгой болезни (RP Medicine)",
                        " и веса вне нормы (потолок уровня). Можно упасть и ниже стартового уровня: тело правда слабеет.")
                .push("physical_decay");
        PHYSICAL_DECAY_ENABLED = B.comment(" Сила и Телосложение могут падать (false — не падают никогда)")
                .define("physical_decay_enabled", true);
        DECAY_PER_CAUSE = B.comment(" Потеря опыта за минуту в сети на каждую причину").defineInRange("xp_per_minute_per_cause", 10.0, 0.0, 10000.0);
        DECAY_MAX = B.comment(" Не больше столько опыта за минуту, сколько бы причин ни было").defineInRange("xp_per_minute_max", 20.0, 0.0, 10000.0);
        DECAY_LOW_PROTEIN_MINUTES = B.comment(" Мало белка (ниже 20) дольше N минут в сети — падает Сила").defineInRange("low_protein_minutes", 360, 1, 100000);
        DECAY_STARVING_MINUTES = B.comment(" Голодает (сытость 0) дольше N минут — падают Сила и Телосложение").defineInRange("starving_minutes", 120, 1, 100000);
        DECAY_LOW_CARBS_MINUTES = B.comment(" Мало углеводов дольше N минут — падает Телосложение").defineInRange("low_carbs_minutes", 360, 1, 100000);
        DECAY_DEHYDRATED_MINUTES = B.comment(" Обезвожен дольше N минут — падает Телосложение").defineInRange("dehydrated_minutes", 60, 1, 100000);
        DECAY_ILL_MINUTES = B.comment(" Тяжело болен (RP Medicine) дольше N минут — падают Сила и Телосложение").defineInRange("ill_minutes", 120, 1, 100000);
        DECAY_ILL_CONDITION = B.comment(" «Тяжело болен» — общее состояние тела ниже этой доли (0–1)").defineInRange("ill_condition", 0.5, 0.0, 1.0);
        CAP_DRIFT = B.comment(" Вес вне нормы: навык выше потолка сползает к нему, опыта за минуту").defineInRange("cap_drift_per_minute", 5.0, 0.0, 10000.0);
        CAP_EXHAUSTED = B.comment(" Потолок Силы и Телосложения при истощении").defineInRange("cap_exhausted", 2, 0, 10);
        CAP_UNDER = B.comment(" Потолок Силы и Телосложения ниже нормы веса").defineInRange("cap_under", 5, 0, 10);
        CAP_OVER = B.comment(" Потолок Телосложения выше нормы веса").defineInRange("cap_over", 6, 0, 10);
        CAP_OBESE = B.comment(" Потолок Телосложения при ожирении").defineInRange("cap_obese", 4, 0, 10);
        OBESE_SPEED_PERCENT = B.comment(" Жирный без RP Culinary (вес никто не ведёт): штраф к скорости, %").defineInRange("obese_speed_penalty_percent", 10.0, 0.0, 100.0);
        B.pop();

        // =====================================================================
        B.comment(" НЕГАТИВНЫЕ ПЕРКИ (cost — сколько баллов получает игрок)").push("negative_perks");

        beginPerk(Perk.PUNY, "Хиляк: Телосложение −3 к старту (0), −10 % запаса выносливости. Растёт на 20 % медленнее (талант от старта)");
        bonus(Perk.PUNY, Stat.STAMINA_MAX, -100.0);
        B.pop();
        beginPerk(Perk.OUT_OF_SHAPE, "Не в форме: Телосложение −2 к старту (1)");
        B.pop();
        beginPerk(Perk.WIMP, "Слабак: Сила −3 к старту (0), −10 % урона и отбрасывания в ближнем бою");
        WIMP_MELEE_PERCENT = B.comment(" Убавка урона и отбрасывания в ближнем бою, % (навсегда)")
                .defineInRange("melee_penalty_percent", 10.0, 0.0, 100.0);
        B.pop();
        beginPerk(Perk.WEAK, "Слабый: Сила −2 к старту (1)");
        B.pop();

        rpPerk(Perk.EMACIATED, "Дистрофик: старт ниже нормы веса (RP Culinary). Сила и Телосложение не выше 5");
        rpPerk(Perk.OVERWEIGHT, "Избыточный вес: старт выше нормы (RP Culinary). Телосложение не выше 6");
        rpPerk(Perk.OBESE, "Жирный: старт с ожирением (RP Culinary). Телосложение не выше 4; без Culinary ещё и медленнее");

        beginPerk(Perk.WEAKLING, "Задохлик: медленнее восстанавливает выносливость, бег и плавание дороже (RP Stamina)");
        bonus(Perk.WEAKLING, Stat.STAMINA_REGEN, -0.2);
        bonus(Perk.WEAKLING, Stat.STAMINA_MOVE_COST, 0.1);
        bonus(Perk.WEAKLING, Stat.STAMINA_SWIM_COST, 0.1);
        B.pop();
        beginPerk(Perk.ASTHMATIC, "Астматик. С RP Medicine: на бегу падает кислород, приступы, нужен ингалятор. Без неё: после долгого бега — одышка (Медлительность)");
        ASTHMA_SPRINT_SECONDS = B.comment(" Без Medicine: после стольких секунд бега подряд — одышка").defineInRange("sprint_seconds", 15, 1, 600);
        ASTHMA_SLOW_SECONDS = B.comment(" Без Medicine: длительность одышки (Медлительность I), секунд").defineInRange("slow_seconds", 10, 1, 600);
        B.pop();

        beginPerk(Perk.DELICATE, "Нежный. С RP Medicine: заживление −20 %, кровопотеря +20 %, боль +20 %. Без неё: медленнее восстанавливает здоровье");
        DELICATE_HEAL_MULT = B.comment(" Без Medicine: множитель восстановления здоровья").defineInRange("heal_multiplier", 0.8, 0.0, 10.0);
        B.pop();
        beginPerk(Perk.FRAGILE, "Хрупкий: +20 % урона по телу (с Medicine — по частям тела), сильнее отбрасывает");
        FRAGILE_DAMAGE_MULT = B.comment(" Без Medicine: множитель получаемого урона").defineInRange("damage_multiplier", 1.2, 0.0, 10.0);
        FRAGILE_KNOCKBACK_PERCENT = B.comment(" Насколько сильнее отбрасывает, %").defineInRange("knockback_percent", 20.0, 0.0, 500.0);
        B.pop();
        rpPerk(Perk.AMPUTEE, "Ампутант: старт без руки или ноги (механика — RP Medicine)");
        rpPerk(Perk.DIABETIC, "Требуется ежедневная доза инсулина (с RP Medicine — настоящий сахар в крови, иначе РП)");

        beginPerk(Perk.WEAK_STOMACH, "Слабый желудок: травится чаще. Без RP Culinary — иногда тошнит от любой еды");
        WEAK_STOMACH_CHANCE_PERCENT = B.comment(" Без Culinary: шанс после еды получить Голод на 15 с").defineInRange("vanilla_sick_chance_percent", 5.0, 0.0, 100.0);
        B.pop();
        rpPerk(Perk.BIG_EATER, "Обжора: голод быстрее, желудок больше (механика — RP Culinary)");
        beginPerk(Perk.ANOREXIC, "Пониженный предел сытости и внезапные отказы от еды");
        ANOREXIA_MAX_FOOD = B.comment(" Без RP Culinary: максимальный уровень голода (из 20; 14 = 7 окорочков). С ним — желудок меньше (настройки Кулинарии)")
                .defineInRange("max_food_level", 14, 1, 20);
        ANOREXIA_REFUSE_CHANCE_PERCENT = B.comment(
                        " Шанс после каждого приёма пищи, что следующая попытка поесть будет отменена",
                        " и запустится таймер «не хочу есть». При голоде 0 новый отказ не возникает.")
                .defineInRange("refuse_chance_percent", 20.0, 0.0, 100.0);
        ANOREXIA_REFUSE_SECONDS = B.comment(" Длительность отказа от еды, реальные секунды")
                .defineInRange("refuse_seconds", 120, 1, 36000);
        B.pop();
        rpPerk(Perk.HIGH_THIRST, "Много пьёт: вода тратится быстрее (механика — RP Culinary)");
        rpPerk(Perk.SLOW_METABOLISM, "Склонность к полноте: тратит на 15 % меньше, легко набирает вес (нужен RP Culinary)");
        beginPerk(Perk.BAD_TEETH, "Плохие зубы: боль при еде. Без RP Culinary — еда иногда ранит");
        BAD_TEETH_CHANCE_PERCENT = B.comment(" Без Culinary: шанс, что еда нанесёт урон").defineInRange("vanilla_hurt_chance_percent", 20.0, 0.0, 100.0);
        B.pop();

        beginPerk(Perk.TURTLE, "Черепаха: пониженная скорость передвижения");
        bonus(Perk.TURTLE, Stat.MOVEMENT_SPEED, -15.0);
        B.pop();
        beginPerk(Perk.CLUMSY_RUNNER, "Неуклюжий бегун: на бегу иногда спотыкается (с RP Medicine — может подвернуть ногу)");
        CLUMSY_RUNNER_CHANCE_PERCENT = B.comment(" Шанс споткнуться за секунду бега").defineInRange("trip_chance_percent", 0.5, 0.0, 100.0);
        CLUMSY_RUNNER_COOLDOWN_MINUTES = B.comment(" Не чаще раза в N минут").defineInRange("cooldown_minutes", 10, 1, 1440);
        B.pop();
        beginPerk(Perk.CLUMSY, "Иногда роняет предмет из рук при использовании");
        CLUMSY_DROP_CHANCE_PERCENT = B.comment(" Шанс выронить предмет при использовании (ПКМ)")
                .defineInRange("drop_chance_percent", 5.0, 0.0, 100.0);
        B.pop();
        beginPerk(Perk.LANDLUBBER, "Не умеет плавать: ходит по дну, не может всплыть");
        bonus(Perk.LANDLUBBER, Stat.SWIM_SPEED, -0.3);
        LANDLUBBER_SINK_SPEED = B.comment(" Скорость погружения в толще воды, блоков за тик")
                .defineInRange("sink_speed", 0.03, 0.0, 1.0);
        B.pop();

        beginPerk(Perk.SLOW_LEARNER, "Плохообучаемый: меньше опыта всех навыков, кроме Силы и Телосложения");
        SLOW_LEARNER_XP = B.comment(" Множитель опыта").defineInRange("xp_multiplier", 0.8, 0.0, 10.0);
        B.pop();
        rpPerk(Perk.SLOW_WORKER, "Медлительный: медицина и готовка на 20 % медленнее (механика — RP Medicine, RP Culinary)");
        rpPerk(Perk.ILLITERATE, "Неграмотный: не читает тексты (с RP PDA — сообщения каракулями, иначе РП)");

        rpPerk(Perk.COWARD, "Трусливый: быстро поддаётся панике (РП; позже — психика)");
        rpPerk(Perk.SUGGESTIBLE, "Внушаемый: легче поддаётся когнитивному воздействию (РП; позже — психика)");
        rpPerk(Perk.HEMOPHOBIC, "Гемофоб: паника при виде крови (с RP Medicine — лечение с кровью труднее, иначе РП)");
        beginPerk(Perk.PACIFIST, "Не воюет с людьми: урон по людям сильно снижен, боевые навыки не выше 1 и почти не растут; мирные навыки учит быстрее");
        B.pop();
        beginPerk(Perk.CAFFEINE_ADDICTION, "Без кофеина падает скорость бега и атаки. После сна — сразу ломка");
        CAFFEINE_WITHDRAWAL_MINUTES = B.comment(" Через сколько реальных минут без кофеина начинается ломка")
                .defineInRange("withdrawal_minutes", 60, 1, 1440);
        CAFFEINE_SPEED_PENALTY_PERCENT = B.comment(" Штраф к скорости во время ломки, %")
                .defineInRange("speed_penalty_percent", 30.0, 0.0, 100.0);
        CAFFEINE_ATTACK_SPEED_PENALTY_PERCENT = B.comment(" Штраф к скорости атаки во время ломки, %")
                .defineInRange("attack_speed_penalty_percent", 30.0, 0.0, 100.0);
        CAFFEINE_ITEMS = B.comment(" ID предметов, которые снимают ломку (едят или пьют)")
                .defineListAllowEmpty(List.of("caffeine_items"), List.of("minecraft:cookie"), Config::isString);
        B.pop();
        rpPerk(Perk.ALCOHOL_ADDICT, "Алкоголик: без алкоголя — стресс, потом ломка (механика — RP Medicine, иначе РП)");
        rpPerk(Perk.SMOKER, "Курильщик: без курения настроение хуже, ломка (механика — RP Medicine, иначе РП)");
        rpPerk(Perk.TEETOTALER, "Не переносит алкоголь (РП)");

        beginPerk(Perk.HOMEBODY, "Домашний: пониженная устойчивость к температуре (LSO). С RP Medicine — иммунитет слабее");
        bonus(Perk.HOMEBODY, Stat.THERMAL_RESISTANCE, -2.0);
        B.pop();
        beginPerk(Perk.SUN_SENSITIVE, "Чувствительность к солнцу: днём под открытым небом болит голова (с RP Medicine), без неё — Слабость");
        B.pop();
        beginPerk(Perk.UNLUCKY, "Неприятности случаются именно с вами");
        bonus(Perk.UNLUCKY, Stat.LUCK, -2.0);
        UNLUCKY_RETARGET_CHANCE_PERCENT = B.comment(
                        " «Магнит для неприятностей»: шанс, что монстр, выбравший целью другого игрока,",
                        " переключится на Неудачливого поблизости (если видит его)")
                .defineInRange("retarget_chance_percent", 25.0, 0.0, 100.0);
        UNLUCKY_RETARGET_RADIUS = B.comment(" Радиус поиска Неудачливого вокруг монстра, блоков")
                .defineInRange("retarget_radius", 16, 1, 64);
        UNLUCKY_MISHAP_COOLDOWN_MINUTES = B.comment(
                        " «Закон подлости»: после любой мелкой неприятности ниже",
                        " следующая возможна не раньше, чем через N реальных минут")
                .defineInRange("mishap_cooldown_minutes", 45, 1, 1440);
        UNLUCKY_FALL_MIN_HEIGHT = B.comment(" Подвёрнутая нога: минимальная высота падения, блоков")
                .defineInRange("sprain_min_fall_height", 4.0, 1.0, 256.0);
        UNLUCKY_FALL_CHANCE_PERCENT = B.comment(" Подвёрнутая нога: шанс при таком падении")
                .defineInRange("sprain_chance_percent", 30.0, 0.0, 100.0);
        UNLUCKY_SPRAIN_SECONDS = B.comment(" Подвёрнутая нога: длительность Замедления I, секунд")
                .defineInRange("sprain_seconds", 60, 1, 3600);
        UNLUCKY_TOOL_CHANCE_PERCENT = B.comment(" Соскользнувший инструмент: шанс при ломании блока")
                .defineInRange("tool_mishap_chance_percent", 2.0, 0.0, 100.0);
        UNLUCKY_TOOL_DAMAGE_PERCENT = B.comment(" Соскользнувший инструмент: потеря прочности, % от максимума")
                .defineInRange("tool_mishap_damage_percent", 5.0, 0.0, 100.0);
        B.pop();
        beginPerk(Perk.DISORGANIZED, "-3 слота инвентаря");
        bonus(Perk.DISORGANIZED, Stat.INVENTORY_SLOTS, -3.0);
        B.pop();

        rpPerk(Perk.TECHNOPHOBE, "Боится и не понимает сложную технику и электронику (РП)");
        rpPerk(Perk.KITCHEN_NIGHTMARE, "Кухонный кошмар: готовит на 3 уровня Кулинарии хуже, чаще подгорает (нужен RP Culinary)");
        B.pop();

        // =====================================================================
        B.comment(" СЛУЖЕБНЫЕ ПЕРКИ").push("service_perks");
        beginPerk(Perk.ADMIN,
                "Снимает все ограничения: ванильные характеристики, выносливость не тратится,",
                " полный инвентарь. Действие всех остальных перков приостановлено, пока Админ активен.");
        B.pop();
        B.pop();

        // На случай, если в enum добавят перк и забудут описать его выше
        List<Perk> forgotten = Arrays.stream(Perk.values()).filter(p -> !COSTS.containsKey(p)).toList();
        if (!forgotten.isEmpty()) {
            B.comment(" Перки без отдельного описания").push("other_perks");
            for (Perk perk : forgotten) rpPerk(perk, "");
            B.pop();
        }

        // =====================================================================
        B.comment(" Несовместимые перки").push("conflicts");
        CONFLICTS = B.comment(
                        " Формат: \"перк = перк2, перк3\". Связь работает в обе стороны.",
                        " При выдаче перка все несовместимые с ним снимаются, баллы за них возвращаются.")
                .defineListAllowEmpty(List.of("pairs"), List.of(
                        "puny = out_of_shape, fit, athletic",
                        "out_of_shape = fit, athletic, emaciated, obese",
                        "fit = athletic, emaciated, overweight, obese",
                        "athletic = emaciated, overweight, obese, weakling, asthmatic, turtle, clumsy_runner",
                        "wimp = weak, strong, strongman, melee_fighter",
                        "weak = strong, strongman",
                        "strong = strongman, emaciated",
                        "strongman = emaciated",
                        "emaciated = overweight, obese, big_eater, slow_metabolism",
                        "overweight = obese, anorexic",
                        "obese = weak_stomach, anorexic, fast_metabolism",
                        "enduring = weakling, asthmatic",
                        "weakling = asthmatic",
                        "survivor = delicate, fragile",
                        "tough = delicate, fragile",
                        "iron_stomach = weak_stomach",
                        "light_eater = big_eater, anorexic",
                        "big_eater = anorexic",
                        "low_thirst = high_thirst",
                        "fast_metabolism = slow_metabolism",
                        "slow_metabolism = anorexic",
                        "fleet = turtle",
                        "agile = clumsy, clumsy_runner",
                        "sailor = landlubber",
                        "fast_learner = slow_learner",
                        "quick_worker = slow_worker",
                        "brave = coward",
                        "strong_willed = suggestible",
                        "medic = first_aid, hemophobic",
                        "pacifist = soldier, gunner, explosives, melee_fighter, basic_training, nerd_rage",
                        "soldier = gunner, basic_training",
                        "alcohol_addict = teetotaler",
                        "alcoholic = teetotaler",
                        "tourist = homebody",
                        "solar_powered = sun_sensitive",
                        "lucky = unlucky",
                        "organized = disorganized",
                        "technophobe = technician, hacker, pilot",
                        "kitchen_nightmare = cook, dietitian, field_cook"
                ), Config::isString);
        B.pop();

        SPEC = B.build();
    }

    // ---------------------------------------------------------------------
    // Построение секций
    // ---------------------------------------------------------------------

    private static void beginPerk(Perk perk, String... description) {
        String kind = switch (perk.getCategory()) {
            case MAIN -> "положительный";
            case SKILL -> "умение";
            case NEGATIVE -> "негативный";
            case SERVICE -> "служебный";
        };
        List<String> lines = new ArrayList<>();
        lines.add(" --- " + perk.getTitle() + " (" + kind + ") ---");
        for (String line : description) {
            if (!line.isEmpty()) lines.add(" " + line);
        }
        B.comment(lines.toArray(String[]::new)).push(perk.getId());

        String costHint = perk.isPositive()
                ? " Цена в баллах (списывается при выдаче)"
                : " Сколько баллов получает игрок, взявший этот перк";
        COSTS.put(perk, B.comment(costHint).defineInRange("cost", perk.getDefaultCost(), 0, 1000));
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends Double>> table(String key, String comment, double... values) {
        List<Double> def = new ArrayList<>();
        for (double v : values) def.add(v);
        return B.comment(comment).defineList(key, def, o -> o instanceof Number);
    }

    /** Таблица в конфиге совпадает со старой по умолчанию — записать новую. */
    @SuppressWarnings("unchecked")
    private static boolean upgradeTable(ForgeConfigSpec.ConfigValue<List<? extends Double>> t, double[] oldDef, double[] newDef) {
        List<? extends Number> l = (List<? extends Number>) (List<?>) t.get();
        if (l.size() != oldDef.length) return false;
        for (int i = 0; i < oldDef.length; i++) if (Math.abs(l.get(i).doubleValue() - oldDef[i]) > 1e-9) return false;
        List<Double> v = new ArrayList<>();
        for (double d : newDef) v.add(d);
        t.set(v);
        t.save();
        return true;
    }

    /** Цены по умолчанию до 2.0.0 (у тех особенностей, чья цена поменялась). */
    private static final Map<Perk, Integer> OLD_COSTS = Map.ofEntries(
            Map.entry(Perk.SURVIVOR, 7), Map.entry(Perk.TOUGH, 6), Map.entry(Perk.STRONGMAN, 5), Map.entry(Perk.NERD_RAGE, 4),
            Map.entry(Perk.SOLAR_POWERED, 3), Map.entry(Perk.LIGHT_TRAVEL, 3), Map.entry(Perk.LUCKY, 5), Map.entry(Perk.ORGANIZED, 5),
            Map.entry(Perk.TOURIST, 5), Map.entry(Perk.SMOKER, 1), Map.entry(Perk.GOURMET, 2), Map.entry(Perk.FRAGILE, 8),
            Map.entry(Perk.DISORGANIZED, 7), Map.entry(Perk.DIABETIC, 7), Map.entry(Perk.ANOREXIC, 6), Map.entry(Perk.WEAKLING, 6),
            Map.entry(Perk.HOMEBODY, 5), Map.entry(Perk.UNLUCKY, 5), Map.entry(Perk.TURTLE, 5), Map.entry(Perk.WIMP, 5),
            Map.entry(Perk.SUGGESTIBLE, 5), Map.entry(Perk.CAFFEINE_ADDICTION, 5), Map.entry(Perk.COWARD, 4), Map.entry(Perk.CLUMSY, 4),
            Map.entry(Perk.LANDLUBBER, 3), Map.entry(Perk.KITCHEN_NIGHTMARE, 3), Map.entry(Perk.SLOW_METABOLISM, 2));

    /**
     * 2.0.0: в конфиге сервера осталось старое значение по умолчанию — записать новое. Своё, изменённое вручную, не трогаем
     * (как с таблицами стрельбы). Возвращает, сколько значений обновлено.
     */
    private static int upgradeDefaults2() {
        // Один раз: иначе своё значение, совпавшее со старым по умолчанию, сбрасывалось при каждом запуске.
        // Новые конфиги и уже обновлённые получают defaults_version = 2 по умолчанию и сюда не заходят.
        if (DEFAULTS_VERSION.get() >= 2) return 0;
        DEFAULTS_VERSION.set(2);
        int n = 1;
        for (var e : OLD_COSTS.entrySet()) {
            ForgeConfigSpec.IntValue v = COSTS.get(e.getKey());
            if (v != null && v.get().equals(e.getValue()) && e.getKey().getDefaultCost() != e.getValue()) {
                v.set(e.getKey().getDefaultCost());
                n++;
            }
        }
        if (PHYSICAL_START.get() == 4) { PHYSICAL_START.set(3); n++; }
        if (Math.abs(STRENGTH_MELEE_PER_LEVEL.get() - 0.03) < 1e-9) { STRENGTH_MELEE_PER_LEVEL.set(0.05); n++; }
        n += upgradeBonus(Perk.TURTLE, Stat.MOVEMENT_SPEED, -17.0, -15.0);
        n += upgradeBonus(Perk.ENDURING, Stat.STAMINA_REGEN, 0.25, 0.2);
        n += upgradeBonus(Perk.WEAKLING, Stat.STAMINA_REGEN, -0.25, -0.2);
        n += upgradeBonus(Perk.TOUGH, Stat.ARMOR_TOUGHNESS, 3.0, 1.0);
        if (n > 0) SPEC.save();
        return n;
    }

    private static int upgradeBonus(Perk perk, Stat stat, double oldDef, double newDef) {
        Map<Stat, ForgeConfigSpec.DoubleValue> m = BONUSES.get(perk);
        ForgeConfigSpec.DoubleValue v = m == null ? null : m.get(stat);
        if (v == null || Math.abs(v.get() - oldDef) > 1e-9) return 0;
        v.set(newDef);
        return 1;
    }

    /** Значение таблицы по уровню 0..10 (не хватает чисел — 1.0). */
    @SuppressWarnings("unchecked")
    public static double table(ForgeConfigSpec.ConfigValue<List<? extends Double>> t, int level) {
        List<? extends Number> l = (List<? extends Number>) (List<?>) t.get();
        int i = Math.max(0, Math.min(10, level));
        return i < l.size() ? l.get(i).doubleValue() : 1.0;
    }

    /** Минимальные уровни навыков, которые даёт перк. */
    public static Map<faygolover.rpperks.skill.SkillDef, Integer> getPerkSkills(Perk perk) {
        return perkSkills.getOrDefault(perk, Map.of());
    }

    private static void bonus(Perk perk, Stat stat, double defaultValue) {
        String key = stat.getKey() + (stat.isPercentBonus() ? "_percent" : "");
        String hint = stat.isPercentBonus() ? " Изменение, % от базы: " : " Изменение: ";
        ForgeConfigSpec.DoubleValue value = B.comment(hint + stat.getTitle())
                .defineInRange(key, defaultValue, -100000.0, 100000.0);
        BONUSES.computeIfAbsent(perk, p -> new EnumMap<>(Stat.class)).put(stat, value);
    }

    /** Перк без настраиваемых эффектов — только цена. */
    private static void rpPerk(Perk perk, String description) {
        beginPerk(perk, description);
        B.pop();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    // ---------------------------------------------------------------------
    // Доступ к значениям
    // ---------------------------------------------------------------------

    /** Серверный конфиг загружен (на клиенте — после входа на сервер). */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    /** Значение параметра или его значение по умолчанию, если конфиг ещё не загружен. */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        return isLoaded() ? value.get() : value.getDefault();
    }

    public static int getCost(Perk perk) {
        ForgeConfigSpec.IntValue value = COSTS.get(perk);
        return value == null ? perk.getDefaultCost() : get(value);
    }

    public static double getBaseline(Stat stat) {
        return get(BASELINE.get(stat));
    }

    /** Изменения характеристик, которые даёт перк. */
    public static Map<Stat, Double> getBonuses(Perk perk) {
        Map<Stat, ForgeConfigSpec.DoubleValue> values = BONUSES.get(perk);
        if (values == null || values.isEmpty()) return Map.of();
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        values.forEach((stat, value) -> result.put(stat, get(value)));
        return result;
    }

    public static Set<Perk> getConflicts(Perk perk) {
        return conflicts.getOrDefault(perk, Set.of());
    }

    /** Перк назначен служебным в конфиге (service_perks). */
    public static boolean isService(Perk perk) {
        Set<Perk> s = servicePerks;  // null, пока конфиг не загружен (и при построении самого конфига)
        return s != null && s.contains(perk);
    }

    public static boolean isHidden(Perk perk) {
        return hiddenPerks.contains(perk);
    }

    public static boolean isCaffeineItem(ResourceLocation id) {
        return caffeineItems.contains(id);
    }

    public static int caffeineWithdrawalTicks() {
        return get(CAFFEINE_WITHDRAWAL_MINUTES) * 1200;
    }

    // ---------------------------------------------------------------------
    // Загрузка
    // ---------------------------------------------------------------------

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) bake();
    }

    /** Файл изменили на работающем сервере: разбираем заново и сразу пересчитываем всех игроков онлайн. */
    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        bake();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(PerkEventHandler::refresh));
        }
    }

    private static void bake() {
        boolean upgraded = upgradeTable(T_SPREAD, SPREAD_V1, SPREAD_V2) | upgradeTable(T_RECOIL, RECOIL_V1, RECOIL_V2)
                | upgradeTable(T_RELOAD, RELOAD_V1, RELOAD_V2) | upgradeTable(T_AIM, AIM_V1, AIM_V2);
        if (upgraded) Rpperks.LOGGER.info("[RP Perks] Таблицы стрельбы обновлены до новых значений по умолчанию (09.10.2026)");
        int changed = upgradeDefaults2();
        if (changed > 0) Rpperks.LOGGER.info("[RP Perks] 2.0.0: {} значений по умолчанию обновлено (цены и числа особенностей). Свои правки не тронуты.", changed);

        // Предметы с кофеином
        Set<ResourceLocation> items = new HashSet<>();
        for (String entry : CAFFEINE_ITEMS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.trim());
            if (id == null) {
                Rpperks.LOGGER.error("[RP Perks] Неверный ID предмета в caffeine_items: {}", entry);
            } else {
                items.add(id);
            }
        }
        caffeineItems = Set.copyOf(items);

        // Скрытые перки
        Set<Perk> hidden = EnumSet.noneOf(Perk.class);
        for (String entry : HIDDEN_PERKS.get()) {
            Perk.fromId(entry.trim()).ifPresentOrElse(hidden::add,
                    () -> Rpperks.LOGGER.error("[RP Perks] Неизвестный перк в hidden_perks: {}", entry));
        }
        hiddenPerks = hidden;

        // Служебные из конфига
        Set<Perk> service = EnumSet.noneOf(Perk.class);
        for (String entry : SERVICE_PERKS.get()) {
            Perk.fromId(entry.trim()).ifPresentOrElse(service::add,
                    () -> Rpperks.LOGGER.error("[RP Perks] Неизвестный перк в service_perks: {}", entry));
        }
        servicePerks = service;

        // Несовместимости
        Map<Perk, Set<Perk>> map = new EnumMap<>(Perk.class);
        for (String line : CONFLICTS.get()) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                Rpperks.LOGGER.error("[RP Perks] Неверная строка в conflicts (нет '='): {}", line);
                continue;
            }
            Optional<Perk> left = Perk.fromId(parts[0].trim());
            if (left.isEmpty()) {
                Rpperks.LOGGER.error("[RP Perks] Неизвестный перк в conflicts: {}", parts[0].trim());
                continue;
            }
            for (String raw : parts[1].split(",")) {
                String id = raw.trim();
                if (id.isEmpty()) continue;
                Optional<Perk> right = Perk.fromId(id);
                if (right.isEmpty() || right.get() == left.get()) {
                    Rpperks.LOGGER.error("[RP Perks] Неверный перк в conflicts: {}", id);
                    continue;
                }
                map.computeIfAbsent(left.get(), k -> EnumSet.noneOf(Perk.class)).add(right.get());
                map.computeIfAbsent(right.get(), k -> EnumSet.noneOf(Perk.class)).add(left.get());
            }
        }
        conflicts = map;

        // Уровни навыков от перков
        Map<Perk, Map<faygolover.rpperks.skill.SkillDef, Integer>> ps = new EnumMap<>(Perk.class);
        for (String line : PERK_SKILLS.get()) {
            String[] parts = line.split("=", 2);
            Optional<Perk> perk = parts.length == 2 ? Perk.fromId(parts[0].trim()) : Optional.empty();
            if (perk.isEmpty()) {
                Rpperks.LOGGER.error("[RP Perks] Неверная строка в perk_skills: {}", line);
                continue;
            }
            Map<faygolover.rpperks.skill.SkillDef, Integer> m = new EnumMap<>(faygolover.rpperks.skill.SkillDef.class);
            for (String raw : parts[1].split(",")) {
                String[] kv = raw.trim().split(":");
                if (kv.length != 2) continue;
                var skill = faygolover.rpperks.skill.SkillDef.byKey(kv[0].trim());
                try {
                    if (skill.isPresent()) m.put(skill.get(), Math.max(0, Math.min(10, Integer.parseInt(kv[1].trim()))));
                    else Rpperks.LOGGER.error("[RP Perks] Неизвестный навык в perk_skills: {}", kv[0]);
                } catch (NumberFormatException ex) {
                    Rpperks.LOGGER.error("[RP Perks] Неверный уровень в perk_skills: {}", raw);
                }
            }
            ps.put(perk.get(), m);
        }
        perkSkills = ps;
    }
}

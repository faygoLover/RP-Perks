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
    public static final ForgeConfigSpec.ConfigValue<String> INVENTORY_COMMAND;

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

    public static final ForgeConfigSpec.IntValue ADMIN_INVENTORY_SLOTS;

    // ---------- [conflicts] ----------
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFLICTS;

    public static final ForgeConfigSpec SPEC;

    // ---------- Разобранные значения (обновляются при загрузке/перезагрузке файла) ----------
    private static volatile Set<ResourceLocation> caffeineItems = Set.of();
    private static volatile Set<Perk> hiddenPerks = EnumSet.of(Perk.ADMIN);
    private static volatile Map<Perk, Set<Perk>> conflicts = Map.of();

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
                        " Перки, которые обычный игрок не видит в окне /perks и не может взять.",
                        " Операторы (уровень 2+) видят их и выдают через /perksadmin <ник>.",
                        " Указываются ID перков, например: [\"admin\", \"nerd_rage\"]")
                .defineListAllowEmpty(List.of("hidden_perks"), List.of("admin"), Config::isString);

        INVENTORY_COMMAND = B.comment(
                        " Команда, которой выставляется число слотов инвентаря.",
                        " {player} — ник игрока, {value} — число слотов. Пустая строка — не выполнять.")
                .define("inventory_slots_command", "inventory_slots set_available {player} {value}");
        B.pop();

        // =====================================================================
        B.comment(
                        " Самостоятельный выбор перков через окно (/perks).",
                        " Взятый перк остаётся навсегда: отказаться от одного перка можно только за жетон отказа,",
                        " сбросить все — за жетон сброса. Жетоны выдаёт администрация:",
                        " в окне /perksadmin <ник>. Там же можно запретить игроку конкретные перки или закрыть ему выбор целиком.")
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

        beginPerk(Perk.SURVIVOR, "Повышенное на 2.5 сердца ХП и более быстрое заживление (РП)");
        bonus(Perk.SURVIVOR, Stat.MAX_HEALTH, 5.0);
        B.pop();

        beginPerk(Perk.ATHLETE, "Повышенная скорость передвижения, подъём на 2 блока, меньше тратит выносливости");
        bonus(Perk.ATHLETE, Stat.MOVEMENT_SPEED, 25.0);
        bonus(Perk.ATHLETE, Stat.STEP_HEIGHT, 0.5);
        bonus(Perk.ATHLETE, Stat.STAMINA_COST, -0.15);
        B.pop();

        beginPerk(Perk.TOUGH, "+1.5 щита брони, +1.5 щита твёрдости, устойчивость к отбрасыванию");
        bonus(Perk.TOUGH, Stat.ARMOR, 3.0);
        bonus(Perk.TOUGH, Stat.ARMOR_TOUGHNESS, 3.0);
        bonus(Perk.TOUGH, Stat.KNOCKBACK_RESISTANCE, 0.2);
        B.pop();

        beginPerk(Perk.STRONGMAN, "Повышенный урон, скорость ударов и отбрасывание в ближнем бою. Пара к Слабаку");
        bonus(Perk.STRONGMAN, Stat.ATTACK_DAMAGE, 0.2);
        bonus(Perk.STRONGMAN, Stat.ATTACK_SPEED, 0.5);
        bonus(Perk.STRONGMAN, Stat.MELEE_KNOCKBACK, 30.0);
        B.pop();

        beginPerk(Perk.TOURIST, "Устойчивость к температуре среды, крепкий иммунитет (РП)");
        bonus(Perk.TOURIST, Stat.THERMAL_RESISTANCE, 2.0);
        B.pop();

        beginPerk(Perk.ORGANIZED, "+3 слота инвентаря");
        bonus(Perk.ORGANIZED, Stat.INVENTORY_SLOTS, 3.0);
        B.pop();

        beginPerk(Perk.LUCKY, "Повышенная удача. Пара к Неудачливому");
        bonus(Perk.LUCKY, Stat.LUCK, 2.0);
        B.pop();

        beginPerk(Perk.ENDURING, "Повышенная выносливость: больше максимум и быстрее восстановление");
        bonus(Perk.ENDURING, Stat.STAMINA_MAX, 250.0);
        bonus(Perk.ENDURING, Stat.STAMINA_REGEN, 0.25);
        B.pop();

        rpPerk(Perk.BRAVE, "Медленнее поддаётся панике (РП)");
        rpPerk(Perk.STRONG_WILLED, "Устойчив к когнитивному воздействию (РП). Пара к Внушаемому");

        beginPerk(Perk.NERD_RAGE, "При низком здоровье получает Силу и Сопротивление");
        NERD_RAGE_HEALTH_PERCENT = B.comment(" Порог срабатывания: здоровье ниже N% от максимума")
                .defineInRange("health_threshold_percent", 20.0, 1.0, 100.0);
        NERD_RAGE_EFFECT_LEVEL = B.comment(" Уровень эффектов (1 = I, 2 = II ...)")
                .defineInRange("effect_level", 2, 1, 10);
        B.pop();

        beginPerk(Perk.AGILE, "Увеличенная дальность взаимодействия с блоками и атак. Пара к Плохой координации");
        bonus(Perk.AGILE, Stat.BLOCK_REACH, 1.0);
        bonus(Perk.AGILE, Stat.ENTITY_REACH, 1.0);
        B.pop();

        beginPerk(Perk.SOLAR_POWERED, "Днём под открытым небом (без дождя): Скорость и быстрое восстановление выносливости");
        SOLAR_SPEED_LEVEL = B.comment(" Уровень эффекта Скорости (0 = не давать)")
                .defineInRange("speed_effect_level", 1, 0, 10);
        SOLAR_STAMINA_REGEN_BONUS = B.comment(
                        " Прибавка к множителю восстановления выносливости на солнце (0.5 = на 50% быстрее)")
                .defineInRange("stamina_regen_bonus", 0.5, 0.0, 10.0);
        B.pop();

        beginPerk(Perk.LIGHT_TRAVEL, "Быстрее передвигается в лёгкой броне");
        LIGHT_TRAVEL_MAX_ARMOR = B.comment(" Бонус действует, пока очков брони не больше N")
                .defineInRange("max_armor", 6, 0, 30);
        LIGHT_TRAVEL_SPEED_PERCENT = B.comment(" Прибавка к скорости, % от базы")
                .defineInRange("movement_speed_percent", 10.0, 0.0, 500.0);
        B.pop();

        rpPerk(Perk.ALCOHOLIC, "Легко переносит алкоголь (РП)");
        rpPerk(Perk.SMOKER, "Курение улучшает психическое состояние, без него — ухудшается (РП)");
        B.pop();

        // =====================================================================
        B.comment(" УМЕНИЯ").push("skills");
        rpPerk(Perk.SOLDIER, "Пистолеты, ПП, автоматы, снайперские винтовки, ограниченно спец. вооружение (РП)");
        rpPerk(Perk.MEDIC, "Сложные медицинские манипуляции, полное обращение с препаратами и оборудованием (РП)");

        beginPerk(Perk.MELEE_FIGHTER, "Умение сражаться в ближнем бою: повышенный урон и скорость ударов, меньше устаёт в бою");
        bonus(Perk.MELEE_FIGHTER, Stat.ATTACK_DAMAGE, 0.2);
        bonus(Perk.MELEE_FIGHTER, Stat.ATTACK_SPEED, 1.0);
        bonus(Perk.MELEE_FIGHTER, Stat.STAMINA_MELEE_COST, -0.2);
        B.pop();

        beginPerk(Perk.BUILDER, "Непрофессиональные инженерно-строительные работы (РП). Добыча блоков меньше утомляет");
        bonus(Perk.BUILDER, Stat.STAMINA_MINE_COST, -0.2);
        B.pop();

        rpPerk(Perk.GUNNER, "Пистолеты, ограниченно пистолеты-пулемёты (РП)");
        rpPerk(Perk.EXPLOSIVES, "Создание, обезвреживание и применение взрывных устройств (РП)");
        rpPerk(Perk.TECHNICIAN, "Работа со сложным оборудованием и техникой, ограниченный ремонт (РП)");
        rpPerk(Perk.HACKER, "Взлом замков, электронных систем, дверей и терминалов (РП)");

        beginPerk(Perk.SAILOR, "Рыбалка, повышенная скорость плавания, плавание меньше утомляет");
        bonus(Perk.SAILOR, Stat.SWIM_SPEED, 0.4);
        bonus(Perk.SAILOR, Stat.STAMINA_SWIM_COST, -0.4);
        B.pop();

        rpPerk(Perk.TAILOR, "Шитьё и ремонт одежды (РП)");
        rpPerk(Perk.FARMER, "Фермерство, сбор и различение диких растений (РП)");
        rpPerk(Perk.COOK, "Сложные блюда и разделка туш (РП)");
        rpPerk(Perk.DRIVER, "Минимальное управление наземным транспортом (РП)");
        rpPerk(Perk.PILOT, "Минимальное управление воздушным транспортом (РП)");
        rpPerk(Perk.FIRST_AID, "Полная первая помощь, базовое обращение с медицинскими вещами (РП)");
        B.pop();

        // =====================================================================
        B.comment(" НЕГАТИВНЫЕ ПЕРКИ (cost — сколько баллов получает игрок)").push("negative_perks");

        beginPerk(Perk.FRAGILE, "Пониженное на 2.5 сердца ХП и медленное заживление (РП)");
        bonus(Perk.FRAGILE, Stat.MAX_HEALTH, -5.0);
        B.pop();

        beginPerk(Perk.DISORGANIZED, "-3 слота инвентаря");
        bonus(Perk.DISORGANIZED, Stat.INVENTORY_SLOTS, -3.0);
        B.pop();

        rpPerk(Perk.DIABETIC, "Требуется ежедневная доза инсулина (РП)");

        beginPerk(Perk.ANOREXIC, "Пониженный предел сытости и внезапные отказы от еды");
        ANOREXIA_MAX_FOOD = B.comment(" Максимальный уровень голода (из 20; 14 = 7 окорочков)")
                .defineInRange("max_food_level", 14, 1, 20);
        ANOREXIA_REFUSE_CHANCE_PERCENT = B.comment(
                        " Шанс после каждого приёма пищи, что следующая попытка поесть будет отменена",
                        " и запустится таймер «не хочу есть». При голоде 0 новый отказ не возникает.")
                .defineInRange("refuse_chance_percent", 20.0, 0.0, 100.0);
        ANOREXIA_REFUSE_SECONDS = B.comment(" Длительность отказа от еды, реальные секунды")
                .defineInRange("refuse_seconds", 120, 1, 36000);
        B.pop();

        beginPerk(Perk.WEAKLING, "Пониженная выносливость: меньше максимум и медленнее восстановление");
        bonus(Perk.WEAKLING, Stat.STAMINA_MAX, -250.0);
        bonus(Perk.WEAKLING, Stat.STAMINA_REGEN, -0.25);
        B.pop();

        beginPerk(Perk.HOMEBODY, "Пониженная устойчивость к температуре и слабый иммунитет (РП)");
        bonus(Perk.HOMEBODY, Stat.THERMAL_RESISTANCE, -2.0);
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

        beginPerk(Perk.TURTLE, "Пониженная скорость передвижения");
        bonus(Perk.TURTLE, Stat.MOVEMENT_SPEED, -17.0);
        B.pop();

        beginPerk(Perk.WIMP, "Пониженный урон, скорость ударов и отбрасывание в ближнем бою");
        bonus(Perk.WIMP, Stat.ATTACK_DAMAGE, -0.1);
        bonus(Perk.WIMP, Stat.ATTACK_SPEED, -0.5);
        bonus(Perk.WIMP, Stat.MELEE_KNOCKBACK, -100.0);
        B.pop();

        rpPerk(Perk.SUGGESTIBLE, "Легче поддаётся когнитивному воздействию (РП)");

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

        rpPerk(Perk.COWARD, "Быстро поддаётся панике (РП)");

        beginPerk(Perk.CLUMSY, "Иногда роняет предмет из рук при использовании");
        CLUMSY_DROP_CHANCE_PERCENT = B.comment(" Шанс выронить предмет при использовании (ПКМ)")
                .defineInRange("drop_chance_percent", 5.0, 0.0, 100.0);
        B.pop();

        rpPerk(Perk.TECHNOPHOBE, "Боится и не понимает сложную технику и электронику (РП)");

        beginPerk(Perk.LANDLUBBER, "Не умеет плавать: ходит по дну, не может всплыть");
        bonus(Perk.LANDLUBBER, Stat.SWIM_SPEED, -0.3);
        LANDLUBBER_SINK_SPEED = B.comment(" Скорость погружения в толще воды, блоков за тик")
                .defineInRange("sink_speed", 0.03, 0.0, 1.0);
        B.pop();

        rpPerk(Perk.TEETOTALER, "Не переносит алкоголь (РП)");
        B.pop();

        // =====================================================================
        B.comment(" СЛУЖЕБНЫЕ ПЕРКИ").push("service_perks");
        beginPerk(Perk.ADMIN,
                "Снимает все ограничения: ванильные характеристики, выносливость не тратится,",
                " полный инвентарь. Действие всех остальных перков приостановлено, пока Админ активен.");
        ADMIN_INVENTORY_SLOTS = B.comment(" Число слотов инвентаря в режиме Админа")
                .defineInRange("inventory_slots", 36, 0, 100);
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
                        "survivor = fragile",
                        "athlete = turtle",
                        "strongman = wimp",
                        "melee_fighter = wimp",
                        "tourist = homebody",
                        "organized = disorganized",
                        "enduring = weakling",
                        "brave = coward",
                        "strong_willed = suggestible",
                        "alcoholic = teetotaler",
                        "sailor = landlubber",
                        "lucky = unlucky",
                        "agile = clumsy",
                        "technophobe = technician, hacker, pilot",
                        "soldier = gunner",
                        "medic = first_aid"
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
    }
}

package faygolover.rpperks.perk;

import faygolover.rpperks.Config;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Реестр всех особенностей (перков). Каталог и цены — RP Stamina Sports, docs/07 (решения брата 13–14.10.2026).
 * Цена здесь — только значение по умолчанию: реальная цена берётся из конфига (секция перка, параметр cost).
 * Несовместимости тоже задаются в конфиге ([conflicts]).
 * <p>
 * Все особенности живут здесь. С «своим» модом у особенности подробная механика, без него — простая или только РП.
 * Немногие без своего мода не имеют смысла — у них {@link #requiredMod()}, и без него они не появляются в окне.
 */
public enum Perk {
    // === Основные (положительные) ===
    // Тело
    ATHLETIC("athletic", Category.MAIN, 11, "Атлетичный"),
    FIT("fit", Category.MAIN, 7, "В форме"),
    STRONGMAN("strongman", Category.MAIN, 11, "Силач"),
    STRONG("strong", Category.MAIN, 7, "Сильный"),
    // Дыхание и выносливость
    ENDURING("enduring", Category.MAIN, 4, "Выносливый"),
    SECOND_WIND("second_wind", Category.MAIN, 3, "Второе дыхание", "rpstamina"),
    QUICK_REST("quick_rest", Category.MAIN, 2, "Быстрый отдых", "rpstamina"),
    GYM_LOVER("gym_lover", Category.MAIN, 4, "Любитель спорта"),
    // Живучесть и прочность
    SURVIVOR("survivor", Category.MAIN, 6, "Живучий"),
    TOUGH("tough", Category.MAIN, 5, "Крепкий"),
    // Еда и вода
    IRON_STOMACH("iron_stomach", Category.MAIN, 3, "Железный желудок"),
    CLEAN("clean", Category.MAIN, 3, "Чистюля", "rpculinary"),
    LIGHT_EATER("light_eater", Category.MAIN, 2, "Малоежка"),
    LOW_THIRST("low_thirst", Category.MAIN, 2, "Мало пьёт", "rpculinary"),
    FAST_METABOLISM("fast_metabolism", Category.MAIN, 1, "Быстрый обмен веществ"),
    GOURMET("gourmet", Category.MAIN, 0, "Гурман"),
    // Движение
    FLEET("fleet", Category.MAIN, 3, "Быстроногий"),
    LIGHT_TRAVEL("light_travel", Category.MAIN, 1, "Путешествие налегке"),
    AGILE("agile", Category.MAIN, 3, "Ловкий"),
    // Ум и обучение
    FAST_LEARNER("fast_learner", Category.MAIN, 5, "Быстрообучаемый"),
    QUICK_WORKER("quick_worker", Category.MAIN, 3, "Расторопный"),
    // Психика и привычки
    BRAVE("brave", Category.MAIN, 4, "Храбрый"),
    STRONG_WILLED("strong_willed", Category.MAIN, 4, "Волевой"),
    NERD_RAGE("nerd_rage", Category.MAIN, 3, "Бешенство ботаника"),
    ALCOHOLIC("alcoholic", Category.MAIN, 2, "Закалённый алкоголик"),
    // Мир вокруг
    TOURIST("tourist", Category.MAIN, 3, "Турист"),
    SOLAR_POWERED("solar_powered", Category.MAIN, 4, "Солнечная батарейка"),
    LUCKY("lucky", Category.MAIN, 2, "Удачливый"),
    ORGANIZED("organized", Category.MAIN, 4, "Организованный"),

    // === Умения ===
    SOLDIER("soldier", Category.SKILL, 12, "Подготовленный солдат"),
    MEDIC("medic", Category.SKILL, 7, "Анастасия"),
    MELEE_FIGHTER("melee_fighter", Category.SKILL, 7, "Боец ближнего боя"),
    BUILDER("builder", Category.SKILL, 6, "Рогалёв"),
    GUNNER("gunner", Category.SKILL, 5, "Курсы владения оружием"),
    EXPLOSIVES("explosives", Category.SKILL, 5, "Взрывчатка"),
    TECHNICIAN("technician", Category.SKILL, 5, "Дентон"),
    HACKER("hacker", Category.SKILL, 5, "Взлом"),
    COOK("cook", Category.SKILL, 4, "Кулинар"),
    BASIC_TRAINING("basic_training", Category.SKILL, 4, "Курс молодого бойца"),
    SAILOR("sailor", Category.SKILL, 4, "Морской"),
    TAILOR("tailor", Category.SKILL, 4, "Кройка"),
    FARMER("farmer", Category.SKILL, 4, "Фермер"),
    DRIVER("driver", Category.SKILL, 4, "Водятел"),
    PILOT("pilot", Category.SKILL, 4, "Пинпиныч"),
    FIRST_AID("first_aid", Category.SKILL, 3, "Курсы первой медицинской помощи"),
    DIETITIAN("dietitian", Category.SKILL, 3, "Диетолог"),
    FIELD_COOK("field_cook", Category.SKILL, 3, "Полевой повар"),

    // === Негативные ===
    // Тело
    PUNY("puny", Category.NEGATIVE, 8, "Хиляк"),
    OUT_OF_SHAPE("out_of_shape", Category.NEGATIVE, 4, "Не в форме"),
    WIMP("wimp", Category.NEGATIVE, 8, "Слабак"),
    WEAK("weak", Category.NEGATIVE, 4, "Слабый"),
    // Вес тела
    EMACIATED("emaciated", Category.NEGATIVE, 3, "Дистрофик"),
    OVERWEIGHT("overweight", Category.NEGATIVE, 1, "Избыточный вес"),
    OBESE("obese", Category.NEGATIVE, 4, "Жирный"),
    // Дыхание
    WEAKLING("weakling", Category.NEGATIVE, 3, "Задохлик"),
    ASTHMATIC("asthmatic", Category.NEGATIVE, 4, "Астматик"),
    // Живучесть и прочность
    DELICATE("delicate", Category.NEGATIVE, 4, "Нежный"),
    FRAGILE("fragile", Category.NEGATIVE, 3, "Хрупкий"),
    AMPUTEE("amputee", Category.NEGATIVE, 8, "Ампутант", "rpmedicine"),
    DIABETIC("diabetic", Category.NEGATIVE, 5, "Диабетик"),
    // Еда и вода
    WEAK_STOMACH("weak_stomach", Category.NEGATIVE, 3, "Слабый желудок"),
    BIG_EATER("big_eater", Category.NEGATIVE, 2, "Обжора"),
    ANOREXIC("anorexic", Category.NEGATIVE, 4, "Анорексичка"),
    HIGH_THIRST("high_thirst", Category.NEGATIVE, 2, "Много пьёт", "rpculinary"),
    SLOW_METABOLISM("slow_metabolism", Category.NEGATIVE, 1, "Склонность к полноте"),
    BAD_TEETH("bad_teeth", Category.NEGATIVE, 2, "Плохие зубы"),
    // Движение
    TURTLE("turtle", Category.NEGATIVE, 3, "Черепаха"),
    CLUMSY_RUNNER("clumsy_runner", Category.NEGATIVE, 2, "Неуклюжий бегун"),
    CLUMSY("clumsy", Category.NEGATIVE, 3, "Плохая координация"),
    LANDLUBBER("landlubber", Category.NEGATIVE, 2, "Сухопутный"),
    // Ум и обучение
    SLOW_LEARNER("slow_learner", Category.NEGATIVE, 3, "Плохообучаемый"),
    SLOW_WORKER("slow_worker", Category.NEGATIVE, 2, "Медлительный"),
    ILLITERATE("illiterate", Category.NEGATIVE, 3, "Неграмотный"),
    // Психика и привычки
    COWARD("coward", Category.NEGATIVE, 3, "Трусливый"),
    SUGGESTIBLE("suggestible", Category.NEGATIVE, 3, "Внушаемый"),
    HEMOPHOBIC("hemophobic", Category.NEGATIVE, 3, "Гемофоб"),
    PACIFIST("pacifist", Category.NEGATIVE, 5, "Пацифист"),
    CAFFEINE_ADDICTION("caffeine_addiction", Category.NEGATIVE, 3, "Зависимость от кофеина"),
    ALCOHOL_ADDICT("alcohol_addict", Category.NEGATIVE, 4, "Алкоголик"),
    SMOKER("smoker", Category.NEGATIVE, 2, "Курильщик"),
    TEETOTALER("teetotaler", Category.NEGATIVE, 2, "Непьющий"),
    CANNIBAL("cannibal", Category.NEGATIVE, 2, "Каннибал", "rpculinary"),
    // Мир вокруг
    HOMEBODY("homebody", Category.NEGATIVE, 2, "Домашний"),
    SUN_SENSITIVE("sun_sensitive", Category.NEGATIVE, 3, "Чувствительность к солнцу"),
    UNLUCKY("unlucky", Category.NEGATIVE, 2, "Неудачливый"),
    DISORGANIZED("disorganized", Category.NEGATIVE, 3, "Неорганизованный"),
    // Пара к умениям
    TECHNOPHOBE("technophobe", Category.NEGATIVE, 4, "Технофоб"),
    KITCHEN_NIGHTMARE("kitchen_nightmare", Category.NEGATIVE, 2, "Кухонный кошмар"),

    // === Служебные ===
    ADMIN("admin", Category.SERVICE, 0, "Админ"),
    // Знания экзотической кухни (RP Culinary): даёт книга «Записи исследований», выдаёт ГМ
    EXOTIC_UNDERGROUND("exotic_underground", Category.SERVICE, 0, "Знание: подземная кухня", "rpculinary"),
    EXOTIC_CANDY("exotic_candy", Category.SERVICE, 0, "Знание: конфетная полость", "rpculinary"),
    EXOTIC_DEEP_SEA("exotic_deep_sea", Category.SERVICE, 0, "Знание: морские глубины", "rpculinary"),
    EXOTIC_FAR_LANDS("exotic_far_lands", Category.SERVICE, 0, "Знание: дальние земли", "rpculinary"),
    EXOTIC_NETHER("exotic_nether", Category.SERVICE, 0, "Знание: Нижний мир", "rpculinary"),
    EXOTIC_END("exotic_end", Category.SERVICE, 0, "Знание: Край", "rpculinary"),
    EXOTIC_PETS("exotic_pets", Category.SERVICE, 0, "Знание: домашние звери", "rpculinary"),
    EXOTIC_MONSTERS("exotic_monsters", Category.SERVICE, 0, "Знание: монстры", "rpculinary"),
    EXOTIC_INFECTED("exotic_infected", Category.SERVICE, 0, "Знание: заражённое", "rpculinary"),
    EXOTIC_RADIOACTIVE("exotic_radioactive", Category.SERVICE, 0, "Знание: радиоактивное", "rpculinary"),
    EXOTIC_ANOMALOUS("exotic_anomalous", Category.SERVICE, 0, "Знание: аномальное", "rpculinary");

    public enum Category {
        MAIN("main"),
        SKILL("skill"),
        NEGATIVE("negative"),
        SERVICE("service");

        private final String id;

        Category(String id) { this.id = id; }

        public String getId() { return id; }
        public String getTranslationKey() { return "category.rpperks." + id; }
    }

    private final String id;
    private final Category category;
    private final int defaultCost;
    private final String title;
    @Nullable private final String requiredMod;

    Perk(String id, Category category, int defaultCost, String title) {
        this(id, category, defaultCost, title, null);
    }

    Perk(String id, Category category, int defaultCost, String title, @Nullable String requiredMod) {
        this.id = id;
        this.category = category;
        this.defaultCost = defaultCost;
        this.title = title;
        this.requiredMod = requiredMod;
    }

    public String getId() { return id; }
    public Category getCategory() {
        if (faygolover.rpperks.Config.isService(this)) return Category.SERVICE;
        return category == Category.NEGATIVE && faygolover.rpperks.Config.isFlippedPositive(this) ? Category.MAIN : category;
    }

    /** Категория из кода, без учёта service_perks в конфиге. */
    public Category getBaseCategory() { return category; }
    public int getDefaultCost() { return defaultCost; }
    /** Русское название — используется в комментариях конфига. */
    public String getTitle() { return title; }

    /** Мод, без которого особенность не имеет смысла и не появляется в окне (null — всегда есть). */
    @Nullable
    public String requiredMod() { return requiredMod; }

    /** Нужный мод установлен (или не нужен). */
    public boolean isAvailable() { return requiredMod == null || ModList.get().isLoaded(requiredMod); }

    /** Актуальная цена из конфига (всегда положительное число). */
    public int getCost() { return Config.getCost(this); }

    /** Положительные перки и умения стоят баллы, негативные — дают. */
    public boolean isPositive() { return category != Category.NEGATIVE || faygolover.rpperks.Config.isFlippedPositive(this); }

    /** На сколько изменится баланс игрока при выдаче перка. */
    public int getPointsDelta() { return isPositive() ? -getCost() : getCost(); }

    public String getNameKey() { return "perk.rpperks." + id + ".name"; }
    public String getDescKey() { return "perk.rpperks." + id + ".desc"; }

    public static Optional<Perk> fromId(String id) {
        for (Perk perk : values()) {
            if (perk.id.equalsIgnoreCase(id)) return Optional.of(perk);
        }
        return Optional.empty();
    }
}

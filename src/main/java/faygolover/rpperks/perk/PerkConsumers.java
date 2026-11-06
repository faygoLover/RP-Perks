package faygolover.rpperks.perk;

import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Кто даёт особенности механику (аудит 06.11: у Расторопного, Медлительного и Чувствительности к солнцу кода не было,
 * а описание его обещало). Список модов по каждой особенности — по исходникам семейства на 06.11.2026.
 * <ul>
 *   <li>{@link #SELF} — механика в самом RP Perks (статы, события), работает всегда;</li>
 *   <li>{@link #RP} — только отыгрыш, механики нет нарочно;</li>
 *   <li>id мода — механику даёт он (черта через RP Core); без него особенность пустая.</li>
 * </ul>
 * GameTest проверяет, что сюда внесена каждая особенность; {@code /rpperks audit} показывает, у каких сейчас нет
 * ни одного загруженного мода с механикой. Новую особенность — вписать сюда вместе с кодом, который её читает.
 */
public final class PerkConsumers {
    private PerkConsumers() {}

    public static final String SELF = "rpperks";
    public static final String RP = "rp";

    private static final String MED = "rpmedicine", CUL = "rpculinary", PSY = "rppsyche", STA = "rpstamina",
            SPO = "rpsports", NEEDS = "rpneeds", PDA = "rppda", INV = "rpinventory", FIX = "rpfixes";

    private static final Map<Perk, List<String>> MODS = new EnumMap<>(Perk.class);

    static {
        // Тело и выносливость
        put(List.of(SELF), Perk.ATHLETIC, Perk.FIT, Perk.STRONGMAN, Perk.STRONG, Perk.PUNY, Perk.OUT_OF_SHAPE, Perk.WIMP, Perk.WEAK,
                Perk.ENDURING, Perk.WEAKLING, Perk.FLEET, Perk.TURTLE, Perk.LANDLUBBER, Perk.CLUMSY_RUNNER, Perk.NERD_RAGE,
                Perk.SOLAR_POWERED, Perk.FAST_LEARNER, Perk.SLOW_LEARNER, Perk.PACIFIST, Perk.LUCKY, Perk.UNLUCKY,
                Perk.SAILOR, Perk.BUILDER, Perk.GUNNER, Perk.EXPLOSIVES, Perk.SOLDIER, Perk.BASIC_TRAINING, Perk.ADMIN,
                Perk.SURVIVOR, Perk.DELICATE, Perk.TOUGH, Perk.FRAGILE, Perk.IRON_STOMACH, Perk.WEAK_STOMACH, Perk.BAD_TEETH,
                Perk.ANOREXIC, Perk.ASTHMATIC, Perk.SUN_SENSITIVE, Perk.CAFFEINE_ADDICTION, Perk.CLUMSY, Perk.AGILE,
                Perk.TOURIST, Perk.HOMEBODY, Perk.MELEE_FIGHTER, Perk.GYM_LOVER, Perk.LIGHT_TRAVEL);
        put(List.of(STA), Perk.SECOND_WIND, Perk.QUICK_REST);
        put(List.of(NEEDS), Perk.UNFUSSY, Perk.DEEP_SLEEPER, Perk.SHORT_SLEEPER, Perk.TIRELESS, Perk.EARLY_BIRD, Perk.NIGHT_OWL,
                Perk.INSOMNIA, Perk.LIGHT_SLEEPER, Perk.SLEEPYHEAD, Perk.NIGHTMARES, Perk.HEAVY_WAKING, Perk.SNORER);
        put(List.of(CUL), Perk.CLEAN, Perk.LIGHT_EATER, Perk.BIG_EATER, Perk.LOW_THIRST, Perk.HIGH_THIRST, Perk.FAST_METABOLISM,
                Perk.SLOW_METABOLISM, Perk.GOURMET, Perk.CANNIBAL, Perk.SEAFOOD_ALLERGY, Perk.EMACIATED, Perk.OVERWEIGHT,
                Perk.OBESE, Perk.COOK, Perk.DIETITIAN, Perk.FIELD_COOK, Perk.VINTNER, Perk.PRESERVER, Perk.KITCHEN_NIGHTMARE);
        put(List.of(MED), Perk.MEDIC, Perk.FIRST_AID, Perk.AMPUTEE, Perk.DIABETIC, Perk.HEMOPHOBIC, Perk.ALCOHOL_ADDICT,
                Perk.ALCOHOLIC, Perk.SMOKER, Perk.TEETOTALER);
        put(List.of(MED), Perk.QUICK_WORKER, Perk.SLOW_WORKER);
        put(List.of(PSY, MED), Perk.BRAVE, Perk.COWARD);
        put(List.of(PSY), Perk.STRONG_WILLED, Perk.SUGGESTIBLE);
        put(List.of(PDA), Perk.ILLITERATE);
        put(List.of(PDA, FIX), Perk.HACKER, Perk.TECHNOPHOBE);
        put(List.of(INV), Perk.ORGANIZED, Perk.DISORGANIZED);
        put(List.of(RP), Perk.TECHNICIAN, Perk.TAILOR, Perk.FARMER, Perk.DRIVER, Perk.PILOT);
        for (Perk p : Perk.values()) if (p.getId().startsWith("exotic_")) put(List.of(CUL), p);
    }

    private static void put(List<String> mods, Perk... perks) {
        for (Perk p : perks) MODS.put(p, mods);
    }

    /** Моды с механикой особенности; пусто — особенность забыли вписать. */
    public static List<String> of(Perk perk) {
        return MODS.getOrDefault(perk, List.of());
    }

    /** Особенности, не внесённые в таблицу (GameTest). */
    public static List<Perk> unlisted() {
        List<Perk> out = new ArrayList<>();
        for (Perk p : Perk.values()) if (!MODS.containsKey(p)) out.add(p);
        return out;
    }

    /** Нет ни одного загруженного мода с механикой (только отыгрыш не в счёт). */
    public static boolean empty(Perk perk) {
        for (String m : of(perk)) if (m.equals(SELF) || m.equals(RP) || ModList.get().isLoaded(m)) return false;
        return true;
    }
}

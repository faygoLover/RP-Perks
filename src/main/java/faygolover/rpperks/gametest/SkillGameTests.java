package faygolover.rpperks.gametest;

import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.skill.SkillDef;
import faygolover.rpperks.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Навыки 2.7.0 (B1, B2 сводки 02.11): ГМ меняет наработанный уровень и не замораживает навык; старое поле ГМа
 * переводится по правилу 31.10. Игрок не нужен — проверяется карточка {@link PlayerPerks}.
 */
@GameTestHolder(Rpperks.MODID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    private SkillGameTests() {}

    private static final String T = "platform";

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) h.fail(what);
    }

    /** ГМ выше наработанного: уровень ГМа и 10 % до следующего; поле ГМа очищено, рост не заморожен. */
    @GameTest(template = T)
    public static void migrateGmHigher(GameTestHelper h) {
        PlayerPerks d = new PlayerPerks();
        SkillDef s = SkillDef.MEDICINE;
        d.setSkillLevel(s.id.toString(), 3);
        check(h, SkillService.level(d, s) == 0, "уровень ГМа больше не действует сам по себе: " + SkillService.level(d, s));
        check(h, SkillService.migrateGmLevels(d) == 1, "переведён один навык");
        check(h, d.getSkillLevel(s.id.toString()) < 0, "поле ГМа очищено");
        check(h, SkillService.level(d, s) == 3, "уровень 3, было " + SkillService.level(d, s));
        check(h, near(SkillService.progress(d, s), 3.1), "прогресс 3,1, было " + SkillService.progress(d, s));
        check(h, SkillService.migrateGmLevels(d) == 0, "второй раз переводить нечего");
        h.succeed();
    }

    /** Умение — стартовый капитал (2.9.0): взял — поднялся до 3; дорос до 5 сам; отказался — выданные 3 ушли, осталось 2. */
    @GameTest(template = T)
    public static void skillPerkIsCapital(GameTestHelper h) {
        PlayerPerks d = new PlayerPerks();
        SkillDef s = SkillDef.MEDICINE;
        d.grantPerk(faygolover.rpperks.perk.Perk.FIRST_AID, null);
        SkillService.syncSkillPerks(d);
        check(h, SkillService.level(d, s) == 3, "умение подняло до 3, было " + SkillService.level(d, s));
        SkillService.syncSkillPerks(d);
        check(h, SkillService.level(d, s) == 3, "второй раз не прибавляет");
        d.setTrained(s.key(), 5);
        d.revokePerk(faygolover.rpperks.perk.Perk.FIRST_AID);
        SkillService.syncSkillPerks(d);
        check(h, SkillService.level(d, s) == 2, "отказ забрал выданное, было " + SkillService.level(d, s));
        h.succeed();
    }

    /** Два умения на один навык: итог не зависит от порядка снятия, наработанное сверху остаётся. */
    @GameTest(template = T)
    public static void skillPerksStackInAnyOrder(GameTestHelper h) {
        var soldier = faygolover.rpperks.perk.Perk.SOLDIER;
        var firstAid = faygolover.rpperks.perk.Perk.FIRST_AID;
        SkillDef s = SkillDef.MEDICINE;
        for (boolean soldierFirst : new boolean[]{true, false}) {
            PlayerPerks d = new PlayerPerks();
            d.grantPerk(soldier, null);
            SkillService.syncSkillPerks(d);
            d.grantPerk(firstAid, null);
            SkillService.syncSkillPerks(d);
            check(h, SkillService.level(d, s) == 3, "оба умения дают 3, было " + SkillService.level(d, s));
            d.setTrained(s.key(), 4);
            d.revokePerk(soldierFirst ? soldier : firstAid);
            SkillService.syncSkillPerks(d);
            d.revokePerk(soldierFirst ? firstAid : soldier);
            SkillService.syncSkillPerks(d);
            check(h, SkillService.level(d, s) == 1, "остался только наработанный уровень, было " + SkillService.level(d, s));
        }
        h.succeed();
    }

    /** Старое сохранение: уровень уже поднят до умения, отказ забирает выданное; сброс ГМа возвращает к уровню умения. */
    @GameTest(template = T)
    public static void oldSaveRefusalTakesCapital(GameTestHelper h) {
        PlayerPerks d = new PlayerPerks();
        d.loadNBT(new net.minecraft.nbt.CompoundTag());
        SkillDef s = SkillDef.MEDICINE;
        d.grantPerk(faygolover.rpperks.perk.Perk.FIRST_AID, null);
        d.setTrained(s.key(), 3);
        SkillService.syncSkillPerks(d);
        SkillService.setLevel(d, s, 7);
        SkillService.setLevel(d, s, -1);
        check(h, SkillService.level(d, s) == 3, "сброс ГМа к уровню умения, было " + SkillService.level(d, s));
        d.revokePerk(faygolover.rpperks.perk.Perk.FIRST_AID);
        SkillService.syncSkillPerks(d);
        check(h, SkillService.level(d, s) == 0, "отказ забрал выданное, было " + SkillService.level(d, s));
        h.succeed();
    }

    /** Наработанный выше уровня ГМа: наработанный уровень, его опыт + 10 %. */
    @GameTest(template = T)
    public static void migrateTrainedHigher(GameTestHelper h) {
        PlayerPerks d = new PlayerPerks();
        SkillDef s = SkillDef.COOKING;
        d.setTrained(s.key(), 5);
        d.setSkillXp(s.key(), SkillService.need(s, 5) * 0.2);
        d.setSkillLevel(s.id.toString(), 3);
        SkillService.migrateGmLevels(d);
        check(h, near(SkillService.progress(d, s), 5.3), "прогресс 5,3, было " + SkillService.progress(d, s));
        h.succeed();
    }

    /** Шаги ГМа: уровень и 10 % уровня, без хвостов округления; края 0 и 10; сброс к стартовому. */
    @GameTest(template = T)
    public static void gmSteps(GameTestHelper h) {
        PlayerPerks d = new PlayerPerks();
        SkillDef s = SkillDef.MEDICINE;
        SkillService.setLevel(d, s, 3);
        check(h, near(SkillService.progress(d, s), 3), "уровень 3: " + SkillService.progress(d, s));
        for (int i = 0; i < 10; i++) SkillService.shift(d, s, 0.1);
        check(h, SkillService.level(d, s) == 4 && near(SkillService.progress(d, s), 4), "десять раз по 10 % — ровно 4: " + SkillService.progress(d, s));
        SkillService.shift(d, s, 1);
        SkillService.shift(d, s, -0.1);
        check(h, SkillService.level(d, s) == 4 && near(SkillService.progress(d, s), 4.9), "4,9: " + SkillService.progress(d, s));
        SkillService.setLevel(d, s, 7);
        check(h, near(SkillService.progress(d, s), 7.9), "уровень 7 с той же долей: " + SkillService.progress(d, s));
        SkillService.shift(d, s, 20);
        check(h, SkillService.level(d, s) == 10 && d.getSkillXp(s.key()) == 0, "потолок 10, опыт 0");
        SkillService.shift(d, s, -20);
        check(h, SkillService.level(d, s) == 0 && near(SkillService.progress(d, s), 0), "пол 0");
        SkillService.shift(d, s, 2.5);
        SkillService.setLevel(d, s, -1);
        check(h, d.getTrained(s.key()) < 0 && d.getSkillXp(s.key()) == 0, "сброс к стартовому");
        h.succeed();
    }
}

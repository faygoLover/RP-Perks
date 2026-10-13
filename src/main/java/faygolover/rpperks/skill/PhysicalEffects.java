package faygolover.rpperks.skill;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpperks.Config;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.events.PerkEventHandler;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.Stat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Баффы Силы и Телосложения по уровню (RP Stamina Sports docs/04 п. 1.8–1.9): от обычного уровня
 * ({@code physical_start_level}, по умолчанию 3) — плюсы выше, минусы ниже.
 * <ul>
 *   <li>Сила — скорость ударов (здесь), урон и отбрасывание в ближнем бою ({@link SkillEvents}), отдача тяжёлого
 *       оружия (TaCZ).</li>
 *   <li>Телосложение — запас и восстановление выносливости (вместе, чтобы полный запас всегда заполнялся за одно время),
 *       траты на бег, скорость передвижения, шаг на блок с уровня 9.</li>
 * </ul>
 * Модификаторы временные: ставятся при каждом пересчёте игрока.
 */
public final class PhysicalEffects {
    private PhysicalEffects() {}

    private static final UUID STR_ATTACK_SPEED = UUID.fromString("7b1e2d3c-4f5a-4b6c-8d7e-9f0a1b2c3d41");
    private static final UUID FIT_SPEED = UUID.fromString("7b1e2d3c-4f5a-4b6c-8d7e-9f0a1b2c3d42");
    private static final UUID FIT_STEP = UUID.fromString("7b1e2d3c-4f5a-4b6c-8d7e-9f0a1b2c3d43");
    private static final UUID FIT_STAMINA_MAX = UUID.fromString("7b1e2d3c-4f5a-4b6c-8d7e-9f0a1b2c3d44");
    private static final UUID FIT_STAMINA_REGEN = UUID.fromString("7b1e2d3c-4f5a-4b6c-8d7e-9f0a1b2c3d45");
    private static final UUID FIT_MOVE_COST = UUID.fromString("7b1e2d3c-4f5a-4b6c-8d7e-9f0a1b2c3d46");

    public static void apply(ServerPlayer p, PlayerPerks d) {
        boolean on = !d.isAdmin();
        int pivot = SkillService.pivot();
        int s = SkillService.level(d, SkillDef.STRENGTH) - pivot;
        int f = SkillService.level(d, SkillDef.FITNESS) - pivot;

        PerkEventHandler.setModifier(p, Attributes.ATTACK_SPEED, STR_ATTACK_SPEED, "RP strength (attack speed)",
                s * Config.get(Config.STRENGTH_ATTACK_SPEED_PER_LEVEL), AttributeModifier.Operation.MULTIPLY_TOTAL, on && s != 0);

        double speed = f * Config.get(Config.FITNESS_SPEED_PER_LEVEL);
        // Жирный без мода веса: штраф к скорости (с Culinary его даёт само ожирение).
        boolean weightTracked = Double.isFinite(RpCoreAPI.weightKg(p)) || RpCoreAPI.nutritionHandles(RpIds.FOOD_WEIGHT);
        if (!weightTracked && d.hasPerk(Perk.OBESE)) speed -= Config.get(Config.OBESE_SPEED_PERCENT) / 100.0;
        PerkEventHandler.setModifier(p, Attributes.MOVEMENT_SPEED, FIT_SPEED, "RP fitness (speed)",
                speed, AttributeModifier.Operation.MULTIPLY_BASE, on && Math.abs(speed) > 1e-9);

        int stepLevel = Config.get(Config.FITNESS_STEP_LEVEL);
        boolean step = on && stepLevel > 0 && f + pivot >= stepLevel;
        PerkEventHandler.setModifier(p, ForgeMod.STEP_HEIGHT_ADDITION.get(), FIT_STEP, "RP fitness (step)",
                0.5, AttributeModifier.Operation.ADDITION, step);

        double stamina = f * Config.get(Config.FITNESS_STAMINA_PER_LEVEL);
        PerkEventHandler.setModifier(p, Stat.STAMINA_MAX.getAttribute(), FIT_STAMINA_MAX, "RP fitness (stamina max)",
                stamina, AttributeModifier.Operation.MULTIPLY_BASE, on && f != 0);
        PerkEventHandler.setModifier(p, Stat.STAMINA_REGEN.getAttribute(), FIT_STAMINA_REGEN, "RP fitness (stamina regen)",
                stamina, AttributeModifier.Operation.MULTIPLY_BASE, on && f != 0);
        PerkEventHandler.setModifier(p, Stat.STAMINA_MOVE_COST.getAttribute(), FIT_MOVE_COST, "RP fitness (move cost)",
                -f * Config.get(Config.FITNESS_MOVE_COST_PER_LEVEL), AttributeModifier.Operation.MULTIPLY_BASE, on && f != 0);
    }

    /** Сила: множитель урона в ближнем бою (с Силачом и Слабаком). */
    public static double meleeDamage(PlayerPerks d) {
        int s = SkillService.level(d, SkillDef.STRENGTH) - SkillService.pivot();
        double m = Math.max(0.3, 1.0 + s * Config.get(Config.STRENGTH_MELEE_PER_LEVEL));
        if (d.hasPerk(Perk.STRONGMAN)) m *= 1.0 + Config.get(Config.STRONGMAN_MELEE_PERCENT) / 100.0;
        if (d.hasPerk(Perk.WIMP)) m *= Math.max(0.0, 1.0 - Config.get(Config.WIMP_MELEE_PERCENT) / 100.0);
        return m;
    }

    /** Сила: множитель отбрасывания от ударов (Слабак — меньше). */
    public static double meleeKnockback(PlayerPerks d) {
        int s = SkillService.level(d, SkillDef.STRENGTH) - SkillService.pivot();
        double m = Math.max(0.0, 1.0 + s * Config.get(Config.STRENGTH_KNOCKBACK_PER_LEVEL));
        if (d.hasPerk(Perk.WIMP)) m *= Math.max(0.0, 1.0 - Config.get(Config.WIMP_MELEE_PERCENT) / 100.0);
        return m;
    }

    /** Сила: множитель отдачи тяжёлого оружия. */
    public static double heavyRecoil(PlayerPerks d) {
        int s = SkillService.level(d, SkillDef.STRENGTH) - SkillService.pivot();
        return Math.max(0.5, 1.0 - s * Config.get(Config.STRENGTH_HEAVY_RECOIL_PER_LEVEL));
    }
}

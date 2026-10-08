package faygolover.rpperks.skill;

import faygolover.rpcore.api.RpIds;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Навыки семейства RP, которые ведёт RP Perks (уровни 0–10). Боевые — оружие и ближний бой; физические — Сила и
 * Форма (растут от нагрузки, от них зависят динамические перки Слабак/Силач, Задохлик/Выносливый/Атлет).
 */
public enum SkillDef {
    MEDICINE(RpIds.MEDICINE, Kind.KNOWLEDGE),
    COOKING(RpIds.COOKING, Kind.KNOWLEDGE),
    SHORT_ARMS(RpIds.SHORT_ARMS, Kind.COMBAT),
    LONG_ARMS(RpIds.LONG_ARMS, Kind.COMBAT),
    HEAVY_ARMS(RpIds.HEAVY_ARMS, Kind.COMBAT),
    EXPLOSIVES(RpIds.EXPLOSIVES, Kind.COMBAT),
    MELEE(RpIds.MELEE, Kind.COMBAT),
    STRENGTH(RpIds.STRENGTH, Kind.PHYSICAL),
    FITNESS(RpIds.FITNESS, Kind.PHYSICAL);

    public enum Kind { KNOWLEDGE, COMBAT, PHYSICAL }

    public static final SkillDef[] VALUES = values();

    public final ResourceLocation id;
    public final Kind kind;

    SkillDef(ResourceLocation id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    /** Короткое имя для конфига и NBT (short_arms). */
    public String key() {
        return id.getPath();
    }

    public String nameKey() {
        return "skill.rpperks." + key();
    }

    public String descKey() {
        return "skill.rpperks." + key() + ".desc";
    }

    public boolean isCombat() {
        return kind == Kind.COMBAT;
    }

    public static Optional<SkillDef> byId(ResourceLocation id) {
        for (SkillDef s : VALUES) if (s.id.equals(id)) return Optional.of(s);
        return Optional.empty();
    }

    public static Optional<SkillDef> byKey(String key) {
        for (SkillDef s : VALUES) if (s.key().equalsIgnoreCase(key) || s.id.toString().equalsIgnoreCase(key)) return Optional.of(s);
        return Optional.empty();
    }
}

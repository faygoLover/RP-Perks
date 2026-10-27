package faygolover.rpperks.compat;

import faygolover.rpcore.api.RpIds;
import faygolover.rpcore.api.TraitProvider;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.events.PerkEventHandler;
import faygolover.rpperks.perk.Perk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RP Perks для RP Core: навыки семейства и черты по особенностям. Другие моды спрашивают Core и не знают о классах
 * Perks — поэтому каждый мод запускается и без Perks.
 * <p>
 * Уровни навыков — {@link faygolover.rpperks.skill.SkillService}: ГМ, наработанный, не ниже уровня от умений
 * (таблица {@code skill_system.perk_skills} в конфиге).
 */
public final class CoreTraits implements TraitProvider {

    /** Черта RP Core → особенности, которые её дают. */
    @SuppressWarnings("deprecation")
    private static final Map<ResourceLocation, Set<Perk>> TRAITS = buildTraits();

    @SuppressWarnings("deprecation")
    private static Map<ResourceLocation, Set<Perk>> buildTraits() {
        Map<ResourceLocation, Set<Perk>> m = new HashMap<>();
        // Тело и характер
        m.put(RpIds.TOUGH, Set.of(Perk.TOUGH));
        m.put(RpIds.FRAGILE, Set.of(Perk.FRAGILE));
        m.put(RpIds.HARDY, Set.of(Perk.SURVIVOR));
        m.put(RpIds.DELICATE, Set.of(Perk.DELICATE));
        m.put(RpIds.FAST_HEALING, Set.of(Perk.SURVIVOR));
        m.put(RpIds.SLOW_HEALING, Set.of(Perk.DELICATE));
        m.put(RpIds.STRONG_IMMUNITY, Set.of(Perk.TOURIST));
        m.put(RpIds.WEAK_IMMUNITY, Set.of(Perk.HOMEBODY));
        m.put(RpIds.BRAVE, Set.of(Perk.BRAVE, Perk.STRONG_WILLED));
        m.put(RpIds.COWARD, Set.of(Perk.COWARD));
        // Психика (RP Psyche, Core 0.7.1)
        m.put(RpIds.STRONG_WILLED, Set.of(Perk.STRONG_WILLED));
        m.put(RpIds.SUGGESTIBLE, Set.of(Perk.SUGGESTIBLE));
        m.put(RpIds.DIABETIC, Set.of(Perk.DIABETIC));
        m.put(RpIds.SMOKER, Set.of(Perk.SMOKER));
        m.put(RpIds.ALCOHOLIC, Set.of(Perk.ALCOHOLIC));
        m.put(RpIds.ALCOHOL_ADDICT, Set.of(Perk.ALCOHOL_ADDICT));
        m.put(RpIds.TEETOTALER, Set.of(Perk.TEETOTALER));
        m.put(RpIds.CAFFEINE_ADDICT, Set.of(Perk.CAFFEINE_ADDICTION));
        m.put(RpIds.AMPUTEE, Set.of(Perk.AMPUTEE));
        m.put(RpIds.HEMOPHOBIC, Set.of(Perk.HEMOPHOBIC));
        m.put(RpIds.SUN_SENSITIVE, Set.of(Perk.SUN_SENSITIVE));
        m.put(RpIds.ILLITERATE, Set.of(Perk.ILLITERATE));
        m.put(RpIds.QUICK_WORKER, Set.of(Perk.QUICK_WORKER));
        m.put(RpIds.SLOW_WORKER, Set.of(Perk.SLOW_WORKER));
        // Выносливость и спорт
        m.put(RpIds.ENDURING, Set.of(Perk.ENDURING));
        m.put(RpIds.SHORT_WINDED, Set.of(Perk.WEAKLING));
        m.put(RpIds.ASTHMATIC, Set.of(Perk.ASTHMATIC));
        m.put(RpIds.SECOND_WIND, Set.of(Perk.SECOND_WIND));
        m.put(RpIds.QUICK_REST, Set.of(Perk.QUICK_REST));
        m.put(RpIds.GYM_LOVER, Set.of(Perk.GYM_LOVER));
        m.put(RpIds.AGILE, Set.of(Perk.AGILE));
        m.put(RpIds.CLUMSY, Set.of(Perk.CLUMSY));
        m.put(RpIds.CLUMSY_RUNNER, Set.of(Perk.CLUMSY_RUNNER));
        m.put(RpIds.UNLUCKY, Set.of(Perk.UNLUCKY));
        m.put(RpIds.MELEE_FIGHTER, Set.of(Perk.MELEE_FIGHTER));
        // Питание (RP Culinary)
        m.put(RpIds.ANOREXIC, Set.of(Perk.ANOREXIC));
        m.put(RpIds.DIETITIAN, Set.of(Perk.DIETITIAN));
        m.put(RpIds.FIELD_COOK, Set.of(Perk.FIELD_COOK));
        m.put(RpIds.CLEAN, Set.of(Perk.CLEAN));
        m.put(RpIds.IRON_STOMACH, Set.of(Perk.IRON_STOMACH));
        m.put(RpIds.WEAK_STOMACH, Set.of(Perk.WEAK_STOMACH));
        m.put(RpIds.GOURMET, Set.of(Perk.GOURMET));
        m.put(RpIds.KITCHEN_NIGHTMARE, Set.of(Perk.KITCHEN_NIGHTMARE));
        m.put(RpIds.FAST_METABOLISM, Set.of(Perk.FAST_METABOLISM));
        m.put(RpIds.SLOW_METABOLISM, Set.of(Perk.SLOW_METABOLISM));
        m.put(RpIds.LIGHT_EATER, Set.of(Perk.LIGHT_EATER));
        m.put(RpIds.BIG_EATER, Set.of(Perk.BIG_EATER));
        m.put(RpIds.LOW_THIRST, Set.of(Perk.LOW_THIRST));
        m.put(RpIds.HIGH_THIRST, Set.of(Perk.HIGH_THIRST));
        m.put(RpIds.BAD_TEETH, Set.of(Perk.BAD_TEETH));
        m.put(RpIds.EMACIATED, Set.of(Perk.EMACIATED));
        m.put(RpIds.OVERWEIGHT, Set.of(Perk.OVERWEIGHT));
        m.put(RpIds.OBESE, Set.of(Perk.OBESE));
        // Экзотика и каннибализм (RP Culinary): черты rpculinary:exotic/<группа>, rpculinary:cannibal
        m.put(new ResourceLocation("rpculinary", "cannibal"), Set.of(Perk.CANNIBAL));
        for (Perk p : Perk.values()) if (p.getId().startsWith("exotic_"))
            m.put(new ResourceLocation("rpculinary", "exotic/" + p.getId().substring("exotic_".length())), Set.of(p));
        return Map.copyOf(m);
    }

    /**
     * Выдать служебный перк-знание по черте (книга «Записи исследований» RP Culinary зовёт отражением, без
     * зависимости). 1 — выдан, 0 — уже есть, −1 — нет такого знания или данных игрока.
     */
    public static int grantKnowledge(ServerPlayer player, ResourceLocation trait) {
        Set<Perk> perks = TRAITS.get(trait);
        PlayerPerks d = data(player);
        if (perks == null || d == null) return -1;
        for (Perk p : perks) {
            if (p.getBaseCategory() != Perk.Category.SERVICE) continue;
            if (d.owns(p)) return 0;
            if (!d.grantPerk(p, null)) return 0;
            PerkEventHandler.refreshPlayer(player, d);
            faygolover.rpperks.Rpperks.LOGGER.info("[RP Perks] {} изучил {}", player.getScoreboardName(), p.getId());
            return 1;
        }
        return -1;
    }

    private static PlayerPerks data(Player p) {
        return p.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }

    @Override
    public boolean managesSkill(ResourceLocation skill) {
        return faygolover.rpperks.skill.SkillDef.byId(skill).isPresent();
    }

    @Override
    public int skill(Player player, ResourceLocation skill) {
        return faygolover.rpperks.skill.SkillDef.byId(skill).map(s -> faygolover.rpperks.skill.SkillService.level(player, s)).orElse(0);
    }

    @Override
    public boolean setSkill(Player player, ResourceLocation skill, int level) {
        PlayerPerks d = data(player);
        if (d == null || faygolover.rpperks.skill.SkillDef.byId(skill).isEmpty()) return false;
        d.setSkillLevel(skill.toString(), level);
        if (player instanceof ServerPlayer sp) PerkEventHandler.refreshPlayer(sp, d);
        return true;
    }

    @Override
    public void practice(Player player, ResourceLocation skill, double amount) {
        if (player instanceof ServerPlayer sp)
            faygolover.rpperks.skill.SkillDef.byId(skill).ifPresent(s -> faygolover.rpperks.skill.SkillService.practice(sp, s, amount));
    }

    @Override
    public void grantXp(Player player, ResourceLocation skill, double xp) {
        if (player instanceof ServerPlayer sp)
            faygolover.rpperks.skill.SkillDef.byId(skill).ifPresent(s -> faygolover.rpperks.skill.SkillService.grantXp(sp, s, xp));
    }

    @Override
    public boolean hasTrait(Player player, ResourceLocation trait) {
        Set<Perk> perks = TRAITS.get(trait);
        if (perks == null) return false;
        PlayerPerks d = data(player);
        if (d == null) return false;
        for (Perk p : perks) if (d.hasPerk(p)) return true;
        return false;
    }
}

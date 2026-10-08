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

import java.util.Map;
import java.util.Set;

/**
 * RP Perks для RP Core: навыки семейства (Медицина, Кулинария) и черты по перкам. Другие моды спрашивают
 * Core и не знают о классах Perks — поэтому каждый мод запускается и без Perks.
 * <p>
 * Уровень навыка: выставленный ГМом ({@code /rpcore skill}), иначе по перкам — «Курсы первой помощи» 3,
 * «Анастасия» 8 (как было в RP Medicine), «Кулинар» 5.
 */
public final class CoreTraits implements TraitProvider {
    private static final Set<ResourceLocation> SKILLS = Set.of(RpIds.MEDICINE, RpIds.COOKING);

    /** Черта RP Core → перки, которые её дают. */
    private static final Map<ResourceLocation, Set<Perk>> TRAITS = Map.of(
            RpIds.TOUGH, Set.of(Perk.SURVIVOR, Perk.TOUGH),
            RpIds.FRAGILE, Set.of(Perk.FRAGILE, Perk.WEAKLING, Perk.WIMP),
            RpIds.BRAVE, Set.of(Perk.BRAVE, Perk.STRONG_WILLED),
            RpIds.COWARD, Set.of(Perk.COWARD),
            RpIds.DIABETIC, Set.of(Perk.DIABETIC),
            RpIds.SMOKER, Set.of(Perk.SMOKER),
            RpIds.ALCOHOLIC, Set.of(Perk.ALCOHOLIC),
            RpIds.TEETOTALER, Set.of(Perk.TEETOTALER),
            RpIds.CAFFEINE_ADDICT, Set.of(Perk.CAFFEINE_ADDICTION));

    private static PlayerPerks data(Player p) {
        return p.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }

    @Override
    public boolean managesSkill(ResourceLocation skill) {
        return SKILLS.contains(skill);
    }

    @Override
    public int skill(Player player, ResourceLocation skill) {
        PlayerPerks d = data(player);
        if (d == null) return 0;
        int set = d.getSkillLevel(skill.toString());
        if (set >= 0) return set;
        if (skill.equals(RpIds.MEDICINE)) {
            if (d.hasPerk(Perk.MEDIC)) return 8;
            if (d.hasPerk(Perk.FIRST_AID)) return 3;
        } else if (skill.equals(RpIds.COOKING)) {
            if (d.hasPerk(Perk.COOK)) return 5;
        }
        return 0;
    }

    @Override
    public boolean setSkill(Player player, ResourceLocation skill, int level) {
        PlayerPerks d = data(player);
        if (d == null) return false;
        d.setSkillLevel(skill.toString(), level);
        if (player instanceof ServerPlayer sp) PerkEventHandler.syncToClient(sp, d);
        return true;
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

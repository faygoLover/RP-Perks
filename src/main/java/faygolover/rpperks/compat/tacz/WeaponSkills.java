package faygolover.rpperks.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import faygolover.rpperks.Config;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.skill.SkillDef;
import faygolover.rpperks.skill.SkillService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Оружейные навыки для TaCZ (грузится, только если TaCZ установлен). Тип ствола → навык: пистолеты и ПП —
 * короткоствольное, винтовки и дробовики — длинноствольное, пулемёты, снайперские, гранатомёты — тяжёлое.
 * Пока ствол в руке, характеристики Tacz Attribute Add (разброс, отдача, перезарядка, прицеливание, взрывы)
 * множатся по таблицам уровня (5 = как в TaCZ). Без taa — только опыт.
 */
public final class WeaponSkills {
    private WeaponSkills() {}

    private static final UUID SPREAD = UUID.fromString("7b2e3d4c-5f60-4b7c-8d9e-0f1a2b3c4d01");
    private static final UUID RECOIL = UUID.fromString("7b2e3d4c-5f60-4b7c-8d9e-0f1a2b3c4d02");
    private static final UUID RELOAD = UUID.fromString("7b2e3d4c-5f60-4b7c-8d9e-0f1a2b3c4d03");
    private static final UUID AIM = UUID.fromString("7b2e3d4c-5f60-4b7c-8d9e-0f1a2b3c4d04");
    private static final UUID BLAST_R = UUID.fromString("7b2e3d4c-5f60-4b7c-8d9e-0f1a2b3c4d05");
    private static final UUID BLAST_D = UUID.fromString("7b2e3d4c-5f60-4b7c-8d9e-0f1a2b3c4d06");

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(WeaponSkills::onTick);
        MinecraftForge.EVENT_BUS.addListener(WeaponSkills::onShoot);
        MinecraftForge.EVENT_BUS.addListener(WeaponSkills::onHit);
    }

    /** Тип ствола TaCZ (pistol, smg, rifle, shotgun, sniper, mg, rpg) или null. */
    @Nullable
    static String gunType(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return null;
        ResourceLocation id = gun.getGunId(stack);
        return TimelessAPI.getCommonGunIndex(id).map(i -> i.getType()).orElse(null);
    }

    @Nullable
    static SkillDef skillFor(@Nullable String type) {
        if (type == null) return null;
        return switch (type) {
            case "pistol", "smg" -> SkillDef.SHORT_ARMS;
            case "rifle", "shotgun" -> SkillDef.LONG_ARMS;
            case "mg", "sniper", "rpg" -> SkillDef.HEAVY_ARMS;
            default -> SkillDef.LONG_ARMS;
        };
    }

    private static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        PlayerPerks d = SkillService.data(p);
        String type = gunType(p.getMainHandItem());
        SkillDef skill = skillFor(type);
        boolean on = skill != null && d != null && !d.isAdmin();
        int lvl = on ? SkillService.level(d, skill) : 5;
        double recoil = Config.table(Config.T_RECOIL, lvl);
        if (on && skill == SkillDef.HEAVY_ARMS) {
            recoil *= faygolover.rpperks.skill.PhysicalEffects.heavyRecoil(d);
        }
        set(p, "inaccuracy", SPREAD, on ? Config.table(Config.T_SPREAD, lvl) : 1.0);
        set(p, "recoil", RECOIL, on ? recoil : 1.0);
        set(p, "reload_time", RELOAD, on ? Config.table(Config.T_RELOAD, lvl) : 1.0);
        set(p, "ads_time", AIM, on ? Config.table(Config.T_AIM, lvl) : 1.0);
        double blast = on && "rpg".equals(type) ? Config.table(Config.T_EXPLOSIVE, SkillService.level(d, SkillDef.EXPLOSIVES)) : 1.0;
        set(p, "explosion_radius", BLAST_R, blast);
        set(p, "explosion_damage", BLAST_D, blast);
    }

    /** Множитель на характеристику taa (своим модификатором; 1 — снять). */
    private static void set(ServerPlayer p, String name, UUID id, double mult) {
        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("taa", name));
        if (a == null) return;
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        double want = mult - 1.0;
        AttributeModifier old = inst.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - want) < 1e-6) return;
        if (old != null) inst.removeModifier(id);
        if (Math.abs(want) > 1e-6) inst.addTransientModifier(new AttributeModifier(id, "RP weapon skill", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void onShoot(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer p)) return;
        String type = gunType(e.getGunItemStack());
        SkillDef skill = skillFor(type);
        if (skill == null) return;
        SkillService.practice(p, skill, Config.get(Config.XP_GUN_SHOT));
        if ("rpg".equals(type)) SkillService.practice(p, SkillDef.EXPLOSIVES, Config.get(Config.XP_EXPLOSIVE_SHOT));
    }

    private static void onHit(EntityHurtByGunEvent.Post e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getAttacker() instanceof ServerPlayer p)) return;
        if (!(e.getHurtEntity() instanceof net.minecraft.world.entity.LivingEntity)) return;
        String type = TimelessAPI.getCommonGunIndex(e.getGunId()).map(i -> i.getType()).orElse(null);
        SkillDef skill = skillFor(type);
        if (skill != null) SkillService.practice(p, skill, Config.get(Config.XP_GUN_HIT));
    }
}

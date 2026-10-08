package faygolover.rpperks.skill;

import faygolover.rpperks.Config;
import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.perk.Perk;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * События навыков (сервер): ближний бой — любое оружие и голые руки (урон и скорость ударов от уровня, урон
 * от Силы, опыт за удар), Пацифист — урон по людям, раз в минуту — потеря Силы и Формы без нагрузки.
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID)
public final class SkillEvents {
    private SkillEvents() {}

    private static final UUID MELEE_SPEED_UUID = UUID.fromString("6a1d2c3b-4e5f-4a6b-9c7d-8e9f0a1b2c3d");
    /** Люди для Пацифиста, кроме игроков: NPC модов (датапак, тег rpperks:humans). */
    public static final TagKey<EntityType<?>> HUMANS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Rpperks.MODID, "humans"));

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        SkillService.tick(p);
        if (p.tickCount % 20 != 0) return;
        PlayerPerks d = SkillService.data(p);
        if (d == null) return;
        // Скорость ударов — от уровня ближнего боя (модификатор на ванильную скорость атаки — для любого оружия).
        double speed = Config.table(Config.T_MELEE_SPEED, SkillService.level(d, SkillDef.MELEE));
        var inst = p.getAttribute(Attributes.ATTACK_SPEED);
        if (inst != null) {
            AttributeModifier old = inst.getModifier(MELEE_SPEED_UUID);
            double want = d.isAdmin() ? 0 : speed - 1.0;
            if (old == null || Math.abs(old.getAmount() - want) > 1e-6) {
                if (old != null) inst.removeModifier(MELEE_SPEED_UUID);
                if (Math.abs(want) > 1e-6)
                    inst.addTransientModifier(new AttributeModifier(MELEE_SPEED_UUID, "RP melee skill", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        if (p.tickCount % 1200 == 0) SkillService.minuteTick(p, d);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) SkillService.forget(p);
    }

    /** До RP Medicine (она превращает урон в травмы): сначала навык и Пацифист меняют силу удара. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || p == e.getEntity()) return;
        PlayerPerks d = SkillService.data(p);
        if (d == null || d.isAdmin()) return;
        LivingEntity target = e.getEntity();
        float amount = e.getAmount();
        boolean melee = e.getSource().getDirectEntity() == p;
        if (melee) {
            int str = SkillService.level(d, SkillDef.STRENGTH);
            amount *= (float) (Config.table(Config.T_MELEE_DAMAGE, SkillService.level(d, SkillDef.MELEE))
                    * Math.max(0.5, 1.0 + (str - 4) * Config.get(Config.STRENGTH_MELEE_PER_LEVEL)));
            if (!(target instanceof ArmorStand) && amount > 0)
                SkillService.practice(p, SkillDef.MELEE, Config.get(Config.XP_MELEE_HIT));
        }
        if (d.hasPerk(Perk.PACIFIST) && isHuman(target)) amount *= Config.get(Config.PACIFIST_HUMAN_DAMAGE).floatValue();
        e.setAmount(amount);
    }

    public static boolean isHuman(LivingEntity e) {
        return e instanceof Player || e.getType().is(HUMANS);
    }
}

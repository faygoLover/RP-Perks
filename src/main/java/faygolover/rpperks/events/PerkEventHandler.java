package faygolover.rpperks.events;

import faygolover.rpperks.Config;
import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.network.NetworkHandler;
import faygolover.rpperks.network.OpenPerkScreenPacket;
import faygolover.rpperks.network.SyncPerksPacket;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.Stat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Жизненный цикл игрока, пересчёт характеристик, синхронизация и наблюдение админа.
 * Потиковые механики перков — в {@link PerkMechanicsHandler}.
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID)
public final class PerkEventHandler {
    private PerkEventHandler() {}

    // UUID временных модификаторов (не сохраняются в мир, пересоздаются механиками)
    static final UUID LIGHT_TRAVEL_UUID = UUID.fromString("1f203a4b-5c6d-7e8f-9a0b-1c2d3e4f5a6b");
    static final UUID CAFFEINE_SPEED_UUID = UUID.fromString("2f304a5b-6c7d-8e9f-0a1b-2c3d4e5f6a7b");
    static final UUID CAFFEINE_ATTACK_UUID = UUID.fromString("3f405a6b-7c8d-9e0f-1a2b-3c4d5e6f7a8b");
    static final UUID SPRINT_BOOST_UUID = UUID.fromString("4f506a7b-8c9d-0e1f-2a3b-4c5d6e7f8a9b");
    static final UUID SOLAR_RECOVERY_UUID = UUID.fromString("5f607a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b");
    /** Постоянный модификатор «Бойца ББ» из старой версии — снимается, чтобы не остался в сохранениях навсегда. */
    private static final UUID LEGACY_SKILL_UUID = UUID.fromString("6aef0182-3df2-4716-bc5b-ee4c12ff96f1");

    private static final UUID[] DYNAMIC_UUIDS = {
            LIGHT_TRAVEL_UUID, CAFFEINE_SPEED_UUID, CAFFEINE_ATTACK_UUID,
            SPRINT_BOOST_UUID, SOLAR_RECOVERY_UUID, LEGACY_SKILL_UUID
    };

    /** Админ -> игрок, чьё окно перков админ открыл последним (окно получает живые обновления). */
    public static final Map<UUID, UUID> ADMIN_SCREENS = new ConcurrentHashMap<>();

    // =====================================================================
    // Жизненный цикл
    // =====================================================================

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        oldPlayer.reviveCaps();
        oldPlayer.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(oldData ->
                event.getEntity().getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(newData -> newData.copyFrom(oldData)));
        oldPlayer.invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 2.7.0: старый уровень ГМа замораживал навык — перевести в наработанный до первой синхронизации.
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            int n = faygolover.rpperks.skill.SkillService.migrateGmLevels(data);
            if (n > 0) faygolover.rpperks.Rpperks.LOGGER.info("[RP Perks] {}: уровень ГМа переведён в наработанный ({} навык.)",
                    player.getScoreboardName(), n);
        });
        refresh(player);
        // 2.0.0: старые версии особенностей сняты с возвратом очков — сказать игроку один раз.
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (data.getMigrationRefunded() < 0) return;
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.rpperks.migrated_2",
                    data.getMigrationRefunded()).withStyle(net.minecraft.ChatFormatting.GOLD));
            data.setMigrationRefunded(-1);
        });
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }

    /** При смене измерения клиент пересоздаёт игрока, и его capability пустеет — отправляем данные заново. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> syncToClient(player, data));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        ADMIN_SCREENS.remove(id);
        ADMIN_SCREENS.values().removeIf(id::equals);
    }

    // =====================================================================
    // Пересчёт характеристик
    // =====================================================================

    public static void refresh(ServerPlayer player) {
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> refreshPlayer(player, data));
    }

    /** Полный пересчёт: база из конфига + изменения от перков (или ванильные значения в режиме Админа). */
    public static void refreshPlayer(ServerPlayer player, PlayerPerks data) {
        float oldHealth = player.getHealth();
        boolean wasFullHealth = oldHealth >= player.getMaxHealth();

        clearDynamicModifiers(player);
        // Навыки: взятая физическая особенность сдвигает Силу или Телосложение (старт).
        faygolover.rpperks.skill.SkillService.syncPhysique(data);

        if (data.isAdmin()) {
            // В режиме Админа обычные статы не действуют — кэш неактуален и не нужен,
            // пока getStatValue() и так возвращает 0.0 для админа в приоритете.
            data.setCachedStats(null);
            applyAdminState(player);
        } else {
            // Считаем один раз и переиспользуем результат: и для применения к атрибутам сейчас,
            // и как кэш для getStatValue() (используется в горячих путях, например при каждом ударе
            // в ближнем бою), чтобы не гонять полный пересчёт по всем перкам и конфигу на каждое обращение.
            EnumMap<Stat, Double> stats = computeStats(data);
            data.setCachedStats(stats);
            applyStats(player, stats);
        }
        // Баффы Силы и Телосложения по уровню (у Админа снимаются).
        faygolover.rpperks.skill.PhysicalEffects.apply(player, data);

        // Сохраняем здоровье после смены максимума
        player.setHealth(wasFullHealth ? player.getMaxHealth() : Math.min(oldHealth, player.getMaxHealth()));

        syncToClient(player, data);
        faygolover.rpperks.skill.SkillService.sync(player);
        // Слоты инвентаря (Организованный / Неорганизованный, Админ) — RP Inventory пересчитает сразу.
        faygolover.rpcore.api.RpCoreAPI.inventoryChanged(player);
    }

    /** Итоговые значения всех характеристик игрока без учёта режима Админа. */
    public static EnumMap<Stat, Double> computeStats(PlayerPerks data) {
        EnumMap<Stat, Double> values = new EnumMap<>(Stat.class);
        EnumMap<Stat, Double> percents = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) values.put(stat, Config.getBaseline(stat));

        for (Perk perk : data.getEffectivePerks()) {
            Config.getBonuses(perk).forEach((stat, bonus) ->
                    (stat.isPercentBonus() ? percents : values).merge(stat, bonus, Double::sum));
        }
        percents.forEach((stat, percent) -> values.put(stat, values.get(stat) * Math.max(0.0, 1.0 + percent / 100.0)));
        return values;
    }

    /**
     * Одна характеристика (для событий вроде отбрасывания в ближнем бою).
     * В режиме Админа — ванильный ноль. Иначе берётся из кэша, заполняемого в {@link #refreshPlayer},
     * а не пересчитывается заново — это горячий путь (вызывается на каждый удар в ближнем бою).
     */
    public static double getStatValue(PlayerPerks data, Stat stat) {
        if (data.isAdmin()) return 0.0;
        EnumMap<Stat, Double> cached = data.getCachedStats();
        if (cached != null) {
            Double value = cached.get(stat);
            if (value != null) return value;
        }
        // Подстраховка на случай обращения до первого refreshPlayer (не должно происходить в обычной игре)
        EnumMap<Stat, Double> computed = computeStats(data);
        data.setCachedStats(computed);
        return computed.get(stat);
    }

    private static void applyStats(ServerPlayer player, Map<Stat, Double> values) {
        // С модом тела (RP Medicine) ванильное здоровье скрыто и всегда полное: «Живучий» и «Хрупкий» работают
        // через черты тела, а максимум здоровья остаётся ванильным.
        boolean bodyHealth = faygolover.rpcore.api.RpCoreAPI.bodyHandles(faygolover.rpcore.api.RpIds.BODY_HEALTH);
        values.forEach((stat, value) -> {
            switch (stat.getKind()) {
                case ATTRIBUTE -> setBase(player, stat.getAttribute(),
                        bodyHealth && stat == Stat.MAX_HEALTH ? vanillaBase(stat.getAttribute()) : value);
                case COMMAND, CUSTOM -> { /* обрабатывается в событиях; слоты инвентаря — compat.CoreInventory */ }
            }
        });
    }

    private static void applyAdminState(ServerPlayer player) {
        for (Stat stat : Stat.values()) {
            if (stat.getKind() != Stat.Kind.ATTRIBUTE) continue;
            Attribute attribute = stat.getAttribute();
            if (attribute != null) setBase(player, attribute, vanillaBase(attribute));
        }
        // Админ не тратит выносливость (если установлен rpstamina)
        setBase(player, Stat.STAMINA_COST.getAttribute(), 0.0);
    }

    /** Ванильное базовое значение атрибута игрока (или значение по умолчанию самого атрибута). */
    private static double vanillaBase(Attribute attribute) {
        AttributeSupplier supplier = DefaultAttributes.getSupplier(EntityType.PLAYER);
        return supplier.hasAttribute(attribute) ? supplier.getBaseValue(attribute) : attribute.getDefaultValue();
    }

    // ---------- Атрибуты ----------

    private static void setBase(Player player, @Nullable Attribute attribute, double value) {
        if (attribute == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** Включает, обновляет или снимает временный модификатор. */
    public static void setModifier(Player player, @Nullable Attribute attribute, UUID id, String name,
                            double amount, AttributeModifier.Operation operation, boolean active) {
        if (attribute == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(id);
        if (active) {
            if (current != null && current.getAmount() == amount && current.getOperation() == operation) return;
            if (current != null) instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        } else if (current != null) {
            instance.removeModifier(id);
        }
    }

    private static void clearDynamicModifiers(Player player) {
        List<Attribute> attributes = new ArrayList<>(List.of(Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED, Attributes.ATTACK_DAMAGE));
        Attribute regen = Stat.STAMINA_REGEN.getAttribute();
        if (regen != null) attributes.add(regen);
        for (Attribute attribute : attributes) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (UUID id : DYNAMIC_UUIDS) instance.removeModifier(id);
        }
    }

    // =====================================================================
    // Синхронизация и окна
    // =====================================================================

    /** Отправляет игроку его данные и обновляет открытые у админов окна этого игрока. */
    public static void syncToClient(ServerPlayer player, PlayerPerks data) {
        NetworkHandler.sendToPlayer(player, SyncPerksPacket.of(data));
        updateAdminScreens(player, data);
    }

    /** Обновляет открытые у админов окна этого игрока (перки и навыки). */
    public static void updateAdminScreens(ServerPlayer player, PlayerPerks data) {
        if (ADMIN_SCREENS.isEmpty()) return;
        UUID id = player.getUUID();
        ADMIN_SCREENS.forEach((adminId, targetId) -> {
            if (!targetId.equals(id)) return;
            ServerPlayer admin = player.server.getPlayerList().getPlayer(adminId);
            if (admin != null) {
                NetworkHandler.sendToPlayer(admin, OpenPerkScreenPacket.of(false, true, player, true, data));
            }
        });
    }

    /** Оператор (уровень 2+) видит скрытые перки и может открыть админское окно. */
    public static boolean canSeeHidden(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** Открывает игроку его собственное окно перков. */
    public static void openSelfScreen(ServerPlayer player) {
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            syncToClient(player, data);
            // Своё окно одинаковое у игрока и ГМа: служебные перки и анкета других — только в /rpperks admin.
            NetworkHandler.sendToPlayer(player, OpenPerkScreenPacket.of(true, false, player, false, data));
        });
    }

    /** Открывает админу окно управления перками игрока. Пока окно открыто, оно обновляется само. */
    public static void openAdminScreen(ServerPlayer admin, ServerPlayer target) {
        target.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            ADMIN_SCREENS.put(admin.getUUID(), target.getUUID());
            NetworkHandler.sendToPlayer(admin, OpenPerkScreenPacket.of(true, true, target, true, data));
        });
    }
}

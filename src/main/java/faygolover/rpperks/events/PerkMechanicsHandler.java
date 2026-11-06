package faygolover.rpperks.events;

import faygolover.rpperks.Config;
import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;

/** Игровые механики перков: тики, еда, бой, неприятности. */
@Mod.EventBusSubscriber(modid = Rpperks.MODID)
public final class PerkMechanicsHandler {
    private PerkMechanicsHandler() {}

    // =====================================================================
    // Тик игрока
    // =====================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            // Движение считает клиент, поэтому Сухопутный работает на обеих сторонах
            if (data.hasPerk(Perk.LANDLUBBER)) tickLandlubber(player);

            if (player instanceof ServerPlayer serverPlayer) tickServer(serverPlayer, data);
        });
    }

    private static void tickServer(ServerPlayer player, PlayerPerks data) {
        // Бег — общая механика для всех игроков (не привязана к владению перком), поэтому считается всегда
        tickSprintBoost(player, data);
        // Остальные механики ниже сами не делают лишней работы (поиск атрибута игрока и т.п.),
        // если у игрока нет соответствующего перка — см. ранние return внутри каждой из них.
        tickLightTravel(player, data);
        tickCaffeine(player, data);
        tickAnorexia(player, data);
        if (player.tickCount % 10 == 0) {
            tickNerdRage(player, data);
            tickSolar(player, data);
            tickSunSensitive(player, data);
        }
        if (player.tickCount % 20 == 0) {
            tickAsthma(player, data);
            tickClumsyRunner(player, data);
        }
        if (data.getRunnerCooldown() > 0) data.setRunnerCooldown(data.getRunnerCooldown() - 1);
        if (data.getUnluckyCooldown() > 0) data.setUnluckyCooldown(data.getUnluckyCooldown() - 1);
    }

    // ---------- Бег (для всех, кроме Админа) ----------
    private static void tickSprintBoost(ServerPlayer player, PlayerPerks data) {
        boolean active = player.isSprinting() && !data.isAdmin();
        double amount = Config.get(Config.SPRINT_BOOST_PERCENT) / 100.0;
        PerkEventHandler.setModifier(player, Attributes.MOVEMENT_SPEED, PerkEventHandler.SPRINT_BOOST_UUID,
                "RP sprint boost", amount, AttributeModifier.Operation.MULTIPLY_TOTAL, active && amount > 0);
    }

    // ---------- Путешествие налегке: в лёгкой одежде движение дешевле по выносливости (2.8.1; было +10 % скорости) ----------
    private static void tickLightTravel(ServerPlayer player, PlayerPerks data) {
        // Модификатор этой механики выставляется только пока владеешь перком (см. ниже), поэтому у игрока
        // без перка его никогда и не было — не тратим время на lookup атрибута и чтение конфига.
        if (!data.hasPerk(Perk.LIGHT_TRAVEL)) return;
        boolean active = player.getArmorValue() <= Config.get(Config.LIGHT_TRAVEL_MAX_ARMOR);
        // Без RP Stamina атрибута нет — перк ничего не делает
        PerkEventHandler.setModifier(player, Stat.STAMINA_MOVE_COST.getAttribute(), PerkEventHandler.LIGHT_TRAVEL_UUID,
                "RP light travel", Config.get(Config.LIGHT_TRAVEL_MOVE_COST), AttributeModifier.Operation.ADDITION, active);
    }

    // ---------- Зависимость от кофеина ----------
    private static void tickCaffeine(ServerPlayer player, PlayerPerks data) {
        // Модификаторы ломки выставляются только пока владеешь этим перком (см. ниже), поэтому у игрока
        // без перка их никогда и не было, а снимаются они через clearDynamicModifiers при потере перка —
        // тикать здесь для остальных 29 игроков на сервере незачем.
        if (!data.hasPerk(Perk.CAFFEINE_ADDICTION)) return;
        // С модом тела ломку ведёт он (вялость, сонливость, головная боль по времени без кофеина) — свой таймер стоит.
        if (faygolover.rpcore.api.RpCoreAPI.bodyHandles(faygolover.rpcore.api.RpIds.BODY_CAFFEINE)) {
            if (data.getCaffeineTimer() != 0) data.setCaffeineTimer(0);
            PerkEventHandler.setModifier(player, Attributes.MOVEMENT_SPEED, PerkEventHandler.CAFFEINE_SPEED_UUID, "RP caffeine crash (speed)", 0,
                    AttributeModifier.Operation.MULTIPLY_BASE, false);
            PerkEventHandler.setModifier(player, Attributes.ATTACK_SPEED, PerkEventHandler.CAFFEINE_ATTACK_UUID, "RP caffeine crash (attack)", 0,
                    AttributeModifier.Operation.MULTIPLY_BASE, false);
            return;
        }

        int threshold = Config.caffeineWithdrawalTicks();
        // Клампим таймер порогом: после срабатывания ломки дальше расти ему незачем (UI показывает
        // только факт ломки), а без этого таймер рос бы неограниченно при постоянном онлайне без кофе.
        // Время RP стоит (/time pause) — таймер ломки тоже.
        if (data.getCaffeineTimer() < threshold && faygolover.rpcore.api.RpCoreAPI.timeRunning()) {
            data.setCaffeineTimer(Math.min(data.getCaffeineTimer() + 1, threshold));
        }

        boolean crash = data.getCaffeineTimer() >= threshold;
        PerkEventHandler.setModifier(player, Attributes.MOVEMENT_SPEED, PerkEventHandler.CAFFEINE_SPEED_UUID,
                "RP caffeine crash (speed)", -Config.get(Config.CAFFEINE_SPEED_PENALTY_PERCENT) / 100.0,
                AttributeModifier.Operation.MULTIPLY_BASE, crash);
        PerkEventHandler.setModifier(player, Attributes.ATTACK_SPEED, PerkEventHandler.CAFFEINE_ATTACK_UUID,
                "RP caffeine crash (attack)", -Config.get(Config.CAFFEINE_ATTACK_SPEED_PENALTY_PERCENT) / 100.0,
                AttributeModifier.Operation.MULTIPLY_BASE, crash);

        // Раз в 5 секунд обновляем таймер на HUD
        if (player.tickCount % 100 == 0) PerkEventHandler.syncToClient(player, data);
    }

    /** Голод ведёт мод питания (RP Culinary): маленький желудок и обмен веществ — у него, по черте rpcore:anorexic. */
    private static boolean culinary() {
        return faygolover.rpcore.api.RpCoreAPI.nutritionHandles(faygolover.rpcore.api.RpIds.FOOD_HUNGER);
    }

    // ---------- Анорексичка: предел сытости и таймер отказа ----------
    private static void tickAnorexia(ServerPlayer player, PlayerPerks data) {
        if (!data.hasPerk(Perk.ANOREXIC)) return;
        // Голодающая не отказывается от еды. С RP Culinary ванильная сытость не падает до 0, поэтому — по стадии
        // голода Culinary; отказ снимается здесь, на сервере, и уходит клиенту (тот решает по тому же флагу).
        if (data.isRefuseNextMeal() && player.tickCount % 20 == 0 && starving(player)) {
            data.setRefuseNextMeal(false);
            PerkEventHandler.syncToClient(player, data);
        }
        if (!culinary()) {
            FoodData food = player.getFoodData();
            int maxFood = Config.get(Config.ANOREXIA_MAX_FOOD);
            if (food.getFoodLevel() > maxFood) food.setFoodLevel(maxFood);
            if (food.getSaturationLevel() > food.getFoodLevel()) food.setSaturation(food.getFoodLevel());
        }

        if (data.getAnorexiaTimer() > 0) {
            data.setAnorexiaTimer(data.getAnorexiaTimer() - 1);
            if (data.getAnorexiaTimer() == 0) PerkEventHandler.syncToClient(player, data);
        }
    }

    // ---------- Бешенство ботаника ----------
    private static void tickNerdRage(ServerPlayer player, PlayerPerks data) {
        if (!data.hasPerk(Perk.NERD_RAGE)) return;
        float threshold = (float) (Config.get(Config.NERD_RAGE_HEALTH_PERCENT) / 100.0);
        // С модом тела — от общего состояния (ванильное здоровье там всегда полное и порог бы не наступал).
        var body = faygolover.rpcore.api.RpCoreAPI.body();
        float condition = body != null && body.handles(faygolover.rpcore.api.RpIds.BODY_HEALTH)
                ? body.condition(player) : player.getHealth() / player.getMaxHealth();
        if (condition > threshold) return;
        int amplifier = Config.get(Config.NERD_RAGE_EFFECT_LEVEL) - 1;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amplifier, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amplifier, true, false, true));
    }

    // ---------- Солнечная батарейка ----------
    private static void tickSolar(ServerPlayer player, PlayerPerks data) {
        if (!data.hasPerk(Perk.SOLAR_POWERED)) return;
        Level level = player.level();
        boolean inSun = level.isDay()
                && !level.isRaining()
                && level.canSeeSky(BlockPos.containing(player.getEyePosition()));

        int speedLevel = Config.get(Config.SOLAR_SPEED_LEVEL);
        if (inSun && speedLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, speedLevel - 1, true, false, true));
        }
        // Прибавка к множителю восстановления выносливости (rpstamina); без мода атрибута нет — ничего не происходит
        PerkEventHandler.setModifier(player, Stat.STAMINA_REGEN.getAttribute(), PerkEventHandler.SOLAR_RECOVERY_UUID,
                "RP solar stamina", Config.get(Config.SOLAR_STAMINA_REGEN_BONUS), AttributeModifier.Operation.ADDITION, inSun);
    }

    private static boolean bodyMod() {
        return faygolover.rpcore.api.RpCoreAPI.body() != null;
    }

    private static boolean inSun(ServerPlayer player) {
        Level level = player.level();
        return level.isDay() && !level.isRaining() && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
    }

    /** Голодает: стадия 3 у мода питания, без него — ванильная сытость 0. */
    private static boolean starving(ServerPlayer player) {
        var n = faygolover.rpcore.api.RpCoreAPI.nutrition();
        return culinary() && n != null ? n.hungerStage(player) >= 3 : player.getFoodData().getFoodLevel() <= 0;
    }

    // ---------- Чувствительность к солнцу: Слабость всегда, с RP Medicine ещё и головная боль у неё ----------
    private static void tickSunSensitive(ServerPlayer player, PlayerPerks data) {
        if (!data.hasPerk(Perk.SUN_SENSITIVE) || !inSun(player)) return;
        // Головной убор (любой предмет в слоте головы) защищает
        if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) return;
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
    }

    // ---------- Астматик без RP Medicine: долгий бег — одышка ----------
    private static void tickAsthma(ServerPlayer player, PlayerPerks data) {
        if (!data.hasPerk(Perk.ASTHMATIC) || bodyMod()) return;
        if (!player.isSprinting()) {
            data.setSprintSeconds(data.getSprintSeconds() - 2);
            return;
        }
        data.setSprintSeconds(data.getSprintSeconds() + 1);
        if (data.getSprintSeconds() < Config.get(Config.ASTHMA_SPRINT_SECONDS)) return;
        data.setSprintSeconds(0);
        player.setSprinting(false);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Config.get(Config.ASTHMA_SLOW_SECONDS) * 20, 0, false, false, true));
        player.displayClientMessage(Component.translatable("message.rpperks.asthma"), true);
    }

    // ---------- Неуклюжий бегун: на бегу иногда спотыкается ----------
    private static void tickClumsyRunner(ServerPlayer player, PlayerPerks data) {
        if (!data.hasPerk(Perk.CLUMSY_RUNNER) || !player.isSprinting() || !player.onGround() || data.getRunnerCooldown() > 0) return;
        if (player.getRandom().nextDouble() >= Config.get(Config.CLUMSY_RUNNER_CHANCE_PERCENT) / 100.0) return;
        data.setRunnerCooldown(Config.get(Config.CLUMSY_RUNNER_COOLDOWN_MINUTES) * 1200);
        player.setSprinting(false);
        // С модом тела иногда — настоящий вывих (вправляет медик); иначе — ушиб и короткое замедление.
        var body = faygolover.rpcore.api.RpCoreAPI.body();
        boolean sprained = body != null && body.handles(faygolover.rpcore.api.RpIds.BODY_SPRAIN)
                && player.getRandom().nextDouble() < 0.25 && body.sprain(player);
        if (!sprained) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
            if (!bodyMod()) player.hurt(player.damageSources().generic(), 1.0f);
        }
        player.displayClientMessage(Component.translatable(sprained ? "message.rpperks.clumsy_runner.sprain" : "message.rpperks.clumsy_runner.trip"), true);
    }

    // ---------- Сухопутный: не всплывает ----------
    private static void tickLandlubber(Player player) {
        if (!player.isInWater() || player.isSpectator() || player.getAbilities().flying
                || player.onClimbable() || player.isPassenger()) {
            return;
        }
        player.setSwimming(false);
        player.setSprinting(false);
        Vec3 motion = player.getDeltaMovement();
        // На дне — без прыжков, в толще воды — тонет
        double maxUp = player.onGround() ? 0.0 : -Config.get(Config.LANDLUBBER_SINK_SPEED);
        if (motion.y > maxUp) player.setDeltaMovement(motion.x, maxUp, motion.z);
    }

    // =====================================================================
    // Еда и предметы
    // =====================================================================

    /** Анорексичка: срабатывает на обеих сторонах, чтобы клиент не начинал анимацию еды. */
    @SubscribeEvent
    public static void onRightClickFood(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.isEdible()) return;
        Player player = event.getEntity();
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (!data.hasPerk(Perk.ANOREXIC)) return;

            int food = player.getFoodData().getFoodLevel();
            FoodProperties properties = stack.getFoodProperties(player);
            boolean alwaysEdible = properties != null && properties.canAlwaysEat();
            // С RP Culinary «наелся» решает его желудок (у анорексички он меньше); здесь — только внезапные отказы.
            boolean full = !alwaysEdible && !culinary() && food >= Config.get(Config.ANOREXIA_MAX_FOOD);
            boolean timerRunning = data.getAnorexiaTimer() > 0;
            // При нулевом голоде новый отказ не возникает, но уже идущий таймер не прерывается
            boolean newRefusal = !timerRunning && data.isRefuseNextMeal() && food > 0;
            if (!full && !timerRunning && !newRefusal) return;

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);

            if (!(player instanceof ServerPlayer serverPlayer)) return;
            if (newRefusal) {
                data.setRefuseNextMeal(false);
                data.setAnorexiaTimer(Config.get(Config.ANOREXIA_REFUSE_SECONDS) * 20);
                PerkEventHandler.syncToClient(serverPlayer, data);
            }
            String key = (timerRunning || newRefusal) ? "message.rpperks.anorexic.refuse" : "message.rpperks.anorexic.full";
            serverPlayer.displayClientMessage(Component.translatable(key), true);
        });
    }

    /**
     * Кофеин снимает ломку — с любой доли (глоток кофе тоже). Кофеин — по общей базе RP Core (и списку caffeine_items,
     * если тело ведёт не RP Medicine: Medicine засчитывает только базу Core, поэтому при ней сообщение — только когда
     * вещество правда засчитано).
     */
    @SubscribeEvent
    public static void onConsume(faygolover.rpcore.api.event.RpConsumeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.fraction() <= 0) return;
        ItemStack item = event.stack();
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
            faygolover.rpcore.api.Consumables.Entry core = faygolover.rpcore.api.Consumables.forItem(item);
            boolean bodyCaffeine = faygolover.rpcore.api.RpCoreAPI.bodyHandles(faygolover.rpcore.api.RpIds.BODY_CAFFEINE);
            boolean caffeine = core != null && core.caffeine() > 0 || !bodyCaffeine && id != null && Config.isCaffeineItem(id);
            if (caffeine && data.hasPerk(Perk.CAFFEINE_ADDICTION)) {
                data.setCaffeineTimer(0);
                player.displayClientMessage(Component.translatable("message.rpperks.caffeine.consumed"), true);
                PerkEventHandler.syncToClient(player, data);
            }
        });
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack item = event.getItem();
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            boolean changed = false;

            // Кофеин — в onConsume (доля: укус, глоток).

            // Без RP Culinary: желудок и зубы — простые версии на ванильной еде.
            if (item.isEdible() && !culinary()) foodFallbacks(player, data, item);

            // Анорексичка: заранее решаем, откажется ли от следующего приёма пищи
            if (item.isEdible() && data.hasPerk(Perk.ANOREXIC)) {
                double chance = Config.get(Config.ANOREXIA_REFUSE_CHANCE_PERCENT) / 100.0;
                data.setRefuseNextMeal(player.getRandom().nextDouble() < chance);
                changed = true;
            }

            if (changed) PerkEventHandler.syncToClient(player, data);
        });
    }

    /** Без RP Culinary: Железный желудок, Слабый желудок, Плохие зубы. */
    private static void foodFallbacks(ServerPlayer player, PlayerPerks data, ItemStack item) {
        FoodProperties food = item.getFoodProperties(player);
        if (data.hasPerk(Perk.IRON_STOMACH) && food != null
                && player.getRandom().nextDouble() >= Config.get(Config.IRON_STOMACH_POISON_MULT)) {
            for (var pair : food.getEffects()) {
                var effect = pair.getFirst().getEffect();
                if (effect == MobEffects.HUNGER || effect == MobEffects.POISON || effect == MobEffects.CONFUSION)
                    player.removeEffect(effect);
            }
        }
        if (data.hasPerk(Perk.WEAK_STOMACH) && player.getRandom().nextDouble() < Config.get(Config.WEAK_STOMACH_CHANCE_PERCENT) / 100.0) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0));
            player.displayClientMessage(Component.translatable("message.rpperks.weak_stomach"), true);
        }
        if (data.hasPerk(Perk.BAD_TEETH) && player.getRandom().nextDouble() < Config.get(Config.BAD_TEETH_CHANCE_PERCENT) / 100.0) {
            player.hurt(player.damageSources().generic(), 1.0f);
            player.displayClientMessage(Component.translatable("message.rpperks.bad_teeth"), true);
        }
    }

    // =====================================================================
    // Здоровье без RP Medicine: Живучий и Нежный, Крепкий и Хрупкий
    // =====================================================================

    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (faygolover.rpcore.api.RpCoreAPI.bodyHandles(faygolover.rpcore.api.RpIds.BODY_HEALTH)) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            double mult = 1.0;
            if (data.hasPerk(Perk.SURVIVOR)) mult *= Config.get(Config.SURVIVOR_HEAL_MULT);
            if (data.hasPerk(Perk.DELICATE)) mult *= Config.get(Config.DELICATE_HEAL_MULT);
            if (mult != 1.0) event.setAmount((float) (event.getAmount() * mult));
        });
    }

    /** После навыков нападающего (HIGH): урон по самому игроку. С модом тела урон по частям меняет он. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurtTaken(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (faygolover.rpcore.api.RpCoreAPI.bodyHandles(faygolover.rpcore.api.RpIds.BODY_HEALTH)) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            double mult = 1.0;
            if (data.hasPerk(Perk.TOUGH)) mult *= Config.get(Config.TOUGH_DAMAGE_MULT);
            if (data.hasPerk(Perk.FRAGILE)) mult *= Config.get(Config.FRAGILE_DAMAGE_MULT);
            if (mult != 1.0) event.setAmount((float) (event.getAmount() * mult));
        });
    }

    /** Кофеинщик после сна сразу в ломке. */
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (!data.hasPerk(Perk.CAFFEINE_ADDICTION)) return;
            data.setCaffeineTimer(Config.caffeineWithdrawalTicks());
            PerkEventHandler.syncToClient(player, data);
        });
    }

    /** Плохая координация. Низкий приоритет: если Анорексичка уже отменила клик, сюда не доходит. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClumsyUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (!data.hasPerk(Perk.CLUMSY)) return;
            if (player.getRandom().nextDouble() >= Config.get(Config.CLUMSY_DROP_CHANCE_PERCENT) / 100.0) return;
            player.drop(stack.copy(), true, false);
            player.setItemInHand(event.getHand(), ItemStack.EMPTY);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            player.displayClientMessage(Component.translatable("message.rpperks.clumsy.drop"), true);
        });
    }

    // =====================================================================
    // Бой
    // =====================================================================

    /**
     * Отбрасывание от ударов в ближнем бою: Сила и Слабак того, кто бьёт (у игрока в ванили нет для этого атрибута),
     * и Хрупкий у того, кого бьют (Крепкий — через устойчивость к отбрасыванию).
     */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        float strength = event.getStrength();
        DamageSource source = target.getLastDamageSource();
        if (source != null && source.getDirectEntity() instanceof ServerPlayer attacker
                && target.getLastHurtByMobTimestamp() == target.tickCount) {
            PlayerPerks data = attacker.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
            if (data != null && !data.isAdmin()) {
                // Значение из кэша, посчитанного при последнем refreshPlayer (горячий путь в массовом PvP).
                double percent = PerkEventHandler.getStatValue(data, Stat.MELEE_KNOCKBACK);
                strength *= (float) (Math.max(0.0, 1.0 + percent / 100.0) * faygolover.rpperks.skill.PhysicalEffects.meleeKnockback(data));
            }
        }
        if (target instanceof Player victim && hasPerk(victim, Perk.FRAGILE)) {
            strength *= (float) (1.0 + Config.get(Config.FRAGILE_KNOCKBACK_PERCENT) / 100.0);
        }
        if (strength != event.getStrength()) event.setStrength(Math.max(0f, strength));
    }

    // =====================================================================
    // Неудачливый
    // =====================================================================

    /**
     * «Магнит для неприятностей»: монстр иногда переключается на Неудачливого. Удачливый — наоборот: монстр иногда
     * уходит от него к другому игроку рядом, а если никого нет — теряет его из виду.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!(event.getNewTarget() instanceof ServerPlayer current)) return;
        if (hasPerk(current, Perk.LUCKY)) {
            if (mob.getRandom().nextDouble() >= Config.get(Config.LUCKY_RETARGET_CHANCE_PERCENT) / 100.0) return;
            double radius = Config.get(Config.UNLUCKY_RETARGET_RADIUS);
            ServerPlayer other = mob.level().getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(radius),
                            p -> p != current && p.isAlive() && !p.isCreative() && !p.isSpectator()
                                    && !hasPerk(p, Perk.LUCKY) && mob.hasLineOfSight(p))
                    .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            if (other != null) event.setNewTarget(other);
            else if (mob.getRandom().nextDouble() < Config.get(Config.LUCKY_LOSE_TARGET_CHANCE_PERCENT) / 100.0) event.setNewTarget(null);
            return;
        }
        if (hasPerk(current, Perk.UNLUCKY)) return;
        if (mob.getRandom().nextDouble() >= Config.get(Config.UNLUCKY_RETARGET_CHANCE_PERCENT) / 100.0) return;

        double radius = Config.get(Config.UNLUCKY_RETARGET_RADIUS);
        mob.level().getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(radius),
                        p -> p != current && p.isAlive() && !p.isCreative() && !p.isSpectator()
                                && hasPerk(p, Perk.UNLUCKY) && mob.hasLineOfSight(p))
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .ifPresent(event::setNewTarget);
    }

    /** «Закон подлости»: подвёрнутая нога при падении. Удачливый — удачно приземлился: урон меньше. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        if (event.getDistance() < Config.get(Config.UNLUCKY_FALL_MIN_HEIGHT)) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (canLuck(player, data, Config.get(Config.LUCKY_FALL_CHANCE_PERCENT))) {
                event.setDamageMultiplier(event.getDamageMultiplier() * Config.get(Config.LUCKY_FALL_DAMAGE_MULT).floatValue());
                player.displayClientMessage(Component.translatable("message.rpperks.lucky.fall"), true);
                startLuckCooldown(data);
                return;
            }
            if (!canMishap(player, data, Config.get(Config.UNLUCKY_FALL_CHANCE_PERCENT))) return;
            // С модом тела — настоящий вывих голеностопа (вправляет медик); без него — замедление.
            var body = faygolover.rpcore.api.RpCoreAPI.body();
            boolean real = body != null && body.handles(faygolover.rpcore.api.RpIds.BODY_SPRAIN) && body.sprain(player);
            if (!real) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    Config.get(Config.UNLUCKY_SPRAIN_SECONDS) * 20, 0, false, false, true));
            player.displayClientMessage(Component.translatable("message.rpperks.unlucky.sprain"), true);
            startMishapCooldown(data);
        });
    }

    /** «Закон подлости»: инструмент соскальзывает и повреждается. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        ItemStack tool = player.getMainHandItem();
        if (!tool.isDamageableItem()) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (canLuck(player, data, Config.get(Config.LUCKY_TOOL_CHANCE_PERCENT))) {
                // Прочность за этот блок спишется уже после события — возвращаем её заранее
                if (tool.getDamageValue() > 0) tool.setDamageValue(tool.getDamageValue() - 1);
                player.displayClientMessage(Component.translatable("message.rpperks.lucky.tool"), true);
                startLuckCooldown(data);
                return;
            }
            if (!canMishap(player, data, Config.get(Config.UNLUCKY_TOOL_CHANCE_PERCENT))) return;
            int damage = Math.max(1, (int) Math.round(tool.getMaxDamage() * Config.get(Config.UNLUCKY_TOOL_DAMAGE_PERCENT) / 100.0));
            tool.hurtAndBreak(damage, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            player.displayClientMessage(Component.translatable("message.rpperks.unlucky.tool"), true);
            startMishapCooldown(data);
        });
    }

    private static boolean canMishap(ServerPlayer player, PlayerPerks data, double chancePercent) {
        return data.hasPerk(Perk.UNLUCKY)
                && data.getUnluckyCooldown() <= 0
                && player.getRandom().nextDouble() < chancePercent / 100.0;
    }

    private static void startMishapCooldown(PlayerPerks data) {
        data.setUnluckyCooldown(Config.get(Config.UNLUCKY_MISHAP_COOLDOWN_MINUTES) * 1200);
    }

    /** Удачливый: тот же таймер, что у Неудачливого (перки несовместимы, поле одно на обоих). */
    private static boolean canLuck(ServerPlayer player, PlayerPerks data, double chancePercent) {
        return data.hasPerk(Perk.LUCKY)
                && data.getUnluckyCooldown() <= 0
                && player.getRandom().nextDouble() < chancePercent / 100.0;
    }

    private static void startLuckCooldown(PlayerPerks data) {
        data.setUnluckyCooldown(Config.get(Config.LUCKY_COOLDOWN_MINUTES) * 1200);
    }

    // =====================================================================

    private static boolean hasPerk(Player player, Perk perk) {
        return player.getCapability(PlayerPerksProvider.PLAYER_PERKS).map(data -> data.hasPerk(perk)).orElse(false);
    }
}

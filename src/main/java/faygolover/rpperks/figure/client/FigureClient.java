package faygolover.rpperks.figure.client;

import faygolover.rpperks.Rpperks;
import faygolover.rpperks.figure.FigureEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Фигура на клиенте: размеры по UUID (присылает сервер), покачивание видимых игроков, временный размер для превью
 * в окне анкеты. Слой груди вешается на оба скина игрока и на тело офлайн-игрока RP Medicine.
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID, value = Dist.CLIENT)
public final class FigureClient {
    private FigureClient() {}

    public record Info(float size, boolean chosen) {}

    private static final Map<UUID, Float> SIZES = new HashMap<>();
    private static final Map<UUID, Info> INFO = new HashMap<>();
    private static final Map<UUID, FigurePhysics> PHYSICS = new HashMap<>();
    /** Пустая броневая модель — образец для вопроса «есть ли у брони своя модель». */
    @Nullable static HumanoidModel<LivingEntity> plainArmorModel;

    @Nullable private static UUID previewId;
    private static float previewSize;

    public static void onSync(UUID id, float size) {
        SIZES.put(id, size);
    }

    public static void onInfo(UUID id, float size, boolean chosen) {
        INFO.put(id, new Info(size, chosen));
    }

    /** Размер без учёта пола и выбран ли он — для своего окна анкеты и для ГМа; null — ещё не пришло. */
    @Nullable
    public static Info info(UUID id) {
        return INFO.get(id);
    }

    /** Размер, с которым рисовать (0 — без груди). */
    public static float size(UUID id) {
        if (id.equals(previewId)) return previewSize;
        return SIZES.getOrDefault(id, 0f);
    }

    /** На время отрисовки превью: показать у {@code id} размер {@code size}; {@code id == null} — снять. */
    public static void preview(@Nullable UUID id, float size) {
        previewId = id;
        previewSize = size;
    }

    @Nullable
    static FigurePhysics physics(UUID id) {
        return FigureClientConfig.PHYSICS.get() ? PHYSICS.get(id) : null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        if (!FigureClientConfig.PHYSICS.get() || plainArmorModel == null) {
            PHYSICS.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            float size = size(p.getUUID());
            if (size <= 0f) continue;
            var chest = p.getItemBySlot(EquipmentSlot.CHEST);
            FigureArmor.Cover cover = FigureArmor.classify(chest, p, plainArmorModel);
            if (cover == FigureArmor.Cover.HIDE) continue;
            float resistance = cover == FigureArmor.Cover.ARMOR ? FigureArmor.resistance(chest) : 0f;
            PHYSICS.computeIfAbsent(p.getUUID(), k -> new FigurePhysics()).tick(p, size, resistance);
        }
        if (mc.level.getGameTime() % 100 == 0) {
            for (Iterator<UUID> it = PHYSICS.keySet().iterator(); it.hasNext(); ) {
                if (mc.level.getPlayerByUUID(it.next()) == null) it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        SIZES.clear();
        INFO.clear();
        PHYSICS.clear();
        previewId = null;
    }

    @Mod.EventBusSubscriber(modid = Rpperks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void onAddLayers(EntityRenderersEvent.AddLayers e) {
            plainArmorModel = new HumanoidModel<>(e.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
            var trims = Minecraft.getInstance().getModelManager();
            for (String skin : e.getSkins()) {
                EntityRenderer<? extends Player> r = e.getSkin(skin);
                if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof PlayerModel) {
                    lr.addLayer(new FigureLayer<AbstractClientPlayer>(lr, trims, AbstractClientPlayer::getUUID, true));
                }
            }
            EntityType<?> body = ForgeRegistries.ENTITY_TYPES.getValue(FigureEvents.BODY_STUB);
            if (body != null && FigureEvents.BODY_STUB.equals(ForgeRegistries.ENTITY_TYPES.getKey(body))) {
                EntityRenderer<?> r = e.getRenderer((EntityType) body);
                if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof PlayerModel) {
                    lr.addLayer(new FigureLayer<LivingEntity>(lr, trims, FigureEvents::bodyOwner, false));
                }
            }
        }

        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> FigureArmor.clear());
        }
    }
}

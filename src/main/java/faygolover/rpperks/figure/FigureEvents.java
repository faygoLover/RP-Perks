package faygolover.rpperks.figure;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.event.BodyProfileChangedEvent;
import faygolover.rpperks.Rpperks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Фигура на сервере: кому и когда слать размер. Себе — при входе и смене измерения; тем, кто начал видеть игрока
 * или тело офлайн-игрока (RP Medicine), — при начале слежения; всем рядом — когда размер или пол поменялись.
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID)
public final class FigureEvents {
    private FigureEvents() {}

    /** Тело офлайн-игрока RP Medicine: модель игрока, владелец — {@code ownerId()}. Medicine для компиляции не нужен. */
    public static final ResourceLocation BODY_STUB = ResourceLocation.fromNamespaceAndPath("rpmedicine", "body_stub");
    @Nullable private static Method ownerMethod;
    private static boolean ownerLookupFailed;

    @Mod.EventBusSubscriber(modid = Rpperks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent e) {
            e.enqueueWork(FigureNetwork::register);
        }
    }

    /** Владелец тела RP Medicine или null, если сущность не тело. Общий для клиента и сервера. */
    @Nullable
    public static UUID bodyOwner(Entity e) {
        if (ownerLookupFailed || !BODY_STUB.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()))) return null;
        try {
            if (ownerMethod == null) ownerMethod = e.getClass().getMethod("ownerId");
            return ownerMethod.invoke(e) instanceof UUID id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            ownerLookupFailed = true;
            Rpperks.LOGGER.warn("RP Perks: тело RP Medicine без ownerId() — фигура на телах выключена", ex);
            return null;
        }
    }

    private static FigureStore store(ServerPlayer p) {
        return FigureStore.get(p.server);
    }

    /** Свежий пол из анкеты RP Core → в хранилище; true — изменился. */
    private static boolean refreshSex(ServerPlayer p) {
        return store(p).setSex(p.getUUID(), RpCoreAPI.profile(p).sex());
    }

    private static FigureNetwork.Sync syncOf(ServerPlayer p) {
        return new FigureNetwork.Sync(p.getUUID(), store(p).shown(p.getUUID()));
    }

    private static FigureNetwork.Info infoOf(ServerPlayer p) {
        FigureStore s = store(p);
        return new FigureNetwork.Info(p.getUUID(), s.size(p.getUUID()), s.chosen(p.getUUID()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        refreshSex(sp);
        FigureNetwork.send(sp, syncOf(sp));
        FigureNetwork.send(sp, infoOf(sp));
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) FigureNetwork.send(sp, syncOf(sp));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer tracker)) return;
        Entity target = e.getTarget();
        if (target instanceof ServerPlayer other) {
            FigureNetwork.send(tracker, syncOf(other));
            return;
        }
        UUID owner = bodyOwner(target);
        if (owner != null) FigureNetwork.send(tracker, new FigureNetwork.Sync(owner, store(tracker).shown(owner)));
    }

    @SubscribeEvent
    public static void onProfileChanged(BodyProfileChangedEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && refreshSex(sp)) FigureNetwork.sendAround(sp, syncOf(sp));
    }

    /** Пришёл выбор размера. ГМ (права 2) — любому в сети и себе; игрок — только себе и пока размер не выбран. */
    static void onSet(ServerPlayer sender, UUID targetId, float size) {
        boolean self = sender.getUUID().equals(targetId);
        ServerPlayer target = self ? sender : sender.server.getPlayerList().getPlayer(targetId);
        if (target == null) return;
        boolean gm = sender.hasPermissions(2);
        if (!gm && (!self || store(sender).chosen(targetId))) return;
        store(target).setSize(targetId, size, true);
        refreshSex(target);
        FigureNetwork.sendAround(target, syncOf(target));
        FigureNetwork.send(target, infoOf(target));
        if (!self) FigureNetwork.send(sender, infoOf(target));
    }

    /** ГМ открыл анкету игрока: прислать сырой размер (без учёта пола) и выбран ли он. */
    static void onAsk(ServerPlayer sender, UUID targetId) {
        if (!sender.hasPermissions(2) && !sender.getUUID().equals(targetId)) return;
        ServerPlayer target = sender.server.getPlayerList().getPlayer(targetId);
        if (target != null) FigureNetwork.send(sender, infoOf(target));
    }
}

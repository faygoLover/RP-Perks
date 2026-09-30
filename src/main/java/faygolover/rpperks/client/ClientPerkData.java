package faygolover.rpperks.client;

import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Клиентские данные, которых нет в capability игрока: чьё окно перков сейчас редактирует админ. */
@Mod.EventBusSubscriber(modid = Rpperks.MODID, value = Dist.CLIENT)
public final class ClientPerkData {
    private ClientPerkData() {}

    /** Снимок данных игрока, открытого в админском окне. */
    @Nullable public static volatile PlayerPerks adminTarget = null;
    @Nullable public static volatile UUID adminTargetId = null;

    /** Сброс при выходе с сервера, чтобы данные не «переехали» на другой сервер или в одиночную игру. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        adminTarget = null;
        adminTargetId = null;
    }
}

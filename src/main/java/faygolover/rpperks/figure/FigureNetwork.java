package faygolover.rpperks.figure;

import faygolover.rpperks.Rpperks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Свой маленький канал фигуры ({@code rpperks:figure}), чтобы не трогать протокол основного канала перков.
 * {@link Sync}: сервер → клиенты, «у этого UUID такой размер». {@link Set}: клиент → сервер, выбор размера
 * (свой — один раз, чужой — ГМ).
 */
public final class FigureNetwork {
    private FigureNetwork() {}

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Rpperks.MODID, "figure"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode)
                .decoder(Sync::decode)
                .consumerMainThread(Sync::handle)
                .add();
        CHANNEL.messageBuilder(Set.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Set::encode)
                .decoder(Set::decode)
                .consumerMainThread(Set::handle)
                .add();
        CHANNEL.messageBuilder(Ask.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Ask::encode)
                .decoder(Ask::decode)
                .consumerMainThread(Ask::handle)
                .add();
        CHANNEL.messageBuilder(Info.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Info::encode)
                .decoder(Info::decode)
                .consumerMainThread(Info::handle)
                .add();
    }

    public static void send(ServerPlayer to, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), packet);
    }

    /** Игроку и всем, кто его видит. */
    public static void sendAround(ServerPlayer about, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> about), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** Размер фигуры игрока {@code id} (0 — без груди). */
    public record Sync(UUID id, float size) {
        static void encode(Sync msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.id);
            buf.writeFloat(msg.size);
        }

        static Sync decode(FriendlyByteBuf buf) {
            return new Sync(buf.readUUID(), buf.readFloat());
        }

        static void handle(Sync msg, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    faygolover.rpperks.figure.client.FigureClient.onSync(msg.id, msg.size));
        }
    }

    /** Выбор размера для {@code target}: свой (один раз) или чужой (ГМ, права 2). */
    public record Set(UUID target, float size) {
        static void encode(Set msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.target);
            buf.writeFloat(msg.size);
        }

        static Set decode(FriendlyByteBuf buf) {
            return new Set(buf.readUUID(), buf.readFloat());
        }

        static void handle(Set msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && Float.isFinite(msg.size)) FigureEvents.onSet(sender, msg.target, msg.size);
        }
    }

    /** ГМ открыл анкету игрока {@code target}: прислать {@link Info}. */
    public record Ask(UUID target) {
        static void encode(Ask msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.target);
        }

        static Ask decode(FriendlyByteBuf buf) {
            return new Ask(buf.readUUID());
        }

        static void handle(Ask msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) FigureEvents.onAsk(sender, msg.target);
        }
    }

    /** Для окна анкеты: размер без учёта пола и выбран ли он (своему игроку и ГМу). */
    public record Info(UUID id, float size, boolean chosen) {
        static void encode(Info msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.id);
            buf.writeFloat(msg.size);
            buf.writeBoolean(msg.chosen);
        }

        static Info decode(FriendlyByteBuf buf) {
            return new Info(buf.readUUID(), buf.readFloat(), buf.readBoolean());
        }

        static void handle(Info msg, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    faygolover.rpperks.figure.client.FigureClient.onInfo(msg.id, msg.size, msg.chosen));
        }
    }
}

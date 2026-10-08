package faygolover.rpperks.network;

import faygolover.rpperks.Rpperks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private NetworkHandler() {}

    private static final String PROTOCOL_VERSION = "7";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Rpperks.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncPerksPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPerksPacket::encode)
                .decoder(SyncPerksPacket::decode)
                .consumerMainThread(SyncPerksPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenPerkScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPerkScreenPacket::encode)
                .decoder(OpenPerkScreenPacket::decode)
                .consumerMainThread(OpenPerkScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncSkillsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSkillsPacket::encode)
                .decoder(SyncSkillsPacket::decode)
                .consumerMainThread(SyncSkillsPacket::handle)
                .add();
        CHANNEL.messageBuilder(PerkActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PerkActionPacket::encode)
                .decoder(PerkActionPacket::decode)
                .consumerMainThread(PerkActionPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}

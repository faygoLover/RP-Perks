package faygolover.rpperks.network;

import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Открывает окно перков (open = true) или обновляет данные уже открытого админского окна (open = false).
 * В режиме игрока окно берёт данные из его собственной capability, снимок нужен только админскому окну.
 */
public class OpenPerkScreenPacket {
    public final boolean open;
    public final boolean adminMode;
    public final UUID targetId;
    public final String targetName;
    public final boolean canSeeHidden;
    public final SyncPerksPacket snapshot;

    public OpenPerkScreenPacket(boolean open, boolean adminMode, UUID targetId, String targetName,
                                boolean canSeeHidden, SyncPerksPacket snapshot) {
        this.open = open;
        this.adminMode = adminMode;
        this.targetId = targetId;
        this.targetName = targetName;
        this.canSeeHidden = canSeeHidden;
        this.snapshot = snapshot;
    }

    public static OpenPerkScreenPacket of(boolean open, boolean adminMode, UUID targetId, String targetName,
                                          boolean canSeeHidden, PlayerPerks data) {
        return new OpenPerkScreenPacket(open, adminMode, targetId, targetName, canSeeHidden, SyncPerksPacket.of(data));
    }

    public static void encode(OpenPerkScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeBoolean(msg.adminMode);
        buf.writeUUID(msg.targetId);
        buf.writeUtf(msg.targetName);
        buf.writeBoolean(msg.canSeeHidden);
        SyncPerksPacket.encode(msg.snapshot, buf);
    }

    public static OpenPerkScreenPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        boolean admin = buf.readBoolean();
        UUID target = buf.readUUID();
        String name = buf.readUtf();
        boolean canSeeHidden = buf.readBoolean();
        SyncPerksPacket snapshot = SyncPerksPacket.decode(buf);
        return new OpenPerkScreenPacket(open, admin, target, name, canSeeHidden, snapshot);
    }

    public static void handle(OpenPerkScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleScreen(msg));
    }
}

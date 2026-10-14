package faygolover.rpperks.network;

import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.client.ClientPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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
    /** Навыки игрока для вкладки «Навыки» админского окна. */
    public final SyncSkillsPacket skills;
    /** Анкета тела игрока (RP Core) и его текущий вес, кг (NaN — веса не ведёт ни один мод). */
    public final CompoundTag profile;
    public final double weightKg;

    public OpenPerkScreenPacket(boolean open, boolean adminMode, UUID targetId, String targetName,
                                boolean canSeeHidden, SyncPerksPacket snapshot, SyncSkillsPacket skills,
                                CompoundTag profile, double weightKg) {
        this.open = open;
        this.adminMode = adminMode;
        this.targetId = targetId;
        this.targetName = targetName;
        this.canSeeHidden = canSeeHidden;
        this.snapshot = snapshot;
        this.skills = skills;
        this.profile = profile;
        this.weightKg = weightKg;
    }

    public static OpenPerkScreenPacket of(boolean open, boolean adminMode, ServerPlayer target,
                                          boolean canSeeHidden, PlayerPerks data) {
        return new OpenPerkScreenPacket(open, adminMode, target.getUUID(), target.getScoreboardName(), canSeeHidden,
                SyncPerksPacket.of(data), SyncSkillsPacket.of(data),
                faygolover.rpcore.api.RpCoreAPI.profile(target).save(), faygolover.rpcore.api.RpCoreAPI.weightKg(target));
    }

    public static void encode(OpenPerkScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeBoolean(msg.adminMode);
        buf.writeUUID(msg.targetId);
        buf.writeUtf(msg.targetName);
        buf.writeBoolean(msg.canSeeHidden);
        SyncPerksPacket.encode(msg.snapshot, buf);
        SyncSkillsPacket.encode(msg.skills, buf);
        buf.writeNbt(msg.profile);
        buf.writeDouble(msg.weightKg);
    }

    public static OpenPerkScreenPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        boolean admin = buf.readBoolean();
        UUID target = buf.readUUID();
        String name = buf.readUtf();
        boolean canSeeHidden = buf.readBoolean();
        SyncPerksPacket snapshot = SyncPerksPacket.decode(buf);
        SyncSkillsPacket skills = SyncSkillsPacket.decode(buf);
        CompoundTag profile = buf.readNbt();
        double weight = buf.readDouble();
        return new OpenPerkScreenPacket(open, admin, target, name, canSeeHidden, snapshot, skills,
                profile == null ? new CompoundTag() : profile, weight);
    }

    public static void handle(OpenPerkScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleScreen(msg));
    }
}

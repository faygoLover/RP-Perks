package faygolover.rpperks.network;

import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.client.ClientPacketHandler;
import faygolover.rpperks.perk.Perk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Снимок данных перков игрока.
 * Сам по себе — синхронизация собственных данных игрока в его клиентскую capability;
 * внутри {@link OpenPerkScreenPacket} — данные игрока для админского окна.
 */
public class SyncPerksPacket {
    public final int points;
    public final Map<Perk, Integer> perks;
    public final int refuseTokens;
    public final int resetTokens;
    public final List<Perk> blocked;
    public final boolean selectionLocked;
    public final int caffeineTimer;
    public final int anorexiaTimer;
    public final boolean refuseNextMeal;

    public SyncPerksPacket(int points, Map<Perk, Integer> perks, int refuseTokens, int resetTokens,
                           List<Perk> blocked, boolean selectionLocked,
                           int caffeineTimer, int anorexiaTimer, boolean refuseNextMeal) {
        this.points = points;
        this.perks = perks;
        this.refuseTokens = refuseTokens;
        this.resetTokens = resetTokens;
        this.blocked = blocked;
        this.selectionLocked = selectionLocked;
        this.caffeineTimer = caffeineTimer;
        this.anorexiaTimer = anorexiaTimer;
        this.refuseNextMeal = refuseNextMeal;
    }

    public static SyncPerksPacket of(PlayerPerks data) {
        Map<Perk, Integer> perks = new EnumMap<>(Perk.class);
        perks.putAll(data.getPerkDeltas());
        return new SyncPerksPacket(data.getPoints(), perks, data.getRefuseTokens(), data.getResetTokens(),
                new ArrayList<>(data.getBlockedPerks()), data.isSelectionLocked(),
                data.getCaffeineTimer(), data.getAnorexiaTimer(), data.isRefuseNextMeal());
    }

    /** Записывает снимок в объект данных (клиентская capability или снимок для админского окна). */
    public void applyTo(PlayerPerks data) {
        data.applyClientSync(points, perks, refuseTokens, resetTokens, blocked, selectionLocked,
                caffeineTimer, anorexiaTimer, refuseNextMeal);
    }

    public static void encode(SyncPerksPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.points);
        writePerkMap(buf, msg.perks);
        buf.writeVarInt(msg.refuseTokens);
        buf.writeVarInt(msg.resetTokens);
        writePerks(buf, msg.blocked);
        buf.writeBoolean(msg.selectionLocked);
        buf.writeVarInt(msg.caffeineTimer);
        buf.writeVarInt(msg.anorexiaTimer);
        buf.writeBoolean(msg.refuseNextMeal);
    }

    public static SyncPerksPacket decode(FriendlyByteBuf buf) {
        int points = buf.readInt();
        Map<Perk, Integer> perks = readPerkMap(buf);
        int refuseTokens = buf.readVarInt();
        int resetTokens = buf.readVarInt();
        List<Perk> blocked = readPerks(buf);
        boolean locked = buf.readBoolean();
        int caffeine = buf.readVarInt();
        int anorexia = buf.readVarInt();
        boolean refuse = buf.readBoolean();
        return new SyncPerksPacket(points, perks, refuseTokens, resetTokens, blocked, locked, caffeine, anorexia, refuse);
    }

    public static void handle(SyncPerksPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
    }

    // ---------- Помощники ----------

    static void writePerks(FriendlyByteBuf buf, List<Perk> perks) {
        buf.writeVarInt(perks.size());
        for (Perk perk : perks) buf.writeEnum(perk);
    }

    static List<Perk> readPerks(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Perk> perks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) perks.add(buf.readEnum(Perk.class));
        return perks;
    }

    static void writePerkMap(FriendlyByteBuf buf, Map<Perk, Integer> perks) {
        buf.writeVarInt(perks.size());
        perks.forEach((perk, delta) -> {
            buf.writeEnum(perk);
            buf.writeInt(delta);
        });
    }

    static Map<Perk, Integer> readPerkMap(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Perk, Integer> perks = new EnumMap<>(Perk.class);
        for (int i = 0; i < size; i++) {
            Perk perk = buf.readEnum(Perk.class);
            perks.put(perk, buf.readInt());
        }
        return perks;
    }
}

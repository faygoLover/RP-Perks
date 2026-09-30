package faygolover.rpperks.network;

import faygolover.rpperks.events.PerkActions;
import faygolover.rpperks.perk.Perk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Нажатие кнопки в окне перков (клиент -> сервер). Все права и правила проверяет сервер. */
public class PerkActionPacket {
    public enum Action {
        // Игрок, только для себя
        TAKE,
        REFUSE,
        RESET,
        // Администратор (уровень прав 2+), для любого игрока
        ADMIN_GRANT,
        ADMIN_REVOKE,
        ADMIN_POINTS,
        ADMIN_REFUSE_TOKENS,
        ADMIN_RESET_TOKENS,
        ADMIN_TOGGLE_BLOCK,
        ADMIN_TOGGLE_SELECTION,
        ADMIN_WIPE;

        public boolean isAdmin() { return ordinal() >= ADMIN_GRANT.ordinal(); }
    }

    public final Action action;
    public final UUID target;
    @Nullable public final Perk perk;
    public final int amount;

    public PerkActionPacket(Action action, UUID target, @Nullable Perk perk, int amount) {
        this.action = action;
        this.target = target;
        this.perk = perk;
        this.amount = amount;
    }

    public static void encode(PerkActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUUID(msg.target);
        buf.writeBoolean(msg.perk != null);
        if (msg.perk != null) buf.writeEnum(msg.perk);
        buf.writeInt(msg.amount);
    }

    public static PerkActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID target = buf.readUUID();
        Perk perk = buf.readBoolean() ? buf.readEnum(Perk.class) : null;
        int amount = buf.readInt();
        return new PerkActionPacket(action, target, perk, amount);
    }

    public static void handle(PerkActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) PerkActions.handle(sender, msg);
    }
}

package faygolover.rpperks.client;

import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.client.screen.PerkScreen;
import faygolover.rpperks.network.OpenPerkScreenPacket;
import faygolover.rpperks.network.SyncPerksPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Обработка пакетов на клиенте. Вызывается только через DistExecutor, на сервер не грузится. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleSync(SyncPerksPacket msg) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(msg::applyTo);
    }

    public static void handleSkills(faygolover.rpperks.network.SyncSkillsPacket msg) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(d -> d.setClientSkills(msg.skills()));
    }

    public static void handleScreen(OpenPerkScreenPacket msg) {
        if (msg.open || msg.targetId.equals(ClientPerkData.adminTargetId)) {
            ClientPerkData.targetProfile = faygolover.rpcore.api.BodyProfile.load(msg.profile);
            ClientPerkData.targetWeightKg = msg.weightKg;
        }
        if (msg.adminMode) {
            // Обновление для другого игрока, чем открыт сейчас, не нужно (админ уже переключился)
            if (!msg.open && !msg.targetId.equals(ClientPerkData.adminTargetId)) return;
            PlayerPerks snapshot = new PlayerPerks();
            msg.snapshot.applyTo(snapshot);
            snapshot.setClientSkills(msg.skills.skills());
            ClientPerkData.adminTarget = snapshot;
            ClientPerkData.adminTargetId = msg.targetId;
        }
        if (msg.open) {
            Minecraft.getInstance().setScreen(new PerkScreen(msg.adminMode, msg.targetId, msg.targetName, msg.canSeeHidden));
        }
    }
}

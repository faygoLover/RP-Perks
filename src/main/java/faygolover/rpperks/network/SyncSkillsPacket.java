package faygolover.rpperks.network;

import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.skill.SkillDef;
import faygolover.rpperks.skill.SkillService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Навыки игрока для вкладки «Навыки» и чтения уровня на клиенте: ключ навыка → {итоговый уровень,
 * доля опыта до следующего, источник (0 — наработан, 1 — перк, 2 — ГМ), уровень от перков, скорость роста (талант
 * и особенности)}.
 */
public record SyncSkillsPacket(Map<String, float[]> skills) {
    public static SyncSkillsPacket of(PlayerPerks d) {
        Map<String, float[]> m = new HashMap<>();
        for (SkillDef s : SkillDef.VALUES) {
            int lvl = SkillService.level(d, s);
            int base = SkillService.base(d, s);
            float frac = base >= 10 ? 1f : (float) Math.min(1.0, d.getSkillXp(s.key()) / SkillService.need(s, base));
            m.put(s.key(), new float[]{lvl, frac, SkillService.source(d, s).ordinal(), SkillService.perkFloor(d, s),
                    (float) SkillService.shownMultiplier(d, s)});
        }
        return new SyncSkillsPacket(m);
    }

    public static void encode(SyncSkillsPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.skills.size());
        p.skills.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeByte((int) v[0]);
            buf.writeFloat(v[1]);
            buf.writeByte((int) v[2]);
            buf.writeByte((int) v[3]);
            buf.writeFloat(v.length > 4 ? v[4] : 1f);
        });
    }

    public static SyncSkillsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, float[]> m = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            m.put(k, new float[]{buf.readByte(), buf.readFloat(), buf.readByte(), buf.readByte(), buf.readFloat()});
        }
        return new SyncSkillsPacket(m);
    }

    public static void handle(SyncSkillsPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpperks.client.ClientPacketHandler.handleSkills(p));
    }
}

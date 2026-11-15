package faygolover.rpperks.gametest;

import com.mojang.authlib.GameProfile;
import faygolover.rpperks.Rpperks;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.skill.SkillDef;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Края состояния перков и навыков: каждое поле в сохранении, смерть, возврат из Края, перезаход. Страховка перед
 * переводом данных на общий механизм RP Core (план {@code _family/refactor}, задача T0.3): тесты обязаны проходить
 * и до, и после.
 */
@GameTestHolder(Rpperks.MODID)
@PrefixGameTestTemplate(false)
public final class PerksEdgeTests {
    private PerksEdgeTests() {}

    static final String T = "platform";
    /** Ветка в ForgeCaps игрока. Менять нельзя: так лежит в сохранениях живого сервера. */
    static final String KEY = "rpperks:player_perks";
    private static int counter;

    /** Поля, которые в NBT не пишутся: снимок на 15.11.2026, разбор в docs/test_findings.md. */
    static final Set<String> NOT_SAVED = Set.of("runnerCooldown", "sprintSeconds");
    /** Поля, которые пишутся только вместе с другим полем. */
    static final Set<String> SAVED_WITH_OTHER = Set.of();
    /** Поля, которые чтение правит (после чтения и повторной записи тег другой). */
    static final Set<String> NOT_RESTORED = Set.of();

    static ServerPlayer player(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "rpk-edge-" + (counter++)));
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, p);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 pos = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(pos.x, pos.y, pos.z, 0, 0);
        p.setOnGround(true);
        return p;
    }

    static void remove(GameTestHelper h, ServerPlayer p) {
        if (!p.isRemoved()) h.getLevel().getServer().getPlayerList().remove(p);
    }

    static CompoundTag save(PlayerPerks d) {
        CompoundTag t = new CompoundTag();
        d.saveNBT(t);
        return t;
    }

    static PlayerPerks data(ServerPlayer p) {
        return p.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }

    static void mark(PlayerPerks d) {
        d.grantPerk(Perk.FIRST_AID, null);
        // Как в игре: после выдачи умения навык сразу сводится (иначе это сделает первое же обновление игрока).
        faygolover.rpperks.skill.SkillService.syncSkillPerks(d);
        String skill = SkillDef.MEDICINE.key();
        d.setTrained(skill, 4);
        d.setSkillXp(skill, 12.5);
        d.setDecayMinutes("test", 7);
    }

    /** Каждое простое поле данных перков попадает в NBT и читается обратно. */
    @GameTest(template = T, batch = "perks_edge_nbt")
    public static void nbtCoversEveryField(GameTestHelper h) {
        NbtCoverage.Result r = NbtCoverage.check(PlayerPerks::new, PerksEdgeTests::save, PlayerPerks::loadNBT, "faygolover.rpperks");
        Set<String> newLost = new TreeSet<>(r.notSaved);
        newLost.removeAll(NOT_SAVED);
        newLost.removeAll(SAVED_WITH_OTHER);
        Set<String> nowSaved = new TreeSet<>(NOT_SAVED);
        nowSaved.removeAll(r.notSaved);
        Set<String> newBroken = new TreeSet<>(r.notRestored);
        newBroken.removeAll(NOT_RESTORED);
        StringBuilder sb = new StringBuilder();
        if (!newLost.isEmpty()) sb.append(" НЕ СОХРАНЯЮТСЯ: ").append(newLost);
        if (!newBroken.isEmpty()) sb.append(" ЧИТАЮТСЯ НЕ ТАК: ").append(newBroken);
        if (!nowSaved.isEmpty()) sb.append(" теперь сохраняются, убрать из NOT_SAVED: ").append(nowSaved);
        if (!r.errors.isEmpty()) sb.append(" ОШИБКИ: ").append(r.errors);
        if (sb.length() > 0) h.fail("Проверено полей " + r.checked + "." + sb + " Не проверялись (коллекции): " + r.skipped);
        h.succeed();
    }

    /** Перки, наработанный уровень, опыт навыка и счётчики: записал → прочитал → записал, тег тот же. */
    @GameTest(template = T, batch = "perks_edge_roundtrip")
    public static void nbtRoundTrip(GameTestHelper h) {
        PlayerPerks a = new PlayerPerks();
        mark(a);
        CompoundTag t1 = save(a);
        PlayerPerks b = new PlayerPerks();
        b.loadNBT(t1);
        String skill = SkillDef.MEDICINE.key();
        if (!b.owns(Perk.FIRST_AID)) h.fail("Перк после чтения потерян");
        if (b.getTrained(skill) != 4) h.fail("Наработанный уровень после чтения: " + b.getTrained(skill));
        if (b.getSkillXp(skill) != 12.5) h.fail("Опыт навыка после чтения: " + b.getSkillXp(skill));
        if (b.getDecayMinutes("test") != 7) h.fail("Счётчик угасания после чтения: " + b.getDecayMinutes("test"));
        if (!t1.equals(save(b))) h.fail("Повторная запись даёт другой тег: было " + t1 + ", стало " + save(b));
        h.succeed();
    }

    /** Смерть и возврат из Края: перки и навыки копируются целиком. */
    private static void respawnKeeps(GameTestHelper h, boolean keepEverything) {
        ServerPlayer p = player(h);
        PlayerPerks d = data(p);
        if (d == null) { h.fail("Нет данных перков у игрока"); return; }
        mark(d);
        CompoundTag before = save(d);
        ServerPlayer n = h.getLevel().getServer().getPlayerList().respawn(p, keepEverything);
        try {
            PlayerPerks nd = data(n);
            if (nd == null) { h.fail("Нет данных перков после возрождения"); return; }
            if (!before.equals(save(nd))) h.fail("Данные перков изменились: было " + before + ", стало " + save(nd));
        } finally {
            remove(h, n);
        }
        h.succeed();
    }

    @GameTest(template = T, batch = "perks_edge_death")
    public static void deathKeepsAll(GameTestHelper h) {
        respawnKeeps(h, false);
    }

    @GameTest(template = T, batch = "perks_edge_end")
    public static void endReturnKeepsAll(GameTestHelper h) {
        respawnKeeps(h, true);
    }

    /** Перезаход: данные игрока записаны и прочитаны, ветка мода в сохранении та же. */
    @GameTest(template = T, batch = "perks_edge_relog")
    public static void relogKeeps(GameTestHelper h) {
        ServerPlayer p = player(h);
        ServerPlayer q = player(h);
        try {
            PlayerPerks d = data(p);
            if (d == null) { h.fail("Нет данных перков у игрока"); return; }
            mark(d);
            CompoundTag saved = p.saveWithoutId(new CompoundTag());
            if (!saved.getCompound("ForgeCaps").contains(KEY)) h.fail("В сохранении игрока нет ветки ForgeCaps." + KEY);
            q.load(saved);
            PlayerPerks qd = data(q);
            if (qd == null || !save(d).equals(save(qd))) h.fail("После перезахода данные перков изменились");
        } finally {
            remove(h, p);
            remove(h, q);
        }
        h.succeed();
    }
}

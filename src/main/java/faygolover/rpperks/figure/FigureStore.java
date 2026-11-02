package faygolover.rpperks.figure;

import faygolover.rpcore.api.BodyProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Фигура персонажа (сервер): размер груди 0–1 и выбран ли он. Лежит в данных мира ({@code data/rpperks_figures.dat}),
 * а не у игрока: размер нужен и для тела офлайн-игрока (RP Medicine). Там же — последний известный пол из анкеты
 * RP Core (анкета офлайн-игрока не загружена).
 */
public final class FigureStore extends SavedData {
    private static final String NAME = "rpperks_figures";
    /** Размер, пока игрок не выбрал свой (анкета заполнена до H1 или ещё не заполнена). */
    public static final float DEFAULT_SIZE = 0.5f;

    private record Entry(float size, boolean chosen, boolean female) {}

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static FigureStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FigureStore::load, FigureStore::new, NAME);
    }

    private Entry entry(UUID id) {
        return entries.getOrDefault(id, new Entry(DEFAULT_SIZE, false, false));
    }

    public boolean chosen(UUID id) {
        return entry(id).chosen;
    }

    /** Размер, который видят все: 0, если пол не женский. */
    public float shown(UUID id) {
        Entry e = entry(id);
        return e.female ? e.size : 0f;
    }

    /** Размер без учёта пола (для ползунка у ГМа). */
    public float size(UUID id) {
        return entry(id).size;
    }

    public void setSize(UUID id, float size, boolean chosen) {
        Entry e = entry(id);
        entries.put(id, new Entry(Mth.clamp(size, 0f, 1f), chosen || e.chosen, e.female));
        setDirty();
    }

    /** Запомнить пол из анкеты; true — изменился. */
    public boolean setSex(UUID id, BodyProfile.Sex sex) {
        Entry e = entry(id);
        boolean female = sex == BodyProfile.Sex.FEMALE;
        if (e.female == female && entries.containsKey(id)) return false;
        entries.put(id, new Entry(e.size, e.chosen, female));
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putFloat("size", e.size);
            t.putBoolean("chosen", e.chosen);
            t.putBoolean("female", e.female);
            list.add(t);
        });
        tag.put("players", list);
        return tag;
    }

    private static FigureStore load(CompoundTag tag) {
        FigureStore store = new FigureStore();
        for (Tag raw : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id")) continue;
            store.entries.put(t.getUUID("id"),
                    new Entry(Mth.clamp(t.getFloat("size"), 0f, 1f), t.getBoolean("chosen"), t.getBoolean("female")));
        }
        return store;
    }
}

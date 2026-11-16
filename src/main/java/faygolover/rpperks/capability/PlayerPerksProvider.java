package faygolover.rpperks.capability;

import faygolover.rpcore.api.data.PlayerStore;
import faygolover.rpperks.Rpperks;
import faygolover.rpperks.events.PerkEventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Данные {@link PlayerPerks} у игрока: capability и описание для RP Core. Хранение, копию при возрождении, вызовы на
 * входе, возрождении, смене измерения и выходе ведёт RP Core ({@link PlayerStore}); имя ветки в сохранении —
 * прежнее, {@code rpperks:player_perks}.
 * <p>
 * Версию формата данных ({@code PlayerPerks.DATA_VERSION}) и её миграцию ведёт сам {@link PlayerPerks} —
 * {@code PlayerStore.dataVersion} здесь не подключать: двух счётчиков версий в одном теге быть не должно.
 */
public final class PlayerPerksProvider {
    private PlayerPerksProvider() {}

    public static final Capability<PlayerPerks> PLAYER_PERKS = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath(Rpperks.MODID, "player_perks");
    /** Заполняется в {@link #register()}. */
    public static PlayerStore<PlayerPerks> STORE;

    /** Описать данные для RP Core. Вызывается из конструктора мода. Карточка переживает и смерть, и возврат из Края. */
    public static void register() {
        STORE = PlayerStore.of(KEY, PlayerPerks.class, PLAYER_PERKS, PlayerPerks::new)
                .save(data -> {
                    CompoundTag nbt = new CompoundTag();
                    data.saveNBT(nbt);
                    return nbt;
                })
                .load(PlayerPerks::loadNBT)
                .clone((from, to, ctx) -> to.copyFrom(from))
                .resync(PerkEventHandler::resync)
                .forget(PerkEventHandler::forget)
                .register();
    }
}

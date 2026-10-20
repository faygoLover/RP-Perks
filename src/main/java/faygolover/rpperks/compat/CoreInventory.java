package faygolover.rpperks.compat;

import faygolover.rpcore.api.InventoryModifier;
import faygolover.rpcore.api.InventoryProvider.Part;
import faygolover.rpperks.Config;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.events.PerkEventHandler;
import faygolover.rpperks.perk.Stat;
import net.minecraft.world.entity.player.Player;

/**
 * Особенности → инвентарь (RP Inventory через Core). «Слоты инвентаря» ({@link Stat#INVENTORY_SLOTS}) — сколько всего
 * ячеек (хотбар + хранилище); отличие от базы в конфиге (Организованный +3, Неорганизованный −3) — прибавка к
 * хранилищу. Режим Админа — весь хотбар и всё хранилище.
 */
public final class CoreInventory implements InventoryModifier {
    @Override
    public void apply(Player player, Changes changes) {
        player.getCapability(PlayerPerksProvider.PLAYER_PERKS).ifPresent(data -> {
            if (data.isAdmin()) {
                changes.add(Part.HOTBAR, Part.HOTBAR.size);
                changes.add(Part.STORAGE, Part.STORAGE.size);
                return;
            }
            double delta = PerkEventHandler.getStatValue(data, Stat.INVENTORY_SLOTS) - Config.getBaseline(Stat.INVENTORY_SLOTS);
            int n = (int) Math.round(delta);
            if (n != 0) changes.add(Part.STORAGE, n);
        });
    }
}

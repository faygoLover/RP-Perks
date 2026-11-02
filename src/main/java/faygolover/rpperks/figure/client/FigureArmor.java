package faygolover.rpperks.figure.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Что в слоте груди и как с ним рисовать грудь. Пусто или не броня (элитры и т. п.) — грудь из скина.
 * Броня на ванильной модели — грудь чуть меньше, поверх текстура брони. Броня со своей 3D-моделью
 * (GeckoLib и т. п., через {@link IClientItemExtensions}) — грудь не рисуем. Исключения — клиентский конфиг.
 */
public final class FigureArmor {
    private FigureArmor() {}

    public enum Cover {
        NONE, ARMOR, HIDE
    }

    /** Есть ли у брони своя модель (по предмету; вызов модов дорогой, а ответ от стека почти не зависит). */
    private static final Map<Item, Boolean> CUSTOM_MODEL = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    public static Cover classify(ItemStack stack, LivingEntity entity, HumanoidModel<?> plainArmorModel) {
        if (stack.isEmpty()) return Cover.NONE;
        if (matches(stack, FigureClientConfig.HIDE_ITEMS.get())) return Cover.HIDE;
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != EquipmentSlot.CHEST) {
            return Cover.NONE;
        }
        if (matches(stack, FigureClientConfig.SHOW_ITEMS.get())) return Cover.ARMOR;
        Boolean custom = CUSTOM_MODEL.get(stack.getItem());
        if (custom == null) {
            boolean c;
            try {
                Model m = IClientItemExtensions.of(stack).getGenericArmorModel(entity, stack, EquipmentSlot.CHEST, plainArmorModel);
                c = m != plainArmorModel;
            } catch (RuntimeException e) {
                c = true;
            }
            custom = c;
            CUSTOM_MODEL.put(stack.getItem(), custom);
        }
        return custom ? Cover.HIDE : Cover.ARMOR;
    }

    /** Насколько броня глушит покачивание, 0–1. */
    public static float resistance(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return 0f;
        if (armor.getMaterial() == ArmorMaterials.LEATHER) return 0.3f;
        if (armor.getMaterial() == ArmorMaterials.CHAIN) return 0.5f;
        return 0.9f;
    }

    private static boolean matches(ItemStack stack, List<? extends String> list) {
        if (list.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (String s : list) {
            if (s.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(s.substring(1));
                if (tag != null && stack.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))) return true;
            } else if (id != null && s.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Текстура нагрудника, как у ванильного слоя брони ({@code _layer_1}, с правкой Forge). */
    @Nullable
    public static ResourceLocation texture(LivingEntity entity, ItemStack stack, @Nullable String type) {
        if (!(stack.getItem() instanceof ArmorItem item)) return null;
        String texture = item.getMaterial().getName();
        String domain = "minecraft";
        int idx = texture.indexOf(':');
        if (idx != -1) {
            domain = texture.substring(0, idx);
            texture = texture.substring(idx + 1);
        }
        String path = String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_1%s.png", domain, texture,
                type == null ? "" : "_" + type);
        path = ForgeHooksClient.getArmorTexture(entity, stack, path, EquipmentSlot.CHEST, type);
        return TEXTURES.computeIfAbsent(path, ResourceLocation::tryParse);
    }

    /** Сбросить кэш (смена ресурсов). */
    public static void clear() {
        CUSTOM_MODEL.clear();
        TEXTURES.clear();
    }
}

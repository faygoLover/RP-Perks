package faygolover.rpperks.figure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;
import java.util.function.Function;

/**
 * Грудь на модели игрока: два наклонных бокса 4×5 пикселей перед туловищем, текстура — тот же участок скина спереди
 * (строки 21–25), сверху — слой куртки скина или нагрудник. Поза — от {@code model.body} после всех анимаций
 * (ваниль, EMF/FA+Player, Player Animator), так что грудь идёт за туловищем.
 * <p>
 * 3D Skin Layers заменяет плоскую куртку туловища объёмными кубиками ~1 пиксель толщиной; без сдвига они торчали бы
 * сквозь верх груди. Поэтому у игроков с курткой при этом моде грудь начинается на пиксель впереди тела.
 */
public class FigureLayer<T extends LivingEntity> extends RenderLayer<T, PlayerModel<T>> {
    private static final boolean SKIN_LAYERS_3D = ModList.get().isLoaded("skinlayers3d");
    /** Пиксель в блоке. */
    private static final float PX = 1f / 16f;
    /** Высота груди и её верх от плеча, пиксели (строки скина 21–25). */
    private static final float H = 5f, TOP = 1f;

    private final ModelManager models;
    private final Function<T, UUID> owner;
    /** Живой игрок: покачивание, дыхание, куртка по настройкам скина, 3D Skin Layers. Тело RP Medicine — нет. */
    private final boolean live;
    private TextureAtlas trimAtlas;

    public FigureLayer(RenderLayerParent<T, PlayerModel<T>> parent, ModelManager models, Function<T, UUID> owner, boolean live) {
        super(parent);
        this.models = models;
        this.owner = owner;
        this.live = live;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || FigureClient.plainArmorModel == null) return;
        UUID id = owner.apply(entity);
        if (id == null) return;
        float size = Mth.clamp(FigureClient.size(id), 0f, 1f);
        if (size < 0.02f) return;
        PlayerModel<T> model = getParentModel();
        if (!model.body.visible) return; // Figura-аватар и т. п. спрятали тело

        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        FigureArmor.Cover cover = FigureArmor.classify(chest, entity, FigureClient.plainArmorModel);
        if (cover == FigureArmor.Cover.HIDE) return;
        boolean armored = cover == FigureArmor.Cover.ARMOR;
        if (armored) size *= 0.85f;

        FigurePhysics phys = live ? FigureClient.physics(id) : null;
        float bounce = phys == null ? 0f : phys.bounce(partialTick);
        float sway = phys == null ? 0f : phys.rotation(partialTick);
        boolean jacket = live ? entity instanceof Player p && p.isModelPartShown(PlayerModelPart.JACKET) : model.jacket.visible;
        float push = live && SKIN_LAYERS_3D && jacket && !armored ? 1f : 0f;

        // Наклон: больше размер — сильнее выступает низ. Покачивание вниз уменьшает наклон, дыхание чуть качает.
        float tilt = 12f + 26f * size - 2.9f * bounce;
        if (live && !entity.isUnderWater()) tilt += -Mth.cos(ageInTicks * 0.09f) * 0.45f + 0.45f;
        tilt = Mth.clamp(tilt, 0f, 45f);
        float depth = H * (float) Math.tan(tilt * Mth.DEG_TO_RAD) + 0.5f;

        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
        ResourceLocation skin = getTextureLocation(entity);
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.scale(PX, PX, PX);
        for (int side = 0; side < 2; side++) {
            float x0 = side == 0 ? 0f : -4f; // +x — левая сторона персонажа
            pose.pushPose();
            if (sway != 0f) pose.mulPose(Axis.YP.rotationDegrees(sway));
            pose.translate(x0 + 2f, TOP + bounce * 0.5f, -2f - push);
            pose.mulPose(Axis.XP.rotationDegrees(-tilt));
            float u = 20f + x0 + 4f;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
            box(pose, vc, 0f, depth, u, 20f + TOP, 64f, 64f, 1f, 1f, 1f, overlay, light);
            if (jacket && !armored) box(pose, vc, push > 0f ? 0.5f : 0.25f, depth, u, 36f + TOP, 64f, 64f, 1f, 1f, 1f, overlay, light);
            if (armored) renderArmor(pose, buffers, light, entity, chest, depth, u);
            pose.popPose();
        }
        pose.popPose();
    }

    private void renderArmor(PoseStack pose, MultiBufferSource buffers, int light, T entity, ItemStack chest, float depth, float u) {
        if (!(chest.getItem() instanceof ArmorItem armor)) return;
        ResourceLocation tex = FigureArmor.texture(entity, chest, null);
        if (tex == null) return;
        float r = 1f, g = 1f, b = 1f;
        ResourceLocation overlayTex = null;
        if (armor instanceof DyeableLeatherItem dye) {
            int color = dye.getColor(chest);
            r = (color >> 16 & 255) / 255f;
            g = (color >> 8 & 255) / 255f;
            b = (color & 255) / 255f;
            overlayTex = FigureArmor.texture(entity, chest, "overlay");
        }
        float inf = 1f;
        box(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(tex)), inf, depth, u, 20f + TOP, 64f, 32f, r, g, b, OverlayTexture.NO_OVERLAY, light);
        if (overlayTex != null) {
            box(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(overlayTex)), inf, depth, u, 20f + TOP, 64f, 32f, 1f, 1f, 1f, OverlayTexture.NO_OVERLAY, light);
        }
        ArmorTrim.getTrim(entity.level().registryAccess(), chest).ifPresent(trim -> {
            if (trimAtlas == null) trimAtlas = models.getAtlas(Sheets.ARMOR_TRIMS_SHEET);
            TextureAtlasSprite sprite = trimAtlas.getSprite(trim.outerTexture(armor.getMaterial()));
            box(pose, sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet())), inf, depth, u, 20f + TOP, 64f, 32f,
                    1f, 1f, 1f, OverlayTexture.NO_OVERLAY, light);
        });
        if (chest.hasFoil()) {
            box(pose, buffers.getBuffer(RenderType.armorEntityGlint()), inf, depth, u, 20f + TOP, 64f, 32f, 1f, 1f, 1f, OverlayTexture.NO_OVERLAY, light);
        }
    }

    /**
     * Бокс груди в своих координатах: x −2…2, y 0…5 (вниз), z 0…depth (назад, в тело); вперёд — −z. Задней грани нет.
     * Текстура: перед — участок 4×5 с левого верхнего угла (u, v); низ, верх и бока тянут крайние ряды/столбцы.
     *
     * @param inf раздувание (куртка 0.25, броня 1), текстура при этом та же
     */
    private static void box(PoseStack pose, VertexConsumer vc, float inf, float depth, float u, float v, float texW, float texH,
                            float r, float g, float b, int overlay, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = -2f - inf, x1 = 2f + inf, y0 = -inf, y1 = H + inf, zf = -inf, zb = depth + inf;
        float u0 = u / texW, u1 = (u + 4f) / texW, v0 = v / texH, v1 = (v + H) / texH;
        float uL = (u + 1f) / texW, uR = (u + 3f) / texW, vT = (v + 1f) / texH, vB = (v + H - 1f) / texH;
        // перед
        quad(vc, m, n, x0, y0, zf, u0, v0, x1, y0, zf, u1, v0, x1, y1, zf, u1, v1, x0, y1, zf, u0, v1, 0, 0, -1, r, g, b, overlay, light);
        // низ: нижний ряд
        quad(vc, m, n, x0, y1, zf, u0, vB, x1, y1, zf, u1, vB, x1, y1, zb, u1, v1, x0, y1, zb, u0, v1, 0, 1, 0, r, g, b, overlay, light);
        // верх: верхний ряд (обычно внутри тела)
        quad(vc, m, n, x0, y0, zb, u0, vT, x1, y0, zb, u1, vT, x1, y0, zf, u1, v0, x0, y0, zf, u0, v0, 0, -1, 0, r, g, b, overlay, light);
        // бока: крайние столбцы
        quad(vc, m, n, x0, y0, zb, uL, v0, x0, y0, zf, u0, v0, x0, y1, zf, u0, v1, x0, y1, zb, uL, v1, -1, 0, 0, r, g, b, overlay, light);
        quad(vc, m, n, x1, y0, zf, u1, v0, x1, y0, zb, uR, v0, x1, y1, zb, uR, v1, x1, y1, zf, u1, v1, 1, 0, 0, r, g, b, overlay, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv,
                             float nx, float ny, float nz, float r, float g, float b, int overlay, int light) {
        vertex(vc, m, n, ax, ay, az, au, av, nx, ny, nz, r, g, b, overlay, light);
        vertex(vc, m, n, bx, by, bz, bu, bv, nx, ny, nz, r, g, b, overlay, light);
        vertex(vc, m, n, cx, cy, cz, cu, cv, nx, ny, nz, r, g, b, overlay, light);
        vertex(vc, m, n, dx, dy, dz, du, dv, nx, ny, nz, r, g, b, overlay, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, float r, float g, float b, int overlay, int light) {
        vc.vertex(m, x, y, z).color(r, g, b, 1f).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}

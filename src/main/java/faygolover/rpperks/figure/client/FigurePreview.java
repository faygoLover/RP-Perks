package faygolover.rpperks.figure.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Крупное превью персонажа в окне анкеты, как в инвентаре, но вращается мышью (зажать и тянуть по горизонтали,
 * по вертикали — слегка наклон). Пока рисуется, у персонажа размер груди из ползунка ({@code size}), до сохранения.
 */
public class FigurePreview extends AbstractWidget {
    private final Supplier<LivingEntity> entity;
    private final DoubleSupplier size;
    private float yaw = 20f, pitch = 0f;

    /**
     * @param entity кого рисовать (null — никого: игрок вне видимости ГМа)
     * @param size   размер 0–1 для превью; отрицательный — как пришло с сервера
     */
    public FigurePreview(int x, int y, int w, int h, Supplier<LivingEntity> entity, DoubleSupplier size) {
        super(x, y, w, h, Component.empty());
        this.entity = entity;
        this.size = size;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LivingEntity e = entity.get();
        if (e == null) {
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    Component.translatable("gui.rpperks.figure.no_preview"), getX() + width / 2, getY() + height / 2 - 4, 0xFF8A8A8A);
            return;
        }
        // Рост игрока ~1,8 блока: фигура занимает почти всю высоту окна превью.
        int scale = Math.max(10, (int) (Math.min(height * 0.47f, width * 0.8f)));
        int cx = getX() + width / 2;
        int cy = getY() + height - Math.max(6, height / 16);
        g.enableScissor(getX(), getY(), getX() + width, getY() + height);
        double s = size.getAsDouble();
        if (s >= 0) FigureClient.preview(e.getUUID(), (float) s);
        try {
            render(g, cx, cy, scale, e);
        } finally {
            FigureClient.preview(null, 0f);
            g.disableScissor();
        }
    }

    private void render(GuiGraphics g, int x, int y, int scale, LivingEntity e) {
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
        pose.mul(camera);
        float bodyRot = e.yBodyRot, yRot = e.getYRot(), xRot = e.getXRot(), headO = e.yHeadRotO, head = e.yHeadRot;
        e.yBodyRot = 180f + yaw;
        e.setYRot(180f + yaw);
        e.setXRot(0f);
        e.yHeadRot = e.getYRot();
        e.yHeadRotO = e.getYRot();
        InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, camera, e);
        e.yBodyRot = bodyRot;
        e.setYRot(yRot);
        e.setXRot(xRot);
        e.yHeadRotO = headO;
        e.yHeadRot = head;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        yaw = Mth.wrapDegrees(yaw - (float) dragX * 2f);
        pitch = Mth.clamp(pitch + (float) dragY, -25f, 25f);
    }

    @Override
    public void playDownSound(SoundManager sounds) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
    }

    @Nullable
    public static LivingEntity byId(java.util.UUID id) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getPlayerByUUID(id);
    }
}

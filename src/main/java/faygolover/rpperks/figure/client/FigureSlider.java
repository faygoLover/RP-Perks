package faygolover.rpperks.figure.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Ползунок «Грудь: N %» (0–100 %). */
public class FigureSlider extends AbstractSliderButton {
    private final Consumer<Float> onChange;

    public FigureSlider(int x, int y, int w, int h, float value, Consumer<Float> onChange) {
        super(x, y, w, h, Component.empty(), value);
        this.onChange = onChange;
        updateMessage();
    }

    public float size() {
        return (float) value;
    }

    public void setSize(float size) {
        value = Math.max(0, Math.min(1, size));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("gui.rpperks.figure.size", Math.round(value * 100)));
    }

    @Override
    protected void applyValue() {
        onChange.accept((float) value);
    }
}

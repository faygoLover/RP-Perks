package faygolover.rpperks.figure.client;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Клиентский конфиг фигуры: {@code config/rpperks-client.toml}. Касается только того, как этот клиент видит всех. */
public final class FigureClientConfig {
    private FigureClientConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PHYSICS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HIDE_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SHOW_ITEMS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("figure");
        PHYSICS = b.comment("Покачивание груди при движении (у всех, кого видите).")
                .define("physics", true);
        HIDE_ITEMS = b.comment("Предметы в слоте груди, с которыми грудь не рисуется. id предмета или #тег.")
                .defineListAllowEmpty("hide_items", List.of(), o -> o instanceof String);
        SHOW_ITEMS = b.comment("Броня со своей 3D-моделью, с которой грудь всё же рисуется (поверх — текстура брони,",
                        "если она есть). id предмета или #тег.")
                .defineListAllowEmpty("show_items", List.of(), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }
}

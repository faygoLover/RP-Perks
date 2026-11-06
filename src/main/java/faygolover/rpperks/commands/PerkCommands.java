package faygolover.rpperks.commands;

import com.mojang.brigadier.CommandDispatcher;
import faygolover.rpperks.Rpperks;
import faygolover.rpperks.events.PerkEventHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Всего две команды — всё остальное делается в окне:
 * <ul>
 *   <li>/rpperks — окно своих перков;</li>
 *   <li>/rpperks admin &lt;ник&gt; — админское окно игрока (без ника — своё).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID)
public final class PerkCommands {
    private PerkCommands() {}

    /** Уровень прав, с которого игрок считается админом. */
    public static final int ADMIN_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpperks")
                .executes(ctx -> openSelf(ctx.getSource()))
                .then(Commands.literal("audit").requires(s -> s.hasPermission(ADMIN_LEVEL))
                        .executes(ctx -> audit(ctx.getSource())))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(ADMIN_LEVEL))
                        .executes(ctx -> openAdmin(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> openAdmin(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))));
    }

    /** Команды для общего списка {@code /rpcore commands}. */
    public static void describe() {
        faygolover.rpcore.api.RpCoreAPI.registerCommand(Rpperks.MODID, "/rpperks", "commands.rpperks.help.self", 0, false);
        faygolover.rpcore.api.RpCoreAPI.registerCommand(Rpperks.MODID, "/rpperks admin [игрок]", "commands.rpperks.help.admin",
                ADMIN_LEVEL, false);
        faygolover.rpcore.api.RpCoreAPI.registerCommand(Rpperks.MODID, "/rpperks audit", "commands.rpperks.help.audit",
                ADMIN_LEVEL, false);
    }

    /** Особенности, которые игрок может взять, а механики при нынешнем наборе модов у них нет. */
    private static int audit(CommandSourceStack source) {
        java.util.List<String> empty = new java.util.ArrayList<>();
        for (faygolover.rpperks.perk.Perk p : faygolover.rpperks.perk.Perk.values()) {
            if (!p.isAvailable() || !faygolover.rpperks.perk.PerkConsumers.empty(p)) continue;
            empty.add(p.getId() + " (" + String.join(", ", faygolover.rpperks.perk.PerkConsumers.of(p)) + ")");
        }
        if (empty.isEmpty()) source.sendSuccess(() -> Component.translatable("commands.rpperks.audit.ok"), false);
        else source.sendSuccess(() -> Component.translatable("commands.rpperks.audit.empty", String.join("; ", empty)), false);
        return empty.size();
    }

    private static int openSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return playerOnly(source);
        PerkEventHandler.openSelfScreen(player);
        return 1;
    }

    private static int openAdmin(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer admin = source.getPlayer();
        if (admin == null || target == null) return playerOnly(source);
        PerkEventHandler.openAdminScreen(admin, target);
        return 1;
    }

    private static int playerOnly(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.rpperks.error.player_only"));
        return 0;
    }
}

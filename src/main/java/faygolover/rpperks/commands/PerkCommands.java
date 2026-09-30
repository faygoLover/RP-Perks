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
 *   <li>/perks — окно своих перков;</li>
 *   <li>/perksadmin &lt;ник&gt; — админское окно игрока (без ника — своё).</li>
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

        dispatcher.register(Commands.literal("perks")
                .executes(ctx -> openSelf(ctx.getSource())));

        dispatcher.register(Commands.literal("perksadmin").requires(s -> s.hasPermission(ADMIN_LEVEL))
                .executes(ctx -> openAdmin(ctx.getSource(), ctx.getSource().getPlayer()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openAdmin(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
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

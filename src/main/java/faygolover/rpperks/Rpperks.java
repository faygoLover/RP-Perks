package faygolover.rpperks;

import com.mojang.logging.LogUtils;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.network.NetworkHandler;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Rpperks.MODID)
public class Rpperks {
    public static final String MODID = "rpperks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Rpperks() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);

        // SERVER-конфиг: лежит в папке мира (serverconfig/rpperks-server.toml) и автоматически отправляется клиентам
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerPerks.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
        LOGGER.info("RP Perks loaded.");
    }
}

package com.mafuyu404.taczaddon;

import com.mafuyu404.taczaddon.bootstrap.InventoryIntegrationSetup;
import com.mafuyu404.taczaddon.bootstrap.SetupEvent;
import com.mafuyu404.taczaddon.config.ClientConfig;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.config.ConfigMigration;
import com.mafuyu404.taczaddon.config.RuleRegistry;
import com.mafuyu404.taczaddon.gunsmith.recipe.ModRecipeSerializers;
import com.mafuyu404.taczaddon.network.NetworkHandler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TACZaddon.MODID)
public class TACZaddon {
    public static final String MODID = "taczaddon";

    public TACZaddon(IEventBus bus, ModContainer modContainer) {
        InventoryIntegrationSetup.register();
        ConfigMigration.migrate(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        NetworkHandler.register(bus);
        ModRecipeSerializers.SERIALIZERS.register(bus);
        bus.addListener(ClientConfig::onConfigLoad);
        bus.addListener(ClientConfig::onConfigReload);
        bus.addListener(SetupEvent::onClientSetup);
        bus.addListener(CommonConfig::onConfigReload);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
            modContainer.registerConfig(
                    ModConfig.Type.CLIENT, ClientConfig.SPEC, "taczaddon-client.toml");
        modContainer.registerConfig(
                ModConfig.Type.COMMON, CommonConfig.SPEC, "taczaddon-common.toml");
        RuleRegistry.init();
    }
}

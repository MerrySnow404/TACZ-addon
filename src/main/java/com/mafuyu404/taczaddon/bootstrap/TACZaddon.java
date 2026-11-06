package com.mafuyu404.taczaddon.bootstrap;

import com.mafuyu404.taczaddon.ModConstants;
import com.mafuyu404.taczaddon.compat.tacz.TaczCompatibility;
import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.config.Config;
import com.mafuyu404.taczaddon.gunsmith.TaczWorkbenchAnchorProvider;
import com.mafuyu404.taczaddon.network.ServerPolicySync;
import com.mafuyu404.taczaddon.recipe.ModRecipeSerializers;
import com.mafuyu404.taczaddon.rule.RuleRegistry;
import com.mafuyu404.taczaddon.storage.WorkbenchAnchorRegistry;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ModConstants.MODID)
public class TACZaddon {

    public TACZaddon(FMLJavaModLoadingContext context) {
        TaczCompatibility.logSummary();
        RuleRegistry.bootstrap();
        RuleRegistry.bindSync(ServerPolicySync::broadcastAttachmentDetail,
                ServerPolicySync::broadcastLiberateAttachment);
        NetworkRegistration.register();
        WorkbenchAnchorRegistry.register(
                new TaczWorkbenchAnchorProvider()
        );

        IEventBus modEventBus = context.getModEventBus();

        ModRecipeSerializers.SERIALIZERS.register(modEventBus);

        context.registerConfig(
                ModConfig.Type.CLIENT,
                Config.SPEC,
                "taczaddon-client.toml"
        );

        context.registerConfig(
                ModConfig.Type.COMMON,
                CommonConfig.SPEC,
                "taczaddon-common.toml"
        );
    }
}

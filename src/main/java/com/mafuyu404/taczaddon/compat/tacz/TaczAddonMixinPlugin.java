package com.mafuyu404.taczaddon.compat.tacz;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class TaczAddonMixinPlugin
        implements IMixinConfigPlugin {
    private static final Logger LOGGER =
            LogUtils.getLogger();

    @Override
    public void onLoad(String mixinPackage) {
        TaczCompatibility.profile();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(
            String targetClassName,
            String mixinClassName
    ) {
        TaczMixinBinding binding =
                TaczContractRegistry.bindingForMixin(mixinClassName);
        if (binding == null) {
            // Stable Vanilla/Forge mixins do not use TaCZ binary contracts.
            return true;
        }

        if (TaczCompatibility.isMixinBindingAvailable(binding)) {
            return true;
        }

        if (!TaczCompatibility.sideAllows(binding)) {
            LOGGER.info(
                    "[TACZ-addon compatibility] "
                            + "feature={} side={} mixin={} "
                            + "status=SIDE_NOT_APPLICABLE",
                    binding.feature(),
                    binding.runtimeSide(),
                    mixinClassName
            );
            return false;
        }

        LOGGER.warn(
                "[TACZ-addon compatibility] "
                        + "feature={} scope={} mixin={} "
                        + "status=SKIPPED profile={} binary={} reason={}",
                binding.feature(),
                binding.scope(),
                mixinClassName,
                TaczCompatibility.profile(),
                TaczCompatibility.binaryStatus(binding.feature()),
                TaczCompatibility.reason(binding.feature())
        );
        return false;
    }

    @Override
    public void acceptTargets(
            Set<String> myTargets,
            Set<String> otherTargets
    ) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
        /*
         * Diagnostics only. Target classes load lazily, so an adapter that has
         * not been applied yet must never be treated as a missing dependency.
         */
        TaczCompatibility.recordAppliedMixin(mixinClassName);
    }
}

package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.ClassContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.InvokeContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.MethodContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import java.util.*;

final class Tacz120Contracts {
    private Tacz120Contracts() {}
    private static final String MIXIN_V2 = "com.mafuyu404.taczaddon.mixin.tacz.v1_2_0.";
    static final FeatureContract AIM_1_2_0 = new FeatureContract(
            TaczFeature.AIM_CAMERA, "1.2.0",
            new ClassContract("com.tacz.guns.client.input.AimKey")
                    .withMethods(new MethodContract("onAimInput", "(Z)Z")));
    static final FeatureContract AMMO_1_2_0 = new FeatureContract(
            TaczFeature.HUD_AMMO, "1.2.0",
            new ClassContract("com.tacz.guns.client.gameplay.ClientAmmoCount")
                    .withMethods(new MethodContract("get",
                            "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/IGun;)I",
                            List.of(new InvokeContract("net/minecraft/client/player/LocalPlayer",
                                    "getInventory", "()Lnet/minecraft/world/entity/player/Inventory;",
                                    1, 1, List.of("m_150109_"))))));

    static TaczMixinBinding bindingForMixin(String name) {
        if ((MIXIN_V2 + "AimInputMixin").equals(name)) return new TaczMixinBinding(name,
                TaczFeature.AIM_CAMERA, AIM_1_2_0, CompatibilityScope.PUBLIC_STABLE, List.of(), TaczRuntimeSide.CLIENT);
        if ((MIXIN_V2 + "ClientAmmoCountMixin").equals(name)) return new TaczMixinBinding(name,
                TaczFeature.HUD_AMMO, AMMO_1_2_0, CompatibilityScope.VERSION_BOUND, List.of(), TaczRuntimeSide.CLIENT);
        return null;
    }
    static FeatureContract adapt(TaczFeature feature, FeatureContract base) {
        if (feature == TaczFeature.AIM_CAMERA) return AIM_1_2_0;
        if (feature == TaczFeature.HUD_AMMO) return AMMO_1_2_0;
        if (feature == TaczFeature.FAST_SWAP) {
            var classes = new ArrayList<>(base.classes());
            classes.add(new ClassContract("com.tacz.guns.util.time.GunTime")
                    .withMethods(new MethodContract("nowMillis", "()J")));
            return new FeatureContract(feature, "1.2.0", classes);
        }
        return base;
    }
}

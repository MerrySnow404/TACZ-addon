package com.mafuyu404.taczaddon.combat.client;

import com.mafuyu404.taczaddon.TACZaddon;
import com.mafuyu404.taczaddon.ammo.BackpackAmmoService;
import com.mafuyu404.taczaddon.compat.BeyondAmmoClientCompat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = TACZaddon.MODID, value = Dist.CLIENT)
public final class InventoryFeedShootGuard {
    private InventoryFeedShootGuard() {}

    @SubscribeEvent
    public static void onShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT
                || !(event.getShooter() instanceof LocalPlayer player)
                || !BeyondAmmoClientCompat.isUsable()) return;

        ItemStack stack = event.getGunItemStack();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || !gun.useInventoryAmmo(stack)) return;
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (!operator.needCheckAmmo()) return;

        boolean chambered =
                gun.hasBulletInBarrel(stack)
                        && TimelessAPI.getClientGunIndex(gun.getGunId(stack))
                                .map(index -> index.getGunData().getBolt() != Bolt.OPEN_BOLT)
                                .orElse(false);
        if (shouldBlock(
                gun.useDummyAmmo(stack),
                gun.getDummyAmmoAmount(stack),
                chambered,
                () -> BackpackAmmoService.hasCompatibleAmmo(player, stack, null),
                () -> BeyondAmmoClientCompat.hasConfirmedAmmo(stack))) {
            // This event precedes TaCZ's state lock, scheduled animation and shoot packet.
            event.setCanceled(true);
        }
    }

    static boolean shouldBlock(
            boolean dummyAmmo,
            int dummyCount,
            boolean chambered,
            BooleanSupplier physicalAmmo,
            BooleanSupplier networkAmmo) {
        if (chambered) return false;
        if (dummyAmmo) return dummyCount <= 0;
        return !physicalAmmo.getAsBoolean() && !networkAmmo.getAsBoolean();
    }
}

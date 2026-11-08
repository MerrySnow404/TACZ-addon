package com.mafuyu404.taczaddon.compat;

import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * Legacy TaCZ extraction path, used only when the direct Beyond Integration API is absent.
 *
 * <p>Beyond Integration augments {@code AbstractGunItem#findAndExtractInventoryAmmo} at RETURN and
 * identifies player-backed extraction through {@link PlayerMainInvWrapper}, so a single
 * player-main pass lets its hook consume the remaining network ammo.
 */
final class BeyondAmmoServerCompatInner {
    private BeyondAmmoServerCompatInner() {}

    static int consume(ServerPlayer player, AbstractGunItem gun, ItemStack stack, int needed) {
        return gun.findAndExtractInventoryAmmo(
                new PlayerMainInvWrapper(player.getInventory()), stack, needed);
    }
}

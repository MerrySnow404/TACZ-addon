package com.mafuyu404.taczaddon.compat;

import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.tacz.guns.api.item.gun.AbstractGunItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

final class BeyondAmmoServerCompatInner {
    private BeyondAmmoServerCompatInner() {}

    static int consume(ServerPlayer player, AbstractGunItem gun, ItemStack stack, int needed) {
        // NeoForge 0.6.5's findAndExtract hook only sees inventory terminals, not every bound
        // network.
        // Mirror its script RETURN hook: bound networks first, terminal fallback only if none
        // supplied ammo.
        int network = TaczAmmoExtractor.tryConsumeFromAll(player, stack, needed);
        return network > 0
                ? network
                : gun.findAndExtractInventoryAmmo(
                        new PlayerMainInvWrapper(player.getInventory()), stack, needed);
    }
}

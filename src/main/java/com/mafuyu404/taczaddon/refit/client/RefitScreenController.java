package com.mafuyu404.taczaddon.refit.client;

import com.mafuyu404.taczaddon.compat.TaczOemCompat;
import com.mafuyu404.taczaddon.inventory.VirtualInventory;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.refit.VirtualAttachmentData;
import com.mafuyu404.taczaddon.refit.VirtualAttachmentRules;
import com.mafuyu404.taczaddon.refit.network.RefitExternalAttachmentInstallPacket;
import com.mafuyu404.taczaddon.refit.network.VirtualAttachmentRefitPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.sound.SoundManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Per-screen attachment controls; all target access goes through Host. */
public final class RefitScreenController {
    public interface Host {
        int taczaddon$width();

        int taczaddon$height();

        int taczaddon$page();

        void taczaddon$setPage(int value);

        void taczaddon$rebuild();

        void taczaddon$addWidget(net.minecraft.client.gui.components.AbstractWidget widget);
    }

    private static final int TACZADDON_ATTACHMENTS_PER_PAGE = 8;
    private static final int TACZADDON_SLOT_SIZE = 18;
    private final Host host;

    public RefitScreenController(Host host) {
        this.host = host;
    }

    public boolean rebuildAttachmentTypeButtons() {
        // Keep official variant and slot-adapter controls; its unload callback is patched
        // separately.
        if (TaczOemCompat.isAvailable()) return false;

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return true;
        }

        ItemStack gunStack = player.getMainHandItem();

        IGun gun = IGun.getIGunOrNull(gunStack);

        if (gun == null) {
            return true;
        }

        int startX = host.taczaddon$width() - 30;
        int y = 10;

        Inventory inventory = player.getInventory();

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
                    TimelessAPI.getGunDisplay(gunStack)
                            .map(GunDisplayInstance::getLaserConfig)
                            .ifPresent(
                                    laserConfig ->
                                            addLaserControls(
                                                    laserConfig, inventory, AttachmentType.NONE));
                }
                continue;
            }

            GunAttachmentSlot slot =
                    new GunAttachmentSlot(
                            startX,
                            y,
                            type,
                            inventory.selected,
                            inventory,
                            this::onTypeButtonPressed);

            if (RefitTransform.getCurrentTransformType() == type) {
                slot.setSelected(true);

                RefitUnloadButton unloadButton =
                        new RefitUnloadButton(
                                startX + 5,
                                y + 18 + 2,
                                button -> onUnloadPressed(slot, inventory, player, button));

                ItemStack attachedItem = slot.getAttachmentItem();

                if (!attachedItem.isEmpty()) {
                    host.taczaddon$addWidget(unloadButton);

                    Item item = attachedItem.getItem();

                    if (item instanceof IAttachment attachment) {
                        ResourceLocation attachmentId = attachment.getAttachmentId(attachedItem);

                        if (attachmentId != null) {
                            TimelessAPI.getClientAttachmentIndex(attachmentId)
                                    .map(ClientAttachmentIndex::getLaserConfig)
                                    .ifPresent(
                                            laserConfig ->
                                                    addLaserControls(laserConfig, inventory, type));
                        }
                    }
                }
            }

            host.taczaddon$addWidget(slot);

            startX -= 18;
        }
        return true;
    }

    private void onTypeButtonPressed(Button button) {
        if (!(button instanceof GunAttachmentSlot slot)) {
            return;
        }

        AttachmentType type = slot.getType();

        if (!slot.isAllow()) {
            if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
                host.taczaddon$rebuild();
            }
            return;
        }

        if (RefitTransform.getCurrentTransformType() == type && type != AttachmentType.NONE) {
            if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
                host.taczaddon$rebuild();
            }
            return;
        }

        if (RefitTransform.changeRefitScreenView(type)) {
            host.taczaddon$rebuild();
        }
    }

    private void onUnloadPressed(
            GunAttachmentSlot slot, Inventory inventory, LocalPlayer player, Button button) {
        ItemStack attachedItem = slot.getAttachmentItem();

        if (attachedItem.isEmpty()) {
            return;
        }

        boolean virtual = VirtualAttachmentData.isVirtual(attachedItem);

        if (virtual || inventory.getFreeSlot() != -1) {
            SoundPlayManager.playerRefitSound(attachedItem, player, SoundManager.UNINSTALL_SOUND);

            NetworkHandler.sendToServer(
                    new ClientMessageUnloadAttachment(
                            inventory.selected, RefitTransform.getCurrentTransformType()));
            return;
        }

        player.sendSystemMessage(Component.translatable("gui.tacz.gun_refit.unload.no_space"));
    }

    private void addLaserControls(
            LaserConfig laserConfig, Inventory inventory, AttachmentType type) {
        if (!laserConfig.canEdit()) {
            return;
        }

        HSVSliderGroup group =
                new HSVSliderGroup(
                        host.taczaddon$width() - 140,
                        host.taczaddon$height() - 64,
                        120,
                        16,
                        inventory,
                        inventory.selected,
                        type);

        host.taczaddon$addWidget(group.getHueSlider());
        host.taczaddon$addWidget(group.getSaturationSlider());
    }

    public void addSafeAttachmentButtons() {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        AttachmentType selectedType = RefitTransform.getCurrentTransformType();

        if (selectedType == AttachmentType.NONE) {
            return;
        }

        Inventory realInventory = minecraft.player.getInventory();

        boolean virtualInventory = VirtualAttachmentRules.isLiberated(minecraft.player);
        Inventory displayedInventory =
                virtualInventory
                        ? VirtualAttachmentRules.useVirtualInventory(realInventory)
                        : RefitExternalSourceState.createDisplayInventory(realInventory);

        int startX = host.taczaddon$width() - 30;
        int startY = 50;

        int pageStart = host.taczaddon$page() * TACZADDON_ATTACHMENTS_PER_PAGE;

        int compatibleCount = 0;
        int currentY = startY;

        ItemStack gunStack = minecraft.player.getMainHandItem();

        IGun gun = IGun.getIGunOrNull(gunStack);

        if (gun == null) {
            return;
        }

        java.util.List<RefitDisplayEntry> entries = new java.util.ArrayList<>();
        java.util.Set<ResourceLocation> displayedIds = new java.util.HashSet<>();
        for (int slot = 0; slot < displayedInventory.getContainerSize(); slot++) {
            ItemStack stack = displayedInventory.getItem(slot);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment != null
                    && attachment.getType(stack) == selectedType
                    && gun.allowAttachment(gunStack, stack)) {
                entries.add(new RefitDisplayEntry(displayedInventory, slot, false));
                displayedIds.add(attachment.getAttachmentId(stack));
            }
        }
        var oemAttachments = TaczOemCompat.visibleAttachments(gunStack, selectedType, displayedIds);
        if (!oemAttachments.isEmpty()) {
            VirtualInventory oemInventory =
                    new VirtualInventory(oemAttachments.size(), minecraft.player);
            for (int slot = 0; slot < oemAttachments.size(); slot++) {
                oemInventory.setItem(slot, oemAttachments.get(slot));
                entries.add(new RefitDisplayEntry(oemInventory, slot, true));
            }
        }
        if (TaczOemCompat.isAvailable()) {
            entries.sort(
                    java.util.Comparator.comparingInt(
                            entry -> TaczOemCompat.isBuiltin(entry.stack()) ? 0 : 1));
        }
        // Clamp before rendering when an external inventory or OEM choice disappears.
        host.taczaddon$setPage(
                Math.min(
                        host.taczaddon$page(),
                        Math.max(0, (entries.size() - 1) / TACZADDON_ATTACHMENTS_PER_PAGE)));
        pageStart = host.taczaddon$page() * TACZADDON_ATTACHMENTS_PER_PAGE;
        for (RefitDisplayEntry entry : entries) {
            compatibleCount++;

            if (compatibleCount <= pageStart) {
                continue;
            }

            if (compatibleCount > pageStart + TACZADDON_ATTACHMENTS_PER_PAGE) {
                continue;
            }

            int capturedSlot = entry.slot();
            Inventory entryInventory = entry.inventory();

            InventoryAttachmentSlot button =
                    new InventoryAttachmentSlot(
                            startX,
                            currentY,
                            capturedSlot,
                            entryInventory,
                            pressedButton -> {
                                ItemStack clickedStack = entryInventory.getItem(capturedSlot);

                                IAttachment clickedAttachment =
                                        IAttachment.getIAttachmentOrNull(clickedStack);

                                if (clickedAttachment == null) {
                                    return;
                                }

                                ResourceLocation attachmentId =
                                        clickedAttachment.getAttachmentId(clickedStack);

                                if (attachmentId == null) {
                                    return;
                                }

                                SoundPlayManager.playerRefitSound(
                                        clickedStack, minecraft.player, SoundManager.INSTALL_SOUND);

                                if (entryInventory instanceof RefitDisplayInventory display) {
                                    var external = display.externalAt(capturedSlot);
                                    if (external != null) {
                                        NetworkHandler.sendToServer(
                                                new RefitExternalAttachmentInstallPacket(
                                                        realInventory.selected,
                                                        external.locator(),
                                                        external.attachmentId(),
                                                        external.type()));
                                        return;
                                    }
                                }

                                int sourceSlot =
                                        (virtualInventory || entry.oem())
                                                ? VirtualAttachmentRefitPacket.VIRTUAL_SOURCE_SLOT
                                                : capturedSlot;

                                NetworkHandler.sendToServer(
                                        new VirtualAttachmentRefitPacket(
                                                sourceSlot, realInventory.selected, attachmentId));
                            });

            host.taczaddon$addWidget(button);

            currentY += TACZADDON_SLOT_SIZE;
        }

        int totalPages =
                compatibleCount == 0 ? 0 : (compatibleCount - 1) / TACZADDON_ATTACHMENTS_PER_PAGE;

        if (host.taczaddon$page() > totalPages) {
            host.taczaddon$setPage(totalPages);
        }

        if (host.taczaddon$page() > 0) {
            host.taczaddon$addWidget(
                    new RefitTurnPageButton(
                            startX,
                            startY - 10,
                            true,
                            button -> {
                                if (host.taczaddon$page() > 0) {
                                    host.taczaddon$setPage(host.taczaddon$page() - 1);
                                    host.taczaddon$rebuild();
                                }
                            }));
        }

        if (host.taczaddon$page() < totalPages) {
            host.taczaddon$addWidget(
                    new RefitTurnPageButton(
                            startX,
                            startY + TACZADDON_SLOT_SIZE * TACZADDON_ATTACHMENTS_PER_PAGE + 2,
                            false,
                            button -> {
                                if (host.taczaddon$page() < totalPages) {
                                    host.taczaddon$setPage(host.taczaddon$page() + 1);
                                    host.taczaddon$rebuild();
                                }
                            }));
        }
    }
}

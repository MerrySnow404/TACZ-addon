package com.mafuyu404.taczaddon.mixin.tacz.v1_1_8;

import com.mafuyu404.taczaddon.client.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.client.refit.GunRefitScreenAccess;
import com.mafuyu404.taczaddon.client.refit.LiberatedRefitClientService;
import com.mafuyu404.taczaddon.client.refit.RefitDisplayInventory;
import com.mafuyu404.taczaddon.client.refit.RefitExternalSourceState;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.network.RefitExternalAttachmentInstallPacket;
import com.mafuyu404.taczaddon.refit.LiberatedRefitService;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Client-only hooks for TaCZ 1.1.8-hotfix (Curse file 8141310).
 */
@Mixin(value = GunRefitScreen.class, remap = false)
public abstract class GunRefitScreenMixin
        implements GunRefitScreenAccess {
    @Shadow
    private int currentPage;

    @Unique
    private final RefitExternalSourceState taczaddon$refitSourceState =
            new RefitExternalSourceState();

    @Redirect(
            method = "addInventoryAttachmentButtons()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
                    remap = true
            ),
            require = 1,
            remap = false
    )
    private Inventory taczaddon$attachmentCatalogInventory(
            LocalPlayer player
    ) {
        Inventory realInventory = player.getInventory();
        if (ClientSyncedConfig.liberateAttachment()) {
            return LiberatedRefitService.createInventory(
                    realInventory,
                    true
            );
        }
        return new RefitDisplayInventory(
                realInventory,
                this.taczaddon$refitSourceState.getExternalCandidates()
        );
    }

    @Redirect(
            method = "addAttachmentTypeButtons()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;",
                    remap = true
            ),
            require = 1,
            remap = false
    )
    private Inventory taczaddon$attachmentControlsInventory(
            LocalPlayer player
    ) {
        Inventory realInventory = player.getInventory();
        /*
         * Gun control widgets must always operate against the real selected
         * gun. Nearby-container display slots are only candidate entries.
         */
        if (!ClientSyncedConfig.liberateAttachment()) {
            return realInventory;
        }
        return LiberatedRefitService.createInventory(
                realInventory,
                true
        );
    }

    @ModifyArg(
            method = "addInventoryAttachmentButtons()V",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tacz/guns/client/gui/components/refit/"
                                    + "InventoryAttachmentSlot;"
                                    + "<init>(IIILnet/minecraft/world/"
                                    + "entity/player/Inventory;"
                                    + "Lnet/minecraft/client/gui/components/"
                                    + "Button$OnPress;)V",
                    remap = false
            ),
            index = 4,
            remap = false,
            require = 1
    )
    private Button.OnPress taczaddon$wrapInventoryAttachmentButton(
            Button.OnPress originalOnPress
    ) {
        return new Button.OnPress() {
            @Override
            public void onPress(Button button) {
                if (button instanceof InventoryAttachmentSlot attachmentSlot) {
                    Inventory exactInventory =
                            ((InventoryAttachmentSlotAccess) attachmentSlot)
                                    .taczaddon$getInventory();
                    GunRefitScreenMixin.this
                            .taczaddon$handleAttachmentCandidate(
                            exactInventory,
                            attachmentSlot,
                            originalOnPress
                    );
                }
            }
        };
    }

    @Override
    public GunSmithSourceScreenAccess.AcceptResult
    taczaddon$acceptRefitSourceSnapshot(
            long requestId,
            List<RefitSourceResolver.RefitExternalCandidate> candidates
    ) {
        GunSmithSourceScreenAccess.AcceptResult result =
                this.taczaddon$refitSourceState.acceptSnapshot(
                        requestId,
                        candidates
                );
        if (result
                == GunSmithSourceScreenAccess.AcceptResult.UPDATED) {
            this.taczaddon$rebuildRefitCandidateButtons();
        }
        return result;
    }

    @Override
    public void taczaddon$requestRefitSourceRefresh() {
        if (ClientSyncedConfig.liberateAttachment()) {
            return;
        }
        this.taczaddon$refitSourceState.requestSourceRefresh();
    }

    @Override
    public void taczaddon$tickRefitSourceRefresh() {
        if (ClientSyncedConfig.liberateAttachment()) {
            return;
        }
        this.taczaddon$refitSourceState.tickSourceRefresh();
    }

    @Override
    public void taczaddon$onRefitScreenInit() {
        if (ClientSyncedConfig.liberateAttachment()) {
            return;
        }
        this.taczaddon$refitSourceState.onScreenInit();
    }

    @Override
    public void taczaddon$rebuildRefitCandidateButtons() {
        int playerSlotCount = Minecraft.getInstance().player == null
                ? 0
                : Minecraft.getInstance().player
                .getInventory()
                .getContainerSize();
        int displaySlotCount = playerSlotCount
                + this.taczaddon$refitSourceState
                .getExternalCandidates()
                .size();
        int maxPage = Math.max(0, (displaySlotCount - 1) / 8);
        this.currentPage = Math.max(
                0,
                Math.min(this.currentPage, maxPage)
        );
        ((GunRefitScreen) (Object) this).init();
    }

    @Unique
    private void taczaddon$handleAttachmentCandidate(
            Inventory exactInventory,
            InventoryAttachmentSlot attachmentSlot,
            Button.OnPress nativePress
    ) {
        if (ClientSyncedConfig.liberateAttachment()) {
            LiberatedRefitClientService
                    .sendAttachmentIdInsteadOfVirtualSlot(
                    exactInventory,
                    attachmentSlot
            );
            return;
        }

        if (!(exactInventory instanceof RefitDisplayInventory display)) {
            nativePress.onPress(attachmentSlot);
            return;
        }

        int displaySlot = attachmentSlot.getSlotIndex();
        RefitDisplayInventory.OriginMapping mapping =
                display.originFor(displaySlot);
        if (mapping.origin()
                == RefitDisplayInventory.Origin.PLAYER) {
            nativePress.onPress(attachmentSlot);
            return;
        }
        if (mapping.origin()
                != RefitDisplayInventory.Origin.EXTERNAL
                || mapping.locator() == null) {
            return;
        }

        ItemStack candidate = display.getItem(displaySlot);
        IAttachment attachment =
                IAttachment.getIAttachmentOrNull(candidate);
        if (attachment == null) {
            return;
        }

        ResourceLocation attachmentId =
                attachment.getAttachmentId(candidate);
        AttachmentType type = attachment.getType(candidate);
        LocalPlayer player =
                display.player instanceof LocalPlayer value
                        ? value
                        : null;
        if (attachmentId == null
                || type == null
                || type == AttachmentType.NONE
                || player == null) {
            return;
        }

        SoundPlayManager.playerRefitSound(
                candidate,
                player,
                SoundManager.INSTALL_SOUND
        );
        NetworkHandler.CHANNEL.sendToServer(
                new RefitExternalAttachmentInstallPacket(
                        mapping.locator(),
                        attachmentId,
                        type,
                        display.selected,
                        this.taczaddon$refitSourceState
                                .getLatestAcceptedRefreshRequestId()
                )
        );
    }

    @Override
    public void taczaddon$rebuildLiberatedAttachmentButtons() {
        this.currentPage = 0;
        ((GunRefitScreen) (Object) this).init();
    }
}

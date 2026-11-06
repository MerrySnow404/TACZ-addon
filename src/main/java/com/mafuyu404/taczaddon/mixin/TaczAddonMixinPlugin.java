package com.mafuyu404.taczaddon.mixin;

import static com.mafuyu404.taczaddon.compat.contract.BinaryContracts.*;

import com.mafuyu404.taczaddon.compat.contract.BinaryContracts.ContractKind;
import com.mafuyu404.taczaddon.compat.sophisticated.SophisticatedPayloadContractState;
import com.mafuyu404.taczaddon.refit.RefitCompatibility;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Binary-contract gate for mixins that target optional dependency classes.
 *
 * <p>Instead of letting a {@code require = 1} mixin turn a contract mismatch into a startup crash,
 * this plugin inspects the actual dependency class bytes (ASM, no target class loading) and only
 * applies the affected mixins when the contract holds.
 *
 * <p>If a contract is missing:
 *
 * <ul>
 *   <li>an ERROR is logged with the expected contract;
 *   <li>the affected mixin is skipped (feature unavailable);
 *   <li>the game keeps starting without a half-broken integration.
 * </ul>
 *
 * <p>Verified contracts:
 *
 * <ul>
 *   <li>ClientMessageUnloadAttachment: static {@code handle(ClientMessageUnloadAttachment,
 *       IPayloadContext)V}, non-static {@code gunSlotIndex:I}, non-static {@code
 *       attachmentType:Lcom/tacz/guns/api/item/attachment/AttachmentType;}
 *   <li>GunRefitScreen: {@code addAttachmentTypeButtons()V}, {@code
 *       addInventoryAttachmentButtons()V} (both non-static), non-static non-final {@code
 *       currentPage:I}
 *   <li>BackpackContentsPayload (Sophisticated Backpacks): static {@code
 *       handlePayload(BackpackContentsPayload, IPayloadContext)V}, record accessors {@code
 *       backpackUuid()Ljava/util/UUID;} and {@code
 *       backpackContents()Lnet/minecraft/nbt/CompoundTag;}
 * </ul>
 *
 * <p>Dependency graph (fail closed):
 *
 * <pre>
 * ClientMessageUnloadAttachmentMixin  requires UNLOAD_PACKET
 * ClientMessageUnloadAttachmentAccessor requires UNLOAD_PACKET
 * GunRefitScreenMixin                  requires REFIT_SCREEN AND UNLOAD_PACKET
 * BackpackContentsPayloadMixin         requires BACKPACK_CONTENTS_PAYLOAD
 * </pre>
 *
 * <p>The server-side provenance-aware unload takeover may stand alone (it protects pre-existing
 * virtual markers even when the client GUI contract changed), but the client virtual refit UI must
 * never be enabled without the server unload protection.
 */
public final class TaczAddonMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaczAddonMixinPlugin.class);

    private static final String PACKAGE = "com.mafuyu404.taczaddon.mixin.";

    private static final String UNLOAD_MIXIN = PACKAGE + "ClientMessageUnloadAttachmentMixin";
    private static final String UNLOAD_ACCESSOR = PACKAGE + "ClientMessageUnloadAttachmentAccessor";
    private static final String REFIT_MIXIN = PACKAGE + "GunRefitScreenMixin";
    private static final String BACKPACK_PAYLOAD_MIXIN = PACKAGE + "BackpackContentsPayloadMixin";

    private static final String UNLOAD_PACKET =
            "com/tacz/guns/network/message/" + "ClientMessageUnloadAttachment";
    private static final String REFIT_SCREEN = "com/tacz/guns/client/gui/GunRefitScreen";
    private static final String BACKPACK_CONTENTS_PAYLOAD =
            "net/p3pp3rf1y/sophisticatedbackpacks/network/" + "BackpackContentsPayload";

    private Boolean unloadContractValid;
    private Boolean refitContractValid;
    private Boolean backpackPayloadContractValid;

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
        if (!mixinClassName.equals(BACKPACK_PAYLOAD_MIXIN)) {
            return;
        }

        boolean installed = hasAppliedBackpackPayloadHook(targetClass);

        SophisticatedPayloadContractState.reportApplied(installed);

        if (!installed) {
            LOGGER.error(
                    "[taczaddon] Sophisticated Backpacks payload preflight passed, but the"
                        + " transformed BackpackContentsPayload does not contain the TACZAddon"
                        + " response hook. Optional immediate invalidation is unavailable; native"
                        + " sync is unaffected.");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(PACKAGE + "GunHudOverlayMixin")) {
            return hasMethod(
                    "com/tacz/guns/client/gui/overlay/GunHudOverlay",
                    "handleInventoryAmmo",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Inventory;)V");
        }
        if (mixinClassName.equals(PACKAGE + "ClientAmmoCountMixin")) {
            return hasMethod(
                    "com/tacz/guns/client/gameplay/ClientAmmoCount",
                    "get",
                    "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/IGun;)I");
        }
        if (mixinClassName.equals(PACKAGE + "AimKeyMixin")) {
            return hasMethod(
                    "com/tacz/guns/client/input/AimKey",
                    "onAimPress",
                    "(Lnet/neoforged/neoforge/client/event/InputEvent$MouseButton$Post;)V");
        }
        if (mixinClassName.equals(PACKAGE + "OfficialAimKeyMixin")) {
            return hasMethod("com/tacz/guns/client/input/AimKey", "onAimInput", "(Z)Z");
        }
        if (mixinClassName.equals(PACKAGE + "OfficialVirtualAttachmentMixin")) {
            return readClassBytes("com/tacz/guns/util/VirtualOemAttachment") != null;
        }
        if (mixinClassName.equals(PACKAGE + "LeawindAimModeResolverMixin")) {
            // Soft target: inspect bytes only, never resolve an optional mod's classes.
            byte[] bytes = readClassBytes(targetClassName.replace('.', '/'));
            if (bytes == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
            boolean supported =
                    node.methods.stream()
                            .anyMatch(
                                    method ->
                                            method.name.equals("shouldAim")
                                                    && method.desc.equals("(ZZZ)Z")
                                                    && (method.access & Opcodes.ACC_STATIC) != 0);
            if (!supported)
                LOGGER.warn(
                        "[taczaddon] Leawind aim integration disabled: shouldAim(ZZZ)Z contract"
                            + " changed");
            return supported;
        }
        if (mixinClassName.equals(BACKPACK_PAYLOAD_MIXIN)) {
            boolean valid = isBackpackPayloadContractValid();

            /*
             * A positive preflight only means the target is eligible for the mixin.
             * The optional callback is usable only after postApply confirms that the
             * transformed handler invokes it. Native CLIENT_SYNC is independent.
             */
            SophisticatedPayloadContractState.reportPreflight(valid);

            return valid;
        }

        if (mixinClassName.equals(UNLOAD_MIXIN) || mixinClassName.equals(UNLOAD_ACCESSOR)) {
            return RefitCompatibility.shouldEnableUnload(isUnloadContractValid());
        }

        if (mixinClassName.equals(REFIT_MIXIN)) {
            return RefitCompatibility.shouldEnableRefit(
                    isUnloadContractValid(), isRefitContractValid());
        }

        return true;
    }

    private boolean isBackpackPayloadContractValid() {
        if (backpackPayloadContractValid == null) {
            backpackPayloadContractValid =
                    verifyContract(
                            BACKPACK_CONTENTS_PAYLOAD,
                            "BackpackContentsPayload",
                            ContractKind.BACKPACK_CONTENTS_PAYLOAD,
                            "client Sophisticated backpack cache refresh");

            if (!backpackPayloadContractValid) {
                LOGGER.error(
                        "[taczaddon] Sophisticated Backpacks payload binary contract mismatch. The"
                            + " BackpackContentsPayload mixin is skipped and the optional immediate"
                            + " invalidation is unavailable; native sync is unaffected; startup"
                            + " continues.");
            }
        }

        return backpackPayloadContractValid;
    }

    private boolean isUnloadContractValid() {
        if (unloadContractValid == null) {
            unloadContractValid =
                    verifyContract(
                            UNLOAD_PACKET,
                            "ClientMessageUnloadAttachment",
                            ContractKind.UNLOAD_PACKET,
                            "server-side virtual attachment unload "
                                    + "protection and client "
                                    + "liberateAttachment refit integration");

            if (!unloadContractValid) {
                LOGGER.error(
                        "[taczaddon] TaCZ unload binary contract mismatch. "
                                + "Server-side virtual attachment unload "
                                + "protection disabled. Client "
                                + "liberateAttachment refit integration will "
                                + "also be disabled.");
            }
        }

        return unloadContractValid;
    }

    private boolean isRefitContractValid() {
        if (refitContractValid == null) {
            if (isServerSide()) {
                /*
                 * GunRefitScreen is a client-side target and its mixin lives
                 * in the "client" section. On a dedicated server the class is
                 * not applicable (and may not even be present in stripped
                 * distributions); this is the normal state, not an
                 * incompatibility.
                 */
                refitContractValid = false;
                return false;
            }

            refitContractValid =
                    verifyContract(
                            REFIT_SCREEN,
                            "GunRefitScreen",
                            ContractKind.REFIT_SCREEN,
                            "client liberateAttachment refit UI");

            if (!refitContractValid) {
                LOGGER.error(
                        "[taczaddon] TaCZ GunRefitScreen binary contract "
                                + "mismatch. Client liberateAttachment refit "
                                + "UI disabled. Server-side "
                                + "provenance-aware unload protection remains "
                                + "enabled if its own contract is valid.");
            }
        }

        return refitContractValid;
    }

    private static boolean isServerSide() {
        return MixinEnvironment.getCurrentEnvironment().getSide() == MixinEnvironment.Side.SERVER;
    }
}

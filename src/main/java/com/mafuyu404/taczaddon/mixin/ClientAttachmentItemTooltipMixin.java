package com.mafuyu404.taczaddon.mixin;

import com.mafuyu404.taczaddon.config.ClientConfig;
import com.mafuyu404.taczaddon.config.ClientRuleState;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced attachment tooltip.
 *
 * <p>Version-bound on the stable private source method {@code
 * addText(Lcom/tacz/guns/api/item/attachment/AttachmentType;)V} (not a synthetic lambda): TaCZ
 * fully populates the {@code components} list first, then this mixin post-processes the
 * already-built rows at RETURN.
 *
 * <p>Only rows that can be identified as attachment modifier property rows (by their translation
 * key) are replaced with the addon's diff text. Tooltip descriptions, laser color, zoom, mag level,
 * pack info and every other non-modifier row are preserved verbatim.
 */
@Mixin(value = ClientAttachmentItemTooltip.class, remap = false)
public class ClientAttachmentItemTooltipMixin {

    @Unique private static final Logger TACZADDON_LOGGER = LogUtils.getLogger();

    @Unique private static boolean taczaddon$loggedTooltipFailure;

    @Shadow @Final private ResourceLocation attachmentId;

    @Shadow @Final private List<Component> components;

    @Inject(method = "getAllAllowGuns", at = @At("RETURN"), cancellable = true)
    private static void modifyShowAllowGun(
            List<ItemStack> output,
            ResourceLocation attachmentId,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> result = new ArrayList<>();
        int amount = ClientConfig.getAllowGunAmount();
        for (int i = 0; i < Math.min(amount, output.size()); i++) {
            result.add(output.get(i).copy());
        }
        cir.setReturnValue(result);
    }

    /**
     * Post-processes the tooltip rows TaCZ already built in {@code addText(...)}. The original
     * behavior was implemented as a {@code JsonProperty.getComponents()} redirect inside the javac
     * lambda {@code lambda$addText$5}; this stable method-bound version is equivalent because
     * non-property components are preserved unchanged.
     */
    @Inject(
            method = "addText(" + "Lcom/tacz/guns/api/item/attachment/AttachmentType;" + ")V",
            at = @At("RETURN"),
            remap = false,
            require = 1)
    private void taczaddon$enhanceAttachmentDetail(CallbackInfo ci) {
        if (!ClientConfig.SHOW_ATTACHMENT_ATTRIBUTE.get()
                || !ClientRuleState.isShowAttachmentDetail()) {
            return;
        }

        Map<String, String> remarks;

        try {
            remarks = taczaddon$buildAttributeRemarks();
        } catch (IllegalArgumentException | NullPointerException ex) {
            if (!taczaddon$loggedTooltipFailure) {
                taczaddon$loggedTooltipFailure = true;
                TACZADDON_LOGGER.debug(
                        "Unable to build enhanced TaCZ attachment tooltip; "
                                + "falling back to original text",
                        ex);
            }
            return;
        }

        if (remarks.isEmpty()) {
            return;
        }

        List<Component> transformed = new ArrayList<>(components.size());

        for (Component component : components) {
            Optional<String> titleKeyOptional =
                    taczaddon$getTooltipPropertyKey(getTranslationKey(component));

            if (titleKeyOptional.isEmpty()) {
                transformed.add(component);
                continue;
            }

            String titleKey = titleKeyOptional.get();

            if (titleKey.equals("inaccuracy")) {
                titleKey = "hipfire_inaccuracy";
            }

            String remark = remarks.get(titleKey);

            if (remark == null) {
                transformed.add(component);
                continue;
            }

            String title =
                    titleKey.equals("hipfire_inaccuracy")
                            ? Component.translatable(
                                            "gui.tacz.gun_refit.property_diagrams."
                                                    + "hipfire_inaccuracy")
                                    .getString()
                            : component.getString().replace("+ ", "").replace("- ", "");

            transformed.add(
                    Component.literal(title + " " + remark).withStyle(component.getStyle()));
        }

        components.clear();
        components.addAll(transformed);
    }

    /**
     * Computes the equipped-gun vs candidate-attachment property diffs for the currently held gun.
     * Returns an empty map when the enhancement does not apply.
     */
    @Unique
    private Map<String, String> taczaddon$buildAttributeRemarks() {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return Map.of();
        }

        ItemStack gunItem = player.getMainHandItem().copy();

        IGun iGun = IGun.getIGunOrNull(gunItem);

        if (iGun == null) {
            return Map.of();
        }

        boolean allowAttachment =
                AllowAttachmentTagMatcher.match(iGun.getGunId(gunItem), this.attachmentId);

        if (!allowAttachment) {
            return Map.of();
        }

        ItemStack attachmentItem = AttachmentItemBuilder.create().setId(this.attachmentId).build();

        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);

        if (iAttachment == null) {
            return Map.of();
        }

        AttachmentType attachmentType = iAttachment.getType(attachmentItem);
        ResourceLocation gunId = iGun.getGunId(gunItem);

        Map<String, Double> originAttr = new HashMap<>();
        Map<String, Double> newAttr = new HashMap<>();
        Map<String, Double> defaultAttr = new HashMap<>();
        Map<String, String> remarks = new HashMap<>();

        TimelessAPI.getCommonGunIndex(gunId)
                .ifPresent(
                        index -> {
                            GunData gunData = index.getGunData();

                            if (Minecraft.getInstance().level == null) {
                                return;
                            }

                            var registryAccess = Minecraft.getInstance().level.registryAccess();

                            ItemStack attachmentTypeItem =
                                    iGun.getAttachment(registryAccess, gunItem, attachmentType);

                            if (!attachmentTypeItem.isEmpty()) {
                                iGun.unloadAttachment(registryAccess, gunItem, attachmentType);
                            }

                            AttachmentCacheProperty cacheProperty = new AttachmentCacheProperty();
                            cacheProperty.eval(gunItem, gunData);

                            AttachmentPropertyManager.getModifiers()
                                    .forEach(
                                            (key, modifier) ->
                                                    modifier.getPropertyDiagramsData(
                                                                    gunItem, gunData, cacheProperty)
                                                            .forEach(
                                                                    diagramsData ->
                                                                            originAttr.putAll(
                                                                                    handleData(
                                                                                            diagramsData))));

                            iGun.installAttachment(registryAccess, gunItem, attachmentItem);

                            cacheProperty.eval(gunItem, gunData);

                            AttachmentPropertyManager.getModifiers()
                                    .forEach(
                                            (key, modifier) ->
                                                    modifier.getPropertyDiagramsData(
                                                                    gunItem, gunData, cacheProperty)
                                                            .forEach(
                                                                    diagramsData -> {
                                                                        Optional<String>
                                                                                propertyKey =
                                                                                        taczaddon$getPropertyKey(
                                                                                                diagramsData
                                                                                                        .titleKey());
                                                                        OptionalDouble
                                                                                defaultValue =
                                                                                        extractValue(
                                                                                                diagramsData
                                                                                                        .defaultString());

                                                                        if (propertyKey.isPresent()
                                                                                && defaultValue
                                                                                        .isPresent()) {
                                                                            defaultAttr.put(
                                                                                    propertyKey
                                                                                            .get(),
                                                                                    defaultValue
                                                                                            .getAsDouble());
                                                                        }

                                                                        newAttr.putAll(
                                                                                handleData(
                                                                                        diagramsData));
                                                                    }));
                        });

        newAttr.forEach(
                (titleKey, newVal) -> {
                    if (!originAttr.containsKey(titleKey)) {
                        return;
                    }

                    double offset = newVal - originAttr.get(titleKey);
                    String remark = "";
                    if (offset > 0) {
                        remark += "+";
                    }
                    remark += (double) (Math.round(offset * 100d) / 100d);
                    if (titleKey.equals("weight")) {
                        remark += "kg";
                    }
                    if (titleKey.equals("ads") || titleKey.contains("time")) {
                        remark += "s";
                    }
                    if (titleKey.equals("aim_inaccuracy") || titleKey.equals("armor_ignore")) {
                        remark += "%";
                    }
                    if (titleKey.equals("rpm")) {
                        remark += "rpm";
                    }
                    if (titleKey.equals("effective_range")) {
                        remark += "m";
                    }
                    if (titleKey.contains("ammo_speed")) {
                        remark += "m/s";
                    }

                    Double defaultValue = defaultAttr.get(titleKey);
                    if (defaultValue != null
                            && defaultValue != 0.0D
                            && Double.isFinite(defaultValue)) {
                        remark +=
                                " ("
                                        + (offset > 0 ? "+" : "")
                                        + Math.ceil(offset / defaultValue * 100)
                                        + "%)";
                    }

                    remarks.put(titleKey, remark);
                });

        return remarks;
    }

    @Unique
    private HashMap<String, Double> handleData(IAttachmentModifier.DiagramsData diagramsData) {
        HashMap<String, Double> result = new HashMap<>();

        if (diagramsData == null) {
            return result;
        }

        Optional<String> propertyKey = taczaddon$getPropertyKey(diagramsData.titleKey());

        if (propertyKey.isEmpty()) {
            return result;
        }

        String positivelyString = diagramsData.positivelyString();
        String negativeString = diagramsData.negativeString();
        String text = positivelyString;
        String[] positiveParts =
                positivelyString == null ? new String[0] : positivelyString.split(" ");

        if (positiveParts.length > 1 && positiveParts[1].contains("+-")) {
            text = negativeString;
        }

        if (text == null || text.isBlank()) {
            text =
                    negativeString == null || negativeString.isBlank()
                            ? positivelyString
                            : negativeString;
        }

        OptionalDouble value = extractValue(text);
        value.ifPresent(v -> result.put(propertyKey.get(), v));
        return result;
    }

    @Unique
    private OptionalDouble extractValue(String text) {
        if (text == null || text.isBlank()) {
            return OptionalDouble.empty();
        }

        String pattern = "[-+]?\\d+(?:\\.\\d+)?";
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        OptionalDouble value = OptionalDouble.empty();

        while (matcher.find()) {
            try {
                value = OptionalDouble.of(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException ignored) {
                return OptionalDouble.empty();
            }
        }

        return value;
    }

    @Unique
    private static Optional<String> taczaddon$getPropertyKey(String titleKey) {
        // Custom gun packs sometimes shorten or replace TaCZ's modifier keys.
        // Fall back to the last segment so malformed keys keep vanilla
        // tooltip text.
        if (titleKey == null || titleKey.isBlank()) {
            return Optional.empty();
        }

        String[] parts = titleKey.split("\\.");

        if (parts.length >= 5) {
            return Optional.of(parts[4]);
        }

        if (parts.length > 0) {
            return Optional.of(parts[parts.length - 1]);
        }

        return Optional.empty();
    }

    @Unique
    private static Optional<String> taczaddon$getTooltipPropertyKey(String titleKey) {
        if (titleKey == null || titleKey.isBlank()) {
            return Optional.empty();
        }

        String[] parts = titleKey.split("\\.");

        if (parts.length >= 4) {
            return Optional.of(parts[3]);
        }

        if (parts.length > 0) {
            return Optional.of(parts[parts.length - 1]);
        }

        return Optional.empty();
    }

    @Unique
    private static String getTranslationKey(Component component) {
        ComponentContents contents = component.getContents();

        if (contents instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }

        return null;
    }
}

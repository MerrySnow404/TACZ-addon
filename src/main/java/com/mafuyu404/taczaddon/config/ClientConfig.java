package com.mafuyu404.taczaddon.config;

import com.mafuyu404.taczaddon.inventory.ItemStackData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-owned presentation and input preferences.
 *
 * <p>Server-authoritative gunsmith policies such as nearby-container access, scan radius, and batch
 * limits belong in CommonConfig.
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue BETTER_AIM_CAMERA;
    public static final ModConfigSpec.BooleanValue GUNSMITHTABLE_CRAFT_TOAST;
    public static final ModConfigSpec.BooleanValue SHOW_ATTACHMENT_ATTRIBUTE;
    public static final ModConfigSpec.IntValue LESS_ALLOW_GUN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MELEE_WEAPON_LIST;
    public static final ModConfigSpec.BooleanValue FAST_SWAP_GUN;
    public static final ModConfigSpec.BooleanValue SHOW_ITEM_RELATION;
    public static final ModConfigSpec.BooleanValue SHOW_ITEM_RELATION_IN_SOPHISTICATED_STORAGE;
    public static final ModConfigSpec.BooleanValue SHOOT_WHILE_RELOADING;

    public static final ModConfigSpec SPEC;

    private static final Set<String> ITEM_BLACKLIST = new HashSet<>();

    static {
        BUILDER.push("Melee Setting");

        MELEE_WEAPON_LIST =
                BUILDER.comment(
                                "Guns in this list are treated as melee weapons. "
                                        + "Firing is replaced with a melee attack.",
                                "Use F3+H to inspect the GunId tag.")
                        .defineList(
                                "MeleeWeaponList",
                                List.of("tacz:type_82", "tacz:type_83"),
                                null,
                                entry ->
                                        entry instanceof String value
                                                && ResourceLocation.tryParse(value) != null);

        BUILDER.pop();

        BUILDER.push("Gun Setting");

        SHOOT_WHILE_RELOADING =
                BUILDER.comment(
                                "Allow firing to interrupt a reload when the gun "
                                        + "still contains fireable ammunition.",
                                "Normal TaCZ client and server firing checks still apply.")
                        .define("enableShootWhileReloading", true);

        BUILDER.pop();

        BUILDER.push("GunSmithTable Presentation");

        GUNSMITHTABLE_CRAFT_TOAST =
                BUILDER.comment(
                                "Show an item toast after a successful craft at "
                                        + "a gun smith table.")
                        .define("enableCraftToast", true);

        BUILDER.pop();

        BUILDER.push("Attachment Setting");

        LESS_ALLOW_GUN =
                BUILDER.comment(
                                "Maximum number of compatible guns shown in "
                                        + "advanced attachment tooltips.")
                        .defineInRange("allowGunDisplayCount", 16, 0, 1024);

        SHOW_ATTACHMENT_ATTRIBUTE =
                BUILDER.comment(
                                "Show detailed attachment stat changes in "
                                        + "attachment tooltips.",
                                "This requires both the client option and the "
                                        + "server showAttachmentDetail gamerule.",
                                "The main hand must also hold a gun that accepts "
                                        + "the attachment.",
                                "The server rule defaults to true and is "
                                        + "authoritative; server operators can "
                                        + "disable detail tooltips with "
                                        + "/gamerule showAttachmentDetail false.")
                        .define("enableAttachmentDetail", true);

        BUILDER.pop();

        BUILDER.push("Other Setting");

        BETTER_AIM_CAMERA =
                BUILDER.comment(
                                "Automatically switch to first person while aiming "
                                        + "from another camera perspective.",
                                "Restore the previous perspective after aiming ends.")
                        .define("enableBetterAimCamera", true);

        FAST_SWAP_GUN =
                BUILDER.comment(
                                "Deprecated: this option no longer controls any "
                                        + "cooldown prediction.",
                                "Fast swap behavior is decided by the server-side "
                                        + "config option enableFastSwapGun.",
                                "The server value is synchronized to clients; "
                                        + "client prediction uses only that "
                                        + "synchronized value.",
                                "This option is kept only to avoid errors in old "
                                        + "configuration files and has no effect.")
                        .define("enableFastSwapGun", true);

        SHOW_ITEM_RELATION =
                BUILDER.comment("Highlight related items while hovering over an item.")
                        .define("enableShowItemRelation", true);

        SHOW_ITEM_RELATION_IN_SOPHISTICATED_STORAGE =
                BUILDER.comment(
                                "Highlight related items in Sophisticated Backpacks "
                                        + "and Sophisticated Storage screens.",
                                "This also requires enableShowItemRelation.")
                        .define("enableShowItemRelationInSophisticatedStorage", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ClientConfig() {}

    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            updateItemBlacklist();
        }
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            updateItemBlacklist();
        }
    }

    private static void updateItemBlacklist() {
        ITEM_BLACKLIST.clear();

        List<? extends String> configuredItems = MELEE_WEAPON_LIST.get();
        if (configuredItems == null) {
            return;
        }

        for (String configuredId : configuredItems) {
            ResourceLocation id = ResourceLocation.tryParse(configuredId);
            if (id != null) {
                ITEM_BLACKLIST.add(id.toString());
            }
        }
    }

    public static boolean isItemInBlacklist(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        CompoundTag tag = ItemStackData.getCustomDataCopy(itemStack);
        if (tag == null) {
            return false;
        }

        String gunId = tag.getString("GunId");
        return !gunId.isEmpty() && ITEM_BLACKLIST.contains(gunId);
    }

    public static boolean enableShootWhileReloading() {
        return SHOOT_WHILE_RELOADING.get();
    }

    public static boolean enableBetterAimCamera() {
        return BETTER_AIM_CAMERA.get();
    }

    public static boolean enableGunSmithTableCraftToast() {
        return GUNSMITHTABLE_CRAFT_TOAST.get();
    }

    public static int getAllowGunAmount() {
        return LESS_ALLOW_GUN.get();
    }

    public static boolean showItemRelationInSophisticatedStorage() {
        return SHOW_ITEM_RELATION.get() && SHOW_ITEM_RELATION_IN_SOPHISTICATED_STORAGE.get();
    }
}

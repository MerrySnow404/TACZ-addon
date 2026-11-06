package com.mafuyu404.taczaddon.client.gunsmith;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Stateless catalog and matching logic; each screen owns its translated data snapshot. */
public final class GunSmithPropertyFilter {
    private static final ResourceLocation ATTACHMENT_WORKBENCH =
            new ResourceLocation(
                    "tacz",
                    "attachment_workbench"
            );

    private GunSmithPropertyFilter() {}

    public static boolean supportsWorkbench(
            @Nullable ResourceLocation tableId
    ) {
        return ATTACHMENT_WORKBENCH.equals(tableId);
    }

    public static List<String> catalogKeys(Iterable<String> modifierIds, Predicate<String> hasTranslation) {
        List<String> keys = new ArrayList<>();
        keys.add("gui.taczaddon.gun_smith_table.default_prop");
        for (String id : modifierIds) {
            if (id.equals("ignite")) {
                keys.add("tooltip.tacz.attachment.ignite.block");
                keys.add("tooltip.tacz.attachment.ignite.entity");
            } else if (!id.equals("weight_modifier") && !id.equals("recoil")) {
                String base = "tooltip.tacz.attachment." + id;
                keys.add(hasTranslation.test(base + ".increase") ? base + ".increase" : base);
            }
        }
        return List.copyOf(keys);
    }

    public static boolean matches(ResourceLocation recipeId, String selectedProperty,
                                  Map<ResourceLocation, String> data,
                                  Function<ResourceLocation, GunSmithTableRecipe> resolver) {
        if (selectedProperty == null || recipeId == null) return true;
        try {
            GunSmithTableRecipe recipe = resolver.apply(recipeId);
            if (recipe == null) return true;
            ItemStack output = recipe.getOutput();
            if (!(output.getItem() instanceof IAttachment attachment)) return true;
            String text = data.get(attachment.getAttachmentId(output));
            return text != null && text.contains(selectedProperty);
        } catch (RuntimeException malformedRecipe) {
            // A broken recipe must not break browsing or discard unrelated categories.
            return true;
        }
    }
}

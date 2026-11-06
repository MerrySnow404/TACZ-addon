package com.mafuyu404.taczaddon.gunsmith.client;

import com.mafuyu404.taczaddon.config.ClientSyncedConfig;
import com.mafuyu404.taczaddon.inventory.VirtualInventory;
import com.mafuyu404.taczaddon.inventory.client.VirtualContainerLoader;
import com.mafuyu404.taczaddon.inventory.network.ContainerPositionPacket;
import com.mafuyu404.taczaddon.network.NetworkHandler;
import com.mafuyu404.taczaddon.presentation.client.DropDown;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import it.unimi.dsi.fastutil.Pair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;

/** Per-screen browse, filter and interaction state. */
public final class GunSmithScreenController {
    public interface Host {
        RecipeHolder<GunSmithTableRecipe> taczaddon$getSelectedRecipe();

        void taczaddon$setSelectedRecipe(RecipeHolder<GunSmithTableRecipe> value);

        List<ResourceLocation> taczaddon$getSelectedRecipeList();

        void taczaddon$setSelectedRecipeList(List<ResourceLocation> value);

        int taczaddon$getIndexPage();

        void taczaddon$setIndexPage(int value);

        ResourceLocation taczaddon$getSelectedType();

        void taczaddon$setSelectedType(ResourceLocation value);

        Map<ResourceLocation, List<ResourceLocation>> taczaddon$getRecipes();

        LinkedHashMap<ResourceLocation, TabConfig> taczaddon$getRecipeKeys();

        int taczaddon$getTypePage();

        void taczaddon$setTypePage(int value);

        GunSmithTableMenu taczaddon$getMenu();

        int taczaddon$getLeftPos();

        int taczaddon$getTopPos();

        void taczaddon$addWidget(net.minecraft.client.gui.components.AbstractWidget widget);

        RecipeHolder<GunSmithTableRecipe> taczaddon$getSelectedRecipe(ResourceLocation id);

        void taczaddon$getPlayerIngredientCount(RecipeHolder<GunSmithTableRecipe> recipe);

        void taczaddon$updateIngredientCount();
    }

    private final Host host;

    public GunSmithScreenController(Host host) {
        this.host = host;
    }

    private boolean containerSnapshotRequested;
    private BlockPos lastRequestedContainerPos;
    private ArrayList<String> attachmentProp;
    private int selectedAttachmentPropIndex;
    private DropDown dropdown;
    private boolean browseStateRestored;
    private int mouseX;
    private int mouseY;
    private HashMap<String, Boolean> hover;

    private void requestNearbyContainerSnapshotIfNeeded() {
        if (!ClientSyncedConfig.enableNearbyContainerSources()) {
            return;
        }

        Optional<BlockPos> maybeBlockPos = GunSmithClientState.getLastGunSmithInteractPos();
        if (maybeBlockPos.isEmpty()) {
            return;
        }
        BlockPos blockPos = maybeBlockPos.get();

        if (!blockPos.equals(this.lastRequestedContainerPos)) {
            this.lastRequestedContainerPos = blockPos;
            this.containerSnapshotRequested = false;
        }

        if (this.containerSnapshotRequested) {
            return;
        }

        this.containerSnapshotRequested = true;
        NetworkHandler.sendToServer(new ContainerPositionPacket(blockPos));
    }

    public void initFields() {
        // Called once from the target screen constructor after its fields are available.
        this.attachmentProp = new ArrayList<>();
        this.selectedAttachmentPropIndex = 0;
        this.dropdown = null;
        this.browseStateRestored = false;
        this.containerSnapshotRequested = false;
        this.lastRequestedContainerPos = null;
        this.hover = new HashMap<>();
        this.mouseX = 0;
        this.mouseY = 0;
        this.loadAttachmentProperties();
    }

    public boolean filterAttachmentProperty(
            List<Pair<ResourceLocation, ResourceLocation>> list, Object e) {
        if (!(e instanceof Pair<?, ?> rawPair)
                || !(rawPair.left() instanceof ResourceLocation group)
                || !(rawPair.right() instanceof ResourceLocation recipeId)) {
            return false;
        }

        Pair<ResourceLocation, ResourceLocation> pair = Pair.of(group, recipeId);

        boolean apply = true;
        String id = recipeId.toString();

        if (id.contains("/")) {
            ResourceLocation itemId =
                    ResourceLocation.tryParse(id.split(":")[0] + ":" + id.split("/")[1]);

            if (itemId == null) {
                apply = false;
            } else if (selectedAttachmentPropIndex > 0
                    && selectedAttachmentPropIndex < attachmentProp.size()) {
                String propKey = attachmentProp.get(selectedAttachmentPropIndex);
                Component selectedOption = Component.translatable(propKey);
                Map<String, String> attachmentData = GunSmithClientState.getAttachmentData();
                String text = attachmentData.get(itemId.toString());
                if (text == null) {
                    apply = false;
                } else if (!text.contains(selectedOption.getString())) {
                    apply = false;
                }
            }
        }

        return apply && list.add(pair);
    }

    public void restoreAttachmentFilterBeforeClassify() {
        if (this.browseStateRestored) {
            return;
        }

        ResourceLocation tableId = host.taczaddon$getMenu().getBlockId();

        if (tableId == null) {
            return;
        }

        GunSmithBrowseState.getBrowseState(tableId)
                .ifPresent(
                        state -> {
                            int maxIndex = Math.max(0, this.attachmentProp.size() - 1);

                            this.selectedAttachmentPropIndex =
                                    this.clamp(state.attachmentPropIndex(), 0, maxIndex);
                        });
    }

    public void restoreBrowseStateAfterClassify() {
        if (this.browseStateRestored) {
            return;
        }

        this.browseStateRestored = true;

        ResourceLocation tableId = host.taczaddon$getMenu().getBlockId();

        if (tableId == null) {
            return;
        }

        GunSmithBrowseState.getBrowseState(tableId).ifPresent(this::applyBrowseState);
    }

    public void applyBrowseState(GunSmithBrowseState.BrowseState state) {
        int maxPropIndex = Math.max(0, this.attachmentProp.size() - 1);
        this.selectedAttachmentPropIndex = this.clamp(state.attachmentPropIndex(), 0, maxPropIndex);

        ResourceLocation savedType = state.selectedType();

        if (savedType != null && host.taczaddon$getRecipes().containsKey(savedType)) {
            host.taczaddon$setSelectedType(savedType);
            host.taczaddon$setSelectedRecipeList(host.taczaddon$getRecipes().get(savedType));
        }

        host.taczaddon$setTypePage(
                this.typePageFor(host.taczaddon$getSelectedType(), state.typePage()));

        if (host.taczaddon$getSelectedRecipeList() == null
                || host.taczaddon$getSelectedRecipeList().isEmpty()) {
            host.taczaddon$setIndexPage(0);
            host.taczaddon$setSelectedRecipe(null);
            return;
        }

        int maxRecipePage = (host.taczaddon$getSelectedRecipeList().size() - 1) / 6;

        ResourceLocation savedRecipeId = state.selectedRecipeId();
        int recipeIndex =
                savedRecipeId == null
                        ? -1
                        : host.taczaddon$getSelectedRecipeList().indexOf(savedRecipeId);

        if (recipeIndex >= 0) {
            host.taczaddon$setIndexPage(recipeIndex / 6);
            host.taczaddon$setSelectedRecipe(host.taczaddon$getSelectedRecipe(savedRecipeId));
        } else {
            host.taczaddon$setIndexPage(this.clamp(state.indexPage(), 0, maxRecipePage));

            int fallbackIndex =
                    Math.min(
                            host.taczaddon$getIndexPage() * 6,
                            host.taczaddon$getSelectedRecipeList().size() - 1);
            host.taczaddon$setSelectedRecipe(
                    host.taczaddon$getSelectedRecipe(
                            host.taczaddon$getSelectedRecipeList().get(fallbackIndex)));
        }

        if (host.taczaddon$getSelectedRecipe() == null) {
            host.taczaddon$setIndexPage(0);
            host.taczaddon$setSelectedRecipe(
                    host.taczaddon$getSelectedRecipe(
                            host.taczaddon$getSelectedRecipeList().get(0)));
        }

        if (host.taczaddon$getSelectedRecipe() != null) {
            host.taczaddon$getPlayerIngredientCount(host.taczaddon$getSelectedRecipe());
        }
    }

    private int typePageFor(ResourceLocation selectedType, int fallbackTypePage) {
        int maxTypePage =
                host.taczaddon$getRecipeKeys().isEmpty()
                        ? 0
                        : (host.taczaddon$getRecipeKeys().size() - 1) / 7;

        if (selectedType == null) {
            return this.clamp(fallbackTypePage, 0, maxTypePage);
        }

        int index = 0;
        for (ResourceLocation type : host.taczaddon$getRecipeKeys().keySet()) {
            if (selectedType.equals(type)) {
                return index / 7;
            }
            index++;
        }

        return this.clamp(fallbackTypePage, 0, maxTypePage);
    }

    private ResourceLocation getSelectedRecipeId() {
        return host.taczaddon$getSelectedRecipe() == null
                ? null
                : host.taczaddon$getSelectedRecipe().id();
    }

    private void saveBrowseState() {
        ResourceLocation tableId = host.taczaddon$getMenu().getBlockId();
        if (tableId == null) {
            return;
        }

        ResourceLocation selectedRecipeId = this.getSelectedRecipeId();

        int propIndex =
                this.dropdown == null
                        ? this.selectedAttachmentPropIndex
                        : this.dropdown.getSelected();

        GunSmithBrowseState.saveBrowseState(
                tableId,
                host.taczaddon$getSelectedType(),
                selectedRecipeId,
                host.taczaddon$getTypePage(),
                host.taczaddon$getIndexPage(),
                propIndex);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public void onScreenChanged() {
        this.requestNearbyContainerSnapshotIfNeeded();

        if (dropdown != null) {
            selectedAttachmentPropIndex = dropdown.getSelected();
        }

        dropdown =
                new DropDown(host.taczaddon$getLeftPos() - 64, host.taczaddon$getTopPos() - 20, 64);

        for (String prop : attachmentProp) {
            String text = Component.translatable(prop).getString().replace("+ ", "");
            dropdown.addOption(Component.literal(text));
        }

        int maxIndex = Math.max(0, attachmentProp.size() - 1);
        selectedAttachmentPropIndex = clamp(selectedAttachmentPropIndex, 0, maxIndex);

        dropdown.setSelected(selectedAttachmentPropIndex);
        dropdown.action = this::refreshRecipes;

        host.taczaddon$addWidget(dropdown);

        this.saveBrowseState();
    }

    private void refreshRecipes(int index) {
        selectedAttachmentPropIndex = index;
        this.saveBrowseState();
        host.taczaddon$updateIngredientCount();
    }

    private void loadAttachmentProperties() {
        HashMap<String, String> StoredAttachmentData = new HashMap<>();
        TimelessAPI.getAllClientAttachmentIndex()
                .forEach(
                        entry -> {
                            StringBuilder data = new StringBuilder();
                            entry.getValue()
                                    .getData()
                                    .getModifier()
                                    .forEach(
                                            (s, jsonProperty) -> {
                                                jsonProperty
                                                        .getComponents()
                                                        .forEach(
                                                                component ->
                                                                        data.append(
                                                                                component
                                                                                        .getString()));
                                            });
                            StoredAttachmentData.put(entry.getKey().toString(), data.toString());
                        });
        if (GunSmithClientState.getAttachmentData().isEmpty()) {
            GunSmithClientState.setAttachmentData(StoredAttachmentData);
        }

        attachmentProp.add("gui.taczaddon.gun_smith_table.default_prop");
        AttachmentPropertyManager.getModifiers()
                .forEach(
                        (s, iAttachmentModifier) -> {
                            String prop =
                                    Component.translatable(
                                                    "tooltip.tacz.attachment." + s + ".increase")
                                            .getString();
                            if (s.equals("ignite")) {
                                attachmentProp.add("tooltip.tacz.attachment.ignite.block");
                                attachmentProp.add("tooltip.tacz.attachment.ignite.entity");
                                return;
                            }
                            if (s.equals("weight_modifier") || s.equals("recoil")) return;
                            if (prop.contains("tooltip"))
                                attachmentProp.add("tooltip.tacz.attachment." + s);
                            else attachmentProp.add("tooltip.tacz.attachment." + s + ".increase");
                        });
    }

    public Component renderPageInfo() {
        if (host.taczaddon$getSelectedType() == null
                || host.taczaddon$getSelectedRecipeList() == null
                || host.taczaddon$getSelectedRecipeList().isEmpty()) {
            return Component.literal(
                    String.format(
                            Component.translatable("gui.taczaddon.gun_smith_table.page_index")
                                    .getString(),
                            "-",
                            0,
                            0));
        }

        String type =
                Component.translatable(
                                String.format(
                                        "tacz.type.%s.name",
                                        host.taczaddon$getSelectedType().getPath()))
                        .getString();
        int maxPage =
                Math.max(
                        1,
                        (int)
                                Math.ceil(
                                        (double) host.taczaddon$getSelectedRecipeList().size()
                                                / 6.0D));
        String title =
                String.format(
                        Component.translatable("gui.taczaddon.gun_smith_table.page_index")
                                .getString(),
                        type,
                        host.taczaddon$getIndexPage() + 1,
                        maxPage);
        return Component.literal(title);
    }

    public boolean guardMouseScrolledWhenNoRecipes(
            double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean insideRecipeList =
                mouseX > (double) (host.taczaddon$getLeftPos() + 143)
                        && mouseX < (double) (host.taczaddon$getLeftPos() + 143 + 94)
                        && mouseY > (double) (host.taczaddon$getTopPos() + 66)
                        && mouseY < (double) (host.taczaddon$getTopPos() + 66 + 85);

        if (!insideRecipeList) {
            return false;
        }

        if (host.taczaddon$getSelectedRecipeList() == null
                || host.taczaddon$getSelectedRecipeList().isEmpty()) {
            host.taczaddon$setIndexPage(0);
            host.taczaddon$setSelectedRecipe(null);
            return true;
        }

        return false;
    }

    public Inventory modifyIngredientShow(Inventory inventory) {
        if (!ClientSyncedConfig.enableNearbyContainerSources()) {
            return inventory;
        }

        Object self = host;
        if (!(self instanceof VirtualContainerLoader loader)) {
            return inventory;
        }

        ArrayList<ItemStack> items = loader.taczaddon$getVirtualContainer();
        if (items == null || items.isEmpty()) {
            return inventory;
        }

        VirtualInventory virtualInventory =
                new VirtualInventory(inventory.getContainerSize() + items.size(), inventory.player);
        virtualInventory.extend();
        for (int i = 0; i < items.size(); i++) {
            virtualInventory.setItem(virtualInventory.getPlayerInventorySize() + i, items.get(i));
        }
        return virtualInventory;
    }

    public void storeMousePosition(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public void renderBetterIngredient(GuiGraphics graphics, ItemStack itemStack, int x, int y) {
        graphics.renderItem(itemStack, x, y);

        String hoverKey = x + ":" + y;

        if (isHovering(x, y)) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font, itemStack, this.mouseX, this.mouseY);

            GunSmithClientState.setGunSmithJeiStack(itemStack);
            this.hover.put(hoverKey, true);
        } else {
            this.hover.put(hoverKey, false);

            boolean anyHovering = this.hover.values().stream().anyMatch(Boolean::booleanValue);

            if (!anyHovering) {
                GunSmithClientState.setGunSmithJeiStack(ItemStack.EMPTY);
            }
        }
    }

    private boolean isHovering(int x, int y) {
        int mouseX = this.mouseX;
        int mouseY = this.mouseY;

        return mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
    }

    public Button.OnPress craftButtonAction() {
        return button -> {
            if (host.taczaddon$getSelectedRecipe() == null) return;
            GunSmithCraftBridgeState.request(
                    host.taczaddon$getMenu().containerId,
                    host.taczaddon$getSelectedRecipe().id(),
                    GunSmithCraftBridgeState.requestedCount(Screen.hasShiftDown()));
        };
    }
}

package com.mafuyu404.taczaddon.gunsmith;

import com.mafuyu404.taczaddon.config.CommonConfig;
import com.mafuyu404.taczaddon.storage.NearbyInventorySourceResolver;
import com.mafuyu404.taczaddon.storage.PlayerInventorySource;
import com.mafuyu404.taczaddon.storage.api.CraftingItemSource;
import com.mafuyu404.taczaddon.storage.api.CraftingSourceKey;
import com.mafuyu404.taczaddon.storage.api.WorkbenchAnchor;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;

public final class GunSmithCraftingSources {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MAX_EXTERNAL_STACKS = 256;

    private GunSmithCraftingSources() {
    }

    public static ResolvedSources resolve(
            ServerPlayer player,
            GunSmithCraftingSession session
    ) {
        return resolve(
                player,
                new WorkbenchAnchor(
                        session.getDimension(),
                        session.getTablePos()
                ),
                session
        );
    }

    public static ResolvedSources resolve(
            ServerPlayer player,
            WorkbenchAnchor anchor
    ) {
        return resolve(player, anchor, null);
    }

    private static ResolvedSources resolve(
            ServerPlayer player,
            WorkbenchAnchor anchor,
            @javax.annotation.Nullable
            GunSmithCraftingSession session
    ) {
        ArrayList<CraftingItemSource> sources = new ArrayList<>();
        ArrayList<ItemStack> externalStacks = new ArrayList<>();
        LinkedHashSet<CraftingSourceKey> sourceKeys =
                new LinkedHashSet<>();
        Set<Object> backendIdentities =
                Collections.newSetFromMap(new IdentityHashMap<>());
        boolean[] displayTruncated = {false};

        PlayerInventorySource playerSource =
                new PlayerInventorySource(player);
        sources.add(playerSource);
        sourceKeys.add(playerSource.key());
        backendIdentities.add(playerSource.backendIdentity());

        if (CommonConfig.enableContainerReader()) {
            List<CraftingItemSource> nearby =
                    NearbyInventorySourceResolver.resolve(
                            player,
                            anchor.pos(),
                            CommonConfig.getContainerScanRadius(),
                            1
            );

            for (CraftingItemSource source : nearby) {
                try {
                    List<ItemStack> sourceDisplayStacks =
                            NearbyInventorySourceResolver
                                    .readAllDisplayStacks(source);
                    addUniqueSource(
                            sources,
                            externalStacks,
                            sourceKeys,
                            backendIdentities,
                            source,
                            sourceDisplayStacks,
                            MAX_EXTERNAL_STACKS,
                            displayTruncated
                    );
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.warn(
                            "Skipping unreadable or binary-incompatible "
                                    + "gunsmith source",
                            failure
                    );
                }
            }
        }

        List<CraftingSourceKey> immutableKeys =
                List.copyOf(sourceKeys);
        if (session != null) {
            session.updateSourceKeys(immutableKeys);
        }

        return new ResolvedSources(
                Collections.unmodifiableList(
                        new ArrayList<>(sources)
                ),
                Collections.unmodifiableList(
                        copyStacks(externalStacks)
                ),
                Collections.unmodifiableList(
                        new ArrayList<>(immutableKeys)
                ),
                displayTruncated[0]
        );
    }

    static boolean addUniqueSource(
            List<CraftingItemSource> sources,
            List<ItemStack> externalStacks,
            Set<CraftingSourceKey> sourceKeys,
            Set<Object> backendIdentities,
            CraftingItemSource source,
            List<ItemStack> displayStacks,
            int maxExternalStacks
    ) {
        return addUniqueSource(
                sources,
                externalStacks,
                sourceKeys,
                backendIdentities,
                source,
                displayStacks,
                maxExternalStacks,
                new boolean[1]
        );
    }

    /**
     * Registers one deduplicated external source.
     *
     * <p>The display budget only ever limits how many entries are sent to the
     * client. It must never decide whether the source itself is usable: a
     * player with more than {@link #MAX_EXTERNAL_STACKS} entries would
     * otherwise lose materials they can legitimately craft with.
     */
    static boolean addUniqueSource(
            List<CraftingItemSource> sources,
            List<ItemStack> externalStacks,
            Set<CraftingSourceKey> sourceKeys,
            Set<Object> backendIdentities,
            CraftingItemSource source,
            List<ItemStack> displayStacks,
            int maxExternalStacks,
            boolean[] displayTruncated
    ) {
        if (sourceKeys.contains(source.key())
                || backendIdentities.contains(
                source.backendIdentity()
        )) {
            return false;
        }

        sourceKeys.add(source.key());
        backendIdentities.add(source.backendIdentity());
        sources.add(source);

        int budget = Math.max(
                0,
                maxExternalStacks - externalStacks.size()
        );
        int displayed = Math.min(budget, displayStacks.size());
        for (int index = 0; index < displayed; index++) {
            externalStacks.add(displayStacks.get(index));
        }
        if (displayed < displayStacks.size()) {
            displayTruncated[0] = true;
        }
        return true;
    }

    /**
     * Aggregated per-ingredient material counts across every resolved source.
     *
     * <p>This is independent of the display budget: a truncated display list
     * still yields complete counts, so a missing page can never look like a
     * missing material.
     */
    public static int[] countIngredients(
            com.tacz.guns.crafting.GunSmithTableRecipe recipe,
            List<CraftingItemSource> sources
    ) {
        List<com.tacz.guns.crafting.GunSmithTableIngredient> inputs =
                recipe.getInputs();
        int[] counts = new int[inputs.size()];

        for (CraftingItemSource source : sources) {
            int slots;
            try {
                slots = source.slotCount();
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Skipping unreadable gunsmith source {} while "
                                + "counting ingredients",
                        source.key(),
                        exception
                );
                continue;
            }

            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack;
                try {
                    stack = source.getStackInSlot(slot);
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "Skipping unreadable gunsmith slot {} of {}",
                            slot,
                            source.key(),
                            exception
                    );
                    continue;
                }
                if (stack.isEmpty()) {
                    continue;
                }
                for (int index = 0; index < inputs.size(); index++) {
                    if (inputs.get(index).getIngredient().test(stack)) {
                        counts[index] += stack.getCount();
                    }
                }
            }
        }
        return counts;
    }

    private static ArrayList<ItemStack> copyStacks(
            List<ItemStack> stacks
    ) {
        ArrayList<ItemStack> copies =
                new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copies.add(stack.copy());
        }
        return copies;
    }

    public record ResolvedSources(
            List<CraftingItemSource> sources,
            List<ItemStack> externalStacks,
            List<CraftingSourceKey> sourceKeys,
            boolean displayTruncated
    ) {
    }
}

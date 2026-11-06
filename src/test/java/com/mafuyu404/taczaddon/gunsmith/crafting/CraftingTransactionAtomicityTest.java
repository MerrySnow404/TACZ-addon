package com.mafuyu404.taczaddon.gunsmith.crafting;

import static org.junit.jupiter.api.Assertions.*;

import com.mafuyu404.taczaddon.gunsmith.GunSmithCraftingSessionManager;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

class CraftingTransactionAtomicityTest {
    private static final class Source implements CraftingItemSource {
        private final ItemStackHandler handler;
        private final CraftingSourceKey key =
                new CraftingSourceKey.PlayerInventory(UUID.randomUUID());
        boolean failCommit;

        Source(ItemStack... stacks) {
            handler = new ItemStackHandler(stacks.length);
            for (int i = 0; i < stacks.length; i++) handler.setStackInSlot(i, stacks[i]);
        }

        public CraftingSourceKey key() {
            return key;
        }

        public int slotCount() {
            return handler.getSlots();
        }

        public ItemStack getStackInSlot(int slot) {
            return handler.getStackInSlot(slot);
        }

        public ItemStack extractItem(int slot, int count, boolean simulate) {
            ItemStack extracted = handler.extractItem(slot, count, simulate);
            if (!simulate && failCommit) throw new IllegalStateException("mutated then failed");
            return extracted;
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return handler.insertItem(slot, stack, simulate);
        }

        public void restoreSlot(int slot, ItemStack stack) {
            handler.setStackInSlot(slot, stack.copy());
        }

        public boolean isValid(ServerPlayer player) {
            return true;
        }

        public void markChanged() {}

        public void synchronize(ServerPlayer player) {}
    }

    private static CraftingTransaction transaction(
            List<CraftingItemSource> sources, GunSmithTableIngredient... ingredients)
            throws Exception {
        Constructor<CraftingTransaction> ctor =
                CraftingTransaction.class.getDeclaredConstructor(
                        ServerPlayer.class,
                        GunSmithCraftingSessionManager.GunSmithCraftingSession.class,
                        GunSmithTableRecipe.class,
                        List.class);
        ctor.setAccessible(true);
        return ctor.newInstance(
                null,
                null,
                new GunSmithTableRecipe((GunSmithTableResult) null, List.of(ingredients)),
                sources);
    }

    private static Object invoke(CraftingTransaction transaction, String name) throws Exception {
        Method method = CraftingTransaction.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(transaction);
    }

    private static GunSmithTableIngredient iron(int count) {
        return new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), count);
    }

    @Test
    void overlappingPredicatesRerouteBroadChoiceWithoutDoubleCounting() throws Exception {
        Source source =
                new Source(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
        var transaction =
                transaction(
                        List.of(source),
                        new GunSmithTableIngredient(
                                Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1),
                        iron(1));
        assertEquals(true, invoke(transaction, "plan"));
        assertEquals(true, invoke(transaction, "simulate"));
        assertEquals(true, invoke(transaction, "commit"));
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertTrue(source.getStackInSlot(1).isEmpty());
    }

    @Test
    void insufficientMaterialsLeaveEverySourceUntouched() throws Exception {
        Source player = new Source(new ItemStack(Items.IRON_INGOT, 3));
        Source nearby = new Source(new ItemStack(Items.IRON_INGOT, 2));
        var transaction = transaction(List.of(player, nearby), iron(3), iron(3));
        assertEquals(false, invoke(transaction, "plan"));
        assertEquals(3, player.getStackInSlot(0).getCount());
        assertEquals(2, nearby.getStackInSlot(0).getCount());
    }

    @Test
    void mixedSourcesConsumeExactlyOnce() throws Exception {
        Source player = new Source(new ItemStack(Items.IRON_INGOT, 3));
        Source nearby = new Source(new ItemStack(Items.IRON_INGOT, 4));
        var transaction = transaction(List.of(player, nearby), iron(5));
        assertEquals(true, invoke(transaction, "plan"));
        assertEquals(true, invoke(transaction, "simulate"));
        assertEquals(true, invoke(transaction, "commit"));
        assertTrue(player.getStackInSlot(0).isEmpty());
        assertEquals(2, nearby.getStackInSlot(0).getCount());
    }

    @Test
    void secondSourceMutationThenThrowRestoresBothSources() throws Exception {
        Source first = new Source(new ItemStack(Items.IRON_INGOT, 3));
        Source second = new Source(new ItemStack(Items.IRON_INGOT, 4));
        second.failCommit = true;
        var transaction = transaction(List.of(first, second), iron(5));
        assertEquals(true, invoke(transaction, "plan"));
        assertEquals(true, invoke(transaction, "simulate"));
        assertThrows(InvocationTargetException.class, () -> invoke(transaction, "commit"));
        assertEquals(
                CraftingTransaction.RollbackResult.FULLY_RESTORED,
                invoke(transaction, "rollbackSafely"));
        assertEquals(3, first.getStackInSlot(0).getCount());
        assertEquals(4, second.getStackInSlot(0).getCount());
    }

    @Test
    void batchStopsAtSeventeenCommittedExecutions() throws Exception {
        Source source = new Source(new ItemStack(Items.IRON_INGOT, 35));
        int committed = 0;
        for (int i = 0; i < 64; i++) {
            var transaction = transaction(List.of(source), iron(2));
            if (!(boolean) invoke(transaction, "plan")) break;
            assertEquals(true, invoke(transaction, "simulate"));
            assertEquals(true, invoke(transaction, "commit"));
            committed++;
        }
        assertEquals(17, committed);
        assertEquals(1, source.getStackInSlot(0).getCount());
    }
}

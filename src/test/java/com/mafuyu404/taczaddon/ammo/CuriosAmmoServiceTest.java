package com.mafuyu404.taczaddon.ammo;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import com.mafuyu404.taczaddon.compat.curios.CuriosCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.mafuyu404.taczaddon.ammo.BackpackAmmoServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class CuriosAmmoServiceTest {
    @BeforeAll static void prepare() throws Exception { BackpackAmmoServiceTest.bootstrap(); }
    private ItemStack gun() { return new ItemStack(Items.STONE); }

    @Test void matchingAndUnrelatedAmmoQueries() {
        assertTrue(BackpackAmmoService.containsCompatibleAmmo(new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 8)), gun()));
        assertFalse(BackpackAmmoService.containsCompatibleAmmo(new FakeHandler(new ItemStack(INCOMPATIBLE_AMMO, 8)), gun()));
    }

    @Test void exactOrdinaryConsumption() {
        var handler = new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 20));
        assertEquals(8, CuriosAmmoService.consumeHandler(handler, gun(), 8));
        assertEquals(12, handler.getStackInSlot(0).getCount());
    }

    @Test void unrelatedAmmoAndEmptyRequestsLeaveInventoryUntouched() {
        var handler = new FakeHandler(new ItemStack(INCOMPATIBLE_AMMO, 20));
        assertEquals(0, CuriosAmmoService.consumeHandler(handler, gun(), 8));
        assertEquals(20, handler.getStackInSlot(0).getCount());
        assertEquals(0, CuriosAmmoService.consumeHandler(handler, gun(), -1));
    }

    @Test void partialAmountContinuesToNextHandler() {
        var first = new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 3));
        var second = new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 10));
        int consumed = CuriosAmmoService.consumeHandler(first, gun(), 8);
        assertEquals(3, consumed);
        consumed += CuriosAmmoService.consumeHandler(second, gun(), 8 - consumed);
        assertEquals(8, consumed);
        assertEquals(5, second.getStackInSlot(0).getCount());
    }

    @Test void nativeBeyondSophisticatedThenCuriosConsumeOnlyTheRemainder() {
        var sophisticated = new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 4));
        var curios = new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 20));
        var outcome = AmmoConsumptionOrchestrator.consumeRemaining(15, 2,
                remaining -> { assertEquals(13, remaining); return ConsumptionOutcome.confirmed(3); },
                remaining -> {
                    assertEquals(10, remaining);
                    return BackpackAmmoService.consumeThroughHandlers(
                            remaining,
                            gun(),
                            visitor -> visitor.test(sophisticated));
                },
                remaining -> {
                    assertEquals(6, remaining);
                    return CuriosAmmoService.consumeHandlerOutcome(curios, gun(), remaining);
                });
        assertEquals(15, outcome.consumed());
        assertEquals(14, curios.getStackInSlot(0).getCount());
    }

    @Test void ammoBoxPersistsEvenWhenHandlerReturnsCopies() {
        ItemStack box = new ItemStack(AMMO_BOX);
        AMMO_BOX.setAmmoCount(box, 20);
        AMMO_BOX.setAmmoId(box, com.tacz.guns.api.DefaultAssets.DEFAULT_AMMO_ID);
        var handler = new CopyingHandler(box);
        assertEquals(8, CuriosAmmoService.consumeHandler(handler, gun(), 8));
        assertEquals(12, AMMO_BOX.getAmmoCount(handler.getStackInSlot(0)));
        assertEquals(20, AMMO_BOX.getAmmoCount(box));
        assertEquals(12, CuriosAmmoService.consumeHandler(handler, gun(), 30));
        assertEquals(0, AMMO_BOX.getAmmoCount(handler.getStackInSlot(0)));
        assertEquals(com.tacz.guns.api.DefaultAssets.EMPTY_AMMO_ID, AMMO_BOX.getAmmoId(handler.getStackInSlot(0)));
    }

    @Test void genericHandlerDeclinesBoxMutationWithoutExtractingOrInserting() {
        ItemStack box = new ItemStack(AMMO_BOX);
        AMMO_BOX.setAmmoCount(box, 20);
        var handler = new FakeHandler(box) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { fail("must not extract box"); return ItemStack.EMPTY; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { fail("must not insert box"); return stack; }
        };
        assertEquals(0, CuriosAmmoService.consumeHandler(handler, gun(), 8));
        assertEquals(20, AMMO_BOX.getAmmoCount(box));
    }

    @Test void queryCompositeIncludesNativeHandlerExactlyOnce() {
        var nativeHandler = new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 17));
        var query = BackpackAmmoService.createQueryHandler(null, nativeHandler);
        int total = 0;
        for (int slot = 0; slot < query.getSlots(); slot++) total += query.getStackInSlot(slot).getCount();
        assertEquals(17, total);
        assertEquals(17, nativeHandler.getStackInSlot(0).getCount());
    }

    @Test void absentCuriosIsNeutralAndOtherSourcesStillWork() {
        assertFalse(CuriosCompat.isInstalled());
        assertFalse(CuriosCompat.visitHandlers(null, handler -> { fail("absent Curios visited"); return true; }));
        assertEquals(0, CuriosAmmoService.consumeAmmo(null, gun(), 8).consumed());
        assertEquals(0, BackpackAmmoService.consumeBackpackAmmo(null, gun(), 8).consumed());
        assertEquals(8, BackpackAmmoService.extractCompatibleAmmoDirectly(
                new FakeHandler(new ItemStack(COMPATIBLE_AMMO, 10)), gun(), 8));
    }

    private static final class CopyingHandler extends FakeHandler implements IItemHandlerModifiable {
        private ItemStack stored;
        CopyingHandler(ItemStack stack) { super(stack); stored = stack.copy(); }
        @Override public ItemStack getStackInSlot(int slot) { return stored.copy(); }
        @Override public void setStackInSlot(int slot, ItemStack stack) { stored = stack.copy(); }
    }
}

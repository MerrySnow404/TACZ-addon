package com.mafuyu404.taczaddon.ammo;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionStatus;

import com.mafuyu404.taczaddon.ammo.AmmoConsumptionOrchestrator;
import com.mafuyu404.taczaddon.ammo.BackpackAmmoService;
import com.mafuyu404.taczaddon.ammo.CuriosAmmoService;

import com.mafuyu404.taczaddon.ammo.api.ConsumptionOutcome;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.mafuyu404.taczaddon.ammo.BackpackAmmoServiceTest.COMPATIBLE_AMMO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end behaviour of the real supplemental ammo orchestration.
 *
 * Only the external item sources are faked; the consumption loop, the
 * accounting and the orchestration are the production implementations.
 */
class SupplementalAmmoConsumptionTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        BackpackAmmoServiceTest.bootstrap();
    }

    private static ItemStack gun() {
        return new ItemStack(Items.STONE);
    }

    /**
     * Requests ten rounds; the backpack extracts four and then the next slot
     * throws {@link NoSuchMethodError}.
     *
     * <p>The four completed rounds are confirmed, but the failing handler call
     * may itself have committed something before losing linkage. The request
     * therefore stops as {@link ConsumptionStatus#STOPPED_UNKNOWN}: Curios must never be
     * asked for ten or six rounds and the gun must not gain ammo out of
     * nowhere.
     */
    @Test
    void linkageFailureMidMutationKeepsFourAndNeverAsksCurios() {
        BackpackAmmoServiceTest.FakeHandler first =
                new BackpackAmmoServiceTest.FakeHandler(
                        new ItemStack(COMPATIBLE_AMMO, 4)
                );
        IItemHandler failing = new LinkageFailingHandler();
        List<Integer> curiosRequests = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        0,
                        remaining -> ConsumptionOutcome.confirmed(0),
                        remaining -> BackpackAmmoService
                                .consumeThroughHandlers(
                                        remaining,
                                        gun(),
                                        visitor -> {
                                            if (!visitor.test(first)) {
                                                visitor.test(failing);
                                            }
                                        }
                                ),
                        remaining -> {
                            curiosRequests.add(remaining);
                            return ConsumptionOutcome.confirmed(0);
                        }
                );

        assertEquals(4, outcome.consumed());
        assertEquals(
                ConsumptionStatus.STOPPED_UNKNOWN,
                outcome.status(),
                "a handler that lost linkage mid-mutation is unknown, not a fully known amount"
        );
        assertEquals(
                0,
                first.getStackInSlot(0).getCount(),
                "the four confirmed rounds are consumed"
        );
        assertTrue(
                curiosRequests.isEmpty(),
                "Curios must not receive 10 or 6 extra rounds: "
                        + curiosRequests
        );
    }

    @Test
    void normalMultiSourceConsumptionAddsUpWithoutOverConsuming() {
        BackpackAmmoServiceTest.FakeHandler backpack =
                new BackpackAmmoServiceTest.FakeHandler(
                        new ItemStack(COMPATIBLE_AMMO, 4)
                );
        BackpackAmmoServiceTest.FakeHandler curios =
                new BackpackAmmoServiceTest.FakeHandler(
                        new ItemStack(COMPATIBLE_AMMO, 20)
                );

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        15,
                        2,
                        remaining -> ConsumptionOutcome.confirmed(
                                Math.min(remaining, 3)
                        ),
                        remaining -> BackpackAmmoService
                                .consumeThroughHandlers(
                                        remaining,
                                        gun(),
                                        visitor -> visitor.test(backpack)
                                ),
                        remaining -> CuriosAmmoService.consumeHandlerOutcome(
                                curios,
                                gun(),
                                remaining
                        )
                );

        assertEquals(15, outcome.consumed());
        assertEquals(ConsumptionStatus.CONFIRMED, outcome.status());
        assertEquals(0, backpack.getStackInSlot(0).getCount());
        assertEquals(14, curios.getStackInSlot(0).getCount());
    }

    @Test
    void zeroConsumptionContinuesToLaterSources() {
        BackpackAmmoServiceTest.FakeHandler curios =
                new BackpackAmmoServiceTest.FakeHandler(
                        new ItemStack(COMPATIBLE_AMMO, 6)
                );

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        6,
                        0,
                        remaining -> ConsumptionOutcome.confirmed(0),
                        remaining -> ConsumptionOutcome.confirmed(0),
                        remaining -> CuriosAmmoService.consumeHandlerOutcome(
                                curios,
                                gun(),
                                remaining
                        )
                );

        assertEquals(6, outcome.consumed());
        assertEquals(0, curios.getStackInSlot(0).getCount());
    }

    @Test
    void finalisationFailureAfterConsumptionKeepsTheConfirmedRounds() {
        BackpackAmmoServiceTest.FakeHandler backpack =
                new BackpackAmmoServiceTest.FakeHandler(
                        new ItemStack(COMPATIBLE_AMMO, 4)
                );
        List<Integer> laterRequests = new ArrayList<>();

        ConsumptionOutcome outcome =
                AmmoConsumptionOrchestrator.consumeRemaining(
                        10,
                        0,
                        remaining -> BackpackAmmoService
                                .consumeThroughHandlers(
                                        remaining,
                                        gun(),
                                        visitor -> {
                                            visitor.test(backpack);
                                            throw new IllegalStateException(
                                                    "finalisation failed"
                                            );
                                        }
                                ),
                        remaining -> {
                            laterRequests.add(remaining);
                            return ConsumptionOutcome.confirmed(0);
                        }
                );

        assertEquals(
                4,
                outcome.consumed(),
                "the rounds confirmed before the failure stay recorded"
        );
        assertEquals(ConsumptionStatus.STOPPED_CONFIRMED, outcome.status());
        assertTrue(
                laterRequests.isEmpty(),
                "the failing request must not be redone through another source"
        );
    }

    /**
     * Simulates a container/handler whose next slot loses linkage in the
     * middle of a mutation pass.
     */
    private static final class LinkageFailingHandler
            implements IItemHandler {
        private final ItemStack stack =
                new ItemStack(COMPATIBLE_AMMO, 4);

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return this.stack;
        }

        @Override
        public @NotNull ItemStack insertItem(
                int slot,
                @NotNull ItemStack stack,
                boolean simulate
        ) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            throw new NoSuchMethodError(
                    "sophisticated ABI changed mid mutation"
            );
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(
                int slot,
                @NotNull ItemStack stack
        ) {
            return false;
        }
    }
}

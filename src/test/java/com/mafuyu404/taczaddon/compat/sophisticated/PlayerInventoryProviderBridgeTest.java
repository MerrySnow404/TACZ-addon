package com.mafuyu404.taczaddon.compat.sophisticated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural bridge tests: the reflective {@code runOnBackpacks} lookup must accept both the
 * historical {@code void}-returning and the current {@code boolean}-returning ABI without branching
 * on the return type.
 */
class PlayerInventoryProviderBridgeTest {

    @Test
    void bridgeAcceptsVoidReturningProvider() {
        List<String> visits = new ArrayList<>();

        PlayerInventoryProviderBridge bridge = newBridge(VoidReturningProvider.class);
        bridge.forEachBackpack(null, visitor(visits));

        assertEquals(2, visits.size());
        assertTrue(visits.get(0).startsWith("main|uuid-void|0|"));
        assertTrue(visits.get(1).startsWith("offhand|uuid-void|1|"));
    }

    @Test
    void bridgeAcceptsBooleanReturningProvider() {
        List<String> visits = new ArrayList<>();

        PlayerInventoryProviderBridge bridge = newBridge(BooleanReturningProvider.class);
        bridge.forEachBackpack(null, visitor(visits));

        assertEquals(2, visits.size());
        assertTrue(visits.get(0).startsWith("main|uuid-bool|0|"));
        assertTrue(visits.get(1).startsWith("offhand|uuid-bool|1|"));
    }

    @Test
    void linkageErrorFromVisitorPropagatesToGuard() {
        PlayerInventoryProviderBridge bridge = newBridge(VoidReturningProvider.class);

        assertThrows(
                NoSuchMethodError.class,
                () ->
                        bridge.forEachBackpack(
                                null,
                                (backpack, name, id, slot) -> {
                                    throw new NoSuchMethodError(
                                            "runOnBackpacks(Player, Consumer)V");
                                }));
    }

    @Test
    void rejectsConsumerWithChangedIdentifierType() {
        SophisticatedCompatibilityException exception =
                assertThrows(
                        SophisticatedCompatibilityException.class,
                        () -> newBridge(WrongIdentifierTypeProvider.class));

        assertTrue(exception.getMessage().contains("PlayerInventoryProvider bridge contract"));
    }

    @Test
    void rejectsConsumerWithChangedReturnType() {
        SophisticatedCompatibilityException exception =
                assertThrows(
                        SophisticatedCompatibilityException.class,
                        () -> newBridge(WrongConsumerReturnProvider.class));

        assertTrue(exception.getMessage().contains("unexpected return type"));
    }

    @Test
    void rejectsRunOnBackpacksWithUnsupportedReturnType() {
        SophisticatedCompatibilityException exception =
                assertThrows(
                        SophisticatedCompatibilityException.class,
                        () -> newBridge(WrongRunReturnProvider.class));

        assertTrue(exception.getMessage().contains("unsupported return type"));
    }

    private static PlayerInventoryProviderBridge newBridge(Class<?> providerFixture) {
        return new PlayerInventoryProviderBridge(
                PlayerInventoryProviderBridgeTest.class.getClassLoader(),
                providerFixture.getName());
    }

    private static PlayerInventoryProviderBridge.BackpackVisitor visitor(List<String> visits) {
        return (backpack, inventoryName, identifier, slot) -> {
            visits.add(
                    inventoryName
                            + "|"
                            + identifier
                            + "|"
                            + slot
                            + "|"
                            + backpack.getItem().toString());
            return false;
        };
    }

    /**
     * Structural stand-in for Sophisticated Backpacks &lt;= 3.25.x whose {@code runOnBackpacks}
     * returns void.
     */
    static final class VoidReturningProvider {

        public static VoidReturningProvider get() {
            return new VoidReturningProvider();
        }

        public void runOnBackpacks(Player player, BackpackInventorySlotConsumer consumer) {
            consumer.accept(new ItemStack(Items.STONE), "main", "uuid-void", 0);
            consumer.accept(new ItemStack(Items.DIRT), "offhand", "uuid-void", 1);
        }

        public interface BackpackInventorySlotConsumer {
            boolean accept(
                    ItemStack backpack, String inventoryHandlerName, String identifier, int slot);
        }
    }

    /**
     * Structural stand-in for Sophisticated Backpacks 3.26.x whose {@code runOnBackpacks} returns
     * boolean.
     */
    static final class BooleanReturningProvider {

        public static BooleanReturningProvider get() {
            return new BooleanReturningProvider();
        }

        public boolean runOnBackpacks(Player player, BackpackInventorySlotConsumer consumer) {
            boolean first = consumer.accept(new ItemStack(Items.STONE), "main", "uuid-bool", 0);
            boolean second = consumer.accept(new ItemStack(Items.DIRT), "offhand", "uuid-bool", 1);
            return first || second;
        }

        public interface BackpackInventorySlotConsumer {
            boolean accept(
                    ItemStack backpack, String inventoryHandlerName, String identifier, int slot);
        }
    }

    /**
     * Simulates a future Sophisticated version changing:
     *
     * <p>accept(ItemStack, String, String, int)
     *
     * <p>into:
     *
     * <p>accept(ItemStack, String, Object, int)
     *
     * <p>The bridge must reject this during construction instead of discovering it later through a
     * ClassCastException on a client tick.
     */
    static final class WrongIdentifierTypeProvider {

        public static WrongIdentifierTypeProvider get() {
            return new WrongIdentifierTypeProvider();
        }

        public void runOnBackpacks(Player player, BackpackInventorySlotConsumer consumer) {}

        public interface BackpackInventorySlotConsumer {
            boolean accept(
                    ItemStack backpack, String inventoryHandlerName, Object identifier, int slot);
        }
    }

    /** Simulates a future callback ABI changing boolean accept(...) to void. */
    static final class WrongConsumerReturnProvider {

        public static WrongConsumerReturnProvider get() {
            return new WrongConsumerReturnProvider();
        }

        public void runOnBackpacks(Player player, BackpackInventorySlotConsumer consumer) {}

        public interface BackpackInventorySlotConsumer {
            void accept(
                    ItemStack backpack, String inventoryHandlerName, String identifier, int slot);
        }
    }

    /**
     * Simulates a future runOnBackpacks generation whose return type is neither the historical void
     * nor the known boolean contract.
     */
    static final class WrongRunReturnProvider {

        public static WrongRunReturnProvider get() {
            return new WrongRunReturnProvider();
        }

        public int runOnBackpacks(Player player, BackpackInventorySlotConsumer consumer) {
            return 0;
        }

        public interface BackpackInventorySlotConsumer {
            boolean accept(
                    ItemStack backpack, String inventoryHandlerName, String identifier, int slot);
        }
    }
}

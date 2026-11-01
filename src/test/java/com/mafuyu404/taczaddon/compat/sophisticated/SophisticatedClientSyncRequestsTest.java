package com.mafuyu404.taczaddon.compat.sophisticated;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsPayload;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.RequestLinkedStorageContentsPayload;
import org.junit.jupiter.api.Test;
import net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production per-pass router with a recording native-payload sender. */
class SophisticatedClientSyncRequestsTest {
    private static final ItemStackReference REF = new ItemStackReference(ItemStackReference.EQUIPMENT_HANDLER, 4, "curios", "back");
    @Test void currentCorePayloadAndClientSyncContractAreAvailable() {
        assertEquals("sophisticatedcore", new RequestLinkedStorageContentsPayload(new UUID(0, 1), -1L, REF)
                .type().id().getNamespace());
        assertTrue(new SophisticatedBackpacksIntegrationImpl().probeClientSync());
    }
    private static final UUID A = new UUID(0, 1);
    private final List<CustomPacketPayload> sent = new ArrayList<>();
    private final List<ItemStack> registered = new ArrayList<>();
    private SophisticatedBackpacksIntegrationImpl.ClientSyncRequests pass() {
        return new SophisticatedBackpacksIntegrationImpl.ClientSyncRequests(sent::add, registered::add);
    }
    @Test void worldJoinAndReconnectBootstrapBothNativeDomainsWithoutUiActions() {
        var planner = new com.mafuyu404.taczaddon.event.BackpackCacheTickPlanner();
        Object player = new Object();
        for (int join = 0; join < 2; join++) {
            assertEquals(com.mafuyu404.taczaddon.event.BackpackCacheTickPlanner.Action.BOOTSTRAP_REQUEST,
                    planner.tick(player, new Object(), false, 0L));
            // Record the actual native payloads emitted by the production router.
            // This is a headless join/reconnect test, not a live network session.
            var requests = new SophisticatedBackpacksIntegrationImpl.ClientSyncRequests(payload -> {
                sent.add(payload);
                com.mojang.logging.LogUtils.getLogger().info("Headless world bootstrap emitted {}", payload);
            }, registered::add);
            assertFalse(requests.request(false, A, ItemStack.EMPTY, REF));
            assertFalse(requests.request(true, A, ItemStack.EMPTY, REF));
            assertFalse(requests.request(true, A, ItemStack.EMPTY, REF));
        }
        assertEquals(List.of(new RequestBackpackInventoryContentsPayload(A, REF),
                new RequestLinkedStorageContentsPayload(A, -1L, REF),
                new RequestBackpackInventoryContentsPayload(A, REF),
                new RequestLinkedStorageContentsPayload(A, -1L, REF)), sent);
    }
    @Test void ordinaryBackpackRequestsOnlyNormalContents() {
        ItemStack stack = new ItemStack(Items.LEATHER);
        assertFalse(pass().request(false, A, stack, REF));
        assertEquals(List.of(new RequestBackpackInventoryContentsPayload(A, REF)), sent);
        assertEquals(List.of(stack), registered);
        assertSame(stack, registered.getFirst());
    }
    @Test void duplicateOrdinaryStorageRequestsOnce() {
        var pass = pass();
        assertFalse(pass.request(false, A, ItemStack.EMPTY, REF));
        assertFalse(pass.request(false, A, ItemStack.EMPTY, REF));
        assertEquals(List.of(new RequestBackpackInventoryContentsPayload(A, REF)), sent);
        assertEquals(1, registered.size());
    }
    @Test void linkedEndpointRequestsOnlyLinkedContents() {
        assertFalse(pass().request(true, A, ItemStack.EMPTY, REF));
        assertEquals(List.of(new RequestLinkedStorageContentsPayload(A, -1L, REF)), sent);
        assertTrue(registered.isEmpty());
    }
    @Test void duplicateLinkedGroupRequestsOnce() {
        var pass = pass();
        assertFalse(pass.request(true, A, ItemStack.EMPTY, REF));
        assertFalse(pass.request(true, A, ItemStack.EMPTY, REF));
        assertEquals(List.of(new RequestLinkedStorageContentsPayload(A, -1L, REF)), sent);
        assertTrue(registered.isEmpty());
    }
    @Test void equalUuidsRemainIndependentNamespaces() {
        var pass = pass();
        assertFalse(pass.request(false, A, ItemStack.EMPTY, REF));
        assertFalse(pass.request(true, A, ItemStack.EMPTY, REF));
        assertEquals(List.of(new RequestBackpackInventoryContentsPayload(A, REF),
                new RequestLinkedStorageContentsPayload(A, -1L, REF)), sent);
    }
    @Test void missingEndpointDataSkipsStackAndContinuesEnumeration() {
        var pass = pass();
        assertFalse(pass.request(true, null, ItemStack.EMPTY, REF));
        assertTrue(sent.isEmpty()); assertTrue(registered.isEmpty());
        assertFalse(pass.request(false, A, ItemStack.EMPTY, REF));
        assertFalse(pass.request(true, A, ItemStack.EMPTY, REF));
        assertEquals(2, sent.size());
    }
    @Test void subsequentWorldBootstrapRequestsFreshFullSnapshot() {
        assertFalse(pass().request(true, A, ItemStack.EMPTY, REF));
        assertFalse(pass().request(true, A, ItemStack.EMPTY, REF));
        assertEquals(List.of(new RequestLinkedStorageContentsPayload(A, -1L, REF),
                new RequestLinkedStorageContentsPayload(A, -1L, REF)), sent);
    }
}

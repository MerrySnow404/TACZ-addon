package com.mafuyu404.taczaddon.compat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which Sophisticated Backpacks / Sophisticated Core generation is installed.
 *
 * <p>Only the 3.26 generation ships the linked-storage API. The verified 3.26
 * contract (Sophisticated Core 1.5.0.2316) is:
 *
 * <ul>
 *     <li>{@code LinkedStorageStackLifecycle.classifyEndpoint(ItemStack) ->
 *     LinkedStorageEndpointStackState}</li>
 *     <li>{@code LinkedStorageStackData.getEndpoint(ItemStack) ->
 *     LinkedStorageEndpointData}</li>
 *     <li>{@code LinkedStorageEndpointData.groupId() -> UUID}</li>
 *     <li>{@code LinkedStorageEndpointStackState.ENDPOINT}</li>
 * </ul>
 *
 * <p>The older 3.24.x line has none of these classes; it only provides the
 * ordinary backpack API, which is verified member by member against the real
 * 3.24.x artifacts.
 */
public enum SophisticatedBackpackGeneration {
    /** No Sophisticated backpack API is visible at all. */
    ABSENT,
    /** Verified ordinary-only 3.24.x generation. */
    ORDINARY_ONLY,
    /** Verified 3.26 linked-storage generation. */
    LINKED_STORAGE,
    /**
     * Sophisticated is present, but neither the ordinary nor the linked
     * contract could be verified. Callers must fail closed instead of
     * guessing.
     */
    UNKNOWN;

    public static final String CORE_MOD_ID = "sophisticatedcore";
    public static final String BACKPACKS_MOD_ID = "sophisticatedbackpacks";

    private static final String BACKPACKS =
            "net.p3pp3rf1y.sophisticatedbackpacks.";
    private static final String CORE =
            "net.p3pp3rf1y.sophisticatedcore.";

    private static final String WRAPPER =
            BACKPACKS + "backpack.wrapper.IBackpackWrapper";
    private static final String WRAPPER_NOOP = WRAPPER + "$Noop";
    private static final String PLAYER_INVENTORY_PROVIDER =
            BACKPACKS + "util.PlayerInventoryProvider";
    private static final String SLOT_CONSUMER =
            PLAYER_INVENTORY_PROVIDER
                    + "$BackpackInventorySlotConsumer";
    private static final String BACKPACK_STORAGE =
            BACKPACKS + "backpack.BackpackStorage";
    private static final String BACKPACK_CONTEXT_ITEM =
            BACKPACKS + "common.gui.BackpackContext$Item";
    private static final String PACKET_HANDLER =
            BACKPACKS + "network.SBPPacketHandler";
    private static final String REQUEST_CONTENTS_MESSAGE =
            BACKPACKS + "network.RequestBackpackInventoryContentsMessage";
    private static final String CONTENTS_MESSAGE =
            BACKPACKS + "network.BackpackContentsMessage";
    private static final String INVENTORY_HANDLER =
            CORE + "inventory.InventoryHandler";

    private static final String LINKED_LIFECYCLE =
            CORE + "linkedstorage.LinkedStorageStackLifecycle";
    private static final String LINKED_STACK_DATA =
            CORE + "linkedstorage.LinkedStorageStackData";
    private static final String LINKED_ENDPOINT_DATA =
            CORE + "linkedstorage.LinkedStorageEndpointData";
    private static final String LINKED_ENDPOINT_STATE =
            CORE + "linkedstorage.LinkedStorageEndpointStackState";

    private static final String ITEM_STACK_DESCRIPTOR =
            "Lnet/minecraft/world/item/ItemStack;";
    private static final String ENDPOINT_DATA_DESCRIPTOR =
            "Lnet/p3pp3rf1y/sophisticatedcore/linkedstorage/LinkedStorageEndpointData;";
    private static final String ENDPOINT_STATE_DESCRIPTOR =
            "Lnet/p3pp3rf1y/sophisticatedcore/linkedstorage/LinkedStorageEndpointStackState;";

    private static final List<String> LINKED_ANCHORS = List.of(
            LINKED_LIFECYCLE,
            LINKED_STACK_DATA,
            LINKED_ENDPOINT_DATA,
            LINKED_ENDPOINT_STATE
    );

    /**
     * Any class that proves some Sophisticated backpack API is present, even
     * when its descriptors do not match a known generation. Presence of any of
     * these means "UNKNOWN", never "ABSENT".
     */
    private static final List<String> API_FOOTPRINT_ANCHORS = List.of(
            WRAPPER,
            WRAPPER_NOOP,
            PLAYER_INVENTORY_PROVIDER,
            SLOT_CONSUMER,
            BACKPACK_STORAGE,
            BACKPACK_CONTEXT_ITEM,
            PACKET_HANDLER,
            REQUEST_CONTENTS_MESSAGE,
            CONTENTS_MESSAGE,
            INVENTORY_HANDLER,
            LINKED_LIFECYCLE,
            LINKED_STACK_DATA,
            LINKED_ENDPOINT_DATA,
            LINKED_ENDPOINT_STATE
    );

    /**
     * Every Sophisticated entry point the ordinary compatibility implementation
     * actually calls, expressed as exact member descriptors.
     *
     * <p>Derived from the compiled {@code SophisticatedBackpacksCompatInner}
     * constant pool and verified against the real 3.24.x / 3.26.x artifacts by
     * {@code SophisticatedBinaryFixtureTest}. Lookup is hierarchy-aware, so a
     * member may live on a supertype.
     *
     * <p>{@code InventoryHandler#getSlots()} is deliberately absent: it comes
     * from Forge's {@code IItemHandler}, a hard runtime dependency that is not
     * part of the Sophisticated contract.
     */
    private static final Map<String, List<ApiShapeProbe.Member>>
            ORDINARY_BACKPACK_CONTRACT = ordinaryBackpackContract();

    public static SophisticatedBackpackGeneration detect(
            ApiShapeProbe.ClassBytes source
    ) {
        if (!hasAnyApiFootprint(source)) {
            return ABSENT;
        }
        boolean ordinary = matchesVerifiedOrdinaryContract(source);
        boolean linked = matchesVerifiedLinkedContract(source);
        if (ordinary && linked) {
            return LINKED_STORAGE;
        }
        if (ordinary && !hasAnyLinkedAnchor(source)) {
            return ORDINARY_ONLY;
        }
        /*
         * Some API footprint is visible, but no complete known contract holds:
         * partial or renamed classes, incompatible descriptors, a linked shape
         * without the ordinary shape it depends on, and so on. Partial ABI is
         * never "absent".
         */
        return UNKNOWN;
    }

    /** The exact ordinary-backpack ABI this addon links against. */
    public static Map<String, List<ApiShapeProbe.Member>>
    ordinaryContract() {
        return ORDINARY_BACKPACK_CONTRACT;
    }

    /**
     * True only when every referenced ordinary class exists and every
     * referenced member matches its exact descriptor. A generation that merely
     * "has two classes" is not accepted.
     */
    static boolean matchesVerifiedOrdinaryContract(
            ApiShapeProbe.ClassBytes source
    ) {
        for (Map.Entry<String, List<ApiShapeProbe.Member>> entry
                : ORDINARY_BACKPACK_CONTRACT.entrySet()) {
            if (entry.getKey().equals(REQUEST_CONTENTS_MESSAGE)) {
                if (!SophisticatedBackpackContentsRequest.matches(source)) return false;
                continue;
            }
            if (!ApiShapeProbe.satisfies(
                    source,
                    entry.getKey(),
                    entry.getValue()
            )) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, List<ApiShapeProbe.Member>>
    ordinaryBackpackContract() {
        Map<String, List<ApiShapeProbe.Member>> contract =
                new LinkedHashMap<>();
        contract.put(
                WRAPPER,
                List.of(
                        ApiShapeProbe.method(
                                "getInventoryHandler",
                                "()" + objectType(INVENTORY_HANDLER)
                        ),
                        ApiShapeProbe.method(
                                "getContentsUuid",
                                "()Ljava/util/Optional;"
                        ),
                        ApiShapeProbe.method(
                                "onContentsNbtUpdated",
                                "()V"
                        ),
                        ApiShapeProbe.method(
                                "getBackpack",
                                "()Lnet/minecraft/world/item/ItemStack;"
                        )
                )
        );
        contract.put(
                WRAPPER_NOOP,
                List.of(ApiShapeProbe.field(
                        "INSTANCE",
                        objectType(WRAPPER_NOOP)
                ))
        );
        contract.put(
                PLAYER_INVENTORY_PROVIDER,
                List.of(
                        ApiShapeProbe.method(
                                "get",
                                "()" + objectType(
                                        PLAYER_INVENTORY_PROVIDER
                                )
                        ),
                        ApiShapeProbe.method(
                                "runOnBackpacks",
                                "(Lnet/minecraft/world/entity/player/Player;"
                                        + objectType(SLOT_CONSUMER) + ")V"
                        )
                )
        );
        contract.put(
                BACKPACK_STORAGE,
                List.of(
                        ApiShapeProbe.method(
                                "get",
                                "()" + objectType(BACKPACK_STORAGE)
                        ),
                        ApiShapeProbe.method(
                                "getOrCreateBackpackContents",
                                "(Ljava/util/UUID;)"
                                        + "Lnet/minecraft/nbt/CompoundTag;"
                        )
                )
        );
        contract.put(
                BACKPACK_CONTEXT_ITEM,
                List.of(
                        ApiShapeProbe.method(
                                "<init>",
                                "(Ljava/lang/String;Ljava/lang/String;I)V"
                        ),
                        ApiShapeProbe.method(
                                "getBackpackWrapper",
                                "(Lnet/minecraft/world/entity/player/Player;)"
                                        + objectType(WRAPPER)
                        ),
                        ApiShapeProbe.method(
                                "canInteractWith",
                                "(Lnet/minecraft/world/entity/player/Player;)Z"
                        )
                )
        );
        contract.put(
                PACKET_HANDLER,
                List.of(
                        ApiShapeProbe.field(
                                "INSTANCE",
                                objectType(PACKET_HANDLER)
                        ),
                        ApiShapeProbe.method(
                                "sendToServer",
                                "(Ljava/lang/Object;)V"
                        ),
                        ApiShapeProbe.method(
                                "sendToClient",
                                "(Lnet/minecraft/server/level/ServerPlayer;"
                                        + "Ljava/lang/Object;)V"
                        )
                )
        );
        contract.put(
                REQUEST_CONTENTS_MESSAGE,
                List.of(ApiShapeProbe.method(
                        "<init>",
                        "(Ljava/util/UUID;)V"
                ))
        );
        contract.put(
                CONTENTS_MESSAGE,
                List.of(ApiShapeProbe.method(
                        "<init>",
                        "(Ljava/util/UUID;"
                                + "Lnet/minecraft/nbt/CompoundTag;)V"
                ))
        );
        contract.put(
                INVENTORY_HANDLER,
                List.of(
                        ApiShapeProbe.field(
                                "INVENTORY_TAG",
                                "Ljava/lang/String;"
                        ),
                        ApiShapeProbe.method(
                                "getStackInSlot",
                                "(I)Lnet/minecraft/world/item/ItemStack;"
                        ),
                        ApiShapeProbe.method(
                                "setStackInSlot",
                                "(ILnet/minecraft/world/item/ItemStack;)V"
                        ),
                        ApiShapeProbe.method(
                                "saveInventory",
                                "()V"
                        ),
                        ApiShapeProbe.method(
                                "serializeNBT",
                                "()Lnet/minecraft/nbt/CompoundTag;"
                        )
                )
        );
        return Map.copyOf(contract);
    }

    private static String objectType(String binaryName) {
        return "L" + binaryName.replace('.', '/') + ";";
    }

    public boolean linkedStorageSupported() {
        return this == LINKED_STORAGE;
    }

    public boolean ordinaryBackpacksSupported() {
        return this == LINKED_STORAGE || this == ORDINARY_ONLY;
    }

    private static boolean matchesVerifiedLinkedContract(
            ApiShapeProbe.ClassBytes source
    ) {
        return ApiShapeProbe.satisfies(
                source,
                LINKED_LIFECYCLE,
                List.of(ApiShapeProbe.method(
                        "classifyEndpoint",
                        "(" + ITEM_STACK_DESCRIPTOR + ")"
                                + ENDPOINT_STATE_DESCRIPTOR
                ))
        ) && ApiShapeProbe.satisfies(
                source,
                LINKED_STACK_DATA,
                List.of(ApiShapeProbe.method(
                        "getEndpoint",
                        "(" + ITEM_STACK_DESCRIPTOR + ")"
                                + ENDPOINT_DATA_DESCRIPTOR
                ))
        ) && ApiShapeProbe.satisfies(
                source,
                LINKED_ENDPOINT_DATA,
                List.of(ApiShapeProbe.method(
                        "groupId",
                        "()Ljava/util/UUID;"
                ))
        ) && ApiShapeProbe.hasField(
                source,
                LINKED_ENDPOINT_STATE,
                "ENDPOINT",
                ENDPOINT_STATE_DESCRIPTOR
        );
    }

    private static boolean hasAnyLinkedAnchor(
            ApiShapeProbe.ClassBytes source
    ) {
        for (String anchor : LINKED_ANCHORS) {
            if (ApiShapeProbe.hasClass(source, anchor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyApiFootprint(
            ApiShapeProbe.ClassBytes source
    ) {
        for (String anchor : API_FOOTPRINT_ANCHORS) {
            if (ApiShapeProbe.hasClass(source, anchor)) {
                return true;
            }
        }
        return false;
    }
}

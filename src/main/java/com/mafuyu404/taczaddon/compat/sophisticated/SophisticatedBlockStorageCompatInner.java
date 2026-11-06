package com.mafuyu404.taczaddon.compat.sophisticated;

import net.p3pp3rf1y.sophisticatedcore.linkedstorage.ILinkedStorageBlockEndpoint;

import java.util.UUID;

final class SophisticatedBlockStorageCompatInner {
    private SophisticatedBlockStorageCompatInner() {}

    static UUID groupId(Object blockEntity) {
        if (!(blockEntity instanceof ILinkedStorageBlockEndpoint endpoint)) return null;
        var data = endpoint.getLinkedStorageEndpointData();
        if (data == null) return null;
        if (data.groupId() == null) {
            throw new IllegalStateException("Linked storage endpoint has no group identity");
        }
        return data.groupId();
    }
}

package com.mafuyu404.taczaddon.compat.sophisticated;

import java.util.Optional;
import java.util.UUID;

record EndpointResolution(
        ResolutionKind kind,
        UUID groupId
) {
    static EndpointResolution notLinked() {
        return new EndpointResolution(
                ResolutionKind.NOT_LINKED,
                null
        );
    }

    static EndpointResolution linked(
            UUID groupId
    ) {
        return new EndpointResolution(
                ResolutionKind.LINKED,
                groupId
        );
    }

    static EndpointResolution malformedLinked() {
        return new EndpointResolution(
                ResolutionKind.MALFORMED_LINKED,
                null
        );
    }

    static EndpointResolution unavailableBridge() {
        return new EndpointResolution(
                ResolutionKind.BRIDGE_UNAVAILABLE,
                null
        );
    }

    boolean linked() {
        return kind == ResolutionKind.LINKED
                || kind == ResolutionKind.MALFORMED_LINKED;
    }

    boolean bridgeUnavailable() {
        return kind
                == ResolutionKind.BRIDGE_UNAVAILABLE;
    }

    Optional<UUID> groupIdOptional() {
        return Optional.ofNullable(
                groupId
        );
    }
}

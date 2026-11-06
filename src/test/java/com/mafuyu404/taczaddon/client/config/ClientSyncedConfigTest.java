package com.mafuyu404.taczaddon.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSyncedConfigTest {
    @AfterEach
    void reset() {
        ClientSyncedConfig.resetToSafeDefaults();
    }

    @Test
    void attachmentDetailFailsClosedAndResetsBetweenServers() {
        ClientSyncedConfig.resetToSafeDefaults();
        assertFalse(ClientSyncedConfig.showAttachmentDetail());
        ClientSyncedConfig.setShowAttachmentDetail(true);
        assertTrue(ClientSyncedConfig.showAttachmentDetail());
        ClientSyncedConfig.resetToSafeDefaults();
        assertFalse(ClientSyncedConfig.showAttachmentDetail());
    }

    @Test
    void resetFailsClosedAcrossConnections() {
        ClientSyncedConfig.setLiberateAttachment(true);
        assertTrue(ClientSyncedConfig.liberateAttachment());

        ClientSyncedConfig.resetToSafeDefaults();

        assertFalse(ClientSyncedConfig.liberateAttachment());
    }
}

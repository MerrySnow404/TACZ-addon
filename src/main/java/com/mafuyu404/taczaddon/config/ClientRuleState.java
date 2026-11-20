package com.mafuyu404.taczaddon.config;

public final class ClientRuleState {
    private ClientRuleState() {}

    private static boolean liberateAttachment;

    public static void clear() {
        liberateAttachment = false;
    }

    public static boolean isLiberateAttachment() {
        return liberateAttachment;
    }

    public static void setLiberateAttachment(boolean liberateAttachment) {
        ClientRuleState.liberateAttachment = liberateAttachment;
    }
}

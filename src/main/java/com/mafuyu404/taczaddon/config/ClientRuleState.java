package com.mafuyu404.taczaddon.config;

import java.util.*;

public final class ClientRuleState {
    private ClientRuleState() {}

    private static boolean liberateAttachment;
    private static boolean showAttachmentDetail;

    public static void clear() {
        liberateAttachment = false;
        showAttachmentDetail = false;
    }

    public static boolean isLiberateAttachment() {
        return liberateAttachment;
    }

    public static boolean isShowAttachmentDetail() {
        return showAttachmentDetail;
    }

    public static void setRuleState(boolean liberateAttachment, boolean showAttachmentDetail) {
        ClientRuleState.liberateAttachment = liberateAttachment;
        ClientRuleState.showAttachmentDetail = showAttachmentDetail;
    }
}

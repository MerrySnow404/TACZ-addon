package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.refit.RefitSourceResolver;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public interface GunRefitScreenAccess {
    void taczaddon$rebuildLiberatedAttachmentButtons();

    GunSmithSourceScreenAccess.AcceptResult
    taczaddon$acceptRefitSourceSnapshot(
            long requestId,
            List<RefitSourceResolver.RefitExternalCandidate> candidates
    );

    void taczaddon$requestRefitSourceRefresh();

    void taczaddon$tickRefitSourceRefresh();

    void taczaddon$onRefitScreenInit();

    void taczaddon$rebuildRefitCandidateButtons();
}

package com.mafuyu404.taczaddon.client.refit;

import com.mafuyu404.taczaddon.refit.RefitSourceResolver;
import com.mafuyu404.taczaddon.client.gunsmith.GunSmithSourceScreenAccess;
import com.mafuyu404.taczaddon.storage.api.RefitSourceLocator;
import com.mafuyu404.taczaddon.testutil.MinecraftTestBootstrap;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefitExternalSourceStateTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.prepare();
    }

    @Test
    void unchangedSnapshotReturnsUnchanged() {
        RefitExternalSourceState state = state();

        state.requestSourceRefresh();
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSnapshot(1L, List.of())
        );

        state.requestSourceRefresh();
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UNCHANGED,
                state.acceptSnapshot(2L, List.of())
        );
    }

    @Test
    void changedSnapshotReturnsUpdated() {
        RefitExternalSourceState state = state();

        state.requestSourceRefresh();
        state.acceptSnapshot(1L, List.of());

        state.requestSourceRefresh();
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSnapshot(
                        2L,
                        List.of(candidate(1))
                )
        );
    }

    @Test
    void staleResponseDoesNotClearNewerPendingRequest() {
        RefitExternalSourceState state = state();

        state.requestSourceRefresh();
        state.requestSourceRefresh();

        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.REJECTED,
                state.acceptSnapshot(1L, List.of())
        );
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSnapshot(
                        2L,
                        List.of(candidate(1))
                )
        );
    }

    @Test
    void timeoutReissuesRequest() {
        AtomicInteger sends = new AtomicInteger();
        RefitExternalSourceState state = new RefitExternalSourceState(
                requestId -> sends.incrementAndGet()
        );

        state.requestSourceRefresh();
        for (int tick = 0; tick < 100; tick++) {
            state.tickSourceRefresh();
        }

        assertEquals(2, sends.get());
        assertEquals(
                GunSmithSourceScreenAccess.AcceptResult.UPDATED,
                state.acceptSnapshot(2L, List.of(candidate(1)))
        );
    }

    private static RefitSourceResolver.RefitExternalCandidate candidate(
            int slot
    ) {
        return new RefitSourceResolver.RefitExternalCandidate(
                Objects.requireNonNull(
                        ResourceLocation.tryBuild("tacz", "scope")
                ),
                AttachmentType.SCOPE,
                new RefitSourceLocator(
                        Level.OVERWORLD,
                        new BlockPos(1, 64, 1),
                        slot
                ),
                new ItemStack(Items.IRON_INGOT)
        );
    }

    private static RefitExternalSourceState state() {
        return new RefitExternalSourceState(requestId -> {
        });
    }
}

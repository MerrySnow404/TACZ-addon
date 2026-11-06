package com.mafuyu404.taczaddon.refit;

import java.util.Objects;

/**
 * Enforces the ownership commit boundary for one successful external refit.
 *
 * <p>{@link Steps#finalizeOwnership()} is the boundary itself and must finish
 * before any nonessential post-commit effect runs. Post-commit failures are
 * reported individually, but every remaining post-commit step is still
 * attempted so authoritative synchronization and screen refresh are not
 * skipped.
 */
public final class RefitExternalInstallCommitSequence {

    public interface Steps {
        void finalizeOwnership();

        void postChangeEvent();

        void dropAmmo();

        void synchronizeAuthoritativeState();

        void refreshScreen();
    }

    @FunctionalInterface
    public interface FailureListener {
        void onPostCommitFailure(Throwable failure);
    }

    private RefitExternalInstallCommitSequence() {
    }

    /**
     * @return true when every post-commit step completed without failure
     */
    public static boolean complete(
            Steps steps,
            FailureListener failureListener
    ) {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(
                failureListener,
                "failureListener"
        );

        /*
         * Exceptions from this step are pre-boundary failures. The caller
         * still treats the transaction as committed and must not retry the
         * extraction or install.
         */
        steps.finalizeOwnership();

        boolean complete = true;
        complete &= runSafely(
                steps::postChangeEvent,
                failureListener
        );
        complete &= runSafely(
                steps::dropAmmo,
                failureListener
        );
        complete &= runSafely(
                steps::synchronizeAuthoritativeState,
                failureListener
        );
        complete &= runSafely(
                steps::refreshScreen,
                failureListener
        );
        return complete;
    }

    private static boolean runSafely(
            Runnable step,
            FailureListener failureListener
    ) {
        try {
            step.run();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            failureListener.onPostCommitFailure(failure);
            return false;
        }
    }
}
